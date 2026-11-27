package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

internal class MessageItemHighlightController(
    private val bubbleContainer: MaxWidthFrameLayout,
    density: Float
) {
    private var highlightAnimator: ValueAnimator? = null
    private var highlightedMessageId: String? = null
    private var highlightOverlay: GradientDrawable? = null
    private var currentHighlightCornerRadii: FloatArray =
        FloatArray(8) { HIGHLIGHT_DEFAULT_CORNER_RADIUS_DP * density }

    fun updateCornerRadii(cornerRadii: FloatArray) {
        currentHighlightCornerRadii = cornerRadii
    }

    fun apply(
        message: MessageInfo,
        colors: ColorTokens,
        shouldHighlight: Boolean,
        onHighlightConsumed: (String) -> Unit
    ) {
        val messageId = message.msgID
        if (!shouldHighlight || messageId.isNullOrBlank()) {
            clear()
            return
        }

        if (highlightedMessageId == messageId && highlightAnimator?.isRunning == true) {
            val existingOverlay = highlightOverlay
            if (existingOverlay != null) {
                existingOverlay.cornerRadii = currentHighlightCornerRadii.copyOf()
                if (bubbleContainer.foreground !== existingOverlay) {
                    bubbleContainer.foreground = existingOverlay
                }
            }
            return
        }

        clear()
        highlightedMessageId = messageId
        val bubbleColor = if (message.isSentBySelf) {
            colors.bgColorBubbleOwn
        } else {
            colors.bgColorBubbleReciprocal
        }
        val isDarkBubble =
            ColorUtils.calculateLuminance(bubbleColor) < HIGHLIGHT_DARK_BUBBLE_LUMINANCE_THRESHOLD
        val highlightColor = if (isDarkBubble) {
            ColorUtils.blendARGB(
                colors.textColorWarning,
                Color.WHITE,
                HIGHLIGHT_DARK_BUBBLE_LIGHTEN_RATIO
            )
        } else {
            colors.textColorWarning
        }
        val highlightMaxAlpha = if (isDarkBubble) HIGHLIGHT_ALPHA_DARK_BUBBLE else HIGHLIGHT_ALPHA
        val overlay = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = currentHighlightCornerRadii.copyOf()
            setColor(ColorUtils.setAlphaComponent(highlightColor, 0))
        }
        highlightOverlay = overlay
        bubbleContainer.foreground = overlay
        highlightAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = HIGHLIGHT_FLASH_DURATION
            repeatMode = ValueAnimator.REVERSE
            repeatCount = HIGHLIGHT_FLASH_COUNT * 2 - 1
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                overlay.setColor(
                    ColorUtils.setAlphaComponent(
                        highlightColor,
                        (fraction * highlightMaxAlpha).toInt()
                    )
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (highlightedMessageId == messageId) {
                        clear()
                        onHighlightConsumed(messageId)
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    clear()
                }
            })
            start()
        }
    }

    fun clear() {
        val animator = highlightAnimator
        highlightAnimator = null
        highlightedMessageId = null
        highlightOverlay = null
        bubbleContainer.foreground = null
        animator?.cancel()
    }
}
