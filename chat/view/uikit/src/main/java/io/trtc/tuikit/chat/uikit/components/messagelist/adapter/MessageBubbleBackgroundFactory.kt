package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageBubbleAppearance
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageBubbleBackground
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageBubbleStroke
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.BubbleStyle
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens

internal class MessageBubbleBackgroundFactory(
    private val context: Context,
    private val config: MessageListConfigProtocol,
    private val density: Float,
    private val bubbleContainer: MaxWidthFrameLayout,
    private val bubbleContentContainer: LinearLayout
) {
    fun apply(
        bubbleStyle: BubbleStyle,
        isLeftAligned: Boolean,
        isSelf: Boolean,
        colors: ColorTokens
    ): FloatArray {
        val bubbleBinding = MessageBubbleBinder.resolveBubbleStyle(
            bubbleStyle = bubbleStyle,
            config = config,
            isLeftAligned = isLeftAligned,
            isSelf = isSelf
        )
        val effectiveStyle = bubbleBinding.effectiveStyle
        val highlightCornerRadii = resolveCornerRadii(
            appearance = bubbleBinding.appearance,
            fallbackRadii = computeHighlightCornerRadii(effectiveStyle, isLeftAligned, density),
            density = density
        )

        when (effectiveStyle) {
            BubbleStyle.DEFAULT -> {
                val minimumSize = bubbleBinding.appearance?.minimumSize
                val defaultMinSize = (DEFAULT_BUBBLE_MIN_SIZE_DP * density).toInt()
                bubbleContainer.minimumWidth = minimumSize?.width?.dpToPx() ?: defaultMinSize
                bubbleContainer.minimumHeight = minimumSize?.height?.dpToPx() ?: defaultMinSize
                bubbleContainer.background = createDefaultBubbleBackground(
                    appearance = bubbleBinding.appearance,
                    fallbackColor = if (isSelf) colors.bgColorBubbleOwn else colors.bgColorBubbleReciprocal,
                    cornerRadii = highlightCornerRadii
                )
                val contentInsets = bubbleBinding.appearance?.contentInsets
                if (contentInsets != null) {
                    bubbleContentContainer.setPadding(
                        contentInsets.left.dpToPx(),
                        contentInsets.top.dpToPx(),
                        contentInsets.right.dpToPx(),
                        contentInsets.bottom.dpToPx()
                    )
                } else {
                    bubbleContentContainer.setPadding(0, 0, 0, 0)
                }
            }

            BubbleStyle.CARD -> {
                bubbleContainer.minimumWidth = 0
                bubbleContainer.minimumHeight = 0
                bubbleContainer.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = CARD_BUBBLE_CORNER_RADIUS_DP * density
                    setColor(colors.bgColorDialog)
                    setStroke((1 * density).toInt(), colors.strokeColorPrimary)
                }
                bubbleContentContainer.setPadding(0, 0, 0, 0)
            }

            BubbleStyle.NONE -> {
                bubbleContainer.minimumWidth = 0
                bubbleContainer.minimumHeight = 0
                bubbleContainer.background = null
                bubbleContentContainer.setPadding(0, 0, 0, 0)
            }

            else -> {
                val minimumSize = bubbleBinding.appearance?.minimumSize
                val defaultMinSize = (DEFAULT_BUBBLE_MIN_SIZE_DP * density).toInt()
                bubbleContainer.minimumWidth = minimumSize?.width?.dpToPx() ?: defaultMinSize
                bubbleContainer.minimumHeight = minimumSize?.height?.dpToPx() ?: defaultMinSize
                bubbleContainer.background = createDefaultBubbleBackground(
                    appearance = bubbleBinding.appearance,
                    fallbackColor = if (isSelf) colors.bgColorBubbleOwn else colors.bgColorBubbleReciprocal,
                    cornerRadii = highlightCornerRadii
                )
                val contentInsets = bubbleBinding.appearance?.contentInsets
                if (contentInsets != null) {
                    bubbleContentContainer.setPadding(
                        contentInsets.left.dpToPx(),
                        contentInsets.top.dpToPx(),
                        contentInsets.right.dpToPx(),
                        contentInsets.bottom.dpToPx()
                    )
                } else {
                    bubbleContentContainer.setPadding(0, 0, 0, 0)
                }
            }
        }

        return highlightCornerRadii
    }

    private fun createDefaultBubbleBackground(
        appearance: MessageBubbleAppearance?,
        fallbackColor: Int,
        cornerRadii: FloatArray
    ): Drawable {
        return when (val background = appearance?.background) {
            is MessageBubbleBackground.DrawableResource -> {
                ContextCompat.getDrawable(context, background.resId)?.copyForBubbleOrNull()
                    ?: createShapeBubbleBackground(background = null, fallbackColor, cornerRadii, appearance?.stroke)
            }

            is MessageBubbleBackground.DrawableValue -> {
                background.drawable.copyForBubbleOrNull()
                    ?: createShapeBubbleBackground(background = null, fallbackColor, cornerRadii, appearance?.stroke)
            }

            else -> createShapeBubbleBackground(background, fallbackColor, cornerRadii, appearance?.stroke)
        }
    }

    private fun createShapeBubbleBackground(
        background: MessageBubbleBackground?,
        fallbackColor: Int,
        cornerRadii: FloatArray,
        stroke: MessageBubbleStroke?
    ): GradientDrawable {
        return when (background) {
            is MessageBubbleBackground.Gradient -> {
                GradientDrawable(background.direction.toGradientOrientation(), background.colors.toIntArray())
            }

            else -> GradientDrawable().apply {
                val color = (background as? MessageBubbleBackground.Color)?.color ?: fallbackColor
                setColor(color)
            }
        }.apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadii = cornerRadii.copyOf()
            if (stroke != null) {
                setStroke(stroke.width.dpToPx(), stroke.color)
            }
        }
    }

    private fun Drawable.copyForBubbleOrNull(): Drawable? {
        return constantState?.newDrawable()?.mutate()
    }

    private fun MessageBubbleBackground.GradientDirection.toGradientOrientation(): GradientDrawable.Orientation {
        return when (this) {
            MessageBubbleBackground.GradientDirection.TOP_BOTTOM -> GradientDrawable.Orientation.TOP_BOTTOM
            MessageBubbleBackground.GradientDirection.BOTTOM_TOP -> GradientDrawable.Orientation.BOTTOM_TOP
            MessageBubbleBackground.GradientDirection.LEFT_RIGHT -> GradientDrawable.Orientation.LEFT_RIGHT
            MessageBubbleBackground.GradientDirection.RIGHT_LEFT -> GradientDrawable.Orientation.RIGHT_LEFT
            MessageBubbleBackground.GradientDirection.TOP_LEFT_BOTTOM_RIGHT -> GradientDrawable.Orientation.TL_BR
            MessageBubbleBackground.GradientDirection.BOTTOM_RIGHT_TOP_LEFT -> GradientDrawable.Orientation.BR_TL
            else -> GradientDrawable.Orientation.TOP_BOTTOM
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * density).toInt()
    }

    companion object {
        internal fun resolveCornerRadii(
            appearance: MessageBubbleAppearance?,
            fallbackRadii: FloatArray,
            density: Float
        ): FloatArray {
            val radius = appearance?.cornerRadius ?: return fallbackRadii
            return floatArrayOf(
                (radius.topLeft?.dpToPx(density)?.toFloat()) ?: fallbackRadii[0],
                (radius.topLeft?.dpToPx(density)?.toFloat()) ?: fallbackRadii[1],
                (radius.topRight?.dpToPx(density)?.toFloat()) ?: fallbackRadii[2],
                (radius.topRight?.dpToPx(density)?.toFloat()) ?: fallbackRadii[3],
                (radius.bottomRight?.dpToPx(density)?.toFloat()) ?: fallbackRadii[4],
                (radius.bottomRight?.dpToPx(density)?.toFloat()) ?: fallbackRadii[5],
                (radius.bottomLeft?.dpToPx(density)?.toFloat()) ?: fallbackRadii[6],
                (radius.bottomLeft?.dpToPx(density)?.toFloat()) ?: fallbackRadii[7]
            )
        }

        internal fun computeHighlightCornerRadii(
            bubbleStyle: BubbleStyle,
            isLeftAligned: Boolean,
            density: Float
        ): FloatArray {
            return when (bubbleStyle) {
                BubbleStyle.DEFAULT -> {
                    val radius = 10 * density
                    val smallRadius = 2 * density
                    val topStartRadius = if (isLeftAligned) smallRadius else radius
                    val topEndRadius = if (isLeftAligned) radius else smallRadius
                    floatArrayOf(
                        topStartRadius, topStartRadius,
                        topEndRadius, topEndRadius,
                        radius, radius,
                        radius, radius
                    )
                }

                BubbleStyle.CARD -> FloatArray(8) { CARD_BUBBLE_CORNER_RADIUS_DP * density }

                BubbleStyle.NONE -> FloatArray(8) { HIGHLIGHT_DEFAULT_CORNER_RADIUS_DP * density }

                else -> {
                    val radius = 10 * density
                    val smallRadius = 2 * density
                    val topStartRadius = if (isLeftAligned) smallRadius else radius
                    val topEndRadius = if (isLeftAligned) radius else smallRadius
                    floatArrayOf(
                        topStartRadius, topStartRadius,
                        topEndRadius, topEndRadius,
                        radius, radius,
                        radius, radius
                    )
                }
            }
        }

        private fun Int.dpToPx(density: Float): Int {
            return (this * density).toInt()
        }
    }
}
