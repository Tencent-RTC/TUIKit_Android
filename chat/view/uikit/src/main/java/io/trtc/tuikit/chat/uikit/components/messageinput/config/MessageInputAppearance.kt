package io.trtc.tuikit.chat.uikit.components.messageinput.config

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

/**
 * Background fill for the input bar and its buttons.
 *
 * The theme only carries one flat color per token, so gradients have to be expressed here rather
 * than through [io.trtc.tuikit.atomicx.theme.tokens.ColorTokens].
 */
sealed class MessageInputFill {
    object None : MessageInputFill()

    data class Color(@ColorInt val color: Int) : MessageInputFill()

    data class Gradient(
        val colors: List<Int>,
        val direction: GradientDirection = GradientDirection.LEFT_RIGHT
    ) : MessageInputFill() {
        init {
            require(colors.size >= MIN_GRADIENT_COLOR_COUNT) {
                "Gradient fill requires at least two colors."
            }
        }
    }

    enum class GradientDirection {
        TOP_BOTTOM,
        BOTTOM_TOP,
        LEFT_RIGHT,
        RIGHT_LEFT,
        TOP_LEFT_BOTTOM_RIGHT,
        BOTTOM_RIGHT_TOP_LEFT
    }

    private companion object {
        const val MIN_GRADIENT_COLOR_COUNT = 2
    }
}

/**
 * Appearance of one toolbar button (voice, emoji, more, send).
 *
 * Every field is nullable and `null` means "keep the value the theme resolved". Use
 * [CORNER_RADIUS_PILL] to get a fully rounded shape regardless of the measured size.
 *
 * Setting [title] switches the button from an icon to a text label, which is what a "发送" style
 * send button needs; [iconRes]/[activeIconRes] let the host swap in flat glyphs when the button
 * sits on a filled background, and [activeIconRes] is used while the button's panel is open.
 */
data class MessageInputButtonAppearance(
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val iconSizeDp: Int? = null,
    val cornerRadiusDp: Int? = null,
    val fill: MessageInputFill? = null,
    @ColorInt val iconTint: Int? = null,
    @DrawableRes val iconRes: Int? = null,
    @DrawableRes val activeIconRes: Int? = null,
    val title: CharSequence? = null,
    @ColorInt val titleColor: Int? = null,
    val titleSizeSp: Float? = null,
    val horizontalPaddingDp: Int? = null
) {
    companion object {
        const val CORNER_RADIUS_PILL = -1
    }
}

/**
 * Appearance of the editable area, which also hosts the press-to-talk button.
 */
data class MessageInputFieldAppearance(
    val fill: MessageInputFill? = null,
    val cornerRadiusDp: Int? = null,
    val minHeightDp: Int? = null,
    val horizontalPaddingDp: Int? = null,
    @ColorInt val textColor: Int? = null,
    @ColorInt val hintColor: Int? = null,
    val hintText: CharSequence? = null
)

/**
 * Appearance of the more-actions panel.
 *
 * [itemFills] is indexed by action position and cycles when it holds fewer entries than the panel
 * has actions, which is how a per-entry color sequence is expressed without the panel knowing
 * anything about the individual actions.
 */
data class MessageInputMorePanelAppearance(
    val fill: MessageInputFill? = null,
    val itemSizeDp: Int? = null,
    val itemIconSizeDp: Int? = null,
    val itemCornerRadiusDp: Int? = null,
    val itemFills: List<MessageInputFill> = emptyList(),
    @ColorInt val itemIconTint: Int? = null,
    @ColorInt val itemTitleColor: Int? = null,
    val itemTitleSizeSp: Float? = null
)

data class MessageInputAppearance(
    val fill: MessageInputFill? = null,
    val showTopDivider: Boolean? = null,
    val field: MessageInputFieldAppearance? = null,
    val audioRecordButton: MessageInputButtonAppearance? = null,
    val emojiButton: MessageInputButtonAppearance? = null,
    val moreButton: MessageInputButtonAppearance? = null,
    val sendButton: MessageInputButtonAppearance? = null,
    val morePanel: MessageInputMorePanelAppearance? = null
)
