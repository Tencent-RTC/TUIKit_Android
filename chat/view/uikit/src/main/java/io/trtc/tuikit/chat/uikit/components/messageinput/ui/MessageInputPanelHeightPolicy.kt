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
