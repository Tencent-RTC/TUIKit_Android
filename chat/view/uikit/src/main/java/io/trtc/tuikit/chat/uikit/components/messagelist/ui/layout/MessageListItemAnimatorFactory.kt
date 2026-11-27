package io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

internal object MessageListItemAnimatorFactory {
    fun create(): RecyclerView.ItemAnimator {
        return DefaultItemAnimator().apply {
            supportsChangeAnimations = false
        }
    }
}
