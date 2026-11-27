package io.trtc.tuikit.chat.uikit.components.search.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.emojipicker.emojiSizeForTextSize
import io.trtc.tuikit.chat.uikit.components.emojipicker.rememberEmojiText

open class HighlightTextItem(
    val text: String,
    val keywords: String = "",
)

@Composable
fun HighlightTitle(
    text: String,
    keywords: String,
    modifier: Modifier = Modifier
) {
    HighlightedText(
        text = text,
        keywords = keywords,
        textColor = LocalTheme.current.colors.textColorPrimary,
        modifier = modifier,
        textSize = 16.sp,
        weight = FontWeight.Normal
    )
}

@Composable
fun HighlightSecondary(
    textList: List<HighlightTextItem>,
    modifier: Modifier = Modifier
) {
    HighlightedText(
        textList = textList,
        textColor = LocalTheme.current.colors.textColorTertiary,
        modifier = modifier,
        textSize = 14.sp,
        weight = FontWeight.Normal
    )
}

@Composable
fun HighlightSecondary(
    text: String,
    keywords: String,
    modifier: Modifier = Modifier
) {
    HighlightedText(
        text = text,
        keywords = keywords,
        textColor = LocalTheme.current.colors.textColorTertiary,
        modifier = modifier,
        textSize = 14.sp,
        weight = FontWeight.Normal
    )
}

@Composable
fun EmojiHighlightText(
    text: String,
    keywords: String,
    modifier: Modifier = Modifier,
    textColor: Color = LocalTheme.current.colors.textColorTertiary,
    textSize: TextUnit = 14.sp,
    maxLines: Int = 1,
    weight: FontWeight = FontWeight.Normal
) {
    val highlightColor = LocalTheme.current.colors.textColorLink
    val (emojiText, inlineContent) = rememberEmojiText(text, emojiSize = emojiSizeForTextSize(textSize))

    val highlightedText = remember(emojiText, keywords, textColor, highlightColor, textSize, weight) {
        buildAnnotatedString {
            append(emojiText)
            val normalStyle = SpanStyle(
                color = textColor,
                fontSize = textSize,
                fontWeight = weight
            )
            addStyle(normalStyle, 0, length)
            if (keywords.isNotBlank()) {
                val highlightStyle = normalStyle.copy(color = highlightColor)
                val regex = Regex.escape(keywords).toRegex(RegexOption.IGNORE_CASE)
                regex.findAll(emojiText.text).forEach { match ->
                    addStyle(highlightStyle, match.range.first, match.range.last + 1)
                }
            }
        }
    }

    Text(
        text = highlightedText,
        inlineContent = inlineContent,
        color = textColor,
        fontSize = textSize,
        fontWeight = weight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun HighlightedText(
    textList: List<HighlightTextItem>,
    textColor: Color,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 14.sp,
    maxLines: Int = 1,
    weight: FontWeight = FontWeight.Normal,
) {
    val colors = LocalTheme.current.colors
    val normalStyle = SpanStyle(
        fontWeight = weight,
        fontSize = textSize,
        color = textColor
    )

    val annotatedString = buildAnnotatedString {
        textList.forEach { item ->
            val highlightStyle = normalStyle.copy(
                color = colors.textColorLink
            )

            if (item.keywords.isEmpty()) {
                withStyle(normalStyle) { append(item.text) }
            } else {
                val regex = Regex.escape(item.keywords).toRegex(RegexOption.IGNORE_CASE)
                val matches = regex.findAll(item.text)
                var lastIndex = 0

                matches.forEach { match ->
                    withStyle(normalStyle) {
                        append(item.text.substring(lastIndex, match.range.first))
                    }
                    withStyle(highlightStyle) {
                        append(item.text.substring(match.range))
                    }
                    lastIndex = match.range.last + 1
                }
                if (lastIndex < item.text.length) {
                    withStyle(normalStyle) {
                        append(item.text.substring(lastIndex))
                    }
                }
            }
        }
    }
    Text(
        text = annotatedString,
        fontSize = textSize,
        color = textColor,
        maxLines = maxLines,
        fontWeight = weight,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun HighlightedText(
    text: String,
    keywords: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 14.sp,
    maxLines: Int = 1,
    weight: FontWeight = FontWeight.Normal,
) {
    HighlightedText(
        textList = listOf(HighlightTextItem(text, keywords)),
        textColor = textColor,
        modifier = modifier,
        textSize = textSize,
        maxLines = maxLines,
        weight = weight
    )
}
