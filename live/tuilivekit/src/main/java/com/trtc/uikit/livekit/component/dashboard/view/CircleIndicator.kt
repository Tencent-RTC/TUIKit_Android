package com.trtc.uikit.livekit.component.dashboard.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil

class CircleIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var normalColor = Color.TRANSPARENT
    private var selectedColor = Color.TRANSPARENT
    private var circleRadius = 0
    private var circleCount = 0
    private var selected = -1

    init {
        orientation = HORIZONTAL
        setNormalColor(context.resources.getColor(R.color.common_text_color_disabled))
        setSelectedColor(context.resources.getColor(R.color.common_text_color_primary))
        setCircleCount(0)
        setSelected(-1)
    }

    fun setCircleCount(count: Int) {
        circleCount = count
    }

    fun setCircleRadius(radius: Int) {
        circleRadius = radius
    }

    fun setSelected(index: Int) {
        selected = index
        update()
    }

    fun setNormalColor(color: Int) {
        normalColor = color
    }

    fun setSelectedColor(color: Int) {
        selectedColor = color
    }

    private fun update() {
        if (childCount != circleCount) {
            removeAllViews()
            for (i in 0 until circleCount) {
                val circleView = createCircleView()
                val params = LayoutParams(2 * circleRadius, 2 * circleRadius).apply {
                    leftMargin = ScreenUtil.dip2px(5f)
                    rightMargin = ScreenUtil.dip2px(5f)
                }
                addView(circleView, params)
            }
        }

        for (i in 0 until childCount) {
            val circleView = getChildAt(i)
            val drawable = circleView.background
            if (drawable is GradientDrawable) {
                val color = if (i == selected) selectedColor else normalColor
                drawable.setColor(color)
                circleView.background = drawable
            }
        }
    }

    private fun createCircleView(): View {
        val view = View(context)
        val drawable = createRoundGradientDrawable(normalColor)
        view.background = drawable
        return view
    }

    private fun createRoundGradientDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = circleRadius.toFloat()
        }
    }
}