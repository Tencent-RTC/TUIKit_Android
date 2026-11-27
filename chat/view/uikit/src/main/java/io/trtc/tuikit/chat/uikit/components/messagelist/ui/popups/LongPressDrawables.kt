package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import androidx.core.graphics.ColorUtils
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens

internal object LongPressDrawables {

    fun createActionItemRipple(colors: ColorTokens, density: Float): Drawable {
        return createHoverableBackground(
            colors = colors,
            cornerRadius = 8 * density,
            rippleAlpha = 26
        )
    }

    fun createEmojiCellRipple(colors: ColorTokens, cellSize: Int): Drawable {
        return createHoverableBackground(
            colors = colors,
            cornerRadius = cellSize / 2f,
            rippleAlpha = 28
        )
    }

    private fun createHoverableBackground(
        colors: ColorTokens,
        cornerRadius: Float,
        rippleAlpha: Int
    ): Drawable {
        val hoverColor = colors.dropdownColorHover
        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(hoverColor)
        }
        val defaultDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(Color.TRANSPARENT)
        }
        val stateListDrawable = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), defaultDrawable)
        }
        val maskDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(Color.WHITE)
        }
        return RippleDrawable(
            ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(colors.textColorPrimary, rippleAlpha)
            ),
            stateListDrawable,
            maskDrawable
        )
    }
}
