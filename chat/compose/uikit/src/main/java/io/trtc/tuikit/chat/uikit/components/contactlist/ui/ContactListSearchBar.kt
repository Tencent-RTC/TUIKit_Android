package io.trtc.tuikit.chat.uikit.components.contactlist.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.SearchBar

internal val ContactListSearchBarHeight = 68.dp

@Composable
fun ContactListSearchBar(
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    query: String? = null,
) {
    SearchBar(
        onQueryChanged = onQueryChange,
        modifier = modifier,
        showBack = false,
        showCancel = false,
        inputHeight = 36.dp,
        hint = stringResource(R.string.contact_list_search),
        debounceMs = 300L,
        paddingVertical = 16.dp,
        expandTouchTargets = false,
        query = query,
    )
}
