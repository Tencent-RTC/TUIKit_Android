package io.trtc.tuikit.chat.uikit.components.search.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.search.GroupSearchInfo
import io.trtc.tuikit.atomicxcore.api.search.SearchOption
import io.trtc.tuikit.atomicxcore.api.search.SearchState
import io.trtc.tuikit.atomicxcore.api.search.SearchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchGroupViewModel internal constructor(
    private val searchStore: SearchStoreGateway
) : ViewModel() {

    constructor() : this(DefaultSearchStoreGateway())

    private val state: SearchState = searchStore.state
    private var lastSubmittedQuery: String = ""

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _searchError = MutableStateFlow<SearchError?>(null)
    val searchError: StateFlow<SearchError?> = _searchError

    private val _isResultCleared = MutableStateFlow(true)
    private val _groupSearchResults = MutableStateFlow<List<GroupSearchInfo>>(emptyList())
    val groupSearchResults: StateFlow<List<GroupSearchInfo>> = _groupSearchResults

    init {
        viewModelScope.launch {
            state.groupList.collectLatest {
                if (!_isResultCleared.value) {
                    _groupSearchResults.value = it
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        if (query.isBlank()) {
            clearSearch()
            return
        }
        if (query == lastSubmittedQuery && (_isSearching.value || _groupSearchResults.value.isNotEmpty())) return

        lastSubmittedQuery = query
        _isResultCleared.value = false
        _isSearching.value = true
        _searchError.value = null
        searchStore.search(
            listOf(query),
            SearchOption(searchScope = listOf(SearchType.GROUP)),
            searchCompletionHandler(_isSearching)
        )
    }

    fun clearSearchError() {
        _searchError.value = null
    }

    fun searchMore() {
        if (_isSearching.value || _isLoadingMore.value || !state.hasMoreGroups.value) return

        _isLoadingMore.value = true
        _searchError.value = null
        searchStore.searchMore(SearchType.GROUP, searchCompletionHandler(_isLoadingMore))
    }

    private fun clearSearch() {
        lastSubmittedQuery = ""
        _isResultCleared.value = true
        _isSearching.value = false
        _isLoadingMore.value = false
        _searchError.value = null
        _groupSearchResults.value = emptyList()
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
