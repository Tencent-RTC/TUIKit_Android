package io.trtc.tuikit.chat.uikit.components.aiassistant.call

/**
 * 通话页四态。
 *
 * 麦克风和 ASR 在 LISTENING / THINKING / SPEAKING 都开着。IDLE 是静音、暂停或未开始。
 */
enum class AICallState {
    /** 未开始或已结束 */
    IDLE,

    /** 聆听中：ASR 上行，按 sliceType 稳态断句 */
    LISTENING,

    /** 已发出文本，等待 AI 的流式回复；用户开口即打断。ASR 会话重建期间也暂用此态 */
    THINKING,

    /** TTS 播放中，ASR 仍在上行，可用识别文本打断 */
    SPEAKING
}

/**
 * 文本态展示的一条对话。
 *
 * [isFailed] 用于标记发送失败的那条，文本态里会标红。
 */
data class TranscriptEntry(
    val isFromUser: Boolean,
    val text: String,
    val isFailed: Boolean = false
)
