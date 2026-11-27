package io.trtc.tuikit.chat.uikit.components.aiassistant

import io.trtc.tuikit.chat.uikit.components.messageinput.asr.RealtimeAsrSpec

/**
 * AI 语音对话相关的固定参数。
 *
 * 音频参数与 IMSDK 后台对 ASR / TTS 的约定一致：Opus / 16K / Mono / 裸 packet。
 *
 * 助手账号由宿主页面的 loader 提供；UIKit 不内置目录地址或业务账号。
 */
object AIConstants {

    const val VOICE_ENTER_ACTION = AIAssistantControlMessages.VOICE_ENTER_ACTION
    const val VOICE_LEAVE_ACTION = AIAssistantControlMessages.VOICE_LEAVE_ACTION
    const val VOICE_SESSION_DESCRIPTION = AIAssistantControlMessages.VOICE_SESSION_DESCRIPTION

    /** 「转人工」按钮发给机器人的文本。后台按这段文字识别转人工请求，所以不随界面语言变化。 */
    const val TRANSFER_TO_HUMAN_TEXT = "转人工"

    const val SAMPLE_RATE = 16000
    const val CHANNEL_COUNT = 1

    /** PCM 帧：20ms @ 16kHz，供 VAD 与音量动画使用。 */
    const val FRAME_SAMPLES = 320
    const val FRAME_BYTES = FRAME_SAMPLES * 2
    const val FRAME_DURATION_MS = 20

    /** VoiceAI SDK 输出的一个 Opus packet 的时长，与 PCM 帧长不同。 */
    const val OPUS_PACKET_DURATION_MS = RealtimeAsrSpec.OPUS_PACKET_DURATION_MS

    /** 每 3 个 packet（120ms）上行一次。 */
    const val UPLOAD_BATCH_PACKETS = RealtimeAsrSpec.UPLOAD_BATCH_PACKETS

    const val ASR_LANGUAGE = "zh"

    /**
     * 聆听态：连续静音多久算说完。
     *
     * 800ms 对「句末收尾」够用，但扛不住自然说话里的换气和想词：逗号级停顿常见 300~800ms，
     * 稍一停就被断句发出去。能量 VAD 做 turn-taking 时，业界常用的静音窗口在 1.2~2.0s
     * （LiveKit 默认 endpointing 0.5s 起、上限 3s；Silero 类方案常见 1~1.5s）。
     * 取 1500ms：比逗号停顿明显更长，说完后到发送大约多等 0.7s，体感仍可接受。
     *
     * 这只是能量层。更稳的做法是「本地静音 + 云端 endpoint / 语义是否说完」一起看，
     * 目前没接实时转写的 isFinished 做断句，也没有标点/完整性判断。
     */
    const val SILENCE_HOLD_MS = 1500L

    /**
     * VAD 噪声基线下限。校准到的 raw 底噪经常只有几十，若不托底，安静房间里门限过低，
     * 风扇/键盘都会像人声，静音断句会乱。
     */
    const val VAD_MIN_NOISE_FLOOR = 100.0

    /**
     * 打断 pre-roll 缓冲的上界：50 个 packet = 2 秒。
     *
     * 缓冲只从人声起点开始装（见 `PreRollBuffer.appendFromSpeechOnset`），正常情况下装到打断
     * 判定成立也就 200~400ms。2 秒是给「说了很久才攒够越线帧」和「startASR 慢」两种情况留的
     * 余量，超了丢最旧的——补推 2 秒以上的历史音频对 ASR 已无意义，反而会把一句话的开头推到
     * 离实时太远的地方。
     */
    const val PRE_ROLL_MAX_PACKETS = 50

    /** 噪声基线校准时长 */
    const val NOISE_CALIBRATION_MS = 300L

    const val ASR_START_TIMEOUT_MS = 8000L

    /**
     * 断句之后等 ASR 尾句结果的收尾窗口上限。
     *
     * ASR 是流式的：VAD 判定静音 800ms 时，后台对尾部音频的识别结果往往还在路上。不等就发，
     * 「给我回复一段很长的文字。」发出去会变成「给我回复一段很长的。」——最后两个字丢了。
     *
     * 正常情况下窗口由 stopASR 的回调提前关掉（一次网络往返，百毫秒量级），700ms 只是后台
     * 不回话时的兜底。上界的代价是等量的发送延迟，所以不能给太大；给太小又回到丢字，
     * 取在两者之间。
     */
    const val ASR_DRAIN_TIMEOUT_MS = 700L

    /**
     * 播放/思考态用 ASR 文本判打断时，至少这么多汉字或字母才认。
     * 单字太容易被漏音、口齿声误触。
     */
    const val ASR_BARGE_IN_MIN_CHARS = 2

    const val STREAM_REPLY_TIMEOUT_MS = 30000L

    /**
     * 播放态看门狗：距上一次收到 TTS 音频分片多久还没播完就认为下行链路断了。
     *
     * 流式播报里分片是跟着大模型出字的节奏来的，正常间隔一两秒；取 10 秒既远高于
     * 这个量级，又明显短于 [STREAM_REPLY_TIMEOUT_MS]——卡在播放态时用户已经听不到声音了，
     * 没必要再让他干等半分钟。收到 isFinished 之后不会再有分片，此时还要额外加上
     * 本轮已入队音频的总时长，那是把它播完所需时间的上界。
     */
    const val SPEAKING_IDLE_TIMEOUT_MS = 10000L
}
