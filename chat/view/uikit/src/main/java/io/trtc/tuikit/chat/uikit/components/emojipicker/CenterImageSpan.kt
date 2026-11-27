package io.trtc.tuikit.chat.uikit.components.emojipicker
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan

class CenterImageSpan(drawable: Drawable) : ImageSpan(drawable) {

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val drawable = getDrawable()
        val rect = drawable.bounds
        val paintFm = paint.fontMetricsInt
        val center = (paintFm.top + paintFm.bottom) / 2
        if (fm != null) {
            fm.ascent = center - (rect.height() / 2)
            fm.descent = center + (rect.height() / 2)
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }

        return rect.right
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val drawable = getDrawable()
        val paintFm = paint.fontMetricsInt
        val center = y + (paintFm.top + paintFm.bottom) / 2
        val transY = center - drawable.bounds.height() / 2
        canvas.save()
        canvas.translate(x, transY.toFloat())
        drawable.draw(canvas)
        canvas.restore()
    }
}
