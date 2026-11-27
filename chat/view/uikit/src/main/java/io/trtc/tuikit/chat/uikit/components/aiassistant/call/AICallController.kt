package io.trtc.tuikit.chat.uikit.components.aiassistant.call

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.imsdk.v2.V2TIMAdvancedMsgListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMMessage
import com.tencent.imsdk.v2.V2TIMSDKListener
import com.tencent.imsdk.v2.V2TIMTTSResponse
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.chat.uikit.BuildConfig
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantControlMessages
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIConstants
import io.trtc.tuikit.chat.uikit.components.aiassistant.audio.AsrBargeIn
import io.trtc.tuikit.chat.uikit.components.aiassistant.audio.AsrSession
import io.trtc.tuikit.chat.uikit.components.aiassistant.audio.AudioPlaybackEngine
import io.trtc.tuikit.chat.uikit.components.aiassistant.audio.SystemOpusDecoder
import io.trtc.tuikit.chat.uikit.components.aiassistant.audio.TtsDecodeWorker
import io.trtc.tuikit.chat.uikit.components.aiassistant.audio.TtsSession
import io.trtc.tuikit.chat.uikit.components.messageinput.asr.AsrServiceError
import io.trtc.tuikit.chat.uikit.components.messageinput.asr.RealtimeAsrAudioCapture
import kotlin.math.sqrt

private const val TAG = "AICallController"

/** [AICallController] 记住的作废回复 msgID 上限 */
private const val MAX_ABANDONED_MSG_IDS = 16

/**
 * 通话页的状态机与全链路编排。
 *
 * 这是整个 AI 语音特性里唯一注册 V2TIMAdvancedMsgListener 的地方，
 * 也是唯一把 audio/ 下那些互不认识的单元串起来的地方。
 *
 * 麦克风和 ASR 会话都常开：LISTENING / THINKING / SPEAKING 都把麦上音频送上去。
 * 断句看 ASR 回包的 sliceType：某一句到了 sliceType=2（稳态）就把它及之前的句子发出去。
 * 思考 / 播放期间用户开口一律打断本轮回复，判据是 ASR 吐出了不像当前 TTS 回声的新文本。
 * 静音、暂停、挂断才会 stopRealtimeTranscriber。
 *
 * ## 线程模型
 *
 * 只有两条线程会进到这个类里：
 * - **主线程**：所有公开方法、所有 `Callbacks` 之外的回调、`AsrSession` / `TtsSession`
 *   的全部调用。
 * - **采集线程**：[RealtimeAsrAudioCapture] 的 PCM / Opus 回调，以及 [onAudioFrame] 里的音量计算。
 *
 * 播放线程是 [AudioPlaybackEngine] 内部的，它只会通过 `onFinished` 回调碰到这里；
 * TTS 音频的解码线程是 [TtsDecodeWorker] 内部的，它只会通过 `onChunkDecoded` 回调碰到这里。
 * 这两个回调第一件事都是 post 回主线程。
 *
 * 字段按这两条线程分成两类，见下面的两组声明。加错了两边都会出问题：该加 volatile
 * 的不加会丢可见性，不该加的加了会让读者以为那个字段可以跨线程碰。
 */
class AICallController(
    private val robotUserId: String,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onStateChanged(state: AICallState)
        fun onTranscriptChanged(entries: List<TranscriptEntry>)
        fun onAudioLevel(level: Float)
        fun onError(messageResId: Int)

        /**
         * ASR 会话起不来，通话停在 IDLE 等用户手动重试（[retryListening]）。
         *
         * 状态回调不足以表达这件事：IDLE 同时也是「还没开始」「已静音」「已暂停」，
         * 照 IDLE 渲染出来的是「正在听…」，而此时没有任何东西在听。
         */
        fun onListeningFailed()

        /**
         * 识别服务未开通或资源包已用完（[AsrServiceError.isServiceUnavailable]）。
         * 重试无效，通话无法继续；此时不再回调 [onError] / [onListeningFailed]。
         */
        fun onAsrServiceUnavailable()

        /** 声纹注册完成，之后只响应当前说话人。主线程回调。 */
        fun onVoiceprintEnrolled()

        /** 调试浮层用的事件行。采集线程也可能来，实现方要切主线程。 */
        fun onDebugLog(line: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val messageInputStore = MessageInputStore.create("c2c_$robotUserId")

    // ------------------------------------------------ 跨线程字段：必须 @Volatile
    //
    // 下面这些字段主线程写、采集线程读（或反之）。没有 volatile 就没有可见性保证：
    // 采集线程靠 [state] / [muted] 决定这一包要不要上行、音量动画取哪一路，
    // 读到旧值会在 IDLE 后多推几包。

    @Volatile
    private var state = AICallState.IDLE

    @Volatile
    private var released = false

    /**
     * 麦克风静音。主线程写、采集线程在 [onAudioFrame] 的第一道守卫上读。
     *
     * 静音期间整帧直接丢弃：不算音量、不编码、也不上行。
     */
    @Volatile
    private var muted = false

    /**
     * 主线程赋值，采集线程在 [onAudioFrame] 里读 `currentLevel` 驱动光球。
     *
     * null 表示播放引擎没能起来，本次通话降级成纯文本——**绝不能在这种情况下切到
     * SPEAKING**：[AudioPlaybackEngine.enqueue] 会全部丢弃、`onFinished` 永不触发，
     * 对话会永久卡死在那个状态。
     */
    @Volatile
    private var playback: AudioPlaybackEngine? = null

    /** 最近一帧 RMS，采集线程写，调试快照读。 */
    @Volatile
    private var lastRms = 0.0

    /** 最近一次送给光球的音量。 */
    @Volatile
    private var lastLevel = 0f

    // ------------------------------------------------ 主线程独占字段：不要加 @Volatile

    private var capture: RealtimeAsrAudioCapture? = null
    private var asrSession: AsrSession? = null

    /**
     * 因外部原因（音频焦点丢失、页面退到后台）而暂停。只在主线程上读写，见 [pause] / [resume]。
     *
     * 两个来源共用一个标志是故意的：来电时两件事往往一起发生（先丢焦点、再退后台），
     * 任一恢复信号都该把通话拉回来，而 [resume] 本身对「没暂停」是 no-op。
     */
    private var pausedExternally = false

    /**
     * startRealtimeTranscriber 失败后停在 IDLE，没有会话在跑，等用户点麦克风重试（[retryListening]）。
     * 只在主线程上读写。
     */
    private var startFailed = false

    /** TTS 音频的解码线程，与 [playback] 同生共死：播放引擎没起来就没有它。 */
    private var ttsDecoder: TtsDecodeWorker? = null

    private val ttsSession = TtsSession()

    /** 当前正在朗读的那条流式消息 */
    private var streamingMsgId: String? = null

    /** 一条消息只触发一次 startTTS，成功回调里置 true */
    private var hasStartedTTS = false

    /** startTTS 已发出但还没有结果。流式消息每来一个增量就重复请求一次是没有意义的 */
    private var ttsStartPending = false

    /** AI 侧文本在 transcript 里的下标，-1 表示本轮还没插入过 */
    private var aiEntryIndex = -1

    /** 本轮已入队的 TTS 音频总时长，给 SPEAKING 看门狗估播完所需时间的上界 */
    private var enqueuedAudioMs = 0L

    /**
     * 还没发出去的识别结果：chunkIndex -> 该句最新完整文本（覆盖式，不是增量）。
     *
     * 负数 key 是上一个 ASR 会话遗留、尚未定稿的句子（见 [carryOverSentences]）：新会话的
     * chunkIndex 从 0 重新编号，挪到负数区间才能保持「旧句在前、新句在后」的顺序。
     */
    private val sentences = sortedMapOf<Int, String>()

    /** 已被接纳进 [sentences] 且收到 sliceType=2（稳态）、但还没发出去的最大 index，-1 表示没有。 */
    private var stableUpTo = -1

    /**
     * 当前 ASR 会话里已经发给机器人的最大 chunkIndex，-1 表示还没发过。
     *
     * 后台会对同一句重推（非 final 包不参与 IMSDK 的 ACK 去重，网关重推、乱序都会再到一次），
     * index 不大于它的回包一律丢掉，否则已发出的句子会被塞回 [sentences] 再发一遍。
     * 新会话 index 从 0 重来，所以换会话时归 -1。
     */
    private var committedChunkCeiling = -1

    /**
     * 已发出问题、还在等机器人这一轮的回复消息。只有这时到达的新流式消息才认作本轮回复。
     * 收到第一条回复消息、或本轮被放弃时落下。
     */
    private var awaitingReply = false

    /**
     * 已作废回复的 msgID。打断 / 超时 / 静音之后服务端仍会继续推这条消息的增量，
     * 不挡掉的话旧回答会继续刷字幕，甚至在下一轮 THINKING 里被当成新回复去起 TTS。
     */
    private val abandonedMsgIds = LinkedHashSet<String>()

    /**
     * 当前绑定回复消息的 timestamp（服务端创建时间，流式增量不会改它），本轮还没绑定回复时为 -1。
     * 用来在 [staleReplyPossible] 时判断新到的回复是不是更新的那一条，见 [handleStreamMessage]。
     */
    private var boundReplyTimestamp = -1L

    /**
     * 上一次正常结束一轮之后，有问题在还没收到回复时就被放弃了（思考态被打断、静音、暂停、超时）。
     *
     * 这时服务端仍会为那个问题回一条消息，而且可能在下一个问题发出之后才到——客户端分不清
     * 它回答的是哪一句。规则是「新的顶掉旧的」：本轮已绑定回复后，再来一条 timestamp 更大的
     * 回复就改播它。只在这个标志为 true 时启用，正常一问一答的轮次不受影响。
     * 某一轮正常播完（或纯文本收完）时落下。
     */
    private var staleReplyPossible = false

    /**
     * 刚调了 [AsrSession.start]、等 onStarted 把状态切到 LISTENING。
     * ASR 在播放中途因超时重开时不能走这条路径，否则会把 SPEAKING 盖成聆听。
     */
    private var awaitingAsrForListening = false

    /** 上一轮已经发给机器人的用户文本，只给调试浮层展示。同句补包的去重靠 [committedChunkCeiling]。 */
    private var committedUserText = ""

    /** 已完成的对话，用于文本态展示 */
    private val transcript = mutableListOf<TranscriptEntry>()

    private val replyTimeout = Runnable {
        if (state == AICallState.THINKING) {
            debugW("stream reply timed out")
            callbacks.onError(R.string.uikit_ai_call_reply_timeout)
            enterListening()
        }
    }

    /**
     * SPEAKING 态的兜底超时。[replyTimeout] 只覆盖 THINKING，音频一开始播就没人管了：
     * 后台断流导致 isFinished 永不到达、或个别机型的 `onFinished` 不触发，都会让对话
     * 永久停在 SPEAKING，用户只能挂断。
     *
     * 恢复动作与正常播完完全一致，走 [enterListening]——它会停掉 TTS、倒掉播放队列里的
     * 残留、再开一轮聆听。不额外弹提示：走到这里用户早就听不到声音了，
     * 一句「助手没有响应」既晚又没有信息量。
     */
    private val speakingWatchdog = Runnable {
        if (released || state != AICallState.SPEAKING) {
            return@Runnable
        }
        debugW("speaking watchdog fired, recovering to listening")
        enterListening()
    }

    /**
     * IMSDK 把消息、TTS 回调派发在主线程上。ASR 结果改走
     * [V2TIMSDKListener.onExperimentalNotify]，key 为 onRecvRealtimeTranscriberResponse。
     *
     * 这里仍然统一 post 一次，一是不让正确性依赖 IMSDK 的内部实现，二是 Handler 的 FIFO
     * 保证了 [handleStreamMessage]（里面投递解码器重置）与 [handleTtsResponse]（里面投递解码）
     * 的先后顺序与 IMSDK 的投递顺序一致——只 post 其中一个就会把 reset 排到分片之后。
     * 解码线程同样是 FIFO，这个顺序会原样带过去。
     */
    private val msgListener = object : V2TIMAdvancedMsgListener() {
        override fun onRecvNewMessage(msg: V2TIMMessage?) {
            msg ?: return
            mainHandler.post { handleStreamMessage(msg) }
        }

        override fun onRecvMessageModified(msg: V2TIMMessage?) {
            msg ?: return
            mainHandler.post { handleStreamMessage(msg) }
        }

        override fun onRecvTTSResponse(response: V2TIMTTSResponse?) {
            response ?: return
            mainHandler.post { handleTtsResponse(response) }
        }
    }

    private val asrNotifyListener = object : V2TIMSDKListener() {
        override fun onExperimentalNotify(key: String?, param: Any?) {
            val response = AsrSession.parseNotify(key, param) ?: return
            mainHandler.post { handleAsrResponse(response) }
        }
    }

    private val asrListener = object : AsrSession.Listener {
        override fun onStarted() {
            if (released) {
                return
            }
            startFailed = false
            if (!awaitingAsrForListening) {
                debugI("asr started, staying in $state (session recovered)")
                return
            }
            awaitingAsrForListening = false
            // 启动窗口里 TTS 可能已经把这一轮抢走。切 LISTENING 会让外放漏音走识别，
            // 也没有「正在听」这回事。
            if (state == AICallState.SPEAKING || ttsSession.boundMsgId != null) {
                debugW("asr started but this turn is taken over by TTS, staying in $state")
                if (state == AICallState.THINKING) {
                    mainHandler.removeCallbacks(replyTimeout)
                    mainHandler.postDelayed(replyTimeout, AIConstants.STREAM_REPLY_TIMEOUT_MS)
                }
                return
            }
            setState(AICallState.LISTENING)
        }

        override fun onStartFailed(code: Int, desc: String) {
            if (released) {
                return
            }
            awaitingAsrForListening = false
            debugE("ASR start failed: $code $desc")
            // 先置标志再切状态：IDLE 的文案要靠它才知道该显示「连接失败，点麦克风重试」
            // 而不是「正在听…」，而 setState 会同步回调过去。
            startFailed = true
            if (AsrServiceError.isServiceUnavailable(code)) {
                // 通话继续不下去了，不必等页面关掉才交还麦克风
                capture?.stop()
                capture = null
                callbacks.onAsrServiceUnavailable()
            } else {
                callbacks.onError(R.string.uikit_ai_call_start_failed)
                callbacks.onListeningFailed()
            }
            setState(AICallState.IDLE)
        }
    }

    /**
     * 调用前必须已获得 RECORD_AUDIO 权限。
     *
     * @return 麦克风启动成功返回 true
     */
    fun start(): Boolean {
        if (released) {
            return false
        }
        if (!RealtimeAsrAudioCapture.isSupported()) {
            debugE("Opus encoder unavailable")
            callbacks.onError(R.string.uikit_ai_call_audio_unavailable)
            return false
        }
        V2TIMManager.getMessageManager().addAdvancedMsgListener(msgListener)
        V2TIMManager.getInstance().addIMSDKListener(asrNotifyListener)

        val engine = openCapture()
        if (engine == null) {
            debugE("RealtimeAsrAudioCapture failed to start")
            V2TIMManager.getMessageManager().removeAdvancedMsgListener(msgListener)
            V2TIMManager.getInstance().removeIMSDKListener(asrNotifyListener)
            callbacks.onError(R.string.uikit_ai_call_audio_unavailable)
            return false
        }
        capture = engine
        debugI("capture ${engine.effectSummary()}")

        // 播放引擎起不来不影响聆听与识别，所以不中断整次通话，只降级成纯文本：
        // canPlayTts() 为 false 时根本不去发起 TTS，回复靠 transcript 显示。
        val player = AudioPlaybackEngine(onFinished = { mainHandler.post { onPlaybackFinished() } })
        playback = if (player.start()) player else null
        ttsDecoder = playback?.let { engine ->
            TtsDecodeWorker(engine) { generation, decodedMs, isFinished ->
                mainHandler.post { onTtsChunkDecoded(generation, decodedMs, isFinished) }
            }
        }
        if (!canPlayTts()) {
            debugE("TTS playback unavailable (track=${playback != null})")
            callbacks.onError(R.string.uikit_ai_call_playback_unavailable)
        }

        asrSession = AsrSession(asrListener)
        // 首轮不走 enterListening()：此时状态是 IDLE，没有要切出的 SPEAKING 与回复可放弃；
        // 光球在 IDLE 下就是聆听态的静帧，等会话起来再切 LISTENING。
        awaitingAsrForListening = true
        startNewAsrSession()
        AIAssistantControlMessages.enterVoiceCall("c2c_$robotUserId")
        return true
    }

    fun release() {
        if (released) {
            return
        }
        released = true
        AIAssistantControlMessages.leaveVoiceCall("c2c_$robotUserId")
        mainHandler.removeCallbacks(replyTimeout)
        mainHandler.removeCallbacks(speakingWatchdog)
        // 先停采集再停会话：采集停下来之后就不会再有新的 post 排到主线程上
        capture?.stop()
        capture = null
        ttsSession.stop()
        ttsDecoder?.release()
        ttsDecoder = null
        playback?.stop()
        playback = null
        asrSession?.stop()
        asrSession = null
        V2TIMManager.getMessageManager().removeAdvancedMsgListener(msgListener)
        V2TIMManager.getInstance().removeIMSDKListener(asrNotifyListener)
        setState(AICallState.IDLE)
    }

    // ------------------------------------------------------------ 静音 / 暂停

    /**
     * 麦克风静音开关。主线程调用。
     *
     * 静音**不只是掐上行**，而是整轮作废：正在朗读的回答立刻停掉，已识别出来的半句也
     * 丢掉。只停上行而任由 AI 把话念完，用户按下静音后还会继续听到声音，那不叫静音。
     *
     * [asrSession] 必须真的 `stop()`，不能只靠 [muted] 把帧拦在采集侧：
     * [AsrSession.start] 在 `currentSessionId != null` 时是直接返回的，会话不关掉的话，
     * 解除静音时那次 `start()` 会被静默吞掉，`onStarted()` 永不到达，通话永久停在 THINKING。
     *
     * 解除静音直接复用 [enterListening]：它顺带把 TTS、播放队列、两个超时
     * 一并归位，与「播完一轮」走的是同一条路。
     */
    fun setMuted(newMuted: Boolean) {
        if (released || muted == newMuted) {
            return
        }
        muted = newMuted
        debugI("muted -> $newMuted")
        if (newMuted) {
            abandonCurrentReply()
            asrSession?.stop()
            awaitingAsrForListening = false
            committedUserText = ""
            clearSentences()
            notifyTranscript()
            setState(AICallState.IDLE)
        } else if (!pausedExternally) {
            // 焦点还没回来时不开会话：采集引擎停着，开了也没有音频送上去。
            // 那种情况由 [resume] 负责把聆听重新拉起来。
            enterListening()
        }
    }

    /**
     * 暂停整次通话：音频焦点丢失（来电、其他 App 抢占），或页面退到后台。主线程调用。
     *
     * 与 [setMuted] 的差别只有一处，但很关键：这里要把采集引擎也停掉，**真的把麦克风
     * 交还给系统**。静音时麦克风还在我们手上（解除静音要立刻能用），来电时不放手会和
     * 通话抢录音。
     *
     * 退到后台必须走这里而不是放任不管：按 Home 键不触发 AUDIOFOCUS_LOSS，但 Activity
     * 不可见之后系统就收走麦克风，`AudioRecord.read()` 开始返回全零。会话若继续开着，
     * 已识别的半句话会在后台被定稿发出去（sliceType=2），
     * 接着 TTS 在后台大声外放。
     *
     * [streamingMsgId] / [hasStartedTTS] 一律不碰：本轮作废靠 [abandonCurrentReply] 解绑
     * `boundMsgId` 并把这条消息记进 [abandonedMsgIds]，在途分片与迟到的
     * `onRecvMessageModified` 分别由 [handleTtsResponse] / [handleStreamMessage] 挡掉。
     */
    fun pause() {
        if (released || pausedExternally) {
            return
        }
        pausedExternally = true
        debugI("paused")
        abandonCurrentReply()
        asrSession?.stop()
        awaitingAsrForListening = false
        committedUserText = ""
        clearSentences()
        notifyTranscript()
        capture?.stop()
        capture = null
        setState(AICallState.IDLE)
    }

    /**
     * 恢复：重新拿回麦克风并回到聆听。焦点回来或页面重新可见时调用，主线程调用。
     *
     * 采集引擎在 [pause] 里连同 `AudioRecord` 一起释放了，这里必须重建一个。
     * 没暂停过就什么都不做——[AICallActivity] 首次进入时 `onStart()` 也会调到这里。
     */
    fun resume() {
        if (released || !pausedExternally) {
            return
        }
        val engine = openCapture()
        if (engine == null) {
            // 焦点刚还回来的那一瞬间麦克风可能还被通话占着。**不清 pausedExternally**，
            // 让下一次 AUDIOFOCUS_GAIN / onStart() 还能再试一次，而不是把通话页钉死在 IDLE 上。
            debugE("resume failed: capture engine did not start")
            callbacks.onError(R.string.uikit_ai_call_audio_unavailable)
            return
        }
        pausedExternally = false
        capture = engine
        debugI("resumed ${engine.effectSummary()}")
        if (muted) {
            // 静音状态下只把麦克风拿回来，会话等解除静音时再开
            return
        }
        enterListening()
    }

    /**
     * startASR 失败之后手动再试一次。主线程调用。
     *
     * 失败不做自动重试：弱网下连续重试只会连续弹 Toast，而用户可能正想挂断。
     * 但恢复手段必须真的够得着，所以通话页把麦克风按钮在这个状态下改成重试入口
     * （见 [Callbacks.onListeningFailed]）。
     *
     * 静音中或暂停中直接忽略：这两种情况下本来就不该有会话在跑，各自的解除路径
     * （[setMuted] / [resume]）会把聆听重新拉起来。
     */
    fun retryListening() {
        if (released || !startFailed || muted || pausedExternally) {
            return
        }
        debugI("retry listening")
        enterListening()
    }

    // ---------------------------------------------------------------- 音频

    private fun openCapture(): RealtimeAsrAudioCapture? {
        val engine = RealtimeAsrAudioCapture(
            onPacket = ::onCapturedPacket,
            onPcmFrame = ::onAudioFrame,
            config = RealtimeAsrAudioCapture.Config.VoiceCommunication,
            onVoiceprintActive = {
                debugI("voiceprint denoise active")
                mainHandler.post {
                    if (!released) {
                        callbacks.onVoiceprintEnrolled()
                    }
                }
            },
        )
        return if (engine.start()) engine else null
    }

    /**
     * 采集线程：编码已由 [RealtimeAsrAudioCapture] 做完。这里只决定这一包要不要上行。
     */
    private fun onCapturedPacket(packet: ByteArray) {
        if (released || muted) {
            return
        }
        val frameState = state
        val shouldUpload = frameState == AICallState.LISTENING ||
            frameState == AICallState.THINKING ||
            frameState == AICallState.SPEAKING
        if (!shouldUpload) {
            return
        }
        mainHandler.post {
            if (!released && state != AICallState.IDLE) {
                asrSession?.pushPacket(packet)
            }
        }
    }

    /**
     * 采集线程回调。音量是纯计算，就地做；
     * 任何触碰状态机或主线程独占字段的动作都要 post 回主线程。
     */
    private fun onAudioFrame(pcm: ShortArray) {
        if (released) {
            return
        }
        // 静音在这里一刀切断，不往下走任何一步。
        // 光球动画不靠这些帧，静音时仍走聆听态呼吸，由 Activity 渲染。
        if (muted) {
            return
        }
        val frameState = state
        val rms = rms(pcm)
        // 播放态用播放引擎的音量，其余状态用麦克风音量。播放态若继续用麦克风，
        // 驱动动画的其实是外放漏进麦克风的、AI 自己的声音。
        val level = if (frameState == AICallState.SPEAKING) {
            playback?.currentLevel ?: 0f
        } else {
            // 3000 是麦克风上的经验满量程参考值，只用来驱动音量动画
            (rms / 3000.0).coerceIn(0.0, 1.0).toFloat()
        }
        lastRms = rms
        lastLevel = level
        callbacks.onAudioLevel(level)

    }

    /** 一帧 PCM 的均方根能量，只用来驱动音量动画。 */
    private fun rms(pcm: ShortArray): Double {
        if (pcm.isEmpty()) {
            return 0.0
        }
        var sum = 0.0
        for (sample in pcm) {
            sum += sample.toDouble() * sample
        }
        return sqrt(sum / pcm.size)
    }

    // ---------------------------------------------------------------- ASR

    private fun startNewAsrSession() {
        // 必须先 stop()：[AsrSession.start] 在会话还活着（或还在启动中）时只打一条日志就返回，
        // 而调用方这边状态早已切成 THINKING 且没有任何超时在排队——onStarted() 永不到达，
        // 通话就永久卡死在「思考中…」。触发路径是一条迟到的失败回调（来电 / 网络切换把
        // sendMessage 拖到 resume() 之后才回 failure）撞上刚建好的新会话。
        //
        // 对空会话 stop() 是廉价的 no-op；对在途的 start，[AsrSession] 的代数令牌会让那个
        // 迟到回调走「已放弃」分支自行 stopRealtimeTranscriber，紧接着的 start() 重新排一次启动超时。
        asrSession?.stop()
        carryOverSentences()
        asrSession?.start()
    }

    /**
     * 换 ASR 会话（主动重开或被后台结束）时调用：新会话的 chunkIndex 从 0 重来，旧会话的
     * 编号状态全部作废。还没定稿的旧句子挪到负数 key 上留着，排在新会话所有句子之前，
     * 等新会话第一句稳态时一起发出——既不和新编号混排，也不把半句话当成定稿强行发出去。
     */
    private fun carryOverSentences() {
        if (sentences.isNotEmpty()) {
            val leftover = sentences.values.toList()
            sentences.clear()
            leftover.forEachIndexed { i, text -> sentences[i - leftover.size] = text }
            debugI("asr session replaced, carry over \"${joinedText()}\"")
        }
        stableUpTo = -1
        committedChunkCeiling = -1
    }

    private fun handleAsrResponse(response: AsrSession.Transcript) {
        if (released) {
            return
        }
        val session = asrSession ?: return
        val responseId = response.sessionID
        if (responseId.isNullOrEmpty() || responseId != session.sessionId) {
            return
        }
        if (response.isFinished) {
            onAsrSessionFinished(session, response)
            return
        }
        val index = response.chunkIndex
        if (index <= committedChunkCeiling) {
            debugI("asr drop committed index $index (ceiling $committedChunkCeiling)")
            return
        }
        val text = response.text
        val stable = response.isSentenceStable
        when (state) {
            AICallState.LISTENING -> {
                if (text.isNotEmpty()) {
                    sentences[index] = text
                }
                // 稳态包文本为空时沿用该 index 最后一次的 partial；该 index 从没被接纳过就不算
                if (stable && sentences.containsKey(index)) {
                    stableUpTo = maxOf(stableUpTo, index)
                    if (commitStableSentences()) {
                        return
                    }
                }
                if (text.isNotEmpty()) {
                    notifyTranscript(pendingUserText = joinedText())
                }
            }
            AICallState.THINKING,
            AICallState.SPEAKING -> if (text.isNotEmpty()) {
                maybeBargeInFromAsr(index, text, stable)
            }
            AICallState.IDLE -> Unit
        }
    }

    /**
     * 后台结束了当前识别会话（断网、服务端出错等；本地 stop 的会话 sessionId 已置空，到不了这里）。
     * 无论处在哪个状态都要换会话，旧会话的编号状态由 [carryOverSentences] 了结。
     */
    private fun onAsrSessionFinished(session: AsrSession, response: AsrSession.Transcript) {
        debugW("ASR session finished remotely, restarting")
        if (state == AICallState.LISTENING && response.text.isNotEmpty() &&
            response.chunkIndex > committedChunkCeiling
        ) {
            sentences[response.chunkIndex] = response.text
        }
        session.noteRemoteFinished(response.sessionID.orEmpty())
        carryOverSentences()
        if (!muted && !pausedExternally) {
            val wantListen = state == AICallState.LISTENING || awaitingAsrForListening
            if (wantListen) {
                awaitingAsrForListening = true
            }
            session.start()
        }
    }

    /**
     * 思考 / 播放态收到 ASR 文本：回声和零星杂音丢掉，剩下的当成用户开口，两种状态都打断本轮回复。
     *
     * @param stable 这一句已到 sliceType=2。打断后回到聆听会立刻把它发出去。
     */
    private fun maybeBargeInFromAsr(chunkIndex: Int, text: String, stable: Boolean) {
        val probe = sortedMapOf<Int, String>().apply {
            putAll(sentences)
            put(chunkIndex, text)
        }
        val candidate = probe.values.joinToString(separator = "")
        val where = state.name.lowercase()
        if (!AsrBargeIn.shouldBargeIn(
                asrText = candidate,
                llmText = currentLlmText(),
                minChars = AIConstants.ASR_BARGE_IN_MIN_CHARS
            )
        ) {
            debugI("asr $where ignored: \"$candidate\"")
            return
        }
        sentences[chunkIndex] = text
        if (stable) {
            stableUpTo = maxOf(stableUpTo, chunkIndex)
        }
        debugI("asr barge-in ($where): \"$candidate\"")
        notifyTranscript(pendingUserText = joinedText())
        if (state == AICallState.SPEAKING) {
            bargeIn()
        } else {
            interruptThinking()
        }
    }

    private fun clearSentences() {
        sentences.clear()
        stableUpTo = -1
    }

    /** 按 chunkIndex 升序拼接，句间无分隔符 */
    private fun joinedText(): String = sentences.values.joinToString(separator = "")

    /**
     * 把 index ≤ [stableUpTo] 的句子（最后一句已 sliceType=2 稳态，之前的必然也已结束）拼起来立即发出，
     * 更大 index 的句子是用户接着说的下一句，留在 [sentences] 里。
     * 只在 LISTENING 调用。发出返回 true（状态已切 THINKING）。
     */
    private fun commitStableSentences(): Boolean {
        val upTo = stableUpTo
        if (upTo < 0) {
            return false
        }
        val done = sentences.headMap(upTo + 1)
        val text = done.values.joinToString(separator = "")
        done.clear()
        stableUpTo = -1
        committedChunkCeiling = maxOf(committedChunkCeiling, upTo)
        debugI("asr stable up to $upTo, commit \"$text\" pending=\"${joinedText()}\"")
        if (text.isBlank()) {
            return false
        }
        submitUserText(text)
        return true
    }

    /** 切 THINKING、记入对话并发给机器人。主线程调用。 */
    private fun submitUserText(text: String) {
        committedUserText = text
        setState(AICallState.THINKING)
        transcript.add(TranscriptEntry(isFromUser = true, text = text))
        // 下标在这里就捕获，不能等失败回调回来再 indexOfLast { isFromUser }：回调迟到时
        // 用户往往已经说完了新的一轮，那样标红的会是新那条。
        val entryIndex = transcript.lastIndex
        notifyTranscript(pendingUserText = joinedText())
        awaitingReply = true
        mainHandler.postDelayed(replyTimeout, AIConstants.STREAM_REPLY_TIMEOUT_MS)
        sendToRobot(text, entryIndex)
    }

    private fun sendToRobot(text: String, entryIndex: Int) {
        messageInputStore.sendMessage(
            SendMessagePayload.TextSendMessagePayload(text),
            null,
            object : CompletionHandler {
                override fun onSuccess() {
                    debugI("sent to robot: $text")
                }

                override fun onFailure(code: Int, desc: String) {
                    debugE("sendMessage failed: $code $desc")
                    mainHandler.post {
                        if (released) {
                            return@post
                        }
                        // 这条失败属于哪一轮，标红与提示都照做；但状态机只在「还在等这一轮
                        // 回复」时才动。回调可能迟到很久（来电、网络切换），那时本轮早已被
                        // 超时或 pause()/resume() 收拾过，此处再 enterListening() 就会去打断
                        // 一轮已经正常进行的对话，甚至在静音 / 暂停的 IDLE 上凭空开一个会话。
                        markUserEntryFailed(entryIndex)
                        callbacks.onError(R.string.uikit_ai_call_send_failed)
                        if (state != AICallState.THINKING) {
                            debugW("sendMessage failure arrived in $state, skip recovery")
                            return@post
                        }
                        mainHandler.removeCallbacks(replyTimeout)
                        enterListening()
                    }
                }
            }
        )
    }

    // ---------------------------------------------------------------- 流式消息 / TTS

    /** 播放引擎缺席，或机型没有 opus 解码器，本次通话就只能走纯文本。 */
    private fun canPlayTts(): Boolean = playback != null && SystemOpusDecoder.isSupported()

    /**
     * 筛出 AI 机器人发来的流式消息，主线程执行。
     *
     * 消息刚到时 streamID 可能还没就绪，startTTS 会被 V2TIMStreamElem 直接以
     * ERR_INVALID_PARAMETERS 同步拒掉；此时不置 [hasStartedTTS]，等下一次
     * onRecvMessageModified 再试，直到成功为止——这就是「首次观察到非空 streamID」。
     */
    private fun handleStreamMessage(msg: V2TIMMessage) {
        if (released) {
            return
        }
        if (msg.sender != robotUserId) {
            return
        }
        if (msg.elemType != V2TIMMessage.V2TIM_ELEM_TYPE_STREAM) {
            return
        }
        val streamElem = msg.streamElem ?: return
        val msgId = msg.msgID ?: return

        // 只认三种消息：正在等的这一轮的第一条回复、已经绑定的本轮回复的后续增量、
        // 以及可能有迟到旧回复时比已绑定回复更新的那一条（见 [staleReplyPossible]）。
        // 其余都是作废轮次的迟到推送——放进来的话旧回答会继续刷字幕，THINKING 里还会
        // 拿它去起 TTS，或因它 isStreamEnded 把正在等的这一轮误判成「无 TTS 结束」。
        if (msgId in abandonedMsgIds) {
            return
        }
        val inReplyState = state == AICallState.THINKING || state == AICallState.SPEAKING
        val isBoundReply = msgId == streamingMsgId && inReplyState
        val isAwaitedReply = msgId != streamingMsgId && state == AICallState.THINKING && awaitingReply
        val isNewerReply = msgId != streamingMsgId && inReplyState && staleReplyPossible &&
            boundReplyTimestamp >= 0 && msg.timestamp > boundReplyTimestamp
        if (!isBoundReply && !isAwaitedReply && !isNewerReply) {
            debugI("drop stale stream msg $msgId (ts=${msg.timestamp}) in $state")
            // 不在等回复时出现的新消息，是被放弃那一轮的回复（或比已绑定回复更旧）：记下来，
            // 免得它后续的增量被当成新一轮回复认领
            if (msgId != streamingMsgId) {
                markReplyAbandoned(msgId)
            }
            return
        }
        if (isNewerReply) {
            preemptBoundReply(msgId)
        }

        // 新一轮回复：重置上一轮的 TTS 状态
        if (streamingMsgId != msgId) {
            debugI("stream msg $streamingMsgId -> $msgId (ts=${msg.timestamp})")
            streamingMsgId = msgId
            awaitingReply = false
            boundReplyTimestamp = msg.timestamp
            hasStartedTTS = false
            ttsStartPending = false
            // 顶替时沿用被顶掉那条的字幕位置，对话里只留一条回答
            if (!isNewerReply) {
                aiEntryIndex = -1
            }
            enqueuedAudioMs = 0L
            // 新一段 TTS 音频流，丢掉上一段残留的解码器状态。放在这里而不是 startTTS 的
            // 成功回调里：这里一定排在本条消息的任何音频分片之前，而成功回调是另一条
            // 独立的投递路径，可能排到第一个分片之后，把解码器重置到流中间。
            ttsDecoder?.beginStream()
        }

        updateAiTranscript(streamElem.markdown.orEmpty())

        if (!hasStartedTTS && !ttsStartPending && state == AICallState.THINKING && canPlayTts()) {
            ttsStartPending = true
            // 只在成功回调里置 hasStartedTTS，不做乐观置位：失败时保持 false，
            // 下一次 onRecvMessageModified 会再试一遍，直到 streamID 就绪。
            // 回调回来时这条消息可能已被顶替或放弃，不能把结果记到别的消息头上。
            val requestMsgId = msgId
            val attached = ttsSession.startFor(
                message = msg,
                onStarted = {
                    if (streamingMsgId == requestMsgId) {
                        ttsStartPending = false
                        if (!released) {
                            hasStartedTTS = true
                        }
                    }
                },
                onFailed = { code, desc ->
                    if (streamingMsgId == requestMsgId) {
                        ttsStartPending = false
                    }
                    debugW("startTTS rejected ($code $desc), will retry on next modify")
                }
            )
            if (!attached) {
                ttsStartPending = false
            }
        }

        // 流已经结束却始终没能起 TTS（后台一直没下发 streamID，或播放链路本来就不可用）：
        // 降级为只显示文本，立即回到聆听，不用干等 30 秒超时。
        // ttsStartPending 也要排除：请求还在路上就退回聆听的话，随后到达的音频会撞上新一轮会话。
        if (streamElem.isStreamEnded && !hasStartedTTS && !ttsStartPending &&
            state == AICallState.THINKING
        ) {
            debugW("stream ended without TTS, falling back to text only")
            // 这一轮按纯文本正常收完
            staleReplyPossible = false
            enterListening()
        }
    }

    /**
     * 有迟到旧回复的可能时，来了一条比已绑定回复更新的回复：放弃正在等 / 正在播的那条，
     * 改等新回复的音频。调用方随后按「新一轮回复」的流程绑定新消息。主线程调用。
     */
    private fun preemptBoundReply(newMsgId: String) {
        debugI("preempt reply $streamingMsgId -> $newMsgId")
        streamingMsgId?.let { markReplyAbandoned(it) }
        mainHandler.removeCallbacks(speakingWatchdog)
        ttsSession.stop()
        ttsDecoder?.invalidate()
        playback?.flush()
        // 回到「已发问、等回复音频」：新回复的第一个音频分片会再把状态推到 SPEAKING
        setState(AICallState.THINKING)
        mainHandler.removeCallbacks(replyTimeout)
        mainHandler.postDelayed(replyTimeout, AIConstants.STREAM_REPLY_TIMEOUT_MS)
    }

    private fun updateAiTranscript(markdown: String) {
        if (markdown.isEmpty()) {
            return
        }
        if (aiEntryIndex >= 0 && aiEntryIndex < transcript.size) {
            transcript[aiEntryIndex] = TranscriptEntry(isFromUser = false, text = markdown)
        } else {
            transcript.add(TranscriptEntry(isFromUser = false, text = markdown))
            aiEntryIndex = transcript.lastIndex
        }
        notifyTranscript()
    }

    /**
     * 把 TTS 音频分片交给解码线程，主线程执行。
     */
    private fun handleTtsResponse(response: V2TIMTTSResponse) {
        if (released) {
            return
        }
        // stopTTS 是异步的网络往返，已经在路上的分片照样会到达。不按 msgID 过滤的话，
        // 上一轮的音频会被解码后塞进刚清空的播放队列，漏进下一轮——调用顺序挡不住在途回调。
        if (response.msgID != ttsSession.boundMsgId) {
            debugI("drop TTS response of ${response.msgID}, bound to ${ttsSession.boundMsgId}")
            return
        }
        val worker = ttsDecoder ?: return
        worker.decode(
            packets = response.audioDataList.filter { it != null && it.isNotEmpty() },
            isFinished = response.isFinished
        )

        // 收到本轮音频就是 THINKING -> SPEAKING 的触发条件，不等解码线程回来：第一包解出来
        // 就开始出声，而整个分片解完可能要几秒。除此之外的状态说明本轮早已被放弃
        // （例如回复超时后 enterListening 已经把 ttsSession 停掉，这里通常已被 msgID 挡住）
        if (state == AICallState.THINKING) {
            mainHandler.removeCallbacks(replyTimeout)
            setState(AICallState.SPEAKING)
        }
        if (state == AICallState.SPEAKING) {
            armSpeakingWatchdog(0L)
        }
    }

    /**
     * 解码线程处理完一个分片，主线程执行。
     *
     * SPEAKING 看门狗在这里也要续期，不能只在分片到达时续：后台下发比实时快，分片早已收齐时
     * 解码线程可能还在消化积压，只按到达时刻算的话，积压还没解完看门狗就会把播报掐断。
     */
    private fun onTtsChunkDecoded(generation: Int, decodedMs: Long, isFinished: Boolean) {
        if (released || generation != ttsDecoder?.generation) {
            return
        }
        enqueuedAudioMs += decodedMs
        if (state == AICallState.SPEAKING) {
            // 收到 isFinished 之后不会再有分片进来，剩下的全是排队播放的时间，
            // 已入队音频的总时长就是它的上界
            armSpeakingWatchdog(if (isFinished) enqueuedAudioMs else 0L)
        }
    }

    private fun armSpeakingWatchdog(pendingPlaybackMs: Long) {
        mainHandler.removeCallbacks(speakingWatchdog)
        mainHandler.postDelayed(speakingWatchdog, AIConstants.SPEAKING_IDLE_TIMEOUT_MS + pendingPlaybackMs)
    }

    private fun onPlaybackFinished() {
        if (released || state != AICallState.SPEAKING) {
            return
        }
        // 本轮 TTS 的收尾全在 enterListening() 里，这里不重复处理状态。
        // 播放期间留在 sentences 里的只可能是没过打断门限的回声 / 杂音，一并清掉。
        // 一轮正常播完，之前被放弃问题的迟到回复不再等了。
        staleReplyPossible = false
        enterListening()
    }

    /**
     * ASR 文本打断：停 TTS、清播放队列，已识别的开口留给下一轮聆听。
     */
    private fun bargeIn() {
        if (released || state != AICallState.SPEAKING) {
            return
        }
        debugI("barge-in detected")
        enterListening(keepAsrText = true)
    }

    /**
     * 思考态被用户开口打断：回复作废，已识别的新文本留着继续听。
     */
    private fun interruptThinking() {
        if (released || state != AICallState.THINKING) {
            return
        }
        debugI("thinking interrupted by ASR text")
        enterListening(keepAsrText = true)
    }

    /**
     * 重新开一轮聆听。
     *
     * ASR 会话若还活着就直接切 LISTENING，不再 stop/start。只有静音、暂停、启动失败
     * 之后会话是空的，才走 [startNewAsrSession]。
     *
     * @param keepAsrText 打断路径要把已经识别出的开口留下来；播完/超时则丢掉回声残字。
     */
    private fun enterListening(keepAsrText: Boolean = false) {
        debugI("enterListening from $state keepAsrText=$keepAsrText")
        abandonCurrentReply()
        if (!keepAsrText) {
            clearSentences()
        }
        val live = asrSession?.isLive == true
        if (live) {
            awaitingAsrForListening = false
            setState(AICallState.LISTENING)
            notifyTranscript(pendingUserText = joinedText())
            // 打断时那句话若已经稳态，回到聆听立刻发掉
            commitStableSentences()
            return
        }
        awaitingAsrForListening = true
        setState(AICallState.THINKING)
        startNewAsrSession()
    }

    /**
     * 放弃本轮回复：撤掉两个超时、解绑并停掉 TTS、倒掉播放队列、复位 Opus 解码器，
     * 并把本轮回复消息记为作废（之后它的增量由 [handleStreamMessage] 挡掉）。
     *
     * [enterListening]（继续下一轮）与 [setMuted] / [pause]（不再继续）共用这半段。
     * 剩下那半段——切状态、开新的 ASR 会话——两边诉求相反，留在各自方法里。
     */
    private fun abandonCurrentReply() {
        mainHandler.removeCallbacks(replyTimeout)
        mainHandler.removeCallbacks(speakingWatchdog)
        if (awaitingReply) {
            // 问题已发出、回复还没到就放弃了：它的回复之后仍会来，可能混进下一轮
            staleReplyPossible = true
        }
        awaitingReply = false
        boundReplyTimestamp = -1L
        streamingMsgId?.let { markReplyAbandoned(it) }
        ttsSession.stop()
        ttsDecoder?.invalidate()
        playback?.flush()
    }

    private fun markReplyAbandoned(msgId: String) {
        abandonedMsgIds.add(msgId)
        // 只需要挡住最近几轮的迟到推送，不让集合随通话时长无限增长
        while (abandonedMsgIds.size > MAX_ABANDONED_MSG_IDS) {
            abandonedMsgIds.remove(abandonedMsgIds.first())
        }
    }

    /**
     * 把发送失败的那条用户文本标红。
     *
     * [index] 由 [submitUserText] 在入队时捕获。[transcript] 只追加、只就地替换，
     * 不会删除或重排，所以这个下标一直有效。
     */
    private fun markUserEntryFailed(index: Int) {
        if (index !in transcript.indices) {
            return
        }
        transcript[index] = transcript[index].copy(isFailed = true)
        notifyTranscript()
    }

    private fun setState(newState: AICallState) {
        if (state == newState) {
            return
        }
        state = newState
        debugI("state -> $newState")
        callbacks.onStateChanged(newState)
    }

    /**
     * 调试浮层的状态快照，一项一行。主线程调用；音量数字来自采集线程，允许稍旧。
     */
    fun debugSnapshot(): String {
        fun n(value: Double): String = if (value.isNaN()) "NaN" else "%.0f".format(value)
        fun dash(value: String?): String = value?.takeIf { it.isNotEmpty() } ?: "-"
        fun clip(value: String): String = value.replace('\n', ' ').take(160).ifEmpty { "-" }
        return buildString {
            appendLine("state: $state")
            appendLine("muted: $muted")
            appendLine("paused: $pausedExternally")
            appendLine("startFail: $startFailed")
            appendLine("asr: ${asrOpenState()}")
            appendLine("asrId: ${dash(asrSession?.sessionId)}")
            appendLine("asrText: ${clip(joinedText())}")
            appendLine("stableUpTo: $stableUpTo  committedIdx: $committedChunkCeiling")
            appendLine("committed: ${clip(committedUserText)}")
            appendLine("tts: ${ttsOpenState()}")
            appendLine("ttsId: ${dash(ttsSession.boundMsgId)}")
            appendLine("streamId: ${dash(streamingMsgId)}  ts: $boundReplyTimestamp  staleReply: $staleReplyPossible")
            appendLine("llmText: ${clip(currentLlmText())}")
            appendLine(capture?.effectSummary() ?: "capture: -")
            append("rms: ${n(lastRms)}  (均方根能量)")
        }
    }

    /** ON 会话已起来；STARTING 正在等 startRealtimeTranscriber；OFF 完全没开。断句不再 stopRealtimeTranscriber。 */
    private fun asrOpenState(): String {
        val session = asrSession ?: return "OFF"
        return when {
            session.sessionId != null -> "ON"
            session.isStarting -> "STARTING"
            else -> "OFF"
        }
    }

    /** ON 已 startTTS 成功；STARTING 请求在路上；OFF 未绑定。 */
    private fun ttsOpenState(): String = when {
        hasStartedTTS -> "ON"
        ttsStartPending -> "STARTING"
        else -> "OFF"
    }

    private fun currentLlmText(): String {
        if (aiEntryIndex in transcript.indices) {
            return transcript[aiEntryIndex].text
        }
        return transcript.lastOrNull { !it.isFromUser }?.text.orEmpty()
    }

    private fun debugI(msg: String) {
        if (!BuildConfig.DEBUG) {
            return
        }
        Log.i(TAG, msg)
        emitDebug(msg)
    }

    private fun debugW(msg: String) {
        if (!BuildConfig.DEBUG) {
            return
        }
        Log.w(TAG, msg)
        emitDebug("W $msg")
    }

    private fun debugE(msg: String) {
        if (!BuildConfig.DEBUG) {
            return
        }
        Log.e(TAG, msg)
        emitDebug("E $msg")
    }

    private fun emitDebug(msg: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callbacks.onDebugLog(msg)
        } else {
            mainHandler.post { callbacks.onDebugLog(msg) }
        }
    }

    private fun notifyTranscript(pendingUserText: String = "") {
        val entries = if (pendingUserText.isEmpty()) {
            transcript.toList()
        } else {
            transcript + TranscriptEntry(isFromUser = true, text = pendingUserText)
        }
        callbacks.onTranscriptChanged(entries)
    }
}
