package io.trtc.tuikit.chat.uikit.components.userpicker.adapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens

internal class SelectionCheckBoxView(context: Context) : View(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp2px(1f, context.resources.displayMetrics)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp2px(1.5f, context.resources.displayMetrics)
    }
    private val checkPath = Path()
    private val crossPath = Path()

    private var isChecked = false
    private var isLocked = false
    private var currentColors: ColorTokens? = null

    fun setCheckedState(checked: Boolean, locked: Boolean, colors: ColorTokens) {
        isChecked = checked
        isLocked = locked
        currentColors = colors
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val colors = currentColors ?: return
        val cx = width / 2f
        val cy = height / 2f
        val radius = width / 2f
        val iconRadius = width * 0.375f

        if (isChecked) {
            fillPaint.color = if (isLocked) colors.textColorLinkDisabled else colors.textColorLink
            canvas.drawCircle(cx, cy, radius, fillPaint)

            iconPaint.color = if (isLocked) colors.textColorButtonDisabled else colors.textColorButton
            checkPath.reset()
            checkPath.moveTo(cx - iconRadius * 0.5f, cy)
            checkPath.lineTo(cx - iconRadius * 0.1f, cy + iconRadius * 0.35f)
            checkPath.lineTo(cx + iconRadius * 0.5f, cy - iconRadius * 0.35f)
            canvas.drawPath(checkPath, iconPaint)
        } else {
            strokePaint.color = colors.scrollbarColorDefault
            canvas.drawCircle(cx, cy, radius - strokePaint.strokeWidth / 2f, strokePaint)

            if (isLocked) {
                iconPaint.color = colors.textColorButtonDisabled
                val crossSize = iconRadius * 0.4f
                crossPath.reset()
                crossPath.moveTo(cx - crossSize, cy - crossSize)
                crossPath.lineTo(cx + crossSize, cy + crossSize)
                crossPath.moveTo(cx + crossSize, cy - crossSize)
                crossPath.lineTo(cx - crossSize, cy + crossSize)
                canvas.drawPath(crossPath, iconPaint)
            }
        }
    }
}
