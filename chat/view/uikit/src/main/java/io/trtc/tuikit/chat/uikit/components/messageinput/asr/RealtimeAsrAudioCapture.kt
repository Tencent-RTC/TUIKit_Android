package io.trtc.tuikit.chat.uikit.components.messageinput.asr

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.tencent.voiceai.TXRealtimeASR
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 实时识别专用的采集通路：由 VoiceAI SDK（`LiteAVSDK_VoiceAI`）负责麦克风采集、前处理与
 * Opus 编码，只用它的本地能力，不连它的云端 ASR：
 *
 * - `enableCloudAsrChannel=false`：不建 WebSocket，识别仍走 IMSDK 的 feedRealtimeAudioData；
 * - `enableLocalOpusOutput=true`：经 `onReceiveLocalOpus` 抛出 16kHz / 单声道 / 40ms 裸 Opus packet；
 * - `enableLocalPcmOutput=true`：经 `onReceiveLocalPcm` 抛出原始 PCM16，供音量动画用。
 *
 * 输入栏与 Demo 通话页共用这一路。默认 [Config.Recognition]；通话页用 [Config.VoiceCommunication]，
 * 采集与 TTS 播放同时进行，切到通话音量并打开声纹降噪。
 *
 * 这条通路与「录语音文件」的 [io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorder]
 * 是**并行**跑的两路采集：前者要的是可上行的实时流，后者要的是可发送的 m4a 文件。
 *
 * ## 线程
 *
 * SDK 的 native 层把回调派到主线程，这里一进回调就转给本实例独占的采集线程：
 * [onPacket] / [onPcmFrame] 都在采集线程上回调，且同一实例内不并发，[stop] 返回后不再回调。
 * [onVoiceprintActive] 留在 SDK 回调线程上。
 */
class RealtimeAsrAudioCapture(
    private val onPacket: (ByteArray) -> Unit,
    private val onPcmFrame: ((ShortArray) -> Unit)? = null,
    private val config: Config = Config.Recognition,
    /** 声纹注册完成、开始只保留该说话人（`onVoiceprintDenoiseStatus` 的 status 变为 Active）。 */
    private val onVoiceprintActive: (() -> Unit)? = null,
) {

    class Config(
        /** 采播同时进行时切到通话音量（SDK 默认媒体音量）。 */
        val useCallVolume: Boolean = false,
        /** 声纹降噪：自动用前 [voiceprintEnrollMs] 毫秒的人声注册声纹，之后只保留该说话人。 */
        val enableVoiceprintDenoise: Boolean = false,
        val voiceprintEnrollMs: Int = 3000,
        val threadName: String = "message-input-asr-capture",
    ) {
        companion object {
            /** 输入栏。 */
            val Recognition = Config()

            /** 通话：采播同时进行。 */
            val VoiceCommunication = Config(
                useCallVolume = true,
                enableVoiceprintDenoise = true,
                threadName = "ai-audio-capture",
            )
        }
    }

    private val running = AtomicBoolean(false)
    private var engine: TXRealtimeASR? = null
    private var worker: HandlerThread? = null

    @Volatile
    private var workerHandler: Handler? = null

    // 只在采集线程上访问：把 SDK 抛出的任意长度 PCM 重新切成 20ms 一帧
    private var pending = ShortArray(RealtimeAsrSpec.FRAME_SAMPLES * 4)
    private var pendingLength = 0

    @Volatile
    private var loggedPcmParams = false

    @Volatile
    private var loggedOpusParams = false

    /** 上一次声纹降噪状态，只在状态切进 Active 时通知一次。 */
    @Volatile
    private var voiceprintActive = false

    // SDK 内部只持有监听器的弱引用，必须由这里强引用住
    private val listener = object : TXRealtimeASR.Listener {
        override fun onExperimentalEvent(eventName: String?, params: String?, data: ByteArray?) {
            if (!running.get()) {
                return
            }
            if (eventName == EVENT_VOICEPRINT_STATUS) {
                handleVoiceprintStatus(params, data)
                return
            }
            if (data == null || data.isEmpty()) {
                return
            }
            when (eventName) {
                EVENT_LOCAL_OPUS, EVENT_LOCAL_PCM ->
                    workerHandler?.post { dispatchAudio(eventName, params, data) }
                else -> Unit
            }
        }

        override fun onRealtimeASRStarted(voiceId: String?) {
            Log.i(TAG, "voiceai started: $voiceId")
        }

        override fun onRealtimeASRStopped() {
            Log.i(TAG, "voiceai stopped")
        }

        override fun onRealtimeASRError(errorCode: Int, errorMsg: String?) {
            Log.w(TAG, "voiceai error: $errorCode $errorMsg")
        }
    }

    val isRunning: Boolean
        get() = running.get()

    fun effectSummary(): String {
        return "engine=voiceai callVolume=${config.useCallVolume} " +
            "voiceprint=${config.enableVoiceprintDenoise} pcm=${onPcmFrame != null}"
    }

    /**
     * 调用前必须已获得 RECORD_AUDIO 权限。
     *
     * @return 启动失败返回 false，调用方据此关掉实时识别，不要重试
     */
    fun start(): Boolean {
        if (running.get()) {
            return true
        }
        val asr = try {
            TXRealtimeASR()
        } catch (throwable: Throwable) {
            Log.w(TAG, "create TXRealtimeASR failed", throwable)
            return false
        }
        try {
            asr.addListener(listener)
            if (config.useCallVolume) {
                asr.callExperimentalAPI(API_CALL_VOLUME)
            }
            asr.callExperimentalAPI(API_DISABLE_CLOUD)
            asr.callExperimentalAPI(API_ENABLE_OPUS)
            if (onPcmFrame != null) {
                asr.callExperimentalAPI(API_ENABLE_PCM)
            }
            if (config.enableVoiceprintDenoise) {
                asr.callExperimentalAPI(voiceprintApi(config.voiceprintEnrollMs))
            }
            pendingLength = 0
            loggedPcmParams = false
            loggedOpusParams = false
            voiceprintActive = false
            startWorker()
            running.set(true)
            asr.startRealtimeASR(
                TXRealtimeASR.Params().apply {
                    // 云端通道已关，鉴权字段不会被使用
                    sdkAppId = LoginStore.shared.sdkAppID.toString()
                    userSig = ""
                    voiceId = UUID.randomUUID().toString()
                    sourceLanguage = ""
                    enableCustomCapture = false
                },
            )
        } catch (throwable: Throwable) {
            Log.w(TAG, "start voiceai capture failed", throwable)
            running.set(false)
            releaseEngine(asr)
            stopWorker()
            return false
        }
        engine = asr
        Log.i(TAG, "capture started: ${effectSummary()}")
        return true
    }

    /** 停止采集并释放资源。可重复调用。 */
    fun stop() {
        running.set(false)
        engine?.let { asr ->
            engine = null
            releaseEngine(asr)
        }
        stopWorker()
    }

    private fun releaseEngine(asr: TXRealtimeASR) {
        runCatching { asr.stopRealtimeASR() }.onFailure { Log.w(TAG, "stopRealtimeASR failed", it) }
        runCatching { asr.removeListener(listener) }
        runCatching { asr.destroy() }.onFailure { Log.w(TAG, "destroy failed", it) }
    }

    private fun startWorker() {
        val thread = HandlerThread(config.threadName).also { it.start() }
        worker = thread
        workerHandler = Handler(thread.looper)
    }

    /**
     * 退出采集线程并等它结束。必须等：通话恢复时会紧接着建新实例，旧线程若还在跑，
     * 两条线程会同时往上层回调 PCM / Opus，打乱帧顺序。
     */
    private fun stopWorker() {
        val thread = worker ?: return
        worker = null
        workerHandler = null
        thread.quit()
        if (thread != Thread.currentThread()) {
            runCatching { thread.join(JOIN_TIMEOUT_MS) }
            if (thread.isAlive) {
                Log.w(TAG, "capture thread still alive after ${JOIN_TIMEOUT_MS}ms")
            }
        }
    }

    /** 采集线程：把 SDK 事件分发给调用方。 */
    private fun dispatchAudio(eventName: String, params: String?, data: ByteArray) {
        if (!running.get()) {
            return
        }
        when (eventName) {
            EVENT_LOCAL_OPUS -> {
                if (!loggedOpusParams) {
                    loggedOpusParams = true
                    Log.i(TAG, "first opus packet on ${Thread.currentThread().name}: $params")
                }
                onPacket(data)
            }
            EVENT_LOCAL_PCM -> {
                if (!loggedPcmParams) {
                    loggedPcmParams = true
                    Log.i(TAG, "first pcm frame on ${Thread.currentThread().name}: $params")
                }
                deliverPcm(data)
            }
            else -> Unit
        }
    }

    /**
     * SDK 回调线程：`onVoiceprintDenoiseStatus`，params 形如 `{"status":"Active",...}`。
     * 只在状态从非 Active 切到 Active 时通知一次，避免重复上报时反复弹提示。
     */
    private fun handleVoiceprintStatus(params: String?, data: ByteArray?) {
        val raw = params?.takeIf { it.isNotBlank() }
            ?: data?.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8)
        Log.i(TAG, "voiceprint status: $raw")
        val status = runCatching { JSONObject(raw.orEmpty()).optString("status") }.getOrDefault("")
        val active = status.equals(VOICEPRINT_STATUS_ACTIVE, ignoreCase = true)
        val becameActive = active && !voiceprintActive
        voiceprintActive = active
        if (becameActive) {
            onVoiceprintActive?.invoke()
        }
    }

    /** 采集线程：PCM16 小端字节 → 按 20ms 切帧回调。 */
    private fun deliverPcm(data: ByteArray) {
        val callback = onPcmFrame ?: return
        val samples = data.size / 2
        if (samples == 0) {
            return
        }
        if (pendingLength + samples > pending.size) {
            pending = pending.copyOf((pendingLength + samples) * 2)
        }
        ByteBuffer.wrap(data, 0, samples * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(pending, pendingLength, samples)
        pendingLength += samples

        var offset = 0
        while (pendingLength - offset >= RealtimeAsrSpec.FRAME_SAMPLES) {
            callback(pending.copyOfRange(offset, offset + RealtimeAsrSpec.FRAME_SAMPLES))
            offset += RealtimeAsrSpec.FRAME_SAMPLES
        }
        if (offset > 0) {
            val remain = pendingLength - offset
            if (remain > 0) {
                System.arraycopy(pending, offset, pending, 0, remain)
            }
            pendingLength = remain
        }
    }

    companion object {
        private const val TAG = "RealtimeAsrCapture"
        private const val JOIN_TIMEOUT_MS = 300L

        private const val EVENT_LOCAL_PCM = "onReceiveLocalPcm"
        private const val EVENT_LOCAL_OPUS = "onReceiveLocalOpus"
        private const val EVENT_VOICEPRINT_STATUS = "onVoiceprintDenoiseStatus"
        private const val VOICEPRINT_STATUS_ACTIVE = "Active"

        private const val API_CALL_VOLUME =
            "{\"api\":\"setPrivateConfig\",\"params\":{\"configs\":" +
                "[{\"key\":\"Liteav.Audio.common.force.system.volume.type\",\"default\":\"0\",\"value\":\"1\"}]}}"
        private const val API_DISABLE_CLOUD = "{\"api\":\"enableCloudAsrChannel\",\"params\":{\"enable\":false}}"
        private const val API_ENABLE_OPUS = "{\"api\":\"enableLocalOpusOutput\",\"params\":{\"enable\":true}}"
        private const val API_ENABLE_PCM = "{\"api\":\"enableLocalPcmOutput\",\"params\":{\"enable\":true}}"

        private fun voiceprintApi(enrollMs: Int): String =
            "{\"api\":\"enableVoiceprintDenoise\",\"params\":{\"enable\":true," +
                "\"voiceprint_file_path\":\"\",\"auto_enroll_duration_ms\":$enrollMs}}"

        /** 采集与 Opus 编码都由 VoiceAI SDK 提供，不再依赖系统 `audio/opus` 编码器。 */
        fun isSupported(): Boolean = true
    }
}
