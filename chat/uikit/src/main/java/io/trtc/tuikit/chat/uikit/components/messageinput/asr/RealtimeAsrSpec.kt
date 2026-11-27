package io.trtc.tuikit.chat.uikit.components.messageinput.asr

/**
 * 长按录音实时识别的固定参数。
 *
 * 音频格式由 IMSDK 的 sendASRAudioData 规定：Opus / 16kHz / 单声道 / 裸 packet。
 */
internal object RealtimeAsrSpec {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_COUNT = 1

    /** PCM 帧：20ms @ 16kHz，供 VAD 与音量动画使用。 */
    const val FRAME_SAMPLES = 320
    const val FRAME_BYTES = FRAME_SAMPLES * 2
    const val FRAME_DURATION_MS = 20

    /**
     * VoiceAI SDK 输出的一个 Opus packet 的时长（`onReceiveLocalOpus` 的 frameDurationMs）。
     * 与 PCM 帧长不同，按时长换算 packet 数时必须用这个值。
     */
    const val OPUS_PACKET_DURATION_MS = 40

    /** 每 3 个 packet（120ms）上行一次，兼顾识别延迟与调用频率。 */
    const val UPLOAD_BATCH_PACKETS = 3

    /**
     * 补推积压音频时，单次 sendASRAudioData 最多带这么多 packet（50 个 = 2 秒）。
     *
     * 会话就绪时要把 [PRE_ROLL_CAPACITY] 那点缓冲一次推完。全塞进一个请求失败了整段一起丢；
     * 照实时节奏按 [UPLOAD_BATCH_PACKETS] 切又会一次性打出几十个请求。取两者之间——切出来的
     * 几片是并行在飞的，实测 9 秒音频 5 片 111ms 全部 ack。
     *
     * 注意 sendASRAudioData 的文档建议单次 100~200ms 音频，那说的是实时推流时「延迟 vs 调用
     * 频率」的取舍，不是包体上限；补推是一次性追赶，属于另一种情形。
     */
    const val UPLOAD_MAX_PACKETS_PER_CALL = 50

    /**
     * 按下就把音频推上去，而不是等手指滑到「转文字」。
     *
     * 必须为 true。曾经取 false 想省额度——按下只建会话不推音频，滑过去再把攒下的整段
     * 补推——两次实测都证明这条路走不通：后台只按实时语速出字，突发灌进去的历史音频换不
     * 来对应的文本。9 秒连续说话 111ms 就推完且逐包 ack，1.3 秒后后台的第一条结果仍是
     * 「第 0 句、3 个字」，整段 24 秒只出 47 字。省下的不是额度，是识别结果。
     *
     * 代价照旧：每一次长按都真的走一遍 ASR，哪怕用户只是想发条语音。按识别时长计费的话
     * 这笔账要自己算。
     */
    const val UPLOAD_FROM_PRESS = true

    /**
     * 上行开关打开前的音频缓冲上界。
     *
     * 它只需要盖住 startASR 那一个网络往返（实测 300~500ms）：会话一就绪就把这段补推上去，
     * 用户说的开头一个字都不会丢。往返迟迟不回时 [START_TIMEOUT_MS] 会放弃本次识别，所以
     * 按这个上限来定容量，再多给 1 秒余量——录音的计时起点和采集的第一帧不是同一时刻。
     *
     * 不要按整段录音时长来定：后台不会加速消化突发音频，攒得再多也换不来文字。
     */
    val PRE_ROLL_CAPACITY: Int
        get() = ((START_TIMEOUT_MS + 1000L) / OPUS_PACKET_DURATION_MS).toInt()

    /** startASR 迟迟不回时放弃本次识别，回落到「上传整段音频再识别」。 */
    const val START_TIMEOUT_MS = 5000L

    /**
     * 松手后等最后一批音频 ack 完再 stopASR 的上限。
     *
     * stopASR 一到服务端就把会话关掉，和它并发在飞的 sendASRAudioData 会撞上
     * `140002 session is closed`，松手前最后那几十毫秒的音频就丢了。所以先等尾包落地。
     * 实测一次往返百毫秒出头，给 400ms 兜底；后台不回话时宁可关掉会话也不能卡着不发。
     */
    const val TAIL_UPLOAD_TIMEOUT_MS = 400L

    /**
     * stopASR 之后等尾句结果的收尾窗口。
     *
     * ASR 是流式的，松手那一刻后台对尾部音频的识别往往还在路上，不等就会丢最后几个字。
     *
     * 注意不能拿 stopASR 的回调当结束信号：它只是「会话已关闭」的 ack（实测 119ms 就回），
     * 不代表结果都到齐了。真正的结束信号是带 `isFinished` 的那条响应，这里是它不来时的兜底。
     */
    const val DRAIN_TIMEOUT_MS = 800L
}
