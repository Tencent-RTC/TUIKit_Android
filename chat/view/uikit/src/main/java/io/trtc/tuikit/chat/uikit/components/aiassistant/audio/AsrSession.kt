package io.trtc.tuikit.chat.uikit.components.aiassistant.audio

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIConstants

private const val TAG = "AsrSession"

/**
 * IMSDK 实时语音识别会话的封装：管 sessionID 生命周期与音频帧聚合。
 *
 * 识别结果不在这里，走 V2TIMSDKListener.onExperimentalNotify("onRecvRealtimeTranscriberResponse")，
 * 由 AICallController 统一接收。
 *
 * 所有公开方法都在主线程调用。
 */
class AsrSession(private val listener: Listener) {

    interface Listener {
        fun onStarted()
        fun onStartFailed(code: Int, desc: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingPackets = mutableListOf<ByteArray>()

    private var currentSessionId: String? = null
    private var starting = false
    private var pendingStartTimeout: Runnable? = null

    /**
     * 启动请求的代数，每次状态跃迁（start / 启动成功 / 启动失败 / 超时 / stop）都 +1。
     * startRealtimeTranscriber 的回调和超时任务各自捕获发起时的代数，代数对不上就说明自己已经过期。
     *
     * 不能用布尔标志代替：stop() 之后紧接着的 start() 会把标志复位，
     * 让上一次在途请求的迟到回调被误当成当前会话接受，反过来把新会话关掉。
     */
    private var generation = 0

    val sessionId: String?
        get() = currentSessionId

    /** startRealtimeTranscriber 已发出、会话 ID 还没回来。 */
    val isStarting: Boolean
        get() = starting

    /** 已经有 sessionID，或 startRealtimeTranscriber 还在路上。 */
    val isLive: Boolean
        get() = currentSessionId != null || starting

    fun start() {
        if (starting || currentSessionId != null) {
            // 调用方漏了 stop() 的表现是「永久卡在 THINKING」——onStarted() 永不到达，
            // 而现场除了这条日志没有任何别的线索，所以宁可吵一点
            Log.w(TAG, "start ignored, session already active (starting=$starting, id=$currentSessionId)")
            return
        }
        starting = true
        val gen = ++generation
        pendingPackets.clear()
        scheduleStartTimeout(gen)

        callExperimental(
            api = API_START,
            param = hashMapOf("language" to AIConstants.ASR_LANGUAGE),
            onSuccess = { data ->
                val id = data as? String ?: ""
                // 迟到的成功回调：调用方已经放弃了，立刻把后台会话关掉，
                // 否则会留下一个没人消费的悬空会话
                if (gen != generation) {
                    Log.w(TAG, "startRealtimeTranscriber succeeded after abandon, closing session $id")
                    if (id.isNotEmpty()) {
                        callExperimental(API_STOP, hashMapOf("sessionID" to id))
                    }
                    return@callExperimental
                }
                cancelStartTimeout()
                generation++
                starting = false
                currentSessionId = id
                Log.i(TAG, "ASR session started: $id")
                listener.onStarted()
            },
            onError = { code, desc ->
                if (gen != generation) {
                    Log.w(TAG, "startRealtimeTranscriber failed after abandon: $code $desc")
                    return@callExperimental
                }
                cancelStartTimeout()
                generation++
                starting = false
                Log.e(TAG, "startRealtimeTranscriber failed: $code $desc")
                listener.onStartFailed(code, desc)
            },
        )
    }

    /**
     * 送入一个裸 Opus packet。攒够 [AIConstants.UPLOAD_BATCH_PACKETS] 个（120ms）自动上行一次。
     * 会话尚未就绪时直接丢弃，startRealtimeTranscriber 成功后才开麦。
     */
    fun pushPacket(packet: ByteArray) {
        val id = currentSessionId ?: return
        pendingPackets.add(packet)
        if (pendingPackets.size >= AIConstants.UPLOAD_BATCH_PACKETS) {
            flush(id)
        }
    }

    /** flush 尾包后停止会话。可重复调用。 */
    fun stop() {
        cancelStartTimeout()
        // 让所有在途的 startRealtimeTranscriber 请求作废；它们的成功回调会自己把拿到的会话关掉
        generation++
        starting = false
        val id = currentSessionId
        currentSessionId = null
        if (id == null) {
            pendingPackets.clear()
            return
        }
        if (pendingPackets.isNotEmpty()) {
            flush(id)
        }
        callExperimental(
            api = API_STOP,
            param = hashMapOf("sessionID" to id),
            onSuccess = { Log.i(TAG, "ASR session stopped: $id") },
            onError = { code, desc -> Log.w(TAG, "stopRealtimeTranscriber failed: $code $desc") },
        )
    }

    /**
     * 后台已经把这个会话关掉了（idle / 硬超时的 isFinished），本地不要再往上推音频，
     * 也不要再调 stopRealtimeTranscriber。随后 [start] 才能开出新会话。
     */
    fun noteRemoteFinished(sessionID: String) {
        if (currentSessionId != sessionID) {
            return
        }
        generation++
        starting = false
        currentSessionId = null
        pendingPackets.clear()
    }

    private fun scheduleStartTimeout(gen: Int) {
        val timeout = Runnable {
            pendingStartTimeout = null
            if (gen != generation) {
                return@Runnable
            }
            Log.w(TAG, "startRealtimeTranscriber timed out after ${AIConstants.ASR_START_TIMEOUT_MS}ms")
            // 代数 +1 作废这一次请求：成功回调若在超时后才到，会走「迟到」分支自行 stopRealtimeTranscriber
            generation++
            starting = false
            listener.onStartFailed(ERR_START_TIMEOUT, "startRealtimeTranscriber timeout")
        }
        pendingStartTimeout = timeout
        mainHandler.postDelayed(timeout, AIConstants.ASR_START_TIMEOUT_MS)
    }

    private fun cancelStartTimeout() {
        pendingStartTimeout?.let { mainHandler.removeCallbacks(it) }
        pendingStartTimeout = null
    }

    private fun flush(sessionID: String) {
        val batch = ArrayList(pendingPackets)
        pendingPackets.clear()
        callExperimental(
            api = API_FEED,
            param = hashMapOf(
                "sessionID" to sessionID,
                "dataList" to batch,
            ),
            onError = { code, desc ->
                // 丢包容忍：记日志继续，不中断会话
                Log.w(TAG, "feedRealtimeAudioData failed: $code $desc")
            },
        )
    }

    private fun callExperimental(
        api: String,
        param: Map<String, Any>,
        onSuccess: ((Any?) -> Unit)? = null,
        onError: ((Int, String) -> Unit)? = null,
    ) {
        val callback = if (onSuccess == null && onError == null) {
            null
        } else {
            object : V2TIMValueCallback<Any> {
                override fun onSuccess(data: Any?) {
                    onSuccess?.invoke(data)
                }

                override fun onError(code: Int, desc: String?) {
                    onError?.invoke(code, desc.orEmpty())
                }
            }
        }
        V2TIMManager.getInstance().callExperimentalAPI(api, param, callback)
    }

    companion object {
        const val ERR_START_TIMEOUT = -1001
        const val NOTIFY_KEY = "onRecvRealtimeTranscriberResponse"
        private const val API_START = "startRealtimeTranscriber"
        private const val API_FEED = "feedRealtimeAudioData"
        private const val API_STOP = "stopRealtimeTranscriber"

        fun parseNotify(key: String?, param: Any?): Transcript? {
            if (key != NOTIFY_KEY || param !is Map<*, *>) {
                return null
            }
            val chunkIndex = param["chunkIndex"]
            return Transcript(
                sessionID = param["sessionID"] as? String,
                chunkIndex = (chunkIndex as? Number)?.toInt() ?: 0,
                text = param["text"] as? String ?: "",
                sliceType = (param["sliceType"] as? Number)?.toInt() ?: SLICE_TYPE_START,
                isFinished = param["isFinished"] == true,
            )
        }

        /** sliceType：一段话开始识别 */
        const val SLICE_TYPE_START = 0

        /** sliceType：一段话识别中（非稳态，结果可能变化） */
        const val SLICE_TYPE_PARTIAL = 1

        /** sliceType：一段话识别结束（稳态，结果不再变化） */
        const val SLICE_TYPE_STABLE = 2
    }

    data class Transcript(
        val sessionID: String?,
        val chunkIndex: Int,
        val text: String,
        /** 识别结果类型，取值见 [SLICE_TYPE_START] / [SLICE_TYPE_PARTIAL] / [SLICE_TYPE_STABLE] */
        val sliceType: Int,
        /** 整个识别会话结束（停止 / 后台超时 / 异常） */
        val isFinished: Boolean,
    ) {
        /** 这一句已识别结束，文本不再变化 */
        val isSentenceStable: Boolean
            get() = sliceType == SLICE_TYPE_STABLE
    }
}
