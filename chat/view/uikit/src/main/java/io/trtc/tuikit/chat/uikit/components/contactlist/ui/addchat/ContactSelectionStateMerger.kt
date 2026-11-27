package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat
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
