package com.trtc.uikit.roomkit.base.avatar

import android.graphics.Typeface
import android.content.Context
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.trtc.uikit.roomkit.R
import com.bumptech.glide.request.transition.Transition

class RoomAvatar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var text: CharSequence? = null
        set(value) {
            field = value
            invalidate()
        }

    var image: Drawable? = null
        set(value) {
            field = value
            invalidate()
        }

    var url: String? = null
        set(value) {
            if (field == value) return
            field = value
            image = null
            loadBitmap(value) { bitmap ->
                if (url == value) {
                    image = bitmap?.let { BitmapDrawable(resources, it) }
                }
            }
        }


    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val clipPath = Path()
    private val matrix = Matrix()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultSize = (32 * resources.displayMetrics.density).toInt()
        val width = resolveSize(defaultSize, widthMeasureSpec)
        val height = resolveSize(defaultSize, heightMeasureSpec)
        val side = minOf(width, height)
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val radius = w / 2f
        canvas.save()
        clipPath.reset()
        clipPath.addCircle(w / 2f, h / 2f, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        val drawable = image
        if (drawable != null) {
            val intrinsicW = maxOf(drawable.intrinsicWidth, 1)
            val intrinsicH = maxOf(drawable.intrinsicHeight, 1)
            val scale = maxOf(w / intrinsicW, h / intrinsicH)
            drawable.setBounds(0, 0, intrinsicW, intrinsicH)
            matrix.reset()
            matrix.setScale(scale, scale)
            matrix.postTranslate((w - intrinsicW * scale) / 2f, (h - intrinsicH * scale) / 2f)
            canvas.concat(matrix)
            drawable.draw(canvas)
        } else {
            bgPaint.color = ContextCompat.getColor(context, R.color.roomkit_accent)
            canvas.drawCircle(w / 2f, h / 2f, radius, bgPaint)

            val initial = initial()
            if (initial.isNotEmpty()) {
                textPaint.color = ContextCompat.getColor(context, R.color.roomkit_fg_on_accent)
                textPaint.textSize = fontSize(w)
                val fm = textPaint.fontMetrics
                val baseline = h / 2f - (fm.ascent + fm.descent) / 2f
                canvas.drawText(initial, w / 2f, baseline, textPaint)
            }
        }

        canvas.restore()
    }

    private fun fontSize(sidePx: Float): Float =
        maxOf(sidePx * 0.375f, 10f * resources.displayMetrics.density)

    private fun initial(): String {
        val src = text?.toString()?.trim().orEmpty()
        if (src.isEmpty()) return ""
        val first = src[0]
        return when {
            first.code in 0x4E00..0x9FFF -> first.toString()
            first.isLetter() -> first.uppercase()
            else -> first.toString()
        }
    }

    private fun loadBitmap(url: String?, onLoaded: (Bitmap?) -> Unit) {
        if (url.isNullOrEmpty()) {
            onLoaded(null)
            return
        }
        Glide.with(context.applicationContext)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    onLoaded(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    onLoaded(null)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    onLoaded(null)
                }
            })
    }
}
