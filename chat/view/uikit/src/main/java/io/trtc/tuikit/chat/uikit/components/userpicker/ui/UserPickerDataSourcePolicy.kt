package io.trtc.tuikit.chat.uikit.components.userpicker.ui
import io.trtc.tuikit.chat.uikit.components.userpicker.adapter.FlatItem
import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData

internal object UserPickerDataSourcePolicy {
    fun deduplicateByKey(items: List<UserPickerData<Any?>>): List<UserPickerData<Any?>> {
        val seenKeys = LinkedHashSet<String>()
        return items.filter { item -> seenKeys.add(item.key) }
    }

    fun shouldResetReachEnd(previousKeys: List<String>, newKeys: List<String>): Boolean {
        return previousKeys != newKeys
    }

    fun findUserItemPositions(items: List<FlatItem>, key: String): List<Int> {
        return items.mapIndexedNotNull { index, item ->
            if (item is FlatItem.UserItem && item.data.key == key) {
                index
            } else {
                null
            }
        }
    }
}
