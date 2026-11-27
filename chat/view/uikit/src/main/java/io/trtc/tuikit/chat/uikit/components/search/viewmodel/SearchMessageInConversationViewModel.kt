package io.trtc.tuikit.chat.uikit.components.search.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchFilter
import io.trtc.tuikit.atomicxcore.api.search.SearchOption
import io.trtc.tuikit.atomicxcore.api.search.SearchState
import io.trtc.tuikit.atomicxcore.api.search.SearchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchMessageInConversationViewModel internal constructor(
    private val searchStore: SearchStoreGateway
) : ViewModel() {

    constructor() : this(DefaultSearchStoreGateway())

    private val state: SearchState = searchStore.state
    private var lastSubmittedConversationID: String = ""
    private var lastSubmittedQuery: String = ""

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _searchError = MutableStateFlow<SearchError?>(null)
    val searchError: StateFlow<SearchError?> = _searchError

    private val _isResultCleared = MutableStateFlow(true)
    private val _messagesInConversation = MutableStateFlow<List<MessageInfo>>(emptyList())
    val messagesInConversation: StateFlow<List<MessageInfo>> = _messagesInConversation

    init {
        viewModelScope.launch {
            state.messageResults.collectLatest { messageResults ->
                if (!_isResultCleared.value) {
                    _messagesInConversation.value = messageResults.firstOrNull()?.messageList ?: emptyList()
                }
            }
        }
    }

    fun updateSearchQuery(conversationID: String, query: String) {
        if (query.isBlank()) {
            clearSearch()
            return
        }
        if (
            conversationID == lastSubmittedConversationID &&
            query == lastSubmittedQuery &&
            (_isSearching.value || _messagesInConversation.value.isNotEmpty())
        ) {
            return
        }

        lastSubmittedConversationID = conversationID
        lastSubmittedQuery = query
        _isResultCleared.value = false
        _isSearching.value = true
        _searchError.value = null
        searchMessagesInConversation(conversationID, query)
    }

    private fun searchMessagesInConversation(conversationID: String, keyword: String) {
        searchStore.search(
            listOf(keyword),
            SearchOption(
                searchScope = listOf(SearchType.MESSAGE),
                messageFilter = MessageSearchFilter(conversationID = conversationID)
            ),
            searchCompletion(_isSearching, _searchError)
        )
    }

    fun clearSearchError() {
        _searchError.value = null
    }

    fun searchMore() {
        if (_isSearching.value || _isLoadingMore.value || !state.hasMoreMessageResults.value) return

        _isLoadingMore.value = true
        _searchError.value = null
        searchStore.searchMore(SearchType.MESSAGE, searchCompletion(_isLoadingMore, _searchError))
    }

    private fun clearSearch() {
        lastSubmittedConversationID = ""
        lastSubmittedQuery = ""
        _isResultCleared.value = true
        _isSearching.value = false
        _isLoadingMore.value = false
        _searchError.value = null
        _messagesInConversation.value = emptyList()
    }
}
