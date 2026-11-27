package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

internal class MessageInputPanelAnimator(
    private val panelContainer: FrameLayout,
    private val lastRenderedKeyboardHeight: () -> Int?,
    initialAnimatedPanelHeight: Int = 0
) {
    private var panelHeightAnimator: ValueAnimator? = null
    private var panelAnimationTarget: Int = 0

    var currentAnimatedPanelHeight = initialAnimatedPanelHeight
        private set

    fun cancel() {
        panelHeightAnimator?.cancel()
        panelHeightAnimator = null
    }

    fun drivePanelHeightAnimation(
        target: Int,
        keyboardHeight: Int,
        isKeyboardAnimationSupported: Boolean,
        keyboardTargetHeight: Int
    ) {
        val animatorRunning = panelHeightAnimator?.isRunning == true
        val isAnimatingToTarget = animatorRunning && panelAnimationTarget == target
        when (
            val decision = MessageInputPanelAnimationDecision.choose(
                targetPanelHeight = target,
                keyboardHeight = keyboardHeight,
                currentPanelHeight = currentAnimatedPanelHeight,
                keyboardTargetHeight = keyboardTargetHeight,
                isKeyboardAnimationSupported = isKeyboardAnimationSupported,
                isAnimatingToTarget = isAnimatingToTarget,
                isAnimatorRunning = animatorRunning
            )
        ) {
            MessageInputPanelAnimationDecision.ClearResidualAfterKeyboardTakeover -> {
                cancel()
                panelAnimationTarget = target
                Log.d(TAG, "clearResidualPanelHeightAfterKeyboardTakeover: $currentAnimatedPanelHeight -> 0 (kbH=$keyboardHeight)")
                currentAnimatedPanelHeight = 0
                syncContainerHeight(keyboardHeight)
            }
            MessageInputPanelAnimationDecision.AnimateResidualToKeyboardTarget -> {
                animateResidualPanelHeightToKeyboardTarget(keyboardTargetHeight, keyboardHeight)
            }
            MessageInputPanelAnimationDecision.HoldResidualUntilKeyboardCatchesUp -> Unit
            MessageInputPanelAnimationDecision.FallbackKeyboardTakeOverResidual -> {
                cancel()
                animateFallbackResidualPanelHeightToKeyboard(keyboardHeight)
            }
            MessageInputPanelAnimationDecision.SnapPanelTarget -> {
                cancel()
                panelAnimationTarget = target
                if (currentAnimatedPanelHeight != target) {
                    Log.d(TAG, "snapPanelTargetForKeyboardTransition: $currentAnimatedPanelHeight -> $target")
                    currentAnimatedPanelHeight = target
                    syncContainerHeight(keyboardHeight)
                }
            }
            MessageInputPanelAnimationDecision.KeepCurrentAnimation,
            MessageInputPanelAnimationDecision.NoOp -> Unit
            is MessageInputPanelAnimationDecision.AnimatePanelTarget -> {
                if (decision.adoptKeyboardHeightAsStart) {
                    Log.d(TAG, "adoptKeyboardHeightAsPanelStart: $currentAnimatedPanelHeight -> $keyboardHeight")
                    currentAnimatedPanelHeight = keyboardHeight
                }
                if (currentAnimatedPanelHeight != target) {
                    setPanelTargetWithAnimation(target)
                }
            }
        }
    }

    fun syncContainerHeight(keyboardHeight: Int) {
        val effectiveHeight = maxOf(keyboardHeight, currentAnimatedPanelHeight)
        val currentHeight = (panelContainer.layoutParams?.height ?: 0).coerceAtLeast(0)
        if (currentHeight != effectiveHeight) {
            logSyncContainerHeight(currentHeight, effectiveHeight, keyboardHeight)
            setPanelContainerHeight(effectiveHeight)
        }
    }

    private fun logSyncContainerHeight(
        currentHeight: Int,
        effectiveHeight: Int,
        keyboardHeight: Int
    ) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        Log.d(
            TAG,
            "syncContainerHeight: current=$currentHeight effective=$effectiveHeight " +
                "(kbH=$keyboardHeight currentAnim=$currentAnimatedPanelHeight)"
        )
    }

    private fun setPanelContainerHeight(height: Int) {
        panelContainer.visibility = if (height > 0) View.VISIBLE else View.GONE
        val lp = panelContainer.layoutParams
        lp.height = height
        panelContainer.layoutParams = lp
    }

    private fun setPanelTargetWithAnimation(target: Int) {
        if (currentAnimatedPanelHeight == target) return
        panelHeightAnimator?.cancel()

        val from = currentAnimatedPanelHeight
        Log.d(TAG, "setPanelTargetWithAnimation: $from -> $target")

        panelAnimationTarget = target
        panelHeightAnimator = ValueAnimator.ofInt(from, target).apply {
            duration = PANEL_ANIM_DURATION_MS
            interpolator = PANEL_INTERPOLATOR
            addUpdateListener { animator ->
                currentAnimatedPanelHeight = animator.animatedValue as Int
                syncContainerHeight(lastRenderedKeyboardHeight() ?: 0)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    currentAnimatedPanelHeight = target
                    syncContainerHeight(lastRenderedKeyboardHeight() ?: 0)
                }
            })
            start()
        }
    }

    private fun animateResidualPanelHeightToKeyboardTarget(
        keyboardTargetHeight: Int,
        keyboardHeight: Int
    ) {
        if (panelHeightAnimator?.isRunning == true && panelAnimationTarget == keyboardTargetHeight) {
            return
        }
        if (currentAnimatedPanelHeight == keyboardTargetHeight) {
            syncContainerHeight(keyboardHeight)
            return
        }
        val from = currentAnimatedPanelHeight
        Log.d(TAG, "animateResidualPanelHeightToKeyboardTarget: $from -> $keyboardTargetHeight")
        panelHeightAnimator?.cancel()
        panelAnimationTarget = keyboardTargetHeight
        panelHeightAnimator = ValueAnimator.ofInt(from, keyboardTargetHeight).apply {
            duration = PANEL_ANIM_DURATION_MS
            interpolator = PANEL_INTERPOLATOR
            addUpdateListener { animator ->
                currentAnimatedPanelHeight = animator.animatedValue as Int
                syncContainerHeight(lastRenderedKeyboardHeight() ?: keyboardHeight)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    currentAnimatedPanelHeight = keyboardTargetHeight
                    syncContainerHeight(lastRenderedKeyboardHeight() ?: keyboardHeight)
                }
            })
            start()
        }
    }

    private fun animateFallbackResidualPanelHeightToKeyboard(keyboardHeight: Int) {
        if (currentAnimatedPanelHeight == keyboardHeight) {
            completeFallbackKeyboardTakeover(keyboardHeight)
            return
        }
        val from = currentAnimatedPanelHeight
        Log.d(TAG, "animateFallbackResidualPanelHeightToKeyboard: $from -> $keyboardHeight")
        panelAnimationTarget = keyboardHeight
        panelHeightAnimator = ValueAnimator.ofInt(from, keyboardHeight).apply {
            duration = PANEL_ANIM_DURATION_MS
            interpolator = PANEL_INTERPOLATOR
            addUpdateListener { animator ->
                currentAnimatedPanelHeight = animator.animatedValue as Int
                syncContainerHeight(lastRenderedKeyboardHeight() ?: keyboardHeight)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    completeFallbackKeyboardTakeover(lastRenderedKeyboardHeight() ?: keyboardHeight)
                }
            })
            start()
        }
    }

    private fun completeFallbackKeyboardTakeover(keyboardHeight: Int) {
        panelHeightAnimator = null
        panelAnimationTarget = 0
        currentAnimatedPanelHeight = 0
        syncContainerHeight(keyboardHeight)
    }

    private companion object {
        private const val PANEL_ANIM_DURATION_MS = 250L
        private const val TAG = "MsgInput.PanelAnimator"
        private val PANEL_INTERPOLATOR = DecelerateInterpolator(1.5f)
    }
}

internal sealed class MessageInputPanelAnimationDecision {
    object ClearResidualAfterKeyboardTakeover : MessageInputPanelAnimationDecision()
    object AnimateResidualToKeyboardTarget : MessageInputPanelAnimationDecision()
    object HoldResidualUntilKeyboardCatchesUp : MessageInputPanelAnimationDecision()
    object FallbackKeyboardTakeOverResidual : MessageInputPanelAnimationDecision()
    object SnapPanelTarget : MessageInputPanelAnimationDecision()
    object KeepCurrentAnimation : MessageInputPanelAnimationDecision()
    object NoOp : MessageInputPanelAnimationDecision()
    data class AnimatePanelTarget(
        val adoptKeyboardHeightAsStart: Boolean
    ) : MessageInputPanelAnimationDecision()

    companion object {
        fun choose(
            targetPanelHeight: Int,
            keyboardHeight: Int,
            currentPanelHeight: Int,
            keyboardTargetHeight: Int,
            isKeyboardAnimationSupported: Boolean,
            isAnimatingToTarget: Boolean,
            isAnimatorRunning: Boolean
        ): MessageInputPanelAnimationDecision {
            if (
                MessageInputPanelHeightPolicy.shouldClearResidualPanelHeightAfterKeyboardTakeover(
                    targetPanelHeight = targetPanelHeight,
                    keyboardHeight = keyboardHeight,
                    currentPanelHeight = currentPanelHeight,
                    isKeyboardAnimationSupported = isKeyboardAnimationSupported
                )
            ) {
                return ClearResidualAfterKeyboardTakeover
            }
            if (
                MessageInputPanelHeightPolicy.shouldAnimateResidualPanelHeightToKeyboardTarget(
                    targetPanelHeight = targetPanelHeight,
                    keyboardHeight = keyboardHeight,
                    currentPanelHeight = currentPanelHeight,
                    keyboardTargetHeight = keyboardTargetHeight,
                    isKeyboardAnimationSupported = isKeyboardAnimationSupported
                )
            ) {
                return AnimateResidualToKeyboardTarget
            }
            if (
                MessageInputPanelHeightPolicy.shouldHoldResidualPanelHeightUntilKeyboardCatchesUp(
                    targetPanelHeight = targetPanelHeight,
                    keyboardHeight = keyboardHeight,
                    currentPanelHeight = currentPanelHeight,
                    isKeyboardAnimationSupported = isKeyboardAnimationSupported
                )
            ) {
                return HoldResidualUntilKeyboardCatchesUp
            }
            if (
                MessageInputPanelHeightPolicy.shouldLetFallbackKeyboardTakeOverResidualPanelHeight(
                    targetPanelHeight = targetPanelHeight,
                    keyboardHeight = keyboardHeight,
                    currentPanelHeight = currentPanelHeight,
                    isKeyboardAnimationSupported = isKeyboardAnimationSupported
                )
            ) {
                return FallbackKeyboardTakeOverResidual
            }
            if (
                MessageInputPanelHeightPolicy.shouldSnapPanelTarget(
                    targetPanelHeight = targetPanelHeight,
                    keyboardHeight = keyboardHeight,
                    currentPanelHeight = currentPanelHeight,
                    isAnimatingToTarget = isAnimatingToTarget
                )
            ) {
                return SnapPanelTarget
            }
            if (isAnimatingToTarget) {
                return KeepCurrentAnimation
            }
            if (!isAnimatorRunning && currentPanelHeight == targetPanelHeight) {
                return NoOp
            }
            return AnimatePanelTarget(
                adoptKeyboardHeightAsStart = MessageInputPanelHeightPolicy.shouldAdoptKeyboardHeightAsAnimationStart(
                    targetPanelHeight = targetPanelHeight,
                    keyboardHeight = keyboardHeight,
                    currentPanelHeight = currentPanelHeight,
                    isAnimatingToTarget = isAnimatingToTarget
                )
            )
        }
    }
}
