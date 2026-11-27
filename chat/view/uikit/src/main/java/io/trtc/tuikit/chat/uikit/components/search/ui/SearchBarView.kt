package io.trtc.tuikit.chat.uikit.components.search.ui
import android.content.Context
import android.util.AttributeSet
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.widgets.SearchBar
import io.trtc.tuikit.chat.uikit.components.widgets.SearchBarConfig

class SearchBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SearchBar(context, attrs, defStyleAttr) {

    init {
        configure(
            SearchBarConfig(
                showBack = false,
                showCancel = true,
                inputHeightDp = 40,
                debounceMs = 300L,
                searchIconRes = R.drawable.search_ic_search,
                clearIconRes = R.drawable.search_ic_search_clear,
                paddingHorizontalDp = 16,
                paddingVerticalDp = 10,
                paddingBottomDp = 16,
                inputCornerRadiusDp = 10,
                searchIconMarginStartDp = 12,
                inputTextPaddingStartDp = 30,
                expandTouchTargets = true
            )
        )
    }
}
