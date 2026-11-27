package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchFilter
import io.trtc.tuikit.atomicxcore.api.search.SearchOption
import io.trtc.tuikit.atomicxcore.api.search.SearchState
import io.trtc.tuikit.atomicxcore.api.search.SearchStore
import io.trtc.tuikit.atomicxcore.api.search.SearchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SearchMessageInConversationViewModel : ViewModel() {

    private val searchStore: SearchStore = SearchStore.create()
    private val searchState: SearchState = searchStore.state
    private var lastSubmittedConversationID: String = ""
    private var lastSubmittedQuery: String = ""

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _searchError = MutableStateFlow<SearchError?>(null)
    val searchError: StateFlow<SearchError?> = _searchError.asStateFlow()

    private val _isResultCleared = MutableStateFlow(true)

    val messagesInConversation: StateFlow<List<MessageInfo>> =
        searchState.messageResults.map { messageResults ->
            messageResults.firstOrNull()?.messageList ?: emptyList()
        }.combine(_isResultCleared) { messageList, isCleared ->
            if (isCleared) emptyList() else messageList
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSearchQuery(conversationID: String, query: String) {
        if (query.isBlank()) {
            clearSearch()
            return
        }
        if (
            conversationID == lastSubmittedConversationID &&
            query == lastSubmittedQuery &&
            (_isSearching.value || messagesInConversation.value.isNotEmpty())
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
            searchCompletionHandler(_isSearching)
        )
    }

    fun clearSearchError() {
        _searchError.value = null
    }

    fun searchMore() {
        if (_isSearching.value || _isLoadingMore.value || !searchState.hasMoreMessageResults.value) {
            return
        }

        _isLoadingMore.value = true
        _searchError.value = null
        searchStore.searchMore(SearchType.MESSAGE, searchCompletionHandler(_isLoadingMore))
    }

    private fun clearSearch() {
        lastSubmittedConversationID = ""
        lastSubmittedQuery = ""
        _isResultCleared.value = true
        _isSearching.value = false
        _isLoadingMore.value = false
        _searchError.value = null
    }

    private fun searchCompletionHandler(loadingState: MutableStateFlow<Boolean>): CompletionHandler {
        return object : CompletionHandler {
            override fun onSuccess() {
                loadingState.value = false
            }

            override fun onFailure(code: Int, desc: String) {
                loadingState.value = false
                _searchError.value = SearchError(code, desc)
            }
        }
    }
}
