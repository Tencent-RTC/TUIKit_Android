package com.trtc.uikit.livekit.common.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R

class BottomSheetBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private companion object {
        /** Width of the Ardot frame, used to scale the horizontal decoration positions. */
        const val DESIGN_WIDTH = 390f

        const val HIGHLIGHT_END_STOP = 0.2f
        const val BOTTOM_GLOW_END_STOP = 1f
        const val BRAND_LINE_START_STOP = 0.3f
        const val BRAND_LINE_END_STOP = 0.7f
        const val BRAND_LINE_WIDTH_RATIO = 0.7f
        const val BRAND_LINE_START_RATIO = 0.15f
    }

    /** One radial decoration blob: a design-space rect plus its centre colour. */
    private class Bokeh(
        val x: Float,
        val heightRatio: Float,
        val yOffset: Float,
        val size: Float,
        val color: Int,
        val endStop: Float = 1f,
    )

    private val density = resources.displayMetrics.density
    private val cornerRadius = resources.getDimension(R.dimen.livekit_bottom_sheet_radius)
    private val handleWidth = resources.getDimension(R.dimen.livekit_bottom_sheet_handle_width)
    private val handleHeight = resources.getDimension(R.dimen.livekit_bottom_sheet_handle_height)
    private val handleTop = resources.getDimension(R.dimen.livekit_bottom_sheet_handle_top)
    private val handleRadius = resources.getDimension(R.dimen.livekit_bottom_sheet_handle_radius)
    private val brandLineHeight =
        resources.getDimension(R.dimen.livekit_bottom_sheet_brand_line_height)

    private val bottomGlowColor =
        color(R.color.livekit_bottom_sheet_bottom_glow)

    private val decorations = listOf(
        Bokeh(-148.8f, 0.08f, -180f, 360f, color(R.color.livekit_bottom_sheet_bokeh1)),
        Bokeh(218.8f, 0.01f, -140f, 280f, color(R.color.livekit_bottom_sheet_bokeh2)),
        Bokeh(135f, 0.43f, -60f, 120f, color(R.color.livekit_bottom_sheet_bokeh3)),
        Bokeh(-45.4f, 0.99f, -100f, 200f, color(R.color.livekit_bottom_sheet_bokeh4)),
        Bokeh(262.2f, 0.99f, -120f, 240f, color(R.color.livekit_bottom_sheet_bokeh5))
    )

    private val blackShadowPaint = shadowPaint(
        R.color.livekit_bottom_sheet_black_shadow,
        R.dimen.livekit_bottom_sheet_black_shadow_radius,
        R.dimen.livekit_bottom_sheet_black_shadow_offset_y
    )
    private val blueShadowPaint = shadowPaint(
        R.color.livekit_bottom_sheet_blue_shadow,
        R.dimen.livekit_bottom_sheet_blue_shadow_radius,
        R.dimen.livekit_bottom_sheet_blue_shadow_offset_y
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val innerTopLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.livekit_bottom_sheet_inner_top_line)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.livekit_bottom_sheet_handle)
    }
    private val brandLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(
            resources.getDimension(R.dimen.livekit_bottom_sheet_brand_line_glow_radius),
            0f,
            0f,
            color(R.color.livekit_bottom_sheet_brand_line_glow)
        )
    }

    private val bounds = RectF()
    private val scratchRect = RectF()
    private val clipPath = Path()
    private val glowMatrix = Matrix()

    private var backgroundShader: Shader? = null
    private var highlightShader: Shader? = null
    private var washShader: Shader? = null
    private var horizontalScale = 1f

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width == 0 || height == 0) return

        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        horizontalScale = width / (DESIGN_WIDTH * density)

        clipPath.reset()
        clipPath.addRoundRect(
            bounds,
            floatArrayOf(
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                0f, 0f,
                0f, 0f
            ),
            Path.Direction.CW
        )

        backgroundShader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            color(R.color.livekit_bottom_sheet_bg_start),
            color(R.color.livekit_bottom_sheet_bg_end),
            Shader.TileMode.CLAMP
        )
        highlightShader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(color(R.color.livekit_bottom_sheet_highlight), Color.TRANSPARENT),
            floatArrayOf(0f, HIGHLIGHT_END_STOP),
            Shader.TileMode.CLAMP
        )
        washShader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            color(R.color.livekit_bottom_sheet_wash_start),
            color(R.color.livekit_bottom_sheet_wash_end),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (bounds.isEmpty) return

        drawShadow(canvas, blackShadowPaint, R.dimen.livekit_bottom_sheet_black_shadow_inset)
        drawShadow(canvas, blueShadowPaint, R.dimen.livekit_bottom_sheet_blue_shadow_inset)
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)

        backgroundShader?.let { drawShader(canvas, it) }
        highlightShader?.let { drawShader(canvas, it) }
        washShader?.let { drawShader(canvas, it) }

        drawBottomGlow(canvas)
        decorations.forEach { drawBokeh(canvas, it) }

        canvas.drawRect(0f, 0f, bounds.width(), 1f, innerTopLinePaint)

        scratchRect.set(
            (bounds.width() - handleWidth) / 2f,
            handleTop,
            (bounds.width() + handleWidth) / 2f,
            handleTop + handleHeight
        )
        canvas.drawRoundRect(scratchRect, handleRadius, handleRadius, handlePaint)

        canvas.restoreToCount(saveCount)

        drawBrandLine(canvas)
        super.onDraw(canvas)
    }

    private fun drawShadow(canvas: Canvas, paint: Paint, insetRes: Int) {
        val inset = resources.getDimension(insetRes)
        if (bounds.width() <= inset * 2f || bounds.height() <= inset * 2f) return
        scratchRect.set(bounds)
        scratchRect.inset(inset, inset)
        val shadowPath = Path().apply {
            addRoundRect(
                scratchRect,
                floatArrayOf(
                    cornerRadius, cornerRadius,
                    cornerRadius, cornerRadius,
                    0f, 0f,
                    0f, 0f,
                ),
                Path.Direction.CW,
            )
        }
        canvas.drawPath(shadowPath, paint)
    }

    private fun drawShader(canvas: Canvas, shader: Shader) {
        fillPaint.shader = shader
        canvas.drawRect(bounds, fillPaint)
        fillPaint.shader = null
    }

    private fun drawBottomGlow(canvas: Canvas) {
        val width = 312f * density * horizontalScale
        val height = 80f * density
        scratchRect.set(
            39f * density * horizontalScale,
            bounds.height() - 40f * density,
            39f * density * horizontalScale + width,
            bounds.height() - 40f * density + height
        )
        drawEllipticalGlow(canvas, scratchRect, bottomGlowColor, BOTTOM_GLOW_END_STOP)
    }

    private fun drawBokeh(canvas: Canvas, bokeh: Bokeh) {
        val size = bokeh.size * density
        val left = bokeh.x * density * horizontalScale
        val top = bokeh.yOffset * density + bokeh.heightRatio * bounds.height()
        scratchRect.set(left, top, left + size * horizontalScale, top + size)
        drawEllipticalGlow(canvas, scratchRect, bokeh.color, bokeh.endStop)
    }

    /**
     * Draws a radial gradient stretched to the rect, matching the CAGradientLayer radial
     * type whose end point sits at the layer corner.
     */
    private fun drawEllipticalGlow(canvas: Canvas, rect: RectF, color: Int, endStop: Float) {
        val radiusX = rect.width() / 2f
        val radiusY = rect.height() / 2f
        if (radiusX <= 0f || radiusY <= 0f) return

        val shader = RadialGradient(
            0f,
            0f,
            radiusX,
            intArrayOf(color, Color.TRANSPARENT),
            floatArrayOf(0f, endStop),
            Shader.TileMode.CLAMP
        )
        glowMatrix.setScale(1f, radiusY / radiusX)
        glowMatrix.postTranslate(rect.centerX(), rect.centerY())
        shader.setLocalMatrix(glowMatrix)

        glowPaint.shader = shader
        canvas.drawRect(rect, glowPaint)
        glowPaint.shader = null
    }

    private fun drawBrandLine(canvas: Canvas) {
        val lineWidth = bounds.width() * BRAND_LINE_WIDTH_RATIO
        val left = bounds.width() * BRAND_LINE_START_RATIO
        brandLinePaint.shader = LinearGradient(
            left,
            0f,
            left + lineWidth,
            0f,
            intArrayOf(
                Color.TRANSPARENT,
                color(R.color.livekit_bottom_sheet_brand_line_start),
                color(R.color.livekit_bottom_sheet_brand_line_end),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, BRAND_LINE_START_STOP, BRAND_LINE_END_STOP, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(left, 0f, left + lineWidth, brandLineHeight, brandLinePaint)
        brandLinePaint.shader = null
    }

    private fun shadowPaint(colorRes: Int, radiusRes: Int, offsetRes: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val shadowColor = color(colorRes)
            this.color = color(R.color.livekit_bottom_sheet_bg_end)
            setShadowLayer(
                resources.getDimension(radiusRes),
                0f,
                -resources.getDimension(offsetRes),
                shadowColor
            )
        }

    private fun color(colorRes: Int): Int = ContextCompat.getColor(context, colorRes)
}
