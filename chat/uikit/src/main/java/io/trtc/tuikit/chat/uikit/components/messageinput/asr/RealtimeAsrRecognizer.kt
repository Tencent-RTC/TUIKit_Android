package io.trtc.tuikit.chat.uikit.components.messageinput.asr

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.imsdk.v2.V2TIMSDKListener

/**
 * 长按录音期间的实时语音识别。
 *
 * 一次长按的完整生命周期：
 *
 * 1. [prime] —— 手指按下、开始录音。麦克风采集起来，同时 startRealtimeTranscriber 把后台会话也建上。
 *    会话必须在这一步就建：它要一个网络往返，等用户滑到「转文字」再建，那几百毫秒到
 *    几秒会原样变成用户盯着空气等的时间。
 * 2. [beginRecognize] —— 手指**第一次**滑到「转文字」。音频从按下就在上行，会话也早已就绪，
 *    所以这一步正常情况下是空操作，滑过去当场就有字。它只兜两件事：按下那次建会话失败了
 *    在这里重试，以及把建会话往返期间攒的那点缓冲补推上去。再滑走、滑回来都不会重开
 *    会话，识别一直连着，滑走期间说的话也不会丢。
 * 3. [finish] —— 在「转文字」上松手。stopRealtimeTranscriber，再留一个收尾窗口等尾句结果，然后把
 *    完整文本交出去。
 * 4. [cancel] —— 滑到「取消」松手、录音失败、页面销毁。全部丢弃。
 *
 * 「会话开没开」和「音频推不推」是两件独立的事，分别由 [sessionStage] 和 [uploading]
 * 表示——把它们塞进一个状态机正是之前「滑过去要等几秒才出字」的来源。
 *
 * 所有公开方法都必须在主线程调用；[onTextUpdated] 也在主线程回调。
 */
internal class RealtimeAsrRecognizer(
    private val language: String,
    private val onTextUpdated: (String) -> Unit,
) {

    private enum class SessionStage {
        /** 还没建，或上一次建失败了。 */
        NONE,

        /** startRealtimeTranscriber 已发出，会话 ID 还没回来。 */
        STARTING,

        /** 会话可用。 */
        READY,

        /** 已 stopRealtimeTranscriber，等尾句结果。 */
        DRAINING,
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val preRoll = RealtimeAsrPacketBuffer(RealtimeAsrSpec.PRE_ROLL_CAPACITY)
    private val pendingUpload = mutableListOf<ByteArray>()

    /** chunkIndex -> 该句当前最完整的文本。后台会对同一句反复下发修正后的结果。 */
    private val sentences = sortedMapOf<Int, String>()

    private var capture: RealtimeAsrAudioCapture? = null

    /** [prime] 成功到 [release] 之间为 true，即麦克风采集是否归本实例所有。 */
    private var active = false

    private var sessionStage = SessionStage.NONE

    /** 音频是否往上推。false 时一律进 [preRoll]，等开关打开再补推。 */
    private var uploading = false

    private var sessionID: String? = null
    private var startTimeout: Runnable? = null
    private var drainTimeout: Runnable? = null
    private var finishCallback: ((String) -> Unit)? = null

    /**
     * 每次状态跃迁都 +1。建会话的回调、超时任务各自捕获发起时的代数，对不上就说明
     * 自己已经过期。不能用布尔量代替：松手后紧接着的下一次长按会把布尔量复位，
     * 让上一轮在途请求的迟到回调污染新一轮。
     */
    private var generation = 0

    private val asrListener = object : V2TIMSDKListener() {
        override fun onExperimentalNotify(key: String?, param: Any?) {
            val response = RealtimeAsrExperimental.parseNotify(key, param) ?: return
            mainHandler.post {
                handleResponse(response.sessionID, response.chunkIndex, response.text, response.isFinished)
            }
        }
    }

    /** 有识别结果可以期待：上行开着，且会话活着。 */
    val isRecognizing: Boolean
        get() = active && uploading &&
            (sessionStage == SessionStage.STARTING || sessionStage == SessionStage.READY)

    /** 当前已识别出的完整文本。 */
    val text: String
        get() = sentences.values.joinToString(separator = "")

    /** 本次长按建会话时后台回了 [AsrServiceError.isServiceUnavailable]，重试也没用。 */
    private var isServiceUnavailable = false

    /**
     * 开始采集，同时把后台会话也建起来。
     *
     * @return 采集通路不可用时返回 false，调用方应当关掉本次长按的实时识别
     */
    fun prime(): Boolean {
        if (active) {
            return true
        }
        reset()
        isServiceUnavailable = false
        val engine = RealtimeAsrAudioCapture(::onCapturedPacket)
        if (!engine.start()) {
            return false
        }
        capture = engine
        active = true
        uploading = RealtimeAsrSpec.UPLOAD_FROM_PRESS
        RealtimeAsrExperimental.addListener(asrListener)
        startSession()
        return true
    }

    /** 手指首次滑到「转文字」。重复调用无副作用。 */
    fun beginRecognize() {
        if (!active) {
            return
        }
        uploading = true
        when (sessionStage) {
            // 按下时那次建会话失败了（网络抖动等），用户既然滑过来了就再试一次
            SessionStage.NONE -> if (!isServiceUnavailable) startSession()
            SessionStage.READY -> flushPreRoll()
            SessionStage.STARTING, SessionStage.DRAINING -> Unit
        }
    }

    private fun startSession() {
        if (sessionStage != SessionStage.NONE) {
            return
        }
        sessionStage = SessionStage.STARTING
        val currentGeneration = ++generation
        scheduleStartTimeout(currentGeneration)
        RealtimeAsrExperimental.start(
            language = language,
            onSuccess = { result ->
                mainHandler.post { onStartSucceeded(currentGeneration, result) }
            },
            onError = { code, desc ->
                mainHandler.post { onStartFailed(currentGeneration, code, desc) }
            },
        )
    }

    /**
     * 在「转文字」上松手：停止采集与会话，等尾句结果后交出文本。
     *
     * [onResult] 一定会被调用一次（收到带 isFinished 的终态响应、stopRealtimeTranscriber 失败、或收尾超时，
     * 三者最早的那个）。识别没能起来时给空串，调用方据此回落到「上传整段音频再识别」。
     */
    fun finish(onResult: (String) -> Unit) {
        if (!active) {
            onResult("")
            return
        }
        stopCapture()
        val activeSessionID = sessionID
        if (sessionStage != SessionStage.READY || activeSessionID == null) {
            // 会话还没建起来（或压根没开始识别），没有尾句可等
            val recognized = text
            release()
            onResult(recognized)
            return
        }
        sessionStage = SessionStage.DRAINING
        generation++
        finishCallback = onResult
        scheduleDrainTimeout()
        flushTailThenStop(activeSessionID)
    }

    /**
     * 把最后一批音频送完再关会话。
     *
     * 不能一边 feedRealtimeAudioData 一边 stopRealtimeTranscriber：后者一到服务端就把会话关了，还在飞的那包
     * 会撞上 `140002 session is closed`，松手前最后几十毫秒的音频连同它对应的尾字一起丢。
     * 后台不回 ack 时由 [RealtimeAsrSpec.TAIL_UPLOAD_TIMEOUT_MS] 兜底，宁可丢尾包也不能卡着不发。
     */
    private fun flushTailThenStop(id: String) {
        pendingUpload.addAll(preRoll.drain())
        val tail = ArrayList(pendingUpload)
        pendingUpload.clear()
        val chunks = tail.chunked(RealtimeAsrSpec.UPLOAD_MAX_PACKETS_PER_CALL)
        if (chunks.isEmpty()) {
            stopSession(id)
            return
        }
        var stopped = false
        var pending = chunks.size
        val stopOnce = {
            if (!stopped) {
                stopped = true
                stopSession(id)
            }
        }
        val guard = Runnable {
            Log.w(TAG, "tail upload timed out, stopping anyway")
            stopOnce()
        }
        mainHandler.postDelayed(guard, RealtimeAsrSpec.TAIL_UPLOAD_TIMEOUT_MS)
        val onChunkDone = {
            pending--
            if (pending == 0) {
                mainHandler.removeCallbacks(guard)
                stopOnce()
            }
        }
        chunks.forEach { chunk ->
            RealtimeAsrExperimental.feed(
                sessionID = id,
                dataList = chunk,
                onSuccess = { mainHandler.post { onChunkDone() } },
                onError = { code, desc ->
                    Log.w(TAG, "tail feedRealtimeAudioData failed: $code $desc")
                    mainHandler.post { onChunkDone() }
                },
            )
        }
    }

    private fun stopSession(id: String) {
        RealtimeAsrExperimental.stop(
            sessionID = id,
            // 成功回调只是「会话已关闭」的 ack，不代表结果到齐了，所以这里不收尾，继续等 isFinished 或收尾超时。
            onSuccess = {},
            onError = { code, desc ->
                Log.w(TAG, "stopRealtimeTranscriber failed: $code $desc")
                // 停不掉的会话不会再吐结果，继续干等只是白白拖慢发送
                mainHandler.post { completeDrain() }
            },
        )
    }

    /** 放弃本次识别：停采集、关会话、丢文本。可重复调用。 */
    fun cancel() {
        if (!active) {
            return
        }
        stopCapture()
        sessionID?.let { id ->
            RealtimeAsrExperimental.stop(id)
        }
        release()
    }

    private fun onStartSucceeded(expectedGeneration: Int, id: String) {
        if (expectedGeneration != generation) {
            // 用户已经松手/取消了，后台却仍然分配了会话，立刻关掉，别留悬空会话
            if (id.isNotEmpty()) {
                RealtimeAsrExperimental.stop(id)
            }
            return
        }
        cancelStartTimeout()
        generation++
        if (id.isEmpty()) {
            sessionStage = SessionStage.NONE
            return
        }
        sessionID = id
        sessionStage = SessionStage.READY
        if (uploading) {
            flushPreRoll()
        }
    }

    /** 把会话就绪前攒下的音频补推上去，连同已在排队的那批。 */
    private fun flushPreRoll() {
        val id = sessionID ?: return
        pendingUpload.addAll(preRoll.drain())
        flushPendingUpload(id)
    }

    private fun onStartFailed(expectedGeneration: Int, code: Int, desc: String) {
        if (expectedGeneration != generation) {
            return
        }
        cancelStartTimeout()
        generation++
        Log.w(TAG, "startRealtimeTranscriber failed: $code $desc")
        isServiceUnavailable = AsrServiceError.isServiceUnavailable(code)
        // 退回 NONE 而不是直接废掉：用户滑到「转文字」时 beginRecognize 还能再试一次
        sessionStage = SessionStage.NONE
    }

    private fun onCapturedPacket(packet: ByteArray) {
        mainHandler.post {
            if (!active) {
                return@post
            }
            val id = sessionID
            if (uploading && sessionStage == SessionStage.READY && id != null) {
                pendingUpload.add(packet)
                if (pendingUpload.size >= RealtimeAsrSpec.UPLOAD_BATCH_PACKETS) {
                    flushPendingUpload(id)
                }
            } else if (sessionStage != SessionStage.DRAINING) {
                preRoll.append(packet)
            }
        }
    }

    private fun flushPendingUpload(id: String) {
        if (pendingUpload.isEmpty()) {
            return
        }
        val batch = ArrayList(pendingUpload)
        pendingUpload.clear()
        // 补推积压时这一批会比实时节奏长，切片发：失败的话丢的只是一片而不是全部。
        // 切出来的几片是并行在飞的，不会因为分片而变慢。
        batch.chunked(RealtimeAsrSpec.UPLOAD_MAX_PACKETS_PER_CALL).forEach { chunk ->
            RealtimeAsrExperimental.feed(id, chunk) { code, desc ->
                // 丢包容忍：记日志继续，不中断会话
                Log.w(TAG, "feedRealtimeAudioData failed: $code $desc")
            }
        }
    }

    private fun handleResponse(responseSessionID: String?, chunkIndex: Int, text: String, isFinished: Boolean) {
        val activeSessionID = sessionID ?: return
        if (!responseSessionID.isNullOrEmpty() && responseSessionID != activeSessionID) {
            return
        }
        // 后台偶发下发 index=0 / text=""，别让它把已累积的句子桶覆盖成空
        if (text.isNotEmpty()) {
            sentences[chunkIndex] = text
            onTextUpdated(this.text)
        }
        if (isFinished && sessionStage == SessionStage.DRAINING) {
            completeDrain()
        }
    }

    private fun scheduleStartTimeout(expectedGeneration: Int) {
        val task = Runnable {
            startTimeout = null
            if (expectedGeneration != generation) {
                return@Runnable
            }
            Log.w(TAG, "startRealtimeTranscriber timed out")
            generation++
            sessionStage = SessionStage.NONE
        }
        startTimeout = task
        mainHandler.postDelayed(task, RealtimeAsrSpec.START_TIMEOUT_MS)
    }

    private fun cancelStartTimeout() {
        startTimeout?.let { mainHandler.removeCallbacks(it) }
        startTimeout = null
    }

    private fun scheduleDrainTimeout() {
        val task = Runnable {
            drainTimeout = null
            completeDrain()
        }
        drainTimeout = task
        mainHandler.postDelayed(task, RealtimeAsrSpec.DRAIN_TIMEOUT_MS)
    }

    private fun completeDrain() {
        val callback = finishCallback ?: return
        finishCallback = null
        drainTimeout?.let { mainHandler.removeCallbacks(it) }
        drainTimeout = null
        val recognized = text
        release()
        callback(recognized)
    }

    private fun stopCapture() {
        capture?.stop()
        capture = null
    }

    private fun release() {
        stopCapture()
        cancelStartTimeout()
        drainTimeout?.let { mainHandler.removeCallbacks(it) }
        drainTimeout = null
        finishCallback = null
        if (active) {
            RealtimeAsrExperimental.removeListener(asrListener)
        }
        generation++
        active = false
        uploading = false
        sessionStage = SessionStage.NONE
        sessionID = null
        reset()
    }

    private fun reset() {
        preRoll.clear()
        pendingUpload.clear()
        sentences.clear()
    }

    private companion object {
        const val TAG = "RealtimeAsrRecognizer"
    }
}
