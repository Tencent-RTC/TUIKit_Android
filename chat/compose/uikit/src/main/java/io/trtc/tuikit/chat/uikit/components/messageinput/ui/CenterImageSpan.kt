package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan

class CenterImageSpan(drawable: Drawable) : ImageSpan(drawable) {

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val drawable = getDrawable()
        val rect = drawable.getBounds()
        val paintFm = paint.getFontMetricsInt()
        val center = (paintFm.top + paintFm.bottom) / 2
        if (fm != null) {
            fm.ascent = center - (rect.height() / 2)
            fm.descent = center + (rect.height() / 2)
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }

        return rect.right
    }
}
