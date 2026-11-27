package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.pow

internal val PanelDecelerateEasing = Easing { fraction ->
    1f - (1f - fraction).toDouble().pow(3.0).toFloat()
}

internal const val PANEL_ANIM_DURATION_MS = 250

internal class MessageInputPanelHeightDriver {
    val heightAnimatable = Animatable(0f)

    // Invoked synchronously when the panel height animation starts or stops running,
    // so observers (e.g. the message list) can react in the same frame.
    var onAnimationRunningChanged: ((Boolean) -> Unit)? = null

    private var panelAnimationTarget: Int = 0
    private var currentAnimatedPanelHeight: Int = 0
    private var animationJob: Job? = null
    private var animationRunningNotified: Boolean = false

    private fun notifyAnimationRunning(running: Boolean) {
        if (animationRunningNotified != running) {
            animationRunningNotified = running
            onAnimationRunningChanged?.invoke(running)
        }
    }

    fun drive(
        scope: CoroutineScope,
        target: Int,
        keyboardHeight: Int,
        isKeyboardAnimationSupported: Boolean,
        keyboardTargetHeight: Int
    ) {
        val animatorRunning = animationJob?.isActive == true
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
                currentAnimatedPanelHeight = 0
                snapTo(scope, 0)
            }
            MessageInputPanelAnimationDecision.AnimateResidualToKeyboardTarget -> {
                animateTo(
                    scope = scope,
                    targetHeight = keyboardTargetHeight,
                    adoptStartHeight = null
                )
            }
            MessageInputPanelAnimationDecision.HoldResidualUntilKeyboardCatchesUp -> Unit
            MessageInputPanelAnimationDecision.FallbackKeyboardTakeOverResidual -> {
                cancel()
                animateFallbackResidualToKeyboard(scope, keyboardHeight)
            }
            MessageInputPanelAnimationDecision.SnapPanelTarget -> {
                cancel()
                panelAnimationTarget = target
                if (currentAnimatedPanelHeight != target) {
                    currentAnimatedPanelHeight = target
                    snapTo(scope, target)
                }
            }
            MessageInputPanelAnimationDecision.KeepCurrentAnimation,
            MessageInputPanelAnimationDecision.NoOp -> Unit
            is MessageInputPanelAnimationDecision.AnimatePanelTarget -> {
                val startHeight = if (decision.adoptKeyboardHeightAsStart) keyboardHeight else null
                if (startHeight != null && currentAnimatedPanelHeight != startHeight) {
                    currentAnimatedPanelHeight = startHeight
                }
                if (currentAnimatedPanelHeight != target) {
                    animateTo(scope, target, startHeight)
                }
            }
        }
    }

    fun cancel() {
        animationJob?.cancel()
        animationJob = null
        notifyAnimationRunning(false)
    }

    private fun snapTo(scope: CoroutineScope, height: Int) {
        animationJob?.cancel()
        notifyAnimationRunning(false)
        animationJob = scope.launch {
            heightAnimatable.snapTo(height.toFloat())
            currentAnimatedPanelHeight = height
            animationJob = null
        }
    }

    private fun animateTo(
        scope: CoroutineScope,
        targetHeight: Int,
        adoptStartHeight: Int?
    ) {
        if (animationJob?.isActive == true && panelAnimationTarget == targetHeight) {
            return
        }
        animationJob?.cancel()
        panelAnimationTarget = targetHeight
        notifyAnimationRunning(true)
        animationJob = scope.launch {
            try {
                if (adoptStartHeight != null) {
                    heightAnimatable.snapTo(adoptStartHeight.toFloat())
                    currentAnimatedPanelHeight = adoptStartHeight
                }
                heightAnimatable.animateTo(
                    targetValue = targetHeight.toFloat(),
                    animationSpec = tween(durationMillis = PANEL_ANIM_DURATION_MS, easing = PanelDecelerateEasing)
                ) {
                    currentAnimatedPanelHeight = value.toInt()
                }
                currentAnimatedPanelHeight = targetHeight
                animationJob = null
                notifyAnimationRunning(false)
            } catch (e: CancellationException) {
                // Superseded by a newer animation or an explicit cancel; the new owner
                // (animateTo/snapTo/cancel) is responsible for the running notification.
                throw e
            }
        }
    }

    private fun animateFallbackResidualToKeyboard(scope: CoroutineScope, keyboardHeight: Int) {
        if (currentAnimatedPanelHeight == keyboardHeight) {
            completeFallbackKeyboardTakeover(scope)
            return
        }
        panelAnimationTarget = keyboardHeight
        notifyAnimationRunning(true)
        animationJob = scope.launch {
            try {
                heightAnimatable.animateTo(
                    targetValue = keyboardHeight.toFloat(),
                    animationSpec = tween(durationMillis = PANEL_ANIM_DURATION_MS, easing = PanelDecelerateEasing)
                ) {
                    currentAnimatedPanelHeight = value.toInt()
                }
                completeFallbackKeyboardTakeover(this)
                notifyAnimationRunning(false)
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private fun completeFallbackKeyboardTakeover(scope: CoroutineScope) {
        animationJob = null
        panelAnimationTarget = 0
        currentAnimatedPanelHeight = 0
        scope.launch {
            heightAnimatable.snapTo(0f)
        }
    }
}
