package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.viewpager2.widget.ViewPager2
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens

internal class LongPressPageIndicator(
    private val context: Context,
    private val density: Float,
    private val colors: ColorTokens
) {

    fun areaHeight(): Int = LongPressDimens.pageIndicatorAreaHeight(density)

    fun build(pageCount: Int): LinearLayout {
        val dotSize = LongPressDimens.pageIndicatorDotSize(density)
        val spacing = LongPressDimens.pageIndicatorDotSpacing(density)
        val activeColor = colors.textColorPrimary
        val inactiveColor = inactiveColor()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                0,
                LongPressDimens.pageIndicatorVerticalPadding(density),
                0,
                LongPressDimens.pageIndicatorVerticalPadding(density)
            )
            for (i in 0 until pageCount) {
                val dot = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (i == 0) activeColor else inactiveColor)
                    }
                }
                addView(
                    dot,
                    LinearLayout.LayoutParams(dotSize, dotSize).apply {
                        if (i > 0) {
                            marginStart = spacing
                        }
                    }
                )
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                areaHeight()
            )
        }
    }

    fun attach(pager: ViewPager2, indicator: LinearLayout) {
        val activeColor = colors.textColorPrimary
        val inactiveColor = inactiveColor()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until indicator.childCount) {
                    val dot = indicator.getChildAt(i)
                    val drawable = dot.background as? GradientDrawable ?: continue
                    drawable.setColor(if (i == position) activeColor else inactiveColor)
                }
            }
        })
    }

    private fun inactiveColor(): Int {
        return ColorUtils.setAlphaComponent(
            colors.textColorPrimary,
            LongPressDimens.PAGE_INDICATOR_INACTIVE_ALPHA
        )
    }
}
