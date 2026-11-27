package io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout

internal class MessageListBoundaryPagingPolicy(
    edgeThreshold: Int = DEFAULT_EDGE_THRESHOLD
) {
    private val edgeThreshold = edgeThreshold.coerceAtLeast(0)
    private var newerEdgeArmed = true
    private var olderEdgeArmed = true

    fun reset() {
        newerEdgeArmed = true
        olderEdgeArmed = true
    }

    fun shouldLoadNewer(firstVisibleItem: Int): Boolean {
        if (firstVisibleItem < 0) {
            return false
        }

        if (firstVisibleItem > edgeThreshold) {
            newerEdgeArmed = true
            return false
        }

        if (!newerEdgeArmed) {
            return false
        }
        newerEdgeArmed = false
        return true
    }

    fun shouldLoadOlder(lastVisibleItem: Int, totalItemCount: Int): Boolean {
        if (lastVisibleItem < 0 || totalItemCount <= 0) {
            return false
        }

        val edgePosition = totalItemCount - 1 - edgeThreshold
        if (lastVisibleItem < edgePosition) {
            olderEdgeArmed = true
            return false
        }

        if (!olderEdgeArmed) {
            return false
        }
        olderEdgeArmed = false
        return true
    }

    private companion object {
        const val DEFAULT_EDGE_THRESHOLD = 2
    }
}
