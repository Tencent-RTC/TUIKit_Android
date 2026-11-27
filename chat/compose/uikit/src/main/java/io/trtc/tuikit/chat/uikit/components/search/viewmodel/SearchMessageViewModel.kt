package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem
import io.trtc.tuikit.atomicxcore.api.search.SearchOption
import io.trtc.tuikit.atomicxcore.api.search.SearchState
import io.trtc.tuikit.atomicxcore.api.search.SearchStore
import io.trtc.tuikit.atomicxcore.api.search.SearchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SearchMessageViewModel : ViewModel() {

    private val searchStore: SearchStore = SearchStore.create()
    private val searchState: SearchState = searchStore.state
    private var lastSubmittedQuery: String = ""

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _searchError = MutableStateFlow<SearchError?>(null)
    val searchError: StateFlow<SearchError?> = _searchError.asStateFlow()

    private val _isResultCleared = MutableStateFlow(true)

    val messageSearchResults: StateFlow<List<MessageSearchResultItem>> =
        searchState.messageResults.combine(_isResultCleared) { messageResults, isCleared ->
            if (isCleared) emptyList() else messageResults
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        if (query.isBlank()) {
            clearSearch()
            return
        }
        if (query == lastSubmittedQuery && (_isSearching.value || messageSearchResults.value.isNotEmpty())) {
            return
        }

        lastSubmittedQuery = query
        _isResultCleared.value = false
        _isSearching.value = true
        _searchError.value = null
        searchStore.search(
            listOf(query),
            SearchOption(searchScope = listOf(SearchType.MESSAGE)),
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
