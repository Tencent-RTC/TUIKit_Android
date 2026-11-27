package io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout
internal object MessageListUnderflowAlignment {
    fun isContentUnderflow(
        viewportHeight: Int,
        paddingTop: Int,
        paddingBottom: Int,
        itemCount: Int,
        childCount: Int,
        firstVisibleItem: Int,
        lastVisibleItem: Int,
        contentTop: Int,
        contentBottom: Int
    ): Boolean {
        if (itemCount <= 0 || childCount <= 0) {
            return false
        }
        if (firstVisibleItem != 0 || lastVisibleItem != itemCount - 1) {
            return false
        }

        val viewportContentHeight = viewportHeight - paddingTop - paddingBottom
        val contentHeight = contentBottom - contentTop
        return viewportContentHeight > 0 && contentHeight > 0 && contentHeight < viewportContentHeight
    }

    fun calculateOffsetToTop(
        viewportHeight: Int,
        paddingTop: Int,
        paddingBottom: Int,
        itemCount: Int,
        childCount: Int,
        firstVisibleItem: Int,
        lastVisibleItem: Int,
        contentTop: Int,
        contentBottom: Int
    ): Int {
        if (!isContentUnderflow(
                viewportHeight = viewportHeight,
                paddingTop = paddingTop,
                paddingBottom = paddingBottom,
                itemCount = itemCount,
                childCount = childCount,
                firstVisibleItem = firstVisibleItem,
                lastVisibleItem = lastVisibleItem,
                contentTop = contentTop,
                contentBottom = contentBottom
            )
        ) {
            return 0
        }

        return paddingTop - contentTop
    }
}
