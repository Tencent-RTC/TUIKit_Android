package com.trtc.uikit.livekit.common.theme.popover

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.ThemeStore
import com.trtc.uikit.livekit.common.theme.tokens.DesignTokenSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

open class AtomicPopover(
    context: Context,
    private val panelGravity: PanelGravity = PanelGravity.BOTTOM,
) : Dialog(
    context,
    when (panelGravity) {
        PanelGravity.BOTTOM -> R.style.dialogStyleFromBottom
        PanelGravity.CENTER -> R.style.dialogStyleCenter
    }
) {
    companion object {
        private val _dismissAllSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val dismissAllSignal = _dismissAllSignal.asSharedFlow()

        @JvmStatic
        fun dismissAll() {
            _dismissAllSignal.tryEmit(Unit)
        }
    }

    private val DIALOG_WIDTH_RATIO = 0.80

    private val dialogScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val rootContainer: FrameLayout
    private val contentContainer: MaxHeightFrameLayout

    private var themeJob: Job? = null
    private var panelHeight: PanelHeight = PanelHeight.WrapContent
    private var isAnimating = false
    private val showAnimation: Boolean = panelGravity == PanelGravity.BOTTOM
    private var useTransparentBackground: Boolean = false
    private var showMask: Boolean = true

    enum class PanelGravity {
        BOTTOM, CENTER
    }

    sealed class PanelHeight {
        object WrapContent : PanelHeight()
        data class Ratio(val value: Float) : PanelHeight()
    }

    init {
        contentContainer = MaxHeightFrameLayout(context).apply {
            val screenHeight = context.resources.displayMetrics.heightPixels
            maxHeight = if (panelGravity == PanelGravity.BOTTOM) {
                (screenHeight * 0.9f).toInt()
            } else {
                0
            }

            layoutParams = when (panelGravity) {
                PanelGravity.BOTTOM -> FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.BOTTOM }

                PanelGravity.CENTER -> FrameLayout.LayoutParams(
                    (context.resources.displayMetrics.widthPixels * DIALOG_WIDTH_RATIO).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER }
            }
            isClickable = true
        }

        rootContainer = FrameLayout(context).apply {
            layoutParams = when (panelGravity) {
                PanelGravity.BOTTOM -> ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                PanelGravity.CENTER -> ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            setOnClickListener {
                dismiss()
            }
            setBackgroundColor(Color.TRANSPARENT)
            addView(contentContainer)
        }
    }

    private var showAnimator: ValueAnimator? = null
    private var dismissAnimator: ValueAnimator? = null
    private val pendingShowRunnable = Runnable {
        updatePanelHeight()
        contentContainer.postDelayed(pendingShowWithAnimationRunnable, 16)
    }
    private val pendingShowWithAnimationRunnable = Runnable {
        showWithAnimation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window?.requestFeature(Window.FEATURE_NO_TITLE)

        super.onCreate(savedInstanceState)
        setContentView(rootContainer)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.setBackgroundColor(Color.TRANSPARENT)
            setWindowAnimations(0)
            setGravity(panelGravity.toAndroidGravity())

            when (panelGravity) {
                PanelGravity.BOTTOM -> {
                    setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                PanelGravity.CENTER -> {
                    setLayout(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            }

            if (showMask) {
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }

        setPanelBackground()
        setMaskColor()
    }


    override fun onStart() {
        super.onStart()

        themeJob?.cancel()
        themeJob = dialogScope.launch {
            launch {
                ThemeStore.shared(context).themeState.collectLatest {
                    setPanelBackground()
                    setMaskColor()
                }

            }

            launch {
                dismissAllSignal.collectLatest {
                    dismiss()
                }
            }

        }
    }

    override fun onStop() {
        releaseSubscriptions()
        super.onStop()
    }

    override fun show() {
        // 如果上一次的 dismiss 动画还在进行中，先取消它，防止 Dialog.isShowing==true
        // 导致 super.show() 直接 no-op、以及用户在动画期内的连续点击都被吞掉。
        cancelDismissAnimation()
        // 清理上一轮 show() 排队但尚未触发的 pending 任务，避免重复排队。
        clearPendingShowTasks()

        super.show()

        if (showAnimation) {
            contentContainer.translationY = contentContainer.height.toFloat()
            contentContainer.visibility = View.INVISIBLE

            contentContainer.post(pendingShowRunnable)
        } else {
            contentContainer.visibility = View.VISIBLE
        }
    }

    override fun dismiss() {
        releaseSubscriptions()
        // dismiss 时立刻清掉 show() 排入 Handler 的 pending 任务，
        // 否则残留的 showWithAnimation 会在 dismiss 动画中/后异步触发，
        // 造成"弹出立即收起 + 后续 dialog 状态被污染"的连锁问题。
        clearPendingShowTasks()
        if (isAnimating) return
        if (!isActivityValid()) {
            return
        }
        if (showAnimation) {
            dismissWithAnimation()
        } else {
            super.dismiss()
        }
    }

    private fun clearPendingShowTasks() {
        contentContainer.removeCallbacks(pendingShowRunnable)
        contentContainer.removeCallbacks(pendingShowWithAnimationRunnable)
        showAnimator?.cancel()
        showAnimator = null
    }

    private fun cancelDismissAnimation() {
        dismissAnimator?.let {
            it.removeAllListeners()
            it.cancel()
        }
        dismissAnimator = null
        isAnimating = false
        // 复位 translationY，避免下次显示时错位。
        contentContainer.translationY = 0f
    }

    fun setContent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
        contentContainer.removeAllViews()
        contentContainer.addView(
            view,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (isShowing) {
            contentContainer.post {
                updatePanelHeight()
            }
        }
    }

    fun setPanelHeight(value: PanelHeight) {
        this.panelHeight = value
        if (isShowing) {
            updatePanelHeight()
        }
    }

    fun setTransparentBackground(transparent: Boolean) {
        useTransparentBackground = transparent
        if (isShowing) {
            setPanelBackground()
        }
    }

    fun setShowMask(show: Boolean) {
        showMask = show
        if (isShowing) {
            setMaskColor()
        }
    }

    private fun showWithAnimation() {
        contentContainer.visibility = View.VISIBLE

        val startY = contentContainer.height.toFloat()
        val endY = 0f

        showAnimator?.cancel()
        showAnimator = ValueAnimator.ofFloat(startY, endY).apply {
            duration = 250
            interpolator = DecelerateInterpolator()

            addUpdateListener { animator ->
                contentContainer.translationY = animator.animatedValue as Float
            }

            doOnEnd {
                showAnimator = null
            }

            start()
        }
    }

    private fun dismissWithAnimation() {
        isAnimating = true

        val startY = contentContainer.translationY
        val endY = contentContainer.height.toFloat()

        dismissAnimator?.cancel()
        dismissAnimator = ValueAnimator.ofFloat(startY, endY).apply {
            duration = 200
            interpolator = DecelerateInterpolator()

            addUpdateListener { animator ->
                contentContainer.translationY = animator.animatedValue as Float
            }

            doOnEnd {
                isAnimating = false
                dismissAnimator = null
                if (isActivityValid()) {
                    super@AtomicPopover.dismiss()
                }
            }

            start()
        }
    }

    private fun updatePanelHeight() {
        if (panelGravity == PanelGravity.CENTER) {
            return
        }

        val screenHeight = context.resources.displayMetrics.heightPixels
        val screenWidth = context.resources.displayMetrics.widthPixels
        val maxHeight = (screenHeight * 0.9f).toInt()

        contentContainer.maxHeight = maxHeight

        val layoutParams = contentContainer.layoutParams as FrameLayout.LayoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT

        Log.d("AtomicPopover", "updatePanelHeight: ${panelHeight}")
        when (panelHeight) {
            is PanelHeight.WrapContent -> {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT

                val widthSpec =
                    View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY)
                val heightSpec =
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)

                contentContainer.measure(widthSpec, heightSpec)
            }

            is PanelHeight.Ratio -> {
                val ratio = (panelHeight as PanelHeight.Ratio).value.coerceIn(0f, 1f)
                layoutParams.height = (screenHeight * ratio).roundToInt()
            }
        }

        contentContainer.requestLayout()
    }

    private fun getCurrentTokens(context: Context): DesignTokenSet {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens
    }

    private fun setMaskColor() {
        if (!showMask) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(0f)
            return
        }
        window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val maskColor = getCurrentTokens(context).color.bgColorMask
        val dimAmount = Color.alpha(maskColor) / 255f
        window?.setDimAmount(dimAmount)
    }

    private fun setPanelBackground() {
        if (useTransparentBackground) {
            contentContainer.background = null
            return
        }

        val finalBgColor = getCurrentTokens(context).color.bgColorDialog
        val bottomCornerRadiusPx = context.resources.getDimension(R.dimen.radius_20)
        val centerCornerRadiusPx = context.resources.getDimension(R.dimen.radius_12)

        val radii = when (panelGravity) {
            PanelGravity.BOTTOM -> {
                floatArrayOf(
                    bottomCornerRadiusPx, bottomCornerRadiusPx,
                    bottomCornerRadiusPx, bottomCornerRadiusPx,
                    0f, 0f,
                    0f, 0f
                )
            }

            PanelGravity.CENTER -> {
                floatArrayOf(
                    centerCornerRadiusPx, centerCornerRadiusPx,
                    centerCornerRadiusPx, centerCornerRadiusPx,
                    centerCornerRadiusPx, centerCornerRadiusPx,
                    centerCornerRadiusPx, centerCornerRadiusPx
                )
            }
        }

        val drawable = GradientDrawable().apply {
            setColor(finalBgColor)
            cornerRadii = radii
        }
        contentContainer.background = drawable
    }

    private fun releaseSubscriptions() {
        themeJob?.cancel()
        themeJob = null
    }

    private fun PanelGravity.toAndroidGravity(): Int {
        return when (this) {
            PanelGravity.BOTTOM -> Gravity.BOTTOM
            PanelGravity.CENTER -> Gravity.CENTER
        }
    }

    private fun isActivityValid(): Boolean {
        val currentContext = context
        val currentActivity = if (currentContext is ContextThemeWrapper) {
            currentContext.baseContext
        } else {
            currentContext
        }
        if (currentActivity is Activity) {
            if (currentActivity.isFinishing || currentActivity.isDestroyed) {
                return false
            }
        }
        return true
    }

    private class MaxHeightFrameLayout(context: Context) : FrameLayout(context) {
        var maxHeight: Int = 0

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            var newHeightMeasureSpec = heightMeasureSpec

            if (maxHeight > 0) {
                val heightSize = MeasureSpec.getSize(heightMeasureSpec)
                val heightMode = MeasureSpec.getMode(heightMeasureSpec)

                newHeightMeasureSpec = when (heightMode) {
                    MeasureSpec.EXACTLY -> MeasureSpec.makeMeasureSpec(
                        minOf(heightSize, maxHeight),
                        MeasureSpec.EXACTLY
                    )

                    MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(
                        minOf(heightSize, maxHeight),
                        MeasureSpec.AT_MOST
                    )

                    else -> MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
                }
            }

            super.onMeasure(widthMeasureSpec, newHeightMeasureSpec)

            if (maxHeight > 0 && measuredHeight > maxHeight) {
                setMeasuredDimension(measuredWidth, maxHeight)
            }
        }
    }
}

private fun ValueAnimator.doOnEnd(action: () -> Unit) {
    addListener(object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) {
            action()
        }

        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
    })
}