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
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageViewModel
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem

@Composable
fun SearchMessageScreen(
    keywords: String,
    onBack: () -> Unit,
    onMessageInConversationClick: (MessageSearchResultItem) -> Unit,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {}
) {
    val colors = LocalTheme.current.colors
    val messageViewModel: SearchMessageViewModel = viewModel()

    var searchQuery by remember { mutableStateOf(keywords) }
    val isSearching by messageViewModel.isSearching.collectAsState()
    val isLoadingMore by messageViewModel.isLoadingMore.collectAsState()
    val messageResults by messageViewModel.messageSearchResults.collectAsState()
    val searchError by messageViewModel.searchError.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        messageViewModel.updateSearchQuery(searchQuery)
    }

    ObserveSearchError(searchError) { messageViewModel.clearSearchError() }
    LoadMoreOnBottomReached(listState) { messageViewModel.searchMore() }
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
                messageViewModel.updateSearchQuery(it)
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
        } else if (messageResults.isEmpty() && searchQuery.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_cannot_found_chat_record),
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
                if (messageResults.isNotEmpty()) {
                    item {
                        Text(
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 4.dp
                            ),
                            text = stringResource(R.string.search_category_chat_record),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textColorPrimary
                        )
                    }
                }
                items(messageResults.size) { index ->
                    val conversation = messageResults[index]
                    ConversationSearchResultItem(
                        keywords = searchQuery,
                        result = conversation,
                        onClick = { onMessageInConversationClick(conversation) }
                    )
                }
                if (isLoadingMore) {
                    item { SearchLoadMoreFooter() }
                }
            }
        }
    }
}
