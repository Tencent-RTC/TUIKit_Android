package io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout
import androidx.recyclerview.widget.RecyclerView

internal class MessageListAlignmentController(
    private val recyclerView: RecyclerView
) {

    private val applyRunnable = Runnable { recyclerView.requestLayout() }

    fun attach() = Unit

    fun detach() {
        recyclerView.removeCallbacks(applyRunnable)
    }

    fun reset() {
        recyclerView.removeCallbacks(applyRunnable)
    }

    fun markDataLoaded() = Unit

    fun requestAlignment() {
        recyclerView.removeCallbacks(applyRunnable)
        recyclerView.post(applyRunnable)
    }

    fun applyAlignmentImmediately() {
        recyclerView.removeCallbacks(applyRunnable)
        recyclerView.requestLayout()
    }
}
