package com.tencent.qcloud.tuikit.tuicallkit.common

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tencent.qcloud.tuikit.tuicallkit.R

internal class CallKitBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Style {
        GENERIC,
        CALL_PANEL
    }

    internal var onDismiss: (() -> Unit)? = null

    val panel: FrameLayout

    private val dimView: View
    private var isDismissing = false
    private val logger = CallKitLogger.getLogger("CallKitBottomSheet")

    init {
        dimView = View(context)
        dimView.setBackgroundColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_mask))
        addView(dimView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        dimView.setOnClickListener { dismiss() }

        panel = HeightCappedPanel(context)
        panel.clipToOutline = true
        ViewCompat.setOnApplyWindowInsetsListener(panel) { _, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val ignoring = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
            logger.info("sheet navBar bottom=$bottom ignoring=$ignoring")
            panel.setPadding(panel.paddingLeft, panel.paddingTop, panel.paddingRight, bottom)
            insets
        }
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    fun show(host: ViewGroup, content: View, style: Style = Style.GENERIC) {
        isDismissing = false
        panel.setBackgroundResource(
            if (style == Style.CALL_PANEL) R.drawable.tuicallkit_bg_sheet_call_panel else R.drawable.tuicallkit_bg_sheet_generic
        )
        panel.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        host.addView(this, ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        ViewCompat.requestApplyInsets(this)
        dimView.alpha = 0f
        panel.post {
            if (isDismissing || parent == null) {
                return@post
            }
            val rootBottom = ViewCompat.getRootWindowInsets(panel)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            logger.info("sheet root navBar bottom=$rootBottom paddingBottom=${panel.paddingBottom}")
            if (rootBottom > panel.paddingBottom) {
                panel.setPadding(panel.paddingLeft, panel.paddingTop, panel.paddingRight, rootBottom)
            }
            panel.translationY = panel.height.toFloat()
            panel.animate().translationY(0f).setDuration(ANIMATION_DURATION_MS).setInterpolator(DecelerateInterpolator()).start()
            dimView.animate().alpha(1f).setDuration(ANIMATION_DURATION_MS).start()
        }
    }

    fun dismiss() {
        if (parent == null || isDismissing) {
            return
        }
        isDismissing = true
        panel.animate().cancel()
        dimView.animate().cancel()
        dimView.animate().alpha(0f).setDuration(ANIMATION_DURATION_MS).start()
        panel.animate().translationY(height.toFloat()).setDuration(ANIMATION_DURATION_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                (parent as? ViewGroup)?.removeView(this@CallKitBottomSheet)
                onDismiss?.invoke()
            }.start()
    }

    override fun onDetachedFromWindow() {
        panel.animate().cancel()
        dimView.animate().cancel()
        super.onDetachedFromWindow()
    }

    private class HeightCappedPanel(context: Context) : FrameLayout(context) {

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maxHeight = (MeasureSpec.getSize(heightMeasureSpec) * MAX_HEIGHT_RATIO).toInt()
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
        }

        private companion object {
            const val MAX_HEIGHT_RATIO = 0.7f
        }
    }

    private companion object {
        const val ANIMATION_DURATION_MS = 240L
    }
}
