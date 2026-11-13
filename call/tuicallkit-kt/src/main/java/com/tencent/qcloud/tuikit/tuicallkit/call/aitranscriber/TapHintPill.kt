package com.tencent.qcloud.tuikit.tuicallkit.call.aitranscriber

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.common.dp

internal class TapHintPill @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var hideTask: Runnable? = null

    init {
        isClickable = false
        isFocusable = false
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = 999f
            setColor(ContextCompat.getColor(context, R.color.tuicallkit_toast_background))
        }
        elevation = 8f.dp(context).toFloat()
        alpha = 0f
        translationY = 6f.dp(context).toFloat()

        val iconView = ImageView(context).apply {
            setImageResource(R.drawable.tuicallkit_icon_tap_hint)
            setColorFilter(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text3))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(iconView, LinearLayout.LayoutParams(22.dp(context), 22.dp(context)))

        val label = TextView(context).apply {
            setText(R.string.tuicallkit_cc_tap_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text3))
        }
        val labelParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        labelParams.marginStart = 8.dp(context)
        addView(label, labelParams)

        val paddingV = 8.dp(context)
        setPadding(10.dp(context), paddingV, 14.dp(context), paddingV)
    }

    fun showTemporarily() {
        hideTask?.let { mainHandler.removeCallbacks(it) }
        animate().cancel()
        animate().alpha(1f).translationY(0f).setDuration(280).start()
        val task = Runnable {
            animate().alpha(0f).translationY(6f.dp(context).toFloat()).setDuration(200).start()
        }
        hideTask = task
        mainHandler.postDelayed(task, 3000)
    }

    override fun onDetachedFromWindow() {
        hideTask?.let { mainHandler.removeCallbacks(it) }
        hideTask = null
        super.onDetachedFromWindow()
    }
}
