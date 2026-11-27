package io.trtc.tuikit.chat.uikit.components.userpicker.ui
internal class UserPickerSelectionState {

    private val selectedKeys = LinkedHashSet<String>()

    val keys: Set<String>
        get() = selectedKeys

    fun replaceWith(keys: List<String>): Set<String> {
        val previous = selectedKeys.toSet()
        selectedKeys.clear()
        selectedKeys.addAll(keys)
        return changedKeys(previous)
    }

    fun onDataSourceChanged() {
    }

    fun toggle(
        key: String,
        maxCount: Int?,
        isLocked: Boolean,
        singleSelect: Boolean = maxCount == 1
    ): Result {
        if (isLocked) return Result.Ignored

        if (singleSelect) {
            val previous = selectedKeys.toSet()
            selectedKeys.clear()
            selectedKeys.add(key)
            return Result.Changed(changedKeys(previous))
        }

        if (selectedKeys.remove(key)) {
            return Result.Changed(setOf(key))
        }

        if (maxCount != null && selectedKeys.size >= maxCount) {
            return Result.MaxCountExceeded
        }

        selectedKeys.add(key)
        return Result.Changed(setOf(key))
    }

    private fun changedKeys(previous: Set<String>): Set<String> {
        return (previous + selectedKeys).filterTo(LinkedHashSet()) { key ->
            previous.contains(key) != selectedKeys.contains(key)
        }
    }

    sealed class Result {
        data class Changed(val changedKeys: Set<String>) : Result()
        object MaxCountExceeded : Result()
        object Ignored : Result()
    }
}

internal object UserPickerReachEndPolicy {
    fun shouldNotify(
        hasReachedEnd: Boolean,
        hasListener: Boolean,
        totalItemCount: Int,
        lastVisibleItemPosition: Int
    ): Boolean {
        return !hasReachedEnd &&
            hasListener &&
            totalItemCount > 0 &&
            lastVisibleItemPosition >= totalItemCount - 1
    }
}
