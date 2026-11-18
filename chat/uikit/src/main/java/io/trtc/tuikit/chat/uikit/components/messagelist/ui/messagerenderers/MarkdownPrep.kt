package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

/**
 * 交给 Markwon 之前的纯文本预处理。
 */
internal fun prepareMarkdown(source: String, isStreaming: Boolean): String {
    val sanitized = if (isStreaming) sanitizeIncompleteMarkdown(source) else source
    return padCjkAdjacentEmphasis(dedupeLinkOnlyLines(sanitized))
}

private val linkOnlyLine = Regex("^\\s*(?:[-*+]|\\d+[.)])?\\s*\\[[^\\]]*]\\(\\s*([^)\\s]+)[^)]*\\)\\s*$")

/**
 * 知识库回答的「参考资料」常把同一篇文档列好几次（多个片段命中同一页）。
 * 只删除「整行只有一个链接、且 URL 与前面某行完全相同」的重复行；代码围栏内原样保留。
 */
internal fun dedupeLinkOnlyLines(source: String): String {
    if (!source.contains("](")) {
        return source
    }
    val seen = HashSet<String>()
    var inFence = false
    var changed = false
    val out = ArrayList<String>()
    for (line in source.split('\n')) {
        if (line.trimStart().startsWith("```")) {
            inFence = !inFence
        }
        if (!inFence) {
            val url = linkOnlyLine.find(line)?.groupValues?.get(1)
            if (url != null && !seen.add(url)) {
                changed = true
                continue
            }
        }
        out.add(line)
    }
    return if (changed) out.joinToString("\n") else source
}

internal fun sanitizeIncompleteMarkdown(source: String): String {
    if (source.isEmpty()) {
        return source
    }
    var text = source
    val fenceCount = Regex("^```", RegexOption.MULTILINE).findAll(text).count()
    if (fenceCount % 2 == 1) {
        text += "\n```"
    }
    val lines = text.split('\n').toMutableList()
    val last = lines.lastOrNull()?.trim().orEmpty()
    if (last.startsWith("|") && last.count { it == '|' } < 2) {
        lines.removeAt(lines.lastIndex)
        text = lines.joinToString("\n")
    }
    return text
}

/**
 * CommonMark 把汉字、拉丁字母都当成 word character。`只有**粗体**才能` 里的 `**`
 * 同时是 left/right-flanking，且前面不是标点，不能开强调，星号会原样显示。
 *
 * 在成对的 `**` / `__` 紧贴字母或数字时补一个空格，让侧翼单向，解析器才能加粗。
 * 代码围栏和行内代码原样保留。
 */
internal fun padCjkAdjacentEmphasis(source: String): String {
    if (source.indexOf("**") < 0 && source.indexOf("__") < 0) {
        return source
    }
    val afterStars = padPairedMarkers(source, "**")
    return padPairedMarkers(afterStars, "__")
}

private fun padPairedMarkers(source: String, marker: String): String {
    val output = StringBuilder(source.length + 8)
    var index = 0
    var inFence = false
    var inInlineCode = false
    val length = source.length
    val markerLength = marker.length

    while (index < length) {
        val fenceEnd = fenceLineEnd(source, index)
        if (fenceEnd != null) {
            inFence = !inFence
            output.append(source, index, fenceEnd)
            index = fenceEnd
            continue
        }
        if (inFence) {
            output.append(source[index])
            index += 1
            continue
        }
        if (source[index] == '`') {
            inInlineCode = !inInlineCode
            output.append('`')
            index += 1
            continue
        }
        if (!inInlineCode && source.startsWith(marker, index)) {
            val close = findClosingMarker(source, index + markerLength, marker)
            if (close == null) {
                output.append(marker)
                index += markerLength
                continue
            }
            val pairEnd = close + markerLength
            val previous = output.lastOrNull()
            if (previous != null && previous.needsEmphasisPad()) {
                output.append(' ')
            }
            output.append(source, index, pairEnd)
            val next = source.getOrNull(pairEnd)
            if (next != null && next.needsEmphasisPad()) {
                output.append(' ')
            }
            index = pairEnd
            continue
        }
        output.append(source[index])
        index += 1
    }
    return output.toString()
}

private fun findClosingMarker(source: String, from: Int, marker: String): Int? {
    var index = from
    while (index < source.length) {
        if (source[index] == '`') {
            val closingTick = source.indexOf('`', index + 1)
            if (closingTick < 0) {
                return null
            }
            index = closingTick + 1
            continue
        }
        val fenceEnd = fenceLineEnd(source, index)
        if (fenceEnd != null) {
            return null
        }
        if (source.startsWith(marker, index)) {
            return index
        }
        index += 1
    }
    return null
}

/**
 * CommonMark 允许围栏前最多 3 个空格。命中则返回该行结束位置（含换行）。
 */
private fun fenceLineEnd(source: String, start: Int): Int? {
    if (start != 0 && source[start - 1] != '\n') {
        return null
    }
    var index = start
    var indent = 0
    while (index < source.length && (source[index] == ' ' || source[index] == '\t')) {
        if (indent == 3) {
            return null
        }
        indent += 1
        index += 1
    }
    if (index + 2 >= source.length) {
        return null
    }
    if (source[index] != '`' || source[index + 1] != '`' || source[index + 2] != '`') {
        return null
    }
    while (index < source.length && source[index] != '\n') {
        index += 1
    }
    if (index < source.length) {
        index += 1
    }
    return index
}

private fun Char.needsEmphasisPad(): Boolean = isLetterOrDigit()
