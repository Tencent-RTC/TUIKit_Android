package io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout
internal object MessageListAdapterUpdatePolicy {
    fun shouldPostponeAdapterNotify(
        isInScrollCallback: Boolean,
        isComputingLayout: Boolean
    ): Boolean {
        return isInScrollCallback || isComputingLayout
    }
}
