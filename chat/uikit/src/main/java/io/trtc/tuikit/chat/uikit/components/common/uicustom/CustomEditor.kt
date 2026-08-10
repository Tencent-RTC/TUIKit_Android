package io.trtc.tuikit.chat.uikit.components.common.uicustom

@CustomItemDsl
class CustomEditor<C : EditorContext, I : CustomItem> internal constructor(
    val editorContext: C,
    defaults: List<I>,
) {
    private val mutableItems: MutableList<I> = ArrayList(defaults.size)
    private var frozen: Boolean = false

    init {
        val seen = HashSet<String>(defaults.size)
        for (item in defaults) {
            requireValidID(item.ID)
            requireUniqueID(item.ID, seen)
            seen.add(item.ID)
            mutableItems.add(item)
        }
    }

    val items: List<I>
        get() = mutableItems.toList()

    fun add(item: I) {
        ensureMutable()
        requireValidID(item.ID)
        requireAbsentID(item.ID)
        mutableItems.add(item)
    }

    fun remove(ID: String): Boolean {
        ensureMutable()
        val index = indexOfID(ID) ?: return false
        mutableItems.removeAt(index)
        return true
    }

    fun replace(ID: String, transform: (I) -> I): Boolean {
        ensureMutable()
        val index = indexOfID(ID) ?: return false
        val replacement = transform(mutableItems[index])
        require(replacement.ID == ID) {
            "replace must preserve item ID: expected $ID, got ${replacement.ID}"
        }
        requireValidID(replacement.ID)
        mutableItems[index] = replacement
        return true
    }

    fun insertBefore(anchorID: String, item: I): Boolean {
        ensureMutable()
        requireValidID(item.ID)
        requireAbsentID(item.ID)
        val anchorIndex = indexOfID(anchorID) ?: return false
        mutableItems.add(anchorIndex, item)
        return true
    }

    fun insertAfter(anchorID: String, item: I): Boolean {
        ensureMutable()
        requireValidID(item.ID)
        requireAbsentID(item.ID)
        val anchorIndex = indexOfID(anchorID) ?: return false
        mutableItems.add(anchorIndex + 1, item)
        return true
    }

    fun moveBefore(ID: String, anchorID: String): Boolean {
        ensureMutable()
        if (ID == anchorID) {
            return indexOfID(ID) != null
        }
        val fromIndex = indexOfID(ID) ?: return false
        val anchorIndex = indexOfID(anchorID) ?: return false
        val item = mutableItems.removeAt(fromIndex)
        val targetIndex = if (fromIndex < anchorIndex) anchorIndex - 1 else anchorIndex
        mutableItems.add(targetIndex, item)
        return true
    }

    fun moveAfter(ID: String, anchorID: String): Boolean {
        ensureMutable()
        if (ID == anchorID) {
            return indexOfID(ID) != null
        }
        val fromIndex = indexOfID(ID) ?: return false
        val anchorIndex = indexOfID(anchorID) ?: return false
        val item = mutableItems.removeAt(fromIndex)
        val adjustedAnchor = if (fromIndex < anchorIndex) anchorIndex - 1 else anchorIndex
        mutableItems.add(adjustedAnchor + 1, item)
        return true
    }

    fun clear() {
        ensureMutable()
        mutableItems.clear()
    }

    internal fun build(): List<I> {
        frozen = true
        return mutableItems.toList()
    }

    private fun ensureMutable() {
        check(!frozen) { "CustomItemEditor is frozen after build(); further mutations are not allowed" }
    }

    private fun indexOfID(ID: String): Int? {
        val index = mutableItems.indexOfFirst { it.ID == ID }
        return if (index >= 0) index else null
    }

    private fun requireValidID(ID: String) {
        require(ID.isNotBlank()) { "Item ID must not be blank" }
    }

    private fun requireAbsentID(ID: String) {
        require(indexOfID(ID) == null) { "Duplicate item ID: $ID" }
    }

    private fun requireUniqueID(ID: String, seen: Set<String>) {
        require(ID !in seen) { "Duplicate item ID: $ID" }
    }
}
