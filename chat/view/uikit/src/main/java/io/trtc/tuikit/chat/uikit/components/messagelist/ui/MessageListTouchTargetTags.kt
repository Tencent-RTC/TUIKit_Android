package io.trtc.tuikit.chat.uikit.components.messagelist.ui
import android.view.View
import io.trtc.tuikit.chat.uikit.R
internal object MessageListTouchTargetTags {
    fun mark(view: View) {
        view.setTag(R.id.message_list_message_touch_target_tag, true)
    }

    fun isMarked(view: View): Boolean {
        return view.getTag(R.id.message_list_message_touch_target_tag) == true
    }
}
