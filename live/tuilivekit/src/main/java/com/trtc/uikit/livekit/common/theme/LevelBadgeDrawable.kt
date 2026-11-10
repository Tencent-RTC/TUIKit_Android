package com.trtc.uikit.livekit.common.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import kotlin.math.ceil

internal class LevelBadgeDrawable private constructor(
    private val context: Context,
    private val level: Int,
) : Drawable() {

    private val tier = Tier.fromLevel(level)
    private val density = context.resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.5f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            8.5f,
            context.resources.displayMetrics,
        )
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        setShadowLayer(dp(2f), 0f, dp(1f), color(R.color.livekit_pusher_message_badge_text_shadow))
    }
    private val rect = RectF()
    private val levelText = level.coerceAtLeast(0).toString()
    private val contentWidth = ceil(maxOf(dp(34f), dp(18f) + textPaint.measureText(levelText))).toInt()
    private val contentHeight = dp(15f).toInt()
    private val icon = AppCompatResources.getDrawable(context, tier.iconRes)?.mutate()?.apply {
        setBounds(0, 0, dp(10f).toInt(), dp(10f).toInt())
    }

    init {
        setBounds(0, 0, contentWidth, contentHeight)
    }

    override fun getIntrinsicWidth(): Int = contentWidth

    override fun getIntrinsicHeight(): Int = contentHeight

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val radius = dp(7.5f)
        if (tier == Tier.ELITE) {
            fillPaint.setShadowLayer(
                dp(6f),
                0f,
                0f,
                color(R.color.livekit_pusher_message_elite_shadow),
            )
        } else {
            fillPaint.clearShadowLayer()
        }
        fillPaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            tier.gradientColors.map(::color).toIntArray(),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        val strokeColor = color(tier.strokeColor)
        if (strokeColor != 0) {
            strokePaint.color = strokeColor
            val inset = strokePaint.strokeWidth / 2f
            rect.inset(inset, inset)
            canvas.drawRoundRect(rect, radius - inset, radius - inset, strokePaint)
            rect.inset(-inset, -inset)
        }

        icon?.let { drawable ->
            val save = canvas.save()
            canvas.translate(rect.left + dp(4f), rect.top + dp(2.5f))
            drawable.draw(canvas)
            canvas.restoreToCount(save)
        }
        textPaint.color = color(tier.textColor)
        val metrics = textPaint.fontMetrics
        val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f
        val textCenter = (rect.left + dp(14f) + rect.right - dp(4f)) / 2f
        canvas.drawText(levelText, textCenter, baseline, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        textPaint.alpha = alpha
        icon?.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        icon?.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun color(@ColorRes resource: Int): Int = ContextCompat.getColor(context, resource)

    private fun dp(value: Float): Float = value * density

    private enum class Tier(
        val gradientColors: IntArray,
        @ColorRes val strokeColor: Int,
        @ColorRes val textColor: Int,
        @DrawableRes val iconRes: Int,
    ) {
        BASIC(
            intArrayOf(R.color.livekit_pusher_message_basic_start, R.color.livekit_pusher_message_basic_end),
            android.R.color.transparent,
            R.color.livekit_pusher_message_basic_text,
            R.drawable.livekit_pusher_message_level_basic,
        ),
        ADVANCED(
            intArrayOf(R.color.livekit_pusher_message_advanced_start, R.color.livekit_pusher_message_advanced_end),
            R.color.livekit_pusher_message_advanced_border,
            R.color.livekit_pusher_message_advanced_text,
            R.drawable.livekit_pusher_message_level_advanced,
        ),
        PREMIUM(
            intArrayOf(
                R.color.livekit_pusher_message_premium_start,
                R.color.livekit_pusher_message_premium_middle,
                R.color.livekit_pusher_message_premium_end,
            ),
            R.color.livekit_pusher_message_premium_border,
            R.color.livekit_pusher_message_premium_text,
            R.drawable.livekit_pusher_message_level_premium,
        ),
        ELITE(
            intArrayOf(
                R.color.livekit_pusher_message_elite_start,
                R.color.livekit_pusher_message_elite_middle,
                R.color.livekit_pusher_message_elite_end,
            ),
            R.color.livekit_pusher_message_elite_border,
            R.color.livekit_pusher_message_elite_text,
            R.drawable.livekit_pusher_message_level_elite,
        );

        companion object {
            fun fromLevel(level: Int): Tier = when (level) {
                in Int.MIN_VALUE until 10 -> BASIC
                in 10 until 25 -> ADVANCED
                in 25 until 40 -> PREMIUM
                else -> ELITE
            }
        }
    }

    companion object {
        fun forLevel(context: Context, level: Int): LevelBadgeDrawable =
            LevelBadgeDrawable(context, level)
    }
}
