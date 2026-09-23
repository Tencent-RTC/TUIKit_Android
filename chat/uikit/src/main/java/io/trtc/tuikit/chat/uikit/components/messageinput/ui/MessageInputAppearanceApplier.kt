package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputButtonAppearance
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputFill

internal object MessageInputAppearanceApplier {

    /**
     * Canvas.drawRoundRect clamps the radius to half the shorter side, so a radius this large
     * renders as a pill whatever the measured size turns out to be. That is what
     * [MessageInputButtonAppearance.CORNER_RADIUS_PILL] resolves to.
     */
    private const val PILL_CORNER_RADIUS_DP = 999f

    data class ActionButtonDefaults(
        val sizeDp: Int,
        @ColorInt val iconTint: Int,
        val fill: MessageInputFill = MessageInputFill.None,
        val cornerRadiusDp: Int? = null
    )

    fun fillDrawable(
        fill: MessageInputFill?,
        cornerRadiusDp: Int?,
        density: Float
    ): Drawable? {
        val radius = when (cornerRadiusDp) {
            null -> 0f
            MessageInputButtonAppearance.CORNER_RADIUS_PILL -> PILL_CORNER_RADIUS_DP * density
            else -> cornerRadiusDp * density
        }
        return when (fill) {
            null, MessageInputFill.None -> null

            is MessageInputFill.Color -> GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(fill.color)
                cornerRadius = radius
            }

            is MessageInputFill.Gradient -> GradientDrawable(
                fill.direction.toGradientOrientation(),
                fill.colors.toIntArray()
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius
            }
        }
    }

    /**
     * Resolves which drawable a toolbar button should show. The host's [MessageInputButtonAppearance]
     * wins over [defaultIcon] so that flat glyphs can be supplied for buttons that sit on a filled
     * background, while state-driven defaults (emoji <-> keyboard, for instance) keep working when
     * the host supplies nothing.
     */
    @DrawableRes
    fun resolveIcon(
        appearance: MessageInputButtonAppearance?,
        isActive: Boolean,
        @DrawableRes defaultIcon: Int
    ): Int {
        if (appearance == null) {
            return defaultIcon
        }
        val activeIcon = appearance.activeIconRes?.takeIf { isActive }
        return activeIcon ?: appearance.iconRes ?: defaultIcon
    }

    fun applyActionButton(
        container: FrameLayout,
        iconView: ImageView,
        labelView: TextView?,
        appearance: MessageInputButtonAppearance?,
        defaults: ActionButtonDefaults
    ) {
        val density = container.resources.displayMetrics.density
        container.background = fillDrawable(
            appearance?.fill ?: defaults.fill,
            appearance?.cornerRadiusDp ?: defaults.cornerRadiusDp,
            density
        )

        val title = appearance?.title
        if (title != null && labelView != null) {
            applyTitleMode(container, iconView, labelView, appearance, defaults, density)
        } else {
            labelView?.visibility = View.GONE
            applyIconMode(container, iconView, appearance, defaults, density)
        }
    }

    private fun applyIconMode(
        container: FrameLayout,
        iconView: ImageView,
        appearance: MessageInputButtonAppearance?,
        defaults: ActionButtonDefaults,
        density: Float
    ) {
        iconView.visibility = View.VISIBLE
        container.setPadding(0, 0, 0, 0)
        container.updateSize(
            width = ((appearance?.widthDp ?: defaults.sizeDp) * density).toInt(),
            height = ((appearance?.heightDp ?: defaults.sizeDp) * density).toInt()
        )
        iconView.setColorFilter(appearance?.iconTint ?: defaults.iconTint)

        val iconParams = iconView.layoutParams as? FrameLayout.LayoutParams ?: return
        val iconSizeDp = appearance?.iconSizeDp
        if (iconSizeDp == null) {
            iconParams.width = FrameLayout.LayoutParams.MATCH_PARENT
            iconParams.height = FrameLayout.LayoutParams.MATCH_PARENT
        } else {
            iconParams.width = (iconSizeDp * density).toInt()
            iconParams.height = (iconSizeDp * density).toInt()
        }
        iconParams.gravity = Gravity.CENTER
        iconView.layoutParams = iconParams
    }

    private fun applyTitleMode(
        container: FrameLayout,
        iconView: ImageView,
        labelView: TextView,
        appearance: MessageInputButtonAppearance,
        defaults: ActionButtonDefaults,
        density: Float
    ) {
        iconView.visibility = View.GONE
        labelView.visibility = View.VISIBLE
        labelView.text = appearance.title
        labelView.setTextColor(appearance.titleColor ?: defaults.iconTint)
        appearance.titleSizeSp?.let { labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, it) }

        val horizontalPadding = ((appearance.horizontalPaddingDp ?: 0) * density).toInt()
        container.setPadding(horizontalPadding, 0, horizontalPadding, 0)
        container.updateSize(
            width = appearance.widthDp?.let { (it * density).toInt() }
                ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            height = ((appearance.heightDp ?: defaults.sizeDp) * density).toInt()
        )
    }

    private fun View.updateSize(width: Int, height: Int) {
        val params = layoutParams ?: return
        if (params.width == width && params.height == height) {
            return
        }
        params.width = width
        params.height = height
        layoutParams = params
    }

    private fun MessageInputFill.GradientDirection.toGradientOrientation(): GradientDrawable.Orientation {
        return when (this) {
            MessageInputFill.GradientDirection.TOP_BOTTOM -> GradientDrawable.Orientation.TOP_BOTTOM
            MessageInputFill.GradientDirection.BOTTOM_TOP -> GradientDrawable.Orientation.BOTTOM_TOP
            MessageInputFill.GradientDirection.LEFT_RIGHT -> GradientDrawable.Orientation.LEFT_RIGHT
            MessageInputFill.GradientDirection.RIGHT_LEFT -> GradientDrawable.Orientation.RIGHT_LEFT
            MessageInputFill.GradientDirection.TOP_LEFT_BOTTOM_RIGHT -> GradientDrawable.Orientation.TL_BR
            MessageInputFill.GradientDirection.BOTTOM_RIGHT_TOP_LEFT -> GradientDrawable.Orientation.BR_TL
        }
    }
}
