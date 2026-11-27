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

    /** PCM 帧：20ms @ 16kHz，播放缓冲按它估算。 */
    const val FRAME_SAMPLES = 320
    const val FRAME_BYTES = FRAME_SAMPLES * 2

    /** VoiceAI SDK 输出的一个 Opus packet 的时长，与 PCM 帧长不同。 */
    const val OPUS_PACKET_DURATION_MS = RealtimeAsrSpec.OPUS_PACKET_DURATION_MS

    /** 每 3 个 packet（120ms）上行一次。 */
    const val UPLOAD_BATCH_PACKETS = RealtimeAsrSpec.UPLOAD_BATCH_PACKETS

    const val ASR_LANGUAGE = "zh"

    const val ASR_START_TIMEOUT_MS = 8000L

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
