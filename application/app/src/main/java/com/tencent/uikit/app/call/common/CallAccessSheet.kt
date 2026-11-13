package com.tencent.uikit.app.call.common

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.tencent.uikit.app.R

internal class CallAccessSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    internal var onDismiss: (() -> Unit)? = null

    private val dimView: View
    private val panel: FrameLayout
    private val titleLabel: TextView
    private val actionButton: TextView
    private val closeButton: View
    private val contentContainer: FrameLayout
    private var isDismissing = false

    init {
        dimView = View(context)
        dimView.setBackgroundColor(ContextCompat.getColor(context, R.color.callaccess_mask))
        addView(dimView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        dimView.setOnClickListener { dismiss() }

        panel = HeightCappedPanel(context)
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        LayoutInflater.from(context).inflate(R.layout.callaccess_sheet, panel, true)
        titleLabel = panel.findViewById(R.id.sheet_title)
        actionButton = panel.findViewById(R.id.sheet_action)
        closeButton = panel.findViewById(R.id.sheet_close)
        contentContainer = panel.findViewById(R.id.sheet_content)
        WindowInsetsHelper.applyNavigationBarInset(contentContainer)
        closeButton.setOnClickListener { dismiss() }
        actionButton.setOnClickListener { onAction?.invoke() }
    }

    internal var onAction: (() -> Unit)? = null
        set(value) {
            field = value
            actionButton.isVisible = value != null
            closeButton.isVisible = value == null
        }

    fun setTitle(title: CharSequence) {
        titleLabel.text = title
    }

    fun setAction(title: CharSequence, isCancel: Boolean) {
        actionButton.text = title
        val colorRes = if (isCancel) R.color.callaccess_call_red else R.color.callaccess_brand
        actionButton.setTextColor(ContextCompat.getColor(context, colorRes))
        actionButton.setTypeface(null, if (isCancel) Typeface.NORMAL else Typeface.BOLD)
    }

    fun setContent(content: View) {
        contentContainer.removeAllViews()
        contentContainer.addView(
            content,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
    }

    fun present(host: ViewGroup) {
        isDismissing = false
        host.addView(this, ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        dimView.alpha = 0f
        panel.post {
            if (isDismissing || parent == null) {
                return@post
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
                (parent as? ViewGroup)?.removeView(this@CallAccessSheet)
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
