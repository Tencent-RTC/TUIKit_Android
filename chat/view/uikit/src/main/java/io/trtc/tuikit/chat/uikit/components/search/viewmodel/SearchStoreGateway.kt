package io.trtc.tuikit.chat.uikit.components.search.viewmodel
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.search.SearchOption
import io.trtc.tuikit.atomicxcore.api.search.SearchState
import io.trtc.tuikit.atomicxcore.api.search.SearchStore
import io.trtc.tuikit.atomicxcore.api.search.SearchType
import kotlinx.coroutines.flow.MutableStateFlow

data class SearchError(
    val code: Int,
    val message: String
)

internal interface SearchStoreGateway {
    val state: SearchState

    fun search(
        keywordList: List<String>,
        option: SearchOption,
        completion: CompletionHandler? = null
    )

    fun searchMore(
        searchType: SearchType,
        completion: CompletionHandler? = null
    )
}

internal class DefaultSearchStoreGateway(
    private val searchStore: SearchStore = SearchStore.create()
) : SearchStoreGateway {
    override val state: SearchState = searchStore.state

    override fun search(
        keywordList: List<String>,
        option: SearchOption,
        completion: CompletionHandler?
    ) {
        searchStore.search(keywordList, option, completion)
    }

    override fun searchMore(searchType: SearchType, completion: CompletionHandler?) {
        searchStore.searchMore(searchType, completion)
    }
}

internal fun searchCompletion(
    loadingState: MutableStateFlow<Boolean>,
    errorState: MutableStateFlow<SearchError?>
): CompletionHandler {
    return object : CompletionHandler {
        override fun onSuccess() {
            loadingState.value = false
        }

        override fun onFailure(code: Int, desc: String) {
            loadingState.value = false
            errorState.value = SearchError(code, desc)
        }
    }
}
