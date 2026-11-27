package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import android.content.Context
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.LinearLayout

class MaxWidthFrameLayout(context: Context) : FrameLayout(context) {
    var maxWidth: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val adjustedWidthSpec = if (maxWidth > 0) {
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            val size = MeasureSpec.getSize(widthMeasureSpec)
            when (mode) {
                MeasureSpec.EXACTLY -> MeasureSpec.makeMeasureSpec(
                    minOf(size, maxWidth), MeasureSpec.EXACTLY
                )
                MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(
                    minOf(size, maxWidth), MeasureSpec.AT_MOST
                )
                else -> MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
            }
        } else {
            widthMeasureSpec
        }
        super.onMeasure(adjustedWidthSpec, heightMeasureSpec)
    }
}

open class MaxWidthLinearLayout(context: Context) : LinearLayout(context) {
    var maxWidth: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val adjustedWidthSpec = if (maxWidth > 0) {
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            val size = MeasureSpec.getSize(widthMeasureSpec)
            when (mode) {
                MeasureSpec.EXACTLY -> MeasureSpec.makeMeasureSpec(
                    minOf(size, maxWidth), MeasureSpec.EXACTLY
                )
                MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(
                    minOf(size, maxWidth), MeasureSpec.AT_MOST
                )
                else -> MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
            }
        } else {
            widthMeasureSpec
        }
        super.onMeasure(adjustedWidthSpec, heightMeasureSpec)
    }
}
