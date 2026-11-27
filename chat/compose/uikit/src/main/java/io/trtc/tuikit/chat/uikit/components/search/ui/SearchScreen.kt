package io.trtc.tuikit.chat.uikit.components.search.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.search.utils.displayName
import io.trtc.tuikit.chat.uikit.components.search.utils.getMessageAbstract
import io.trtc.tuikit.chat.uikit.components.search.utils.userAvatarURL
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchAllViewModel
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchCategory
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchError
import io.trtc.tuikit.chat.uikit.components.widgets.SearchBar
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.search.FriendSearchInfo
import io.trtc.tuikit.atomicxcore.api.search.GroupSearchInfo
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem
import io.trtc.tuikit.atomicxcore.api.search.SearchType

enum class SearchFlowStep {
    GLOBAL_SEARCH,
    CONTACT_DETAIL,
    GROUP_DETAIL,
    MESSAGE_DETAIL,
    MESSAGE_IN_CONVERSATION_DETAIL
}

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onContactSelect: (FriendSearchInfo) -> Unit = {},
    onGroupSelect: (GroupSearchInfo) -> Unit = {},
    onConversationSelect: (MessageSearchResultItem) -> Unit = {},
    onMessageSelect: (MessageInfo) -> Unit = {}
) {
    val colors = LocalTheme.current.colors
    val globalViewModel: SearchAllViewModel = viewModel()

    var searchAllQuery by remember { mutableStateOf("") }
    val isSearching by globalViewModel.isSearching.collectAsState()
    val searchCategories by globalViewModel.searchCategories.collectAsState()
    val searchError by globalViewModel.searchError.collectAsState()

    ObserveSearchError(searchError) { globalViewModel.clearSearchError() }

    var currentFlowStep by remember { mutableStateOf(SearchFlowStep.GLOBAL_SEARCH) }
    var prevFlowStep by remember { mutableStateOf(SearchFlowStep.GLOBAL_SEARCH) }
    var selectedConversation by remember { mutableStateOf<MessageSearchResultItem?>(null) }

    Column(
        modifier = modifier
            .background(colors.bgColorOperate)
            .fillMaxSize()
    ) {
        Crossfade(
            targetState = currentFlowStep,
        ) { targetStep ->
            when (targetStep) {
                SearchFlowStep.GLOBAL_SEARCH -> {
                    GlobalSearchPage(
                        searchAllQuery = searchAllQuery,
                        onSearchQueryChange = {
                            searchAllQuery = it
                            globalViewModel.updateSearchQuery(it)
                        },
                        onBack = onBack,
                        isSearching = isSearching,
                        searchCategories = searchCategories,
                        onResultClick = { resultInfo ->
                            when (resultInfo) {
                                is FriendSearchInfo -> {
                                    onContactSelect(resultInfo)
                                }

                                is GroupSearchInfo -> {
                                    onGroupSelect(resultInfo)
                                }

                                is MessageSearchResultItem -> {
                                    selectedConversation = resultInfo
                                    prevFlowStep = currentFlowStep
                                    currentFlowStep = SearchFlowStep.MESSAGE_IN_CONVERSATION_DETAIL
                                }
                            }
                        },
                        onShowMore = { searchType ->
                            when (searchType) {
                                SearchType.FRIEND -> {
                                    prevFlowStep = currentFlowStep
                                    currentFlowStep = SearchFlowStep.CONTACT_DETAIL
                                }

                                SearchType.GROUP -> {
                                    prevFlowStep = currentFlowStep
                                    currentFlowStep = SearchFlowStep.GROUP_DETAIL
                                }

                                SearchType.MESSAGE -> {
                                    prevFlowStep = currentFlowStep
                                    currentFlowStep = SearchFlowStep.MESSAGE_DETAIL
                                }

                                else -> {
                                }
                            }
                        }
                    )
                }

                SearchFlowStep.CONTACT_DETAIL -> {
                    SearchContactScreen(
                        keywords = searchAllQuery,
                        onQueryChange = { searchAllQuery = it },
                        onBack = {
                            prevFlowStep = currentFlowStep
                            currentFlowStep = SearchFlowStep.GLOBAL_SEARCH
                            selectedConversation = null
                        },
                        onContactClick = { contact ->
                            onContactSelect(contact)
                        }
                    )
                }

                SearchFlowStep.GROUP_DETAIL -> {
                    SearchGroupScreen(
                        keywords = searchAllQuery,
                        onQueryChange = { searchAllQuery = it },
                        onBack = {
                            prevFlowStep = currentFlowStep
                            currentFlowStep = SearchFlowStep.GLOBAL_SEARCH
                            selectedConversation = null
                        },
                        onGroupClick = { group ->
                            onGroupSelect(group)
                        }
                    )
                }

                SearchFlowStep.MESSAGE_DETAIL -> {
                    SearchMessageScreen(
                        keywords = searchAllQuery,
                        onQueryChange = { searchAllQuery = it },
                        onBack = {
                            prevFlowStep = currentFlowStep
                            currentFlowStep = SearchFlowStep.GLOBAL_SEARCH
                            selectedConversation = null
                        },
                        onMessageInConversationClick = { conversation ->
                            selectedConversation = conversation
                            prevFlowStep = currentFlowStep
                            currentFlowStep = SearchFlowStep.MESSAGE_IN_CONVERSATION_DETAIL
                        }
                    )
                }

                SearchFlowStep.MESSAGE_IN_CONVERSATION_DETAIL -> {
                    selectedConversation?.let { conversation ->
                        SearchMessageInConversationScreen(
                            conversation = conversation,
                            keyword = searchAllQuery,
                            onQueryChange = { searchAllQuery = it },
                            onBack = {
                                if (prevFlowStep == SearchFlowStep.GLOBAL_SEARCH) {
                                    prevFlowStep = currentFlowStep
                                    currentFlowStep = SearchFlowStep.GLOBAL_SEARCH
                                } else if (prevFlowStep == SearchFlowStep.MESSAGE_DETAIL) {
                                    prevFlowStep = currentFlowStep
                                    currentFlowStep = SearchFlowStep.MESSAGE_DETAIL
                                }
                            },
                            onMessageClick = { message ->
                                onMessageSelect(message)
                            },
                            onConversationClick = { conversationInfo ->
                                onConversationSelect(conversationInfo)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchPage(
    searchAllQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    isSearching: Boolean,
    searchCategories: List<SearchCategory>,
    onResultClick: (Any) -> Unit,
    onShowMore: (SearchType) -> Unit
) {
    val colors = LocalTheme.current.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorDefault)
    ) {
        SearchHeader(
            query = searchAllQuery,
            onQueryChange = onSearchQueryChange,
            onBack = onBack
        )

        if (searchAllQuery.isNotBlank()) {
            GlobalSearchResults(
                keywords = searchAllQuery,
                categories = searchCategories,
                isLoading = isSearching,
                onResultClick = onResultClick,
                onShowMore = onShowMore
            )
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit
) {
    SearchBar(
        onQueryChanged = onQueryChange,
        showBack = false,
        showCancel = true,
        inputHeight = 40.dp,
        debounceMs = 300L,
        paddingBottom = 16.dp,
        searchIconMarginStart = 12.dp,
        inputTextPaddingStart = 30.dp,
        query = query,
        autoFocus = true,
        onCancel = onBack
    )
}

@Composable
fun SubScreenSearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit
) {
    SearchBar(
        onQueryChanged = onQueryChange,
        showBack = true,
        showCancel = true,
        inputHeight = 40.dp,
        debounceMs = 300L,
        paddingBottom = 16.dp,
        searchIconMarginStart = 12.dp,
        inputTextPaddingStart = 30.dp,
        query = query,
        autoFocus = true,
        onCancel = onBack,
        onBack = onBack
    )
}

@Composable
private fun GlobalSearchResults(
    keywords: String,
    categories: List<SearchCategory>,
    isLoading: Boolean,
    onResultClick: (Any) -> Unit,
    onShowMore: (SearchType) -> Unit
) {
    val colors = LocalTheme.current.colors
    val listState = rememberLazyListState()
    HideKeyboardOnScroll(listState)

    if (isLoading) {
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
    } else if (categories.isEmpty()) {
        SearchEmptyState()
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hideKeyboardOnTouch(),
            state = listState
        ) {
            categories.forEach { category ->
                item(key = "header_${category.type}") {
                    SearchCategoryHeader(category.type)
                }
                category.results.forEachIndexed { index, result ->
                    item(key = "${category.type}_$index") {
                        when (result) {
                            is FriendSearchInfo -> ContactSearchResultItem(
                                keywords = keywords,
                                result = result,
                                onClick = { onResultClick(result) }
                            )

                            is GroupSearchInfo -> GroupSearchResultItem(
                                keywords = keywords,
                                result = result,
                                onClick = { onResultClick(result) }
                            )

                            is MessageSearchResultItem -> ConversationSearchResultItem(
                                keywords = keywords,
                                result = result,
                                onClick = { onResultClick(result) }
                            )
                        }
                    }
                }
                if (category.hasMore) {
                    item(key = "more_${category.type}") {
                        SearchCategoryMore(
                            searchType = category.type,
                            onClick = { onShowMore(category.type) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState() {
    val colors = LocalTheme.current.colors
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.search_ic_search),
            contentDescription = null,
            tint = colors.textColorTertiary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.search_no_result),
            color = colors.textColorSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SearchCategoryHeader(searchType: SearchType) {
    val colors = LocalTheme.current.colors

    Text(
        text = when (searchType) {
            SearchType.FRIEND -> stringResource(R.string.search_category_contact)
            SearchType.GROUP -> stringResource(R.string.search_category_group)
            SearchType.MESSAGE -> stringResource(R.string.search_category_chat_record)
            else -> ""
        },
        fontSize = 14.sp,
        color = colors.textColorSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 1.dp)
            .background(colors.bgColorOperate)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun SearchCategoryMore(
    searchType: SearchType,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors

    Text(
        text = when (searchType) {
            SearchType.FRIEND -> stringResource(R.string.search_view_more_contacts)
            SearchType.GROUP -> stringResource(R.string.search_view_more_groups)
            SearchType.MESSAGE -> stringResource(R.string.search_view_more_messages)
            else -> stringResource(R.string.search_more)
        },
        fontSize = 14.sp,
        color = colors.textColorLink,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgColorOperate)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
internal fun ContactSearchResultItem(
    keywords: String,
    result: FriendSearchInfo,
    onClick: () -> Unit
) {
    SearchResultItemLayout(
        avatarUrl = result.userAvatarURL,
        avatarName = result.displayName,
        onClick = onClick
    ) {
        HighlightTitle(
            text = result.displayName,
            keywords = keywords
        )
        HighlightSecondary(
            textList = listOf(
                HighlightTextItem(text = "ID: "),
                HighlightTextItem(text = result.userID, keywords = keywords)
            )
        )
    }
}

@Composable
internal fun GroupSearchResultItem(
    keywords: String,
    result: GroupSearchInfo,
    onClick: () -> Unit
) {
    SearchResultItemLayout(
        avatarUrl = result.groupAvatarURL,
        avatarName = result.displayName,
        onClick = onClick
    ) {
        HighlightTitle(
            text = result.displayName,
            keywords = keywords
        )
        HighlightSecondary(
            textList = listOf(
                HighlightTextItem(text = "${stringResource(R.string.search_group_id)}: "),
                HighlightTextItem(text = result.groupID, keywords = keywords)
            )
        )
    }
}

@Composable
internal fun ConversationSearchResultItem(
    keywords: String,
    result: MessageSearchResultItem,
    onClick: () -> Unit
) {
    SearchResultItemLayout(
        avatarUrl = result.conversationAvatarURL,
        avatarName = result.displayName,
        onClick = onClick
    ) {
        HighlightTitle(
            text = result.displayName,
            keywords = keywords
        )
        if (result.messageCount > 1) {
            HighlightSecondary(
                textList = listOf(
                    HighlightTextItem(
                        text = stringResource(
                            R.string.search_related_chat_record_count,
                            result.messageCount
                        )
                    )
                )
            )
        } else {
            val abstract = result.messageList.firstOrNull()
                ?.getMessageAbstract()
                ?: ""
            EmojiHighlightText(
                text = abstract,
                keywords = keywords
            )
        }
    }
}

@Composable
private fun SearchResultItemLayout(
    avatarUrl: String?,
    avatarName: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = LocalTheme.current.colors
    val hairlineWidth = with(LocalDensity.current) { 1.toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(61.dp)
            .background(colors.bgColorOperate)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                url = avatarUrl,
                name = avatarName,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                content()
            }
        }

        HorizontalDivider(
            thickness = hairlineWidth,
            color = colors.strokeColorPrimary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 3.dp)
        )
    }
}

@Composable
internal fun HideKeyboardOnScroll(listState: LazyListState) {
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                keyboardController?.hide()
            }
        }
    }
}

internal fun Modifier.hideKeyboardOnTouch(): Modifier = composed {
    val keyboardController = LocalSoftwareKeyboardController.current
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial)
            keyboardController?.hide()
        }
    }
}

@Composable
internal fun ObserveSearchError(
    searchError: SearchError?,
    onConsumed: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(searchError) {
        val error = searchError ?: return@LaunchedEffect
        val message = error.message.ifBlank { error.code.toString() }
        Toast.error(context, message)
        onConsumed()
    }
}

@Composable
internal fun LoadMoreOnBottomReached(
    listState: LazyListState,
    onLoadMore: () -> Unit
) {
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                total > 0 && lastVisible >= total - 1
            )
        }.collect { (_, _, endReached) ->
            if (endReached) {
                onLoadMore()
            }
        }
    }
}

@Composable
internal fun SearchLoadMoreFooter() {
    val colors = LocalTheme.current.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = colors.textColorLink,
            strokeWidth = 2.dp
        )
    }
}
