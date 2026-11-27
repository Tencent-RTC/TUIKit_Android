package io.trtc.tuikit.chat.uikit.components.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.search.utils.displayName
import io.trtc.tuikit.chat.uikit.components.search.utils.getMessageAbstract
import io.trtc.tuikit.chat.uikit.components.search.utils.messageSender
import io.trtc.tuikit.chat.uikit.components.search.utils.messageSenderAvatarUrl
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageInConversationViewModel
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem

@Composable
fun SearchMessageInConversationScreen(
    conversation: MessageSearchResultItem,
    keyword: String,
    onBack: () -> Unit,
    onMessageClick: (MessageInfo) -> Unit,
    onConversationClick: (MessageSearchResultItem) -> Unit,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {}
) {
    val colors = LocalTheme.current.colors

    val viewModel: SearchMessageInConversationViewModel = viewModel()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val messagesInConversation by viewModel.messagesInConversation.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    var searchQuery by remember { mutableStateOf(keyword) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.updateSearchQuery(conversation.conversationID, searchQuery)
    }

    ObserveSearchError(searchError) { viewModel.clearSearchError() }
    LoadMoreOnBottomReached(listState) { viewModel.searchMore() }
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
                viewModel.updateSearchQuery(conversation.conversationID, it)
                onQueryChange(it)
            },
            onBack = onBack
        )

        ConversationInfoWidget(conversation) {
            onConversationClick(conversation)
        }
        HorizontalDivider(
            thickness = with(LocalDensity.current) { 1.toDp() },
            color = colors.strokeColorSecondary,
            modifier = Modifier.padding(horizontal = 16.dp)
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
        } else if (messagesInConversation.isEmpty() && searchQuery.isNotBlank()) {
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
        } else if (searchQuery.isNotBlank()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hideKeyboardOnTouch(),
                state = listState
            ) {
                items(messagesInConversation.size) { index ->
                    val message = messagesInConversation[index]
                    MessageInConversationItem(
                        message = message,
                        searchQuery = searchQuery,
                        onClick = { onMessageClick(message) }
                    )
                }
                if (isLoadingMore) {
                    item { SearchLoadMoreFooter() }
                }
            }
        }
    }
}

@Composable
private fun ConversationInfoWidget(conversation: MessageSearchResultItem, onClick: () -> Unit) {
    val colors = LocalTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(colors.bgColorOperate)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            url = conversation.conversationAvatarURL,
            name = conversation.displayName,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = conversation.displayName,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = colors.textColorPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        Icon(
            painter = painterResource(R.drawable.search_ic_arrow_right),
            contentDescription = "",
            tint = colors.textColorSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun MessageInConversationItem(
    message: MessageInfo,
    searchQuery: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgColorOperate)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Avatar(
            url = message.messageSenderAvatarUrl,
            name = message.messageSender,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = message.messageSender,
                color = colors.textColorPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            EmojiHighlightText(
                text = message.getMessageAbstract(),
                keywords = searchQuery,
                textColor = colors.textColorSecondary,
                textSize = 12.sp,
                maxLines = 2
            )
        }
    }
}
