package io.trtc.tuikit.chat.uikit.components.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchContactViewModel
import io.trtc.tuikit.atomicxcore.api.search.FriendSearchInfo

@Composable
fun SearchContactScreen(
    keywords: String,
    onBack: () -> Unit,
    onContactClick: (FriendSearchInfo) -> Unit,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {}
) {
    val colors = LocalTheme.current.colors
    val contactViewModel: SearchContactViewModel = viewModel()

    var searchQuery by remember { mutableStateOf(keywords) }
    val isSearching by contactViewModel.isSearching.collectAsState()
    val isLoadingMore by contactViewModel.isLoadingMore.collectAsState()
    val contactResults by contactViewModel.contactSearchResults.collectAsState()
    val searchError by contactViewModel.searchError.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        contactViewModel.updateSearchQuery(searchQuery)
    }

    ObserveSearchError(searchError) { contactViewModel.clearSearchError() }
    LoadMoreOnBottomReached(listState) { contactViewModel.searchMore() }
    HideKeyboardOnScroll(listState)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgColorOperate)
    ) {
        SubScreenSearchHeader(
            query = searchQuery,
            onQueryChange = {
                searchQuery = it
                contactViewModel.updateSearchQuery(it)
                onQueryChange(it)
            },
            onBack = onBack
        )

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = colors.textColorLink,
                    strokeWidth = 2.dp
                )
            }
        } else if (contactResults.isEmpty() && searchQuery.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_cannot_found_contact),
                    color = colors.textColorSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hideKeyboardOnTouch(),
                state = listState
            ) {
                if (contactResults.isNotEmpty()) {
                    item {
                        Text(
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 4.dp
                            ),
                            text = stringResource(R.string.search_category_contact),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textColorPrimary
                        )
                    }
                }
                items(contactResults.size) { index ->
                    val contact = contactResults[index]
                    ContactSearchResultItem(
                        keywords = searchQuery,
                        result = contact,
                        onClick = { onContactClick(contact) }
                    )
                }
                if (isLoadingMore) {
                    item { SearchLoadMoreFooter() }
                }
            }
        }
    }
}
