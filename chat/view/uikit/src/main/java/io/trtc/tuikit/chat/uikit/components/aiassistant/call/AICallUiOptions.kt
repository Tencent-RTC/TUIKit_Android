package io.trtc.tuikit.chat.uikit.components.aiassistant.call

import java.io.Serializable

/**
 * AI 通话页的可选外观与引导配置。不传或全部为默认值时，通话页保持原样。
 *
 * @property auroraStyle 流光光球 + 深色渐变背景
 * @property plainTranscript 字幕去掉 Markdown 标记、链接和「参考资料」段落，只留口语化正文
 * @property sampleHints 用户还没开口时，在状态文案下方轮播的示例问题
 * @property showButtonLabels 底部三个按钮下方显示文字标签
 * @property showCallTimer 顶部标签显示通话时长，替代「自由对话」
 */
data class AICallUiOptions(
    val auroraStyle: Boolean = false,
    val plainTranscript: Boolean = false,
    val sampleHints: List<String> = emptyList(),
    val showButtonLabels: Boolean = false,
    val showCallTimer: Boolean = false,
) : Serializable {

    /** 序列化进 Intent 时把列表换成 ArrayList，避免不可序列化的 List 实现。 */
    internal fun forIntent(): AICallUiOptions = copy(sampleHints = ArrayList(sampleHints))

    companion object {
        private const val serialVersionUID = 1L
    }
}
