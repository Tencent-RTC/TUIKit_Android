package io.trtc.tuikit.chat.uikit.components.messagelist.ui
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

internal object MessageListTouchTargetHitTester {
    fun isMessageTouchTargetHit(recyclerView: RecyclerView, event: MotionEvent): Boolean {
        val child = recyclerView.findChildViewUnder(event.x, event.y) ?: return false
        val localX = event.x - child.left + child.scrollX - child.translationX
        val localY = event.y - child.top + child.scrollY - child.translationY
        return child.hasMarkedTouchTargetAt(localX, localY)
    }

    private fun View.hasMarkedTouchTargetAt(x: Float, y: Float): Boolean {
        if (visibility != View.VISIBLE || x < 0f || y < 0f || x > width || y > height) {
            return false
        }
        if (MessageListTouchTargetTags.isMarked(this)) {
            return true
        }
        if (this !is ViewGroup) {
            return false
        }
        for (index in childCount - 1 downTo 0) {
            val child = getChildAt(index)
            val childX = x - child.left + child.scrollX - child.translationX
            val childY = y - child.top + child.scrollY - child.translationY
            if (child.hasMarkedTouchTargetAt(childX, childY)) {
                return true
            }
        }
        return false
    }
}
