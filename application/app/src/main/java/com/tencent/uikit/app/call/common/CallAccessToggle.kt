package com.tencent.uikit.app.call.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.tencent.uikit.app.R

internal class CallAccessToggle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Style {
        SETTINGS,
        CAPTION
    }

    internal var onChange: ((Boolean) -> Unit)? = null

    var isOn: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            applyState(animated = true)
        }

    private var style: Style = Style.SETTINGS
    private val knob: View
    private var knobAnimator: ValueAnimator? = null

    init {
        background = ContextCompat.getDrawable(context, R.drawable.callaccess_bg_toggle_track)?.mutate()
        knob = View(context)
        knob.setBackgroundResource(R.drawable.callaccess_bg_toggle_knob)
        knob.elevation = 2.dp(context).toFloat()
        val knobSize = 24.dp(context)
        val params = LayoutParams(knobSize, knobSize)
        params.marginStart = 2.dp(context)
        params.topMargin = 2.dp(context)
        addView(knob, params)
        setOnClickListener {
            isOn = !isOn
            onChange?.invoke(isOn)
        }
        applyState(animated = false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 用固定规格走 FrameLayout 测量流程，确保 knob 子 View 被测量（否则尺寸为 0 不可见）
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(46.dp(context), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(28.dp(context), MeasureSpec.EXACTLY)
        )
    }

    fun setStyle(value: Style) {
        style = value
        applyState(animated = false)
    }

    private fun applyState(animated: Boolean) {
        val trackColor = when {
            isOn && style == Style.CAPTION -> R.color.callaccess_brand
            isOn -> R.color.callaccess_green
            style == Style.CAPTION -> R.color.callaccess_caption_switch_off
            else -> R.color.callaccess_switch_off
        }
        val track: Drawable? = background
        track?.let { DrawableCompat.setTint(DrawableCompat.wrap(it), ContextCompat.getColor(context, trackColor)) }

        // knob 布局已含 2dp 左边距；ON 态目标 x=20dp（对齐 iOS），故平移 18dp
        val targetX = if (isOn) 18.dp(context).toFloat() else 0f
        knobAnimator?.cancel()
        if (animated) {
            knobAnimator = ValueAnimator.ofFloat(knob.translationX, targetX).apply {
                duration = 180
                addUpdateListener { knob.translationX = it.animatedValue as Float }
                start()
            }
        } else {
            knob.translationX = targetX
        }
    }

    override fun onDetachedFromWindow() {
        knobAnimator?.cancel()
        knobAnimator = null
        super.onDetachedFromWindow()
    }
}
