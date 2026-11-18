package io.trtc.tuikit.chat.uikit.components.aiassistant.call.ui

/**
 * 把 AI 的 Markdown 回答转成适合在通话字幕里显示的纯文本：
 * 去掉「参考资料」之后的整段、链接只留文字、裸 URL 删掉、强调/标题/引用/代码标记去掉。
 */
internal object SpokenText {

    private val referenceHeader = Regex(
        "^\\s*(?:#{1,6}\\s*)?(?:\\*\\*|__)?\\s*(?:参考资料|参考文档|参考链接|相关文档|References?|Sources?)\\s*(?:\\*\\*|__)?\\s*[:：]?\\s*$",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE),
    )
    private val link = Regex("!?\\[([^\\]]*)]\\([^)]*\\)")
    private val bareUrl = Regex("https?://\\S+")
    private val fence = Regex("^```.*$", RegexOption.MULTILINE)
    private val heading = Regex("^\\s{0,3}#{1,6}\\s+", RegexOption.MULTILINE)
    private val quote = Regex("^\\s*>\\s?", RegexOption.MULTILINE)
    private val emphasis = Regex("\\*\\*|__|~~|`")
    private val bullet = Regex("^(\\s*)[-*+]\\s+", RegexOption.MULTILINE)
    private val extraBlankLines = Regex("\\n{3,}")
    private val trailingSpaces = Regex("[ \\t]+\\n")

    fun from(markdown: String): String {
        if (markdown.isBlank()) {
            return markdown
        }
        var text = markdown
        referenceHeader.find(text)?.let { text = text.substring(0, it.range.first) }
        text = link.replace(text) { it.groupValues[1] }
        text = bareUrl.replace(text, "")
        text = fence.replace(text, "")
        text = heading.replace(text, "")
        text = quote.replace(text, "")
        text = bullet.replace(text) { it.groupValues[1] + "• " }
        text = emphasis.replace(text, "")
        text = trailingSpaces.replace(text, "\n")
        text = extraBlankLines.replace(text, "\n\n")
        return text.trim()
    }
}
