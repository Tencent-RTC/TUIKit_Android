package io.trtc.tuikit.chat.uikit.components.messagelist.config

import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomActionContext

@DslMarker
annotation class MessageActionDsl

@MessageActionDsl
class MessageActionEditor internal constructor(
    val editorContext: MessageCustomActionContext,
    defaults: List<MessageCustomAction>,
) {
    private val mutableItems: MutableList<MessageCustomAction> = ArrayList(defaults.size)
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

    val items: List<MessageCustomAction>
        get() = mutableItems.toList()

    fun add(item: MessageCustomAction) {
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

    fun replace(ID: String, transform: (MessageCustomAction) -> MessageCustomAction): Boolean {
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

    fun insertBefore(anchorID: String, item: MessageCustomAction): Boolean {
        ensureMutable()
        requireValidID(item.ID)
        requireAbsentID(item.ID)
        val anchorIndex = indexOfID(anchorID) ?: return false
        mutableItems.add(anchorIndex, item)
        return true
    }

    fun insertAfter(anchorID: String, item: MessageCustomAction): Boolean {
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

    internal fun build(): List<MessageCustomAction> {
        frozen = true
        return mutableItems.toList()
    }

    private fun ensureMutable() {
        check(!frozen) { "MessageActionEditor is frozen after build(); further mutations are not allowed" }
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

fun interface MessageActionCustomizer {
    fun customize(editor: MessageActionEditor)
}
