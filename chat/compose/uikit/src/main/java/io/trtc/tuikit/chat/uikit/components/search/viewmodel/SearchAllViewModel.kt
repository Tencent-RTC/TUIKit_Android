package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.search.SearchOption
import io.trtc.tuikit.atomicxcore.api.search.SearchState
import io.trtc.tuikit.atomicxcore.api.search.SearchStore
import io.trtc.tuikit.atomicxcore.api.search.SearchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SearchCategory(
    val type: SearchType,
    val results: List<Any>,
    val hasMore: Boolean = false,
    val totalCount: Int = 0
)

data class SearchError(
    val code: Int,
    val message: String
)

class SearchAllViewModel : ViewModel() {

    private val searchStore: SearchStore = SearchStore.create()
    private val searchState: SearchState = searchStore.state
    private var lastSubmittedQuery: String = ""

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<SearchError?>(null)
    val searchError: StateFlow<SearchError?> = _searchError.asStateFlow()

    private val _isResultCleared = MutableStateFlow(true)
    private val _searchCategories = MutableStateFlow<List<SearchCategory>>(emptyList())
    val searchCategories: StateFlow<List<SearchCategory>> = _searchCategories.asStateFlow()

    init {
        val friendSource = combine(
            searchState.friendList,
            searchState.friendTotalCount,
            searchState.hasMoreFriends
        ) { results, totalCount, hasMore ->
            SearchCategory(SearchType.FRIEND, results.take(3), hasMore || results.size > 3, totalCount)
        }
        val groupSource = combine(
            searchState.groupList,
            searchState.groupTotalCount,
            searchState.hasMoreGroups
        ) { results, totalCount, hasMore ->
            SearchCategory(SearchType.GROUP, results.take(3), hasMore || results.size > 3, totalCount)
        }
        val messageSource = combine(
            searchState.messageResults,
            searchState.messageResultTotalCount,
            searchState.hasMoreMessageResults
        ) { results, totalCount, hasMore ->
            SearchCategory(SearchType.MESSAGE, results.take(3), hasMore || results.size > 3, totalCount)
        }

        viewModelScope.launch {
            combine(friendSource, groupSource, messageSource, _isResultCleared) { friend, group, message, isCleared ->
                if (isCleared) {
                    emptyList()
                } else {
                    listOf(friend, group, message).filter { it.results.isNotEmpty() }
                }
            }.collect { _searchCategories.value = it }
        }
    }

    fun updateSearchQuery(query: String) {
        if (query.isBlank()) {
            clearSearch()
            return
        }
        if (query == lastSubmittedQuery && (_isSearching.value || _searchCategories.value.isNotEmpty())) {
            return
        }

        lastSubmittedQuery = query
        _isResultCleared.value = false
        _isSearching.value = true
        _searchError.value = null
        searchStore.search(
            listOf(query),
            SearchOption(
                searchScope = listOf(
                    SearchType.FRIEND,
                    SearchType.GROUP,
                    SearchType.MESSAGE
                )
            ),
            object : CompletionHandler {
                override fun onSuccess() {
                    _isSearching.value = false
                }

                override fun onFailure(code: Int, desc: String) {
                    _isSearching.value = false
                    _searchError.value = SearchError(code, desc)
                }
            }
        )
    }

    fun clearSearchError() {
        _searchError.value = null
    }

    private fun clearSearch() {
        lastSubmittedQuery = ""
        _isSearching.value = false
        _searchError.value = null
        _isResultCleared.value = true
        _searchCategories.value = emptyList()
    }
}
