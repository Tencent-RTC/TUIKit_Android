package com.trtc.uikit.roomkit.inroom.common.toolbarbutton

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.dp
import com.trtc.uikit.roomkit.base.util.dpF
import kotlin.math.min
import kotlin.math.roundToInt

open class RoomToolbarButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var icon: Drawable?
        get() = baseIcon
        set(value) {
            if (baseIcon == value) return
            baseIcon = value
            invalidate()
        }

    private var baseIcon: Drawable? = null

    var roomDisabledIcon: Drawable? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var isActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var badgeCount: Int? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var hasFault: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var isRoomDisabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            alpha = if (value) ROOM_DISABLED_ALPHA else 1f
            invalidate()
        }

    private val effectiveIcon: Drawable?
        get() = if (isRoomDisabled) roomDisabledIcon ?: baseIcon else baseIcon

    var micVolume: Int? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val pressedAnimator get() = animate().setDuration(PRESSED_DURATION.toLong())

    private val faultBadgeDrawable: Drawable? = AppCompatResources.getDrawable(context, R.drawable.roomkit_ic_fault_badge)
    private val faultGlyphDrawable: Drawable? = AppCompatResources.getDrawable(context, R.drawable.roomkit_ic_fault_glyph)

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val scale = if (pressed) PRESSED_SCALE else 1f
        pressedAnimator.scaleX(scale).scaleY(scale)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)
        drawMicLevel(canvas)
        drawIcon(canvas)
        drawBadge(canvas)
        if (!isRoomDisabled) drawFaultBadge(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                isActive -> color(R.color.roomkit_bg_hover_strong)
                else -> color(R.color.roomkit_bg_hover)
            }
        }
        val radius = min(width, height) / 2f
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
    }

    private fun drawIcon(canvas: Canvas) {
        val icon = effectiveIcon ?: return
        val size = context.dp(ICON_SIZE_DP).toFloat()
        val intrinsicWidth = icon.intrinsicWidth.takeIf { it > 0 } ?: size.toInt()
        val intrinsicHeight = icon.intrinsicHeight.takeIf { it > 0 } ?: size.toInt()
        val scale = min(size / intrinsicWidth, size / intrinsicHeight)
        val drawWidth = intrinsicWidth * scale
        val drawHeight = intrinsicHeight * scale
        val left = ((width - drawWidth) / 2).roundToInt()
        val top = ((height - drawHeight) / 2).roundToInt()
        icon.setBounds(left, top, left + drawWidth.roundToInt(), top + drawHeight.roundToInt())
        icon.draw(canvas)
    }

    private fun drawMicLevel(canvas: Canvas) {
        val volume = micVolume ?: return
        val size = context.dp(ICON_SIZE_DP)
        val iconLeft = (width - size) / 2f
        val iconTop = (height - size) / 2f
        val cavityLeft = iconLeft + size * CAVITY_X
        val cavityTop = iconTop + size * CAVITY_Y
        val cavityWidth = size * CAVITY_WIDTH
        val cavityHeight = size * CAVITY_HEIGHT
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.save()
        canvas.clipRect(cavityLeft, cavityTop, cavityLeft + cavityWidth, cavityTop + cavityHeight)
        paint.color = color(R.color.roomkit_success)
        val fillHeight = cavityHeight * min(volume.coerceAtLeast(0), MAX_VOLUME) / MAX_VOLUME.toFloat()
        canvas.drawRect(
            cavityLeft,
            cavityTop + cavityHeight - fillHeight,
            cavityLeft + cavityWidth,
            cavityTop + cavityHeight,
            paint,
        )
        canvas.restore()
    }

    private fun drawBadge(canvas: Canvas) {
        val count = badgeCount ?: return
        if (count <= 0) return
        val text = if (count > BADGE_MAX) "$BADGE_MAX+" else count.toString()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = color(R.color.roomkit_fg_primary)
            textSize = context.dpF(BADGE_TEXT_SP.toFloat())
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val textHeight = context.dp(BADGE_HEIGHT)
        val baseline = height / 2f - context.dp(BADGE_BOTTOM_TO_CENTER) - textHeight / 2f - paint.ascent() / 2f
        canvas.drawText(text, width / 2f + context.dp(BADGE_LEFT_TO_CENTER), baseline, paint)
    }

    private fun drawFaultBadge(canvas: Canvas) {
        if (!hasFault) return
        val size = context.dp(ICON_SIZE_DP)
        val iconLeft = (width - size) / 2f
        val iconTop = (height - size) / 2f
        val badgeSize = context.dp(FAULT_BADGE_SIZE)
        val left = (iconLeft + size + context.dp(FAULT_BADGE_RIGHT_OFFSET) - badgeSize).roundToInt()
        val top = (iconTop - context.dp(FAULT_BADGE_TOP_OFFSET)).roundToInt()
        val right = left + badgeSize
        val bottom = top + badgeSize

        faultBadgeDrawable?.mutate()?.let { drawable ->
            drawable.setBounds(left, top, right, bottom)
            drawable.draw(canvas)
        }
        faultGlyphDrawable?.let { drawable ->
            val glyphWidth = context.dp(FAULT_GLYPH_WIDTH_DP)
            val glyphHeight = context.dp(FAULT_GLYPH_HEIGHT_DP)
            val glyphLeft = ((left + right - glyphWidth) / 2f).roundToInt()
            val glyphTop = ((top + bottom - glyphHeight) / 2f).roundToInt()
            drawable.setBounds(glyphLeft, glyphTop, glyphLeft + glyphWidth, glyphTop + glyphHeight)
            drawable.draw(canvas)
        }
    }

    private fun color(res: Int): Int = ContextCompat.getColor(context, res)

    companion object {
        private const val ICON_SIZE_DP = 20
        private const val PRESSED_SCALE = 0.92f
        private const val PRESSED_DURATION = 80
        private const val MAX_VOLUME = 100
        private const val CAVITY_X = 0.34375f
        private const val CAVITY_Y = 0.125f
        private const val CAVITY_WIDTH = 0.3125f
        private const val CAVITY_HEIGHT = 0.53125f
        private const val BADGE_MAX = 99
        private const val BADGE_TEXT_SP = 10
        private const val BADGE_HEIGHT = 12
        private const val BADGE_LEFT_TO_CENTER = 8
        private const val BADGE_BOTTOM_TO_CENTER = 6
        private const val FAULT_BADGE_SIZE = 14
        private const val FAULT_GLYPH_WIDTH_DP = 4
        private const val FAULT_GLYPH_HEIGHT_DP = 9
        private const val FAULT_BADGE_TOP_OFFSET = 6
        private const val FAULT_BADGE_RIGHT_OFFSET = 8
        private const val ROOM_DISABLED_ALPHA = 0.45f
    }
}
