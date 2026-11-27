package io.trtc.tuikit.chat.uikit.components.messagelist.ui
internal object MessageListInputDismissPolicy {
    fun shouldDismissInputForTap(isMessageTouchTargetHit: Boolean): Boolean {
        return !isMessageTouchTargetHit
    }

    fun shouldDismissInputForScroll(isDragging: Boolean): Boolean {
        return isDragging
    }
}
