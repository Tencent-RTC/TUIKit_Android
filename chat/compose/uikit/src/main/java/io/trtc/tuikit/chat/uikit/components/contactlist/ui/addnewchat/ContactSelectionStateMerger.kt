package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addnewchat

import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo

internal object ContactSelectionStateMerger {

    fun <Selected, Visible, Key> merge(
        currentSelected: List<Selected>,
        visibleItems: List<Visible>,
        visibleSelectedItems: List<Visible>,
        selectedKeySelector: (Selected) -> Key,
        visibleKeySelector: (Visible) -> Key,
        visibleToSelectedMapper: (Visible) -> Selected
    ): List<Selected> {
        val visibleKeys = visibleItems.map(visibleKeySelector).toSet()
        val mergedSelected = currentSelected
            .filterNot { selectedKeySelector(it) in visibleKeys }
            .toMutableList()
        val mergedKeys = mergedSelected.map(selectedKeySelector).toMutableSet()

        visibleSelectedItems.forEach { visibleItem ->
            val key = visibleKeySelector(visibleItem)
            if (mergedKeys.add(key)) {
                mergedSelected.add(visibleToSelectedMapper(visibleItem))
            }
        }

        return mergedSelected
    }
}

internal fun ContactInfo.matchesSearchQuery(query: String): Boolean {
    val keyword = query.trim()
    if (keyword.isEmpty()) {
        return true
    }
    return listOf(displayName, userID, nickname, friendRemark)
        .filterNotNull()
        .distinct()
        .any { value ->
            value.contains(keyword, ignoreCase = true)
        }
}
