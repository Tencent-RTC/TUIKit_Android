package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.trtc.tuikit.chat.uikit.components.theme.Colors
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageBubbleAppearance
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageBubbleBackground
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlin.math.roundToInt

private val DefaultBubbleRadius = 10.dp
private val DefaultBubbleSmallRadius = 2.dp
private val CardBubbleRadius = 12.dp

internal data class MessageBubblePresentation(
    val style: BubbleStyle,
    val appearance: MessageBubbleAppearance?
)

internal fun resolveMessageBubblePresentation(
    renderConfig: MessageRenderConfig,
    config: MessageListConfigProtocol,
    isLeftAligned: Boolean,
    isSelf: Boolean
): MessageBubblePresentation {
    val style = when {
        renderConfig.bubbleStyle != BubbleStyle.NONE -> renderConfig.bubbleStyle
        renderConfig.useDefaultBubble -> BubbleStyle.DEFAULT
        else -> BubbleStyle.NONE
    }
    val appearance = if (style == BubbleStyle.DEFAULT) {
        resolveMergedBubbleAppearance(config, isLeftAligned, isSelf)
    } else {
        null
    }
    return MessageBubblePresentation(
        style = style,
        appearance = appearance
    )
}

internal fun resolveMergedBubbleAppearance(
    config: MessageListConfigProtocol,
    isLeftAligned: Boolean,
    isSelf: Boolean
): MessageBubbleAppearance? {
    val positionAppearance = if (isLeftAligned) {
        config.leftBubbleAppearance
    } else {
        config.rightBubbleAppearance
    }
    val senderAppearance = if (isSelf) {
        config.ownBubbleAppearance
    } else {
        config.incomingBubbleAppearance
    }
    val base = config.defaultBubbleAppearance ?: MessageBubbleAppearance()
    val resolved = base.mergeWith(positionAppearance).mergeWith(senderAppearance)
    return resolved.takeUnless { it.isEmpty }
}

@Composable
internal fun MessageBubbleSurface(
    message: MessageInfo,
    isAggregation: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalTheme.current.colors
    val config = LocalMessageListConfig.current
    val renderConfig = LocalMessageRenderConfig.current
    val highlightManager = runCatching { LocalHighlightManager.current }.getOrNull()
    val isLeftAligned = MessageItemDisplayPolicy.resolveIsLeftAligned(
        alignment = config.alignment,
        isSelf = message.isSentBySelf,
        isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    )
    val presentation = resolveMessageBubblePresentation(
        renderConfig = renderConfig,
        config = config,
        isLeftAligned = isLeftAligned,
        isSelf = message.isSentBySelf
    )
    val shape = resolveBubbleShape(
        style = presentation.style,
        isLeftAligned = isLeftAligned,
        appearance = presentation.appearance
    )
    val fallbackColor = when (presentation.style) {
        BubbleStyle.DEFAULT -> {
            if (message.isSentBySelf) colors.bgColorBubbleOwn else colors.bgColorBubbleReciprocal
        }
        BubbleStyle.CARD -> colors.bgColorDialog
        BubbleStyle.NONE -> Colors.Transparent
    }
    val highlightShape = if (presentation.style == BubbleStyle.NONE) RectangleShape else shape
    val minWidth = presentation.appearance?.minimumSize?.width?.dp
        ?: if (presentation.style == BubbleStyle.DEFAULT) MessageItemDisplayPolicy.DEFAULT_BUBBLE_MIN_SIZE_DP.dp else 0.dp
    val minHeight = presentation.appearance?.minimumSize?.height?.dp
        ?: if (presentation.style == BubbleStyle.DEFAULT) MessageItemDisplayPolicy.DEFAULT_BUBBLE_MIN_SIZE_DP.dp else 0.dp
    val contentInsets = presentation.appearance?.contentInsets
    val stroke = presentation.appearance?.stroke
    val clipShape = if (presentation.style == BubbleStyle.NONE) RectangleShape else shape

    Column(
        modifier = modifier
            .then(
                if (presentation.style == BubbleStyle.DEFAULT || presentation.style == BubbleStyle.CARD) {
                    Modifier.defaultMinSize(minWidth, minHeight)
                } else {
                    Modifier
                }
            )
            .then(
                if (presentation.style == BubbleStyle.CARD) {
                    Modifier.border(1.dp, colors.strokeColorPrimary, shape)
                } else if (stroke != null && presentation.style == BubbleStyle.DEFAULT) {
                    Modifier.border(stroke.width.dp, Color(stroke.color), shape)
                } else {
                    Modifier
                }
            )
            .clip(clipShape)
            .then(
                bubbleBackgroundModifier(
                    style = presentation.style,
                    appearance = presentation.appearance,
                    fallbackColor = fallbackColor,
                    shape = highlightShape,
                    highlightKey = message.msgID.orEmpty(),
                    highlightManager = highlightManager
                )
            )
            .then(
                when {
                    contentInsets != null && presentation.style == BubbleStyle.DEFAULT -> {
                        Modifier.padding(
                            start = contentInsets.left.dp,
                            top = contentInsets.top.dp,
                            end = contentInsets.right.dp,
                            bottom = contentInsets.bottom.dp
                        )
                    }
                    else -> Modifier
                }
            )
    ) {
        content()
    }
}

@Composable
private fun bubbleBackgroundModifier(
    style: BubbleStyle,
    appearance: MessageBubbleAppearance?,
    fallbackColor: Color,
    shape: Shape,
    highlightKey: String,
    highlightManager: io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.HighlightManager?
): Modifier {
    val context = LocalContext.current
    val background = appearance?.background
    val drawable = remember(background) {
        when (background) {
            is MessageBubbleBackground.DrawableResource -> {
                ContextCompat.getDrawable(context, background.resId)?.constantState?.newDrawable()?.mutate()
            }
            is MessageBubbleBackground.DrawableValue -> {
                background.drawable.constantState?.newDrawable()?.mutate() ?: background.drawable
            }
            else -> null
        }
    }
    val colorOrBrushModifier = when {
        drawable != null -> Modifier.drawBehind {
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(drawContext.canvas.nativeCanvas)
        }
        background is MessageBubbleBackground.Gradient -> {
            Modifier.background(brush = background.toBrush(), shape = shape)
        }
        background is MessageBubbleBackground.Color -> {
            Modifier.background(Color(background.color), shape)
        }
        else -> Modifier.background(fallbackColor, shape)
    }
    val overlayBaseColor = when {
        background is MessageBubbleBackground.Color -> Color(background.color)
        else -> fallbackColor
    }
    val highlightModifier = if (highlightManager != null) {
        Modifier.highlightBackground(
            color = overlayBaseColor,
            highlightKey = highlightKey,
            shape = shape,
            highlightManager = highlightManager
        )
    } else {
        Modifier
    }
    return colorOrBrushModifier.then(highlightModifier)
}

private fun MessageBubbleBackground.Gradient.toBrush(): Brush {
    val composeColors = colors.map { Color(it) }
    return when (direction) {
        MessageBubbleBackground.GradientDirection.TOP_BOTTOM -> Brush.verticalGradient(composeColors)
        MessageBubbleBackground.GradientDirection.BOTTOM_TOP -> Brush.verticalGradient(composeColors.reversed())
        MessageBubbleBackground.GradientDirection.LEFT_RIGHT -> Brush.horizontalGradient(composeColors)
        MessageBubbleBackground.GradientDirection.RIGHT_LEFT -> Brush.horizontalGradient(composeColors.reversed())
        MessageBubbleBackground.GradientDirection.TOP_LEFT_BOTTOM_RIGHT -> Brush.linearGradient(
            colors = composeColors,
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
        MessageBubbleBackground.GradientDirection.BOTTOM_RIGHT_TOP_LEFT -> Brush.linearGradient(
            colors = composeColors.reversed(),
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }
}

private fun resolveBubbleShape(
    style: BubbleStyle,
    isLeftAligned: Boolean,
    appearance: MessageBubbleAppearance?
): Shape {
    val radius = appearance?.cornerRadius
    return when (style) {
        BubbleStyle.CARD -> RoundedCornerShape(CardBubbleRadius)
        BubbleStyle.NONE -> RectangleShape
        BubbleStyle.DEFAULT -> {
            val topLeftRadius = radius?.topLeft?.dp
                ?: if (isLeftAligned) DefaultBubbleSmallRadius else DefaultBubbleRadius
            val topRightRadius = radius?.topRight?.dp
                ?: if (isLeftAligned) DefaultBubbleRadius else DefaultBubbleSmallRadius
            val bottomRightRadius = radius?.bottomRight?.dp ?: DefaultBubbleRadius
            val bottomLeftRadius = radius?.bottomLeft?.dp ?: DefaultBubbleRadius
            AbsoluteRoundedCornerShape(
                topLeft = topLeftRadius,
                topRight = topRightRadius,
                bottomRight = bottomRightRadius,
                bottomLeft = bottomLeftRadius
            )
        }
    }
}
