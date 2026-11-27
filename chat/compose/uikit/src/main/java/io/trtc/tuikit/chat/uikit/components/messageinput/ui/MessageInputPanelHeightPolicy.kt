package io.trtc.tuikit.chat.uikit.components.messageinput.ui

internal object MessageInputPanelHeightPolicy {
    fun shouldAdoptKeyboardHeightAsAnimationStart(
        targetPanelHeight: Int,
        keyboardHeight: Int,
        currentPanelHeight: Int,
        isAnimatingToTarget: Boolean
    ): Boolean {
        return !isAnimatingToTarget &&
            targetPanelHeight > 0 &&
            keyboardHeight > 0 &&
            currentPanelHeight < keyboardHeight
    }

    fun shouldSnapPanelTarget(
        targetPanelHeight: Int,
        keyboardHeight: Int,
        currentPanelHeight: Int,
        isAnimatingToTarget: Boolean
    ): Boolean {
        if (isAnimatingToTarget) {
            return false
        }
        return targetPanelHeight > 0 &&
            keyboardHeight > 0 &&
            targetPanelHeight == keyboardHeight &&
            currentPanelHeight < targetPanelHeight
    }

    fun shouldLetFallbackKeyboardTakeOverResidualPanelHeight(
        targetPanelHeight: Int,
        keyboardHeight: Int,
        currentPanelHeight: Int,
        isKeyboardAnimationSupported: Boolean
    ): Boolean {
        return !isKeyboardAnimationSupported &&
            targetPanelHeight == 0 &&
            keyboardHeight > 0 &&
            currentPanelHeight >= keyboardHeight
    }

    fun shouldClearResidualPanelHeightAfterKeyboardTakeover(
        targetPanelHeight: Int,
        keyboardHeight: Int,
        currentPanelHeight: Int,
        isKeyboardAnimationSupported: Boolean
    ): Boolean {
        return isKeyboardAnimationSupported &&
            targetPanelHeight == 0 &&
            keyboardHeight > 0 &&
            currentPanelHeight in 1..keyboardHeight
    }

    fun shouldAnimateResidualPanelHeightToKeyboardTarget(
        targetPanelHeight: Int,
        keyboardHeight: Int,
        currentPanelHeight: Int,
        keyboardTargetHeight: Int,
        isKeyboardAnimationSupported: Boolean
    ): Boolean {
        return isKeyboardAnimationSupported &&
            targetPanelHeight == 0 &&
            keyboardHeight > 0 &&
            keyboardTargetHeight > 0 &&
            currentPanelHeight > keyboardHeight &&
            currentPanelHeight != keyboardTargetHeight
    }

    fun shouldHoldResidualPanelHeightUntilKeyboardCatchesUp(
        targetPanelHeight: Int,
        keyboardHeight: Int,
        currentPanelHeight: Int,
        isKeyboardAnimationSupported: Boolean
    ): Boolean {
        return isKeyboardAnimationSupported &&
            targetPanelHeight == 0 &&
            keyboardHeight > 0 &&
            currentPanelHeight > keyboardHeight
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
