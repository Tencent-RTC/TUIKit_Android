package io.trtc.tuikit.chat.uikit.components.search.utils
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

object HighlightUtils {

    fun highlight(
        text: String,
        keywords: String,
        highlightColor: Int,
        defaultColor: Int
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder(text)
        builder.setSpan(
            ForegroundColorSpan(defaultColor),
            0,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        if (keywords.isBlank()) return builder

        val lowerText = text.lowercase()
        val lowerKeyword = keywords.lowercase()
        var startIndex = 0
        while (startIndex < lowerText.length) {
            val index = lowerText.indexOf(lowerKeyword, startIndex)
            if (index < 0) break
            builder.setSpan(
                ForegroundColorSpan(highlightColor),
                index,
                index + keywords.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = index + keywords.length
        }
        return builder
    }

    fun highlightMultiple(
        segments: List<HighlightSegment>,
        highlightColor: Int,
        defaultColor: Int
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        for (segment in segments) {
            val start = builder.length
            builder.append(segment.text)
            val end = builder.length
            builder.setSpan(
                ForegroundColorSpan(defaultColor),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (segment.keywords.isNotBlank()) {
                val lowerText = segment.text.lowercase()
                val lowerKeyword = segment.keywords.lowercase()
                var searchStart = 0
                while (searchStart < lowerText.length) {
                    val index = lowerText.indexOf(lowerKeyword, searchStart)
                    if (index < 0) break
                    builder.setSpan(
                        ForegroundColorSpan(highlightColor),
                        start + index,
                        start + index + segment.keywords.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    searchStart = index + segment.keywords.length
                }
            }
        }
        return builder
    }
}

data class HighlightSegment(
    val text: String,
    val keywords: String = ""
)
