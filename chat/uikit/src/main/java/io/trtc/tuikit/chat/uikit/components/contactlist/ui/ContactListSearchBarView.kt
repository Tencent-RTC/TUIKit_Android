package io.trtc.tuikit.chat.uikit.components.contactlist.ui
import android.content.Context
import android.util.AttributeSet
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.widgets.SearchBar
import io.trtc.tuikit.chat.uikit.components.widgets.SearchBarConfig

internal class ContactListSearchBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SearchBar(context, attrs, defStyleAttr) {

    init {
        configure(
            SearchBarConfig(
                showBack = false,
                showCancel = false,
                inputHeightDp = 36,
                debounceMs = 300L,
                searchIconRes = R.drawable.contact_list_ic_search,
                clearIconRes = R.drawable.contact_list_ic_search_clear,
                paddingHorizontalDp = 16,
                paddingVerticalDp = 16,
                expandTouchTargets = false
            )
        )
    }

    fun setQuery(query: String) {
        setQuery(query, notify = true)
    }
}
