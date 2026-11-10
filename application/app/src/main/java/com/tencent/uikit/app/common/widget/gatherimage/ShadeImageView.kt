package com.tencent.uikit.app.common.widget.gatherimage

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.*
import android.util.AttributeSet
import android.util.SparseArray
import android.widget.ImageView
import com.tencent.uikit.app.R
import com.tencent.uikit.app.common.utils.ScreenUtil

@SuppressLint("AppCompatCustomView")
open class ShadeImageView : ImageView {
    companion object {
        private val sRoundBitmapArray = SparseArray<Bitmap>()
    }

    private val mShadePaint = Paint()
    private var mRoundBitmap: Bitmap? = null
    private var radius: Int = 0

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        radius = ScreenUtil.dp2px(4.0f, resources.displayMetrics).toInt()
        val array: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.RoundRectImageStyle)
        radius = array.getDimensionPixelSize(R.styleable.RoundRectImageStyle_round_radius, radius)
        array.recycle()
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mShadePaint.color = Color.RED
        mShadePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        mRoundBitmap = sRoundBitmapArray[measuredWidth + radius]
        if (mRoundBitmap == null) {
            mRoundBitmap = getRoundBitmap()
            sRoundBitmapArray.put(measuredWidth + radius, mRoundBitmap)
        }
        canvas.drawBitmap(mRoundBitmap!!, 0f, 0f, mShadePaint)
    }

    /**
     * Get rounded rectangle
     *
     * @return Bitmap
     */
    private fun getRoundBitmap(): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val color = Color.parseColor("#cfd3d8")
        val rect = Rect(0, 0, measuredWidth, measuredHeight)
        val rectF = RectF(rect)
        val paint = Paint()
        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color
        canvas.drawRoundRect(rectF, radius.toFloat(), radius.toFloat(), paint)
        return output
    }

    fun getRadius(): Int {
        return radius
    }

    fun setRadius(radius: Int) {
        this.radius = radius
        invalidate()
    }
}