package com.trtc.uikit.livekit.features.anchorview.view.battle.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class BattleCountdownBackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val viewPadding = 80
    private val circlePaint = Paint().apply {
        color = Color.WHITE
        alpha = 0xCC
        isAntiAlias = true
    }
    
    private val arcPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    
    private val ripplePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val arcRect = RectF()
    private val arcPath = Path()

    private var circleRadius = 0f
    private var rotationAngle = 0

    private val rippleCircles = mutableListOf<RippleCircle>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width
        val height = height
        val size = min(width, height)
        
        if (circleRadius == 0f) {
            circleRadius = (size - viewPadding * 2) / 2f
            arcRect.apply {
                left = (width - circleRadius * 2 - arcPaint.strokeWidth / 2f) / 2f
                top = (height - circleRadius * 2 - arcPaint.strokeWidth / 2f) / 2f
                right = left + circleRadius * 2
                bottom = top + circleRadius * 2
            }
        }
        
        drawCircle(canvas)
        draw2Arc(canvas)
        drawRipple(canvas)
        invalidate()
    }

    private fun drawCircle(canvas: Canvas) {
        val width = width
        val height = height
        canvas.drawCircle(width / 2f, height / 2f, circleRadius, circlePaint)
    }

    private fun draw2Arc(canvas: Canvas) {
        rotationAngle++
        rotationAngle %= 360
        arcPath.apply {
            reset()
            addArc(arcRect, 120f + rotationAngle, 60f)
            addArc(arcRect, -120f + rotationAngle, 180f)
        }
        canvas.drawPath(arcPath, arcPaint)
    }

    private fun drawRipple(canvas: Canvas) {
        val width = width
        val height = height
        
        if (rippleCircles.isEmpty()) {
            // Init with two circles
            var radius = circleRadius
            var alpha = genRippleCircleAlpha(radius)
            rippleCircles.add(RippleCircle(radius, alpha))
            
            radius = circleRadius + viewPadding / 2f
            alpha = genRippleCircleAlpha(radius)
            rippleCircles.add(RippleCircle(radius, alpha))
        }
        
        for (circle in rippleCircles) {
            ripplePaint.alpha = circle.alpha
            canvas.drawCircle(width / 2f, height / 2f, circle.radius, ripplePaint)

            if (circle.radius >= width / 2f) {
                circle.radius = circleRadius
            } else {
                circle.radius += 1
            }
            circle.alpha = genRippleCircleAlpha(circle.radius)
        }
    }

    private fun genRippleCircleAlpha(radius: Float): Int {
        return ((1 - (radius - circleRadius) / viewPadding) * 0xFF).toInt()
    }

    private data class RippleCircle(
        var radius: Float,
        var alpha: Int
    )
}