package com.trtc.uikit.livekit.common.theme

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.popover.AtomicPopover

open class BottomSheetView(context: Context) : AtomicPopover(context) {

    protected val contentView = BottomSheetBackgroundView(context)
    private val titleLabel = LiveText(context).apply {
        role = LiveTheme.TextRole.SHEET_TITLE
        gravity = Gravity.CENTER
        includeFontPadding = false
        isVisible = false
    }
    private var heightAnimator: ValueAnimator? = null
    private var dismissRequested = false

    /** Optional 15sp standard title shared by sheets with the same title geometry. */
    protected var sheetTitle: String? = null
        set(value) {
            field = value
            titleLabel.text = value.orEmpty()
            titleLabel.isVisible = !value.isNullOrEmpty()
        }

    protected open val sheetHeight: Int
        get() = ViewGroup.LayoutParams.WRAP_CONTENT

    init {
        contentView.addView(
            titleLabel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.livekit_bottom_sheet_title_height),
            ).apply {
                gravity = Gravity.TOP
                topMargin = context.resources.getDimensionPixelSize(
                    R.dimen.livekit_bottom_sheet_title_top,
                )
                marginStart = context.resources.getDimensionPixelSize(
                    R.dimen.livekit_bottom_sheet_title_horizontal_inset,
                )
                marginEnd = marginStart
            },
        )
        setTransparentBackground(true)
        setContent(contentView)
        setOnDismissListener {
            heightAnimator?.cancel()
            heightAnimator = null
            dismissRequested = false
            sheetDidDismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        window?.let { sheetWindow ->
            WindowCompat.setDecorFitsSystemWindows(sheetWindow, false)
            sheetWindow.setDimAmount(DIM_AMOUNT)
            sheetWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            sheetWindow.navigationBarColor = Color.TRANSPARENT
            WindowInsetsControllerCompat(
                sheetWindow,
                sheetWindow.decorView,
            ).isAppearanceLightNavigationBars = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                sheetWindow.navigationBarDividerColor = Color.TRANSPARENT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                sheetWindow.isNavigationBarContrastEnforced = false
            }
        }
    }

    override fun show() {
        dismissRequested = false
        applySheetHeight()
        sheetWillPresent()
        super.show()
    }

    override fun dismiss() {
        if (!isShowing || dismissRequested) return
        dismissRequested = true
        sheetWillDismiss()
        super.dismiss()
    }

    protected open fun sheetWillPresent() = Unit

    protected open fun sheetWillDismiss() = Unit

    protected open fun sheetDidDismiss() = Unit

    protected fun updateSheetHeight(animated: Boolean) {
        val previousHeight = contentView.layoutParams?.height ?: 0
        val nextHeight = sheetHeight
        if (!animated || !isShowing || previousHeight <= 0) {
            updateContentHeight(nextHeight)
            return
        }
        heightAnimator?.cancel()
        heightAnimator = ValueAnimator.ofInt(previousHeight, nextHeight).apply {
            duration = HEIGHT_DURATION_MS
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { updateContentHeight(it.animatedValue as Int) }
            start()
        }
    }

    private fun applySheetHeight() {
        updateContentHeight(sheetHeight)
    }

    private fun updateContentHeight(height: Int) {
        val layoutParams = contentView.layoutParams ?: FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height,
        )
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = height
        contentView.layoutParams = layoutParams
    }

    private companion object {
        const val DIM_AMOUNT = 0.4f
        const val HEIGHT_DURATION_MS = 220L
    }
}
