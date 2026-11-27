package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.emojipicker.emojiSizeForTextSize
import io.trtc.tuikit.chat.uikit.components.emojipicker.rememberEmojiText
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageContentMaxWidth
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import kotlin.math.ceil

class TextMessageRenderer : MessageRenderer {
    @Composable
    override fun Render(message: MessageInfo) {
        val colors = LocalTheme.current.colors
        val contentColor = if (message.isSentBySelf) {
            colors.textColorAntiPrimary
        } else {
            colors.textColorPrimary
        }
        val (annotatedString, inlineContent) = rememberEmojiText(
            (message.messagePayload as? TextMessagePayload)?.text.orEmpty(),
            emojiSize = emojiSizeForTextSize(16.sp)
        )
        val textMeasurer = rememberTextMeasurer()
        val contentMaxWidth = LocalMessageContentMaxWidth.current

        SubcomposeLayout(
            modifier = if (contentMaxWidth != Dp.Unspecified) {
                Modifier.widthIn(max = contentMaxWidth)
            } else {
                Modifier
            }
        ) { constraints ->
            val content: @Composable () -> Unit = {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 16.sp,
                    lineHeight = 20.8.sp,
                    text = annotatedString,
                    inlineContent = inlineContent,
                    color = contentColor,
                )
            }
            val placeable = subcompose("measure", content)[0].measure(constraints)
            if (placeable.width <= 0) {
                return@SubcomposeLayout layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
            val horizontalPadding = (12.dp.toPx() * 2).toInt()
            val layoutResult = textMeasurer.measure(
                text = annotatedString,
                constraints = Constraints(
                    maxWidth = (constraints.maxWidth - horizontalPadding).coerceAtLeast(0)
                )
            )
            val compactWidth = if (layoutResult.lineCount <= 1) {
                placeable.width
            } else {
                val maxLineWidth = (0 until layoutResult.lineCount).maxOf { line ->
                    ceil(layoutResult.getLineRight(line) - layoutResult.getLineLeft(line)).toInt()
                }
                TextMessageMeasurePolicy.resolveCompactWidth(
                    measuredWidth = placeable.width,
                    maxLineWidth = maxLineWidth,
                    horizontalPadding = horizontalPadding,
                    maxWidth = constraints.maxWidth
                )
            }
            val finalPlaceable = if (compactWidth in 1 until placeable.width) {
                subcompose("compact", content)[0].measure(
                    constraints.copy(minWidth = compactWidth, maxWidth = compactWidth)
                )
            } else {
                placeable
            }
            layout(finalPlaceable.width, finalPlaceable.height) {
                finalPlaceable.place(0, 0)
            }
        }
    }
}
