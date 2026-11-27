package io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout
import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal class MessageListLayoutManager(context: Context) : LinearLayoutManager(context) {

    init {
        reverseLayout = true
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        super.onLayoutChildren(recycler, state)
        if (state?.isPreLayout == true) {
            return
        }
        alignTopWhenAllItemsFit()
    }

    private fun alignTopWhenAllItemsFit() {
        if (itemCount <= 0 || childCount <= 0) {
            return
        }

        var contentTop = Int.MAX_VALUE
        var contentBottom = Int.MIN_VALUE
        for (index in 0 until childCount) {
            val child = getChildAt(index) ?: continue
            contentTop = minOf(contentTop, getDecoratedTop(child))
            contentBottom = maxOf(contentBottom, getDecoratedBottom(child))
        }

        val offset = MessageListUnderflowAlignment.calculateOffsetToTop(
            viewportHeight = height,
            paddingTop = paddingTop,
            paddingBottom = paddingBottom,
            itemCount = itemCount,
            childCount = childCount,
            firstVisibleItem = findFirstVisibleItemPosition(),
            lastVisibleItem = findLastVisibleItemPosition(),
            contentTop = contentTop,
            contentBottom = contentBottom
        )
        if (offset != 0) {
            offsetChildrenVertical(offset)
        }
    }
}
