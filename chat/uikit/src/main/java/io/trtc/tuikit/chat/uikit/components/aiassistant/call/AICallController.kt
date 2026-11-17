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
import io.trtc.tuikit.chat.uikit.components.aiassistant.audio.EnergyVad
import io.trtc.tuikit.chat.uikit.components.aiassistant.audio.SystemOpusDecoder
import io.trtc.tuikit.chat.uikit.components.aiassistant.audio.PreRollBuffer
import io.trtc.tuikit.chat.uikit.components.aiassistant.audio.TtsDecodeWorker
import io.trtc.tuikit.chat.uikit.components.aiassistant.audio.TtsSession
import io.trtc.tuikit.chat.uikit.components.messageinput.asr.AsrServiceError
import io.trtc.tuikit.chat.uikit.components.messageinput.asr.RealtimeAsrAudioCapture

private const val TAG = "AICallController"

/**
 * 通话页的状态机与全链路编排。
 *
 * 这是整个 AI 语音特性里唯一注册 V2TIMAdvancedMsgListener 的地方，
 * 也是唯一把 audio/ 下那些互不认识的单元串起来的地方。
 *
 * 麦克风和 ASR 会话都常开：LISTENING / THINKING / SPEAKING 都把麦上音频送上去。
 * 断句仍用本地能量 VAD；播放期打断优先看 ASR 有没有吐出不像当前 TTS 的新文本，
 * 能量门限只作备份。静音、暂停、挂断才会 stopRealtimeTranscriber。
 *
 * ## 线程模型
 *
 * 只有两条线程会进到这个类里：
 * - **主线程**：所有公开方法、所有 `Callbacks` 之外的回调、`AsrSession` / `TtsSession`
 *   的全部调用。
 * - **采集线程**：[RealtimeAsrAudioCapture] 的 PCM / Opus 回调，以及 [onAudioFrame] 里的 VAD。
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

        /** 调试浮层用的事件行。采集线程也可能来，实现方要切主线程。 */
        fun onDebugLog(line: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 采集线程写、主线程只读观测量；跨线程语义由 [EnergyVad] 自己保证。 */
    private val vad = EnergyVad(
        frameDurationMs = AIConstants.FRAME_DURATION_MS,
        calibrationFrames = (AIConstants.NOISE_CALIBRATION_MS / AIConstants.FRAME_DURATION_MS).toInt(),
        minNoiseFloor = AIConstants.VAD_MIN_NOISE_FLOOR
    )

    private val messageInputStore = MessageInputStore.create("c2c_$robotUserId")

    /**
     * 打断前后的音频暂存。**本类里唯一被两条线程同时碰的容器**（采集线程写、主线程取），
     * 所以它自己带锁，不靠下面那套 `@Volatile` + 单向所有权的约定——详见 [PreRollBuffer]。
     */
    private val preRoll = PreRollBuffer(AIConstants.PRE_ROLL_MAX_PACKETS)

    // ------------------------------------------------ 跨线程字段：必须 @Volatile
    //
    // 下面这些字段主线程写、采集线程读（或反之）。没有 volatile 就没有可见性保证，
    // 两个方向都会出事：主线程切到 SPEAKING 之后采集线程若仍看到 LISTENING，会继续
    // 把音频推给 ASR——推的正是 AI 自己的声音；反过来切回 LISTENING 后采集线程若仍
    // 看到 THINKING，用户整轮说话会被静默丢弃，表现为「说了没反应」。

    @Volatile
    private var state = AICallState.IDLE

    @Volatile
    private var released = false

    /**
     * 麦克风静音。主线程写、采集线程在 [onAudioFrame] 的第一道守卫上读。
     *
     * 静音期间整帧直接丢弃：不喂 VAD、不编码、也不上行。[EnergyVad] 因此不会再收到任何
     * 帧，两条累积冻结在静音那一刻——不必在这里补 `vad.reset()`，解除静音走的
     * [enterListening] 里就有，而且冻结期间状态是 IDLE，没有任何判定会去读它们。
     */
    @Volatile
    private var muted = false

    /**
     * [sentences] 是否已有非空文本。断句是「静音 800ms **且** 本轮已有文本」的与门，
     * 后半个条件要在采集线程上判，而 [sentences] 是主线程独占的 TreeMap，不能跨线程读
     * （边读边被 put / clear 会抛 ConcurrentModificationException 或读到半截结果）。
     * 所以在主线程把结论落到这个标志上，采集线程只读它。
     */
    @Volatile
    private var hasRecognizedText = false

    /**
     * 打断已判定、新 ASR 会话还没起来的那段窗口（状态是 THINKING，几百毫秒）。
     * 主线程写（[bargeIn] 置起，[takePreRoll] / [discardPreRoll] 落下），采集线程在
     * [onAudioFrame] 上读，用来决定这段窗口里还要不要继续往 [preRoll] 里塞。
     *
     * 没有它的话这段窗口的音频会被直接丢掉，用户正说在兴头上的半句话就断在这里——那是
     * pre-roll 本来要修的同一个毛病，只是换了个位置发生。
     */
    @Volatile
    private var preRollActive = false

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

    /** 本轮 ASR 的识别结果：chunkIndex -> 该句最新完整文本（覆盖式，不是增量） */
    private val sentences = sortedMapOf<Int, String>()

    /**
     * ASR 收尾窗口的代数，每开一次窗 +1。stopRealtimeTranscriber 的完成回调与超时任务各自捕获开窗时的代数，
     * 对不上就说明这一次收尾早已被取消或已经结束，自行退场。
     *
     * 与 [AsrSession] 内部那套启动代数是两回事，各管各的一段生命周期，不要合并：
     * 那一套管的是「startRealtimeTranscriber 请求有没有过期」，这一套管的是「这次收尾还算不算数」。
     * 只在主线程上读写。
     */
    private var drainGeneration = 0

    /**
     * 收尾窗口的超时任务，非 null 即表示**窗口开着**——[handleAsrResponse] 靠它放行迟到的
     * 识别结果。只在主线程上读写。
     */
    private var drainTimeout: Runnable? = null

    /**
     * 刚调了 [AsrSession.start]、等 onStarted 把状态切到 LISTENING。
     * ASR 在播放中途因超时重开时不能走这条路径，否则会把 SPEAKING 盖成聆听。
     */
    private var awaitingAsrForListening = false

    /**
     * 上一轮已经发给机器人的用户文本。常开会话里，后台还会对同一句补尾包，
     * 那些更新不能当成播放期打断。
     */
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
            // 噪声基线是整条链路最容易出问题又最难从现象反推的东西：基线被抬高表现为
            // 「说话没反应」，压低表现为「乱断句」。raw 与 effective 不等即说明被
            // minNoiseFloor / maxNoiseFloor 收敛过，此时判定已退化成固定绝对门限。
            debugI(
                "vad noise floor: raw=${vad.rawNoiseFloor}, " +
                    "effective=${vad.effectiveNoiseFloor}, calibrated=${vad.isCalibrated}"
            )
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
                discardPreRoll()
                if (state == AICallState.THINKING) {
                    mainHandler.removeCallbacks(replyTimeout)
                    mainHandler.postDelayed(replyTimeout, AIConstants.STREAM_REPLY_TIMEOUT_MS)
                }
                return
            }
            val preRollPackets = takePreRoll()
            setState(AICallState.LISTENING)
            if (preRollPackets.isNotEmpty()) {
                debugI(
                    "push pre-roll: ${preRollPackets.size} packets " +
                        "(${preRollPackets.size * AIConstants.FRAME_DURATION_MS}ms)"
                )
                preRollPackets.forEach { asrSession?.pushPacket(it) }
            }
        }

        override fun onStartFailed(code: Int, desc: String) {
            if (released) {
                return
            }
            // 会话起不来，没人会消费这段音频了
            discardPreRoll()
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
        // 首轮不走 enterListening()：此时状态是 IDLE，没有要切出的 SPEAKING，
        // 而 IDLE 的暗色光球恰好就是「还没开始聆听」的正确呈现。
        awaitingAsrForListening = true
        // 编码器刚建好，不要立刻 reset：flush/重建会跟 startRealtimeTranscriber 抢 MediaCodec
        startNewAsrSession(resetEncoder = false)
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
        closeDrainWindow()
        // 先停采集再停会话：采集停下来之后就不会再有新的 post 排到主线程上
        capture?.stop()
        capture = null
        discardPreRoll()
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
     * 解除静音直接复用 [enterListening]：它顺带把 TTS、播放队列、两个超时、VAD 累积、
     * 编码器状态一并归位，与「播完一轮」走的是同一条路。
     */
    fun setMuted(newMuted: Boolean) {
        if (released || muted == newMuted) {
            return
        }
        muted = newMuted
        debugI("muted -> $newMuted")
        if (newMuted) {
            abandonCurrentReply()
            discardPreRoll()
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
     * 不可见之后系统就收走麦克风，`AudioRecord.read()` 开始返回全零。那些零帧会被 VAD
     * 判成静音，若此时已有半句识别文本，800ms 后断句与门成立，半句话就被自动发出去，
     * 接着 TTS 在后台大声外放。
     *
     * [streamingMsgId] / [hasStartedTTS] 一律不碰，理由见 [enterListening]：本轮作废靠
     * [abandonCurrentReply] 里的 `ttsSession.stop()` 解绑 `boundMsgId`，在途分片由
     * [handleTtsResponse] 的 msgID 比对挡掉；提前清这两个字段反而会让迟到的
     * `onRecvMessageModified` 被当成新一轮，把同一条回答重念一遍。
     */
    fun pause() {
        if (released || pausedExternally) {
            return
        }
        pausedExternally = true
        debugI("paused")
        abandonCurrentReply()
        discardPreRoll()
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
        // 来电这一趟下来环境可能已经完全不同了（换了地方、开过免提），基线重测一遍
        vad.resetCalibration()
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
     * 采集线程回调。VAD 是纯计算，就地做；
     * 任何触碰状态机或主线程独占字段的动作都要 post 回主线程。
     */
    private fun onAudioFrame(pcm: ShortArray) {
        if (released) {
            return
        }
        // 静音在这里一刀切断，不往下走任何一步：VAD 不喂。
        // 光球动画不靠这些帧，静音时仍走聆听态呼吸，由 Activity 渲染。
        if (muted) {
            return
        }
        val frameState = state
        val rms = vad.accept(pcm)
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

        if (frameState == AICallState.LISTENING) {
            // 与门：静音够久 **且** 本轮已经识别出东西。少了后半个条件，用户还没开口就会被断句
            if (vad.hasSilenceFor(AIConstants.SILENCE_HOLD_MS) && hasRecognizedText) {
                mainHandler.post { finishListeningTurn() }
            }
        }
    }

    /**
     * 取走 pre-roll 并关掉捕获窗口，主线程调用。
     *
     * 只有真的因为打断而开的会话（[preRollActive]）才拿得到内容；其余情况下顺手清空，
     * 免得上一次打断的残留跨轮次漏到下一个会话上。
     *
     * 置位与取走之间存在一帧的竞态：采集线程可能已经读到 `preRollActive == true` 并在这之后
     * 才把 packet 追加进去，于是缓冲里留下一个 20ms 的孤儿包。**不做额外同步**是故意的——
     * 它既不会被推上去（下一次 [takePreRoll] 若 [preRollActive] 为假会先清空），也活不过
     * 下一次进播放态（`appendFromSpeechOnset` 在说话累积为 0 时清空），为一个 20ms 的包
     * 给采集线程加一把跨方法的锁不划算。
     */
    private fun takePreRoll(): List<ByteArray> {
        if (!preRollActive) {
            preRoll.clear()
            return emptyList()
        }
        preRollActive = false
        return preRoll.drain()
    }

    /** 丢弃 pre-roll：本轮不会再有会话来消费它了。主线程调用。 */
    private fun discardPreRoll() {
        preRollActive = false
        preRoll.clear()
    }

    // ---------------------------------------------------------------- ASR

    /**
     * @param resetEncoder 是否丢弃 Opus 的帧间预测状态。默认 true；**只有打断这条路径传
     *   false**，见下面的说明。
     */
    private fun startNewAsrSession(resetEncoder: Boolean = true) {
        // 必须先 stop()：[AsrSession.start] 在会话还活着（或还在启动中）时只打一条日志就返回，
        // 而调用方这边状态早已切成 THINKING 且没有任何超时在排队——onStarted() 永不到达，
        // 通话就永久卡死在「思考中…」。触发路径是一条迟到的失败回调（来电 / 网络切换把
        // sendMessage 拖到 resume() 之后才回 failure）撞上刚建好的新会话。
        //
        // 对空会话 stop() 是廉价的 no-op；对在途的 start，[AsrSession] 的代数令牌会让那个
        // 迟到回调走「已放弃」分支自行 stopRealtimeTranscriber，紧接着的 start() 重新排一次启动超时。
        asrSession?.stop()
        vad.reset()
        // 麦克风常开而会话按轮次开关，两轮之间音频不连续。不重置的话，
        // 这一轮的第一个字会用上一轮末尾的预测状态去编码——而那恰恰是最不该识别错的字。
        //
        // 打断是唯一的例外：那一刻音频在物理上是连续的（播放态也在逐帧编码），补推的 pre-roll
        // 与随后的实时包本就是同一段码流。在中间插一次重置，等于告诉解码端「这里换了一路音频」，
        // 拼接处会出一段杂音，而那正好压在用户说的第一个字上。
        if (resetEncoder) {
            capture?.requestEncoderReset()
        }
        asrSession?.start()
    }

    private fun handleAsrResponse(response: AsrSession.Transcript) {
        if (released) {
            return
        }
        val session = asrSession ?: return
        val responseId = response.sessionID
        if (responseId.isNullOrEmpty()) {
            return
        }
        val accepted = when {
            responseId == session.sessionId -> true
            drainTimeout != null && responseId == session.drainSessionId -> true
            else -> false
        }
        if (!accepted) {
            return
        }
        if (response.isFinished && responseId == session.sessionId) {
            debugW("ASR session finished remotely, restarting")
            session.noteRemoteFinished(responseId)
            if (!muted && !pausedExternally) {
                val wantListen = state == AICallState.LISTENING || awaitingAsrForListening
                if (wantListen) {
                    awaitingAsrForListening = true
                }
                session.start()
            }
        }
        val text = response.text.orEmpty()
        if (text.isEmpty()) {
            return
        }
        when (state) {
            AICallState.LISTENING -> {
                sentences[response.chunkIndex] = text
                hasRecognizedText = true
                notifyTranscript(pendingUserText = joinedText())
            }
            AICallState.THINKING -> if (drainTimeout != null) {
                sentences[response.chunkIndex] = text
                hasRecognizedText = true
                notifyTranscript(pendingUserText = joinedText())
            } else {
                maybeBargeInFromAsr(response.chunkIndex, text, "thinking")
            }
            AICallState.SPEAKING -> maybeBargeInFromAsr(response.chunkIndex, text, "speaking")
            AICallState.IDLE -> Unit
        }
    }

    /**
     * 思考/播放态收到 ASR 文本。尾包和 TTS 回声丢掉，剩下的当成用户开口。
     */
    private fun maybeBargeInFromAsr(chunkIndex: Int, text: String, where: String) {
        val probe = sortedMapOf<Int, String>().apply {
            putAll(sentences)
            put(chunkIndex, text)
        }
        val candidate = probe.values.joinToString(separator = "")
        val llm = currentLlmText()
        if (!AsrBargeIn.shouldBargeIn(
                asrText = candidate,
                committed = committedUserText,
                llmText = llm,
                minChars = AIConstants.ASR_BARGE_IN_MIN_CHARS
            )
        ) {
            debugI("asr $where ignored: \"$candidate\"")
            return
        }
        sentences[chunkIndex] = text
        hasRecognizedText = true
        val extra = AsrBargeIn.extraBeyondCommitted(candidate, committedUserText)
        if (extra != null) {
            sentences.clear()
            sentences[0] = extra
        }
        debugI("asr barge-in ($where): \"$candidate\" keep=\"${joinedText()}\"")
        notifyTranscript(pendingUserText = joinedText())
        if (state == AICallState.SPEAKING) {
            bargeIn()
        } else {
            interruptThinking()
        }
    }

    private fun clearSentences() {
        sentences.clear()
        hasRecognizedText = false
    }

    /** 按 chunkIndex 升序拼接，句间无分隔符 */
    private fun joinedText(): String = sentences.values.joinToString(separator = "")

    /**
     * 一轮说完：停会话、切 THINKING、开收尾窗口。**这里不再快照文本，也不发送**。
     *
     * 原先是「先 `joinedText()` 拿文本、再 `stop()`」，顺序看着无害，实际上丢字：ASR 是流式的，
     * VAD 判定静音 800ms 的那一刻，后台对尾部音频的识别结果往往还在路上。真机上「给我回复
     * 一段很长的文字。」发出去是「给我回复一段很长的。」，最后两个字就是这么没的。而且
     * `stop()` 之后 sessionID 置空、状态切 THINKING，[handleAsrResponse] 那两道守卫会把随后
     * 到达的尾句全部丢弃——音频送到了，结果被我们自己扔了。
     *
     * 所以文本的快照推迟到 [finishDrain]，[clearSentences] 也跟着推迟（提前清会把收尾窗口
     * 正要并进来的内容清掉）。用户这期间看到的是已识别部分，尾句补全会实时刷上去。
     */
    private fun finishListeningTurn() {
        if (state != AICallState.LISTENING) {
            return
        }
        // 空文本不发送这条语义保留下来，只是判断时机后移到 [finishDrain]。这里不能提前判：
        // 尾句还没回来的时候文本本来就可能是空的，那正是要等的东西。
        val gen = ++drainGeneration
        // 先开窗再 stop()，顺序不能反：stop() 在没有会话可停时会**同步**回调 onStopped，
        // 反过来写的话那次回调会落在窗口开起来之前，finishDrain 提前跑完，随后赋的
        // drainTimeout 就成了一个永远没人关的窗口。
        val timeout = Runnable { finishDrain(gen) }
        drainTimeout = timeout
        mainHandler.postDelayed(timeout, AIConstants.ASR_DRAIN_TIMEOUT_MS)
        // 会话常开：不断 ASR，只等一小段把尾句收进来。
        vad.reset()

        setState(AICallState.THINKING)
        notifyTranscript(pendingUserText = joinedText())
    }

    /**
     * 收尾窗口结束：快照文本并发送。stopRealtimeTranscriber 回调与超时两者先到者触发，另一个由 [drainGeneration]
     * 作废。主线程执行。
     *
     * [replyTimeout] 仍然与**实际发送时刻**对齐，而不是与断句时刻对齐——等待尾句的这几百毫秒
     * 不该算进「等 AI 回复」的预算里。
     */
    private fun finishDrain(gen: Int) {
        if (released || gen != drainGeneration) {
            return
        }
        closeDrainWindow()

        val text = joinedText()
        committedUserText = text
        clearSentences()

        // 收尾期间状态被别人改走了（迟到的 TTS 分片把 THINKING 推成了 SPEAKING，等等），
        // 这一轮已经不归我们管，发出去只会和正在进行的那一轮撞车。
        if (state != AICallState.THINKING) {
            debugW("asr drain finished in $state, skip sending \"$text\"")
            return
        }
        if (text.isEmpty()) {
            // 理论上到不了这里：断句是「静音够久 **且** 已有非空文本」的与门。真到了说明
            // 后台把已识别的内容又收回去了，此时会话已经停掉，必须自己回到聆听，
            // 否则通话就永久停在 THINKING。
            debugW("asr drain finished with empty text, back to listening")
            enterListening()
            return
        }

        transcript.add(TranscriptEntry(isFromUser = true, text = text))
        // 下标在这里就捕获，不能等失败回调回来再 indexOfLast { isFromUser }：回调迟到时
        // 用户往往已经说完了新的一轮，那样标红的会是新那条。
        val entryIndex = transcript.lastIndex
        notifyTranscript()
        mainHandler.postDelayed(replyTimeout, AIConstants.STREAM_REPLY_TIMEOUT_MS)
        sendToRobot(text, entryIndex)
    }

    /**
     * 关掉收尾窗口并作废还没跑的那一半（超时任务 / stopRealtimeTranscriber 回调）。
     *
     * 两类调用方，语义不同但动作一样：
     * - [finishDrain] 正常收尾结束；
     * - [abandonCurrentReply]（打断 / 静音 / 暂停）与 [release] 半路取消——这几条路径上这一轮
     *   本来就不发送了，识别结果留着也没用，Handler 回调更不能泄漏到下一轮去。
     *
     * 代数 +1 是关键：超时任务已经 post 出去时 `removeCallbacks` 未必来得及拦住它（它可能
     * 已经在消息队列里排着了），代数对不上它自己会退场。
     */
    private fun closeDrainWindow() {
        drainGeneration++
        drainTimeout?.let { mainHandler.removeCallbacks(it) }
        drainTimeout = null
        asrSession?.endDrain()
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

        // 新一轮回复：重置上一轮的 TTS 状态
        if (streamingMsgId != msg.msgID) {
            debugI("stream msg $streamingMsgId -> ${msg.msgID}")
            streamingMsgId = msg.msgID
            hasStartedTTS = false
            ttsStartPending = false
            aiEntryIndex = -1
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
            // 下一次 onRecvMessageModified 会再试一遍，直到 streamID 就绪
            val attached = ttsSession.startFor(
                message = msg,
                onStarted = {
                    ttsStartPending = false
                    if (!released) {
                        hasStartedTTS = true
                    }
                },
                onFailed = { code, desc ->
                    ttsStartPending = false
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
        //
        // 收尾窗口开着时（drainTimeout != null）同样要排除。那几百毫秒里状态也是 THINKING，但
        // 本轮的问题还没发出去，根本没有「在等的回复」可降级；放行的话 enterListening() 会把
        // 收尾窗口一并取消，用户刚说完的那句话连发都没发出去就没了。能走到这儿的只有上一轮
        // 迟到重投的流式消息。
        if (streamElem.isStreamEnded && !hasStartedTTS && !ttsStartPending &&
            state == AICallState.THINKING && drainTimeout == null
        ) {
            debugW("stream ended without TTS, falling back to text only")
            enterListening()
        }
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
        // 本轮 TTS 的收尾全在 enterListening() 里，这里不重复处理状态
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
        if (released || state != AICallState.THINKING || drainTimeout != null) {
            return
        }
        debugI("thinking interrupted by ASR text")
        enterListening(keepAsrText = true)
    }

    /**
     * 重新开一轮聆听。
     *
     * ASR 会话若还活着就直接切 LISTENING，不再 stop/start。只有静音、暂停、远端超时
     * 之后会话是空的，才走 [startNewAsrSession]。
     *
     * @param keepAsrText 打断路径要把已经识别出的开口留下来；播完/超时则丢掉回声残字。
     */
    private fun enterListening(resetEncoder: Boolean = true, keepAsrText: Boolean = false) {
        debugI("enterListening from $state keepAsrText=$keepAsrText")
        abandonCurrentReply()
        if (!keepAsrText) {
            clearSentences()
        }
        vad.reset()
        discardPreRoll()
        val live = asrSession?.isLive == true
        if (live) {
            awaitingAsrForListening = false
            setState(AICallState.LISTENING)
            notifyTranscript(pendingUserText = joinedText())
            return
        }
        awaitingAsrForListening = true
        setState(AICallState.THINKING)
        startNewAsrSession(resetEncoder)
    }

    /**
     * 放弃本轮回复：撤掉两个超时与收尾窗口、解绑并停掉 TTS、倒掉播放队列、复位 Opus 解码器。
     *
     * [enterListening]（继续下一轮）与 [setMuted] / [pause]（不再继续）共用这半段。
     * 剩下那半段——切状态、开新的 ASR 会话——两边诉求相反，留在各自方法里。
     *
     * 收尾窗口放在这里取消，是因为所有「本轮不作数了」的路径都必经此处：打断、静音、暂停、
     * 回复超时、发送失败。窗口开着的时候状态是 THINKING，这些路径恰好都能在那时进来。
     */
    private fun abandonCurrentReply() {
        mainHandler.removeCallbacks(replyTimeout)
        mainHandler.removeCallbacks(speakingWatchdog)
        closeDrainWindow()
        ttsSession.stop()
        ttsDecoder?.invalidate()
        playback?.flush()
    }

    /**
     * 把发送失败的那条用户文本标红。
     *
     * [index] 由 [finishListeningTurn] 在入队时捕获。[transcript] 只追加、只就地替换，
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
     * 调试浮层的状态快照。主线程调用；VAD 数字来自采集线程，允许稍旧。
     *
     * 一项一行，字段说明见 demo/docs/ai-call-debug-overlay.md。
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
            appendLine("committed: ${clip(committedUserText)}")
            appendLine("tts: ${ttsOpenState()}")
            appendLine("ttsId: ${dash(ttsSession.boundMsgId)}")
            appendLine("streamId: ${dash(streamingMsgId)}")
            appendLine("llmText: ${clip(currentLlmText())}")
            appendLine("aec: ON  (VOICE_COMMUNICATION)")
            appendLine(capture?.effectSummary() ?: "aec: -")
            appendLine(
                "silence: ${vad.silenceAccumulationMs}/${AIConstants.SILENCE_HOLD_MS}  (静音累积/断句门限)"
            )
            append("rms: ${n(lastRms)}  (均方根能量)")
        }
    }

    /** ON 会话已起来；STARTING 正在等 startRealtimeTranscriber；OFF 完全没开。断句不再 stopRealtimeTranscriber。 */
    private fun asrOpenState(): String {
        val session = asrSession ?: return "OFF"
        return when {
            session.sessionId != null -> "ON"
            session.isStarting -> "STARTING"
            session.drainSessionId != null -> "DRAINING"
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
