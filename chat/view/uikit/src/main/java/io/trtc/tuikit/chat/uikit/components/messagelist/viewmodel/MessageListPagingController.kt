package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import io.trtc.tuikit.chat.uikit.components.messagelist.model.LoadingState
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadDirection
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class MessageListPagingController(
    private val loadMore: (MessageLoadDirection, CompletionHandler) -> Unit,
    private val hasOlderMessages: StateFlow<Boolean>,
    private val hasNewerMessages: StateFlow<Boolean>
) {
    constructor(
        messageListStore: MessageListStore,
        hasOlderMessages: StateFlow<Boolean>,
        hasNewerMessages: StateFlow<Boolean>
    ) : this(
        loadMore = { direction, completion ->
            when (direction) {
                MessageLoadDirection.OLDER -> messageListStore.loadOlderMessages(completion)
                MessageLoadDirection.NEWER -> messageListStore.loadNewerMessages(completion)
                MessageLoadDirection.BOTH -> messageListStore.loadMessages(
                    MessageLoadOption(direction = MessageLoadDirection.BOTH),
                    completion
                )
                else -> messageListStore.loadMessages(
                    MessageLoadOption(direction = MessageLoadDirection.BOTH),
                    completion
                )
            }
        },
        hasOlderMessages = hasOlderMessages,
        hasNewerMessages = hasNewerMessages
    )

    private val _loadingState = MutableStateFlow(LoadingState())
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    fun loadMoreMessages(
        direction: MessageLoadDirection,
        completion: CompletionHandler? = null
    ) {
        val isOlder = direction == MessageLoadDirection.OLDER
        val hasMore = if (isOlder) {
            hasOlderMessages.value
        } else {
            hasNewerMessages.value
        }
        val isLoading = if (isOlder) {
            _loadingState.value.isLoadingOlder
        } else {
            _loadingState.value.isLoadingNewer
        }
        if (isLoading || !hasMore) {
            completion?.onFailure(-1, "No more messages or loading in progress")
            return
        }

        updateLoadingState(isOlder, isLoading = true)
        loadMore(direction, object : CompletionHandler {
            override fun onSuccess() {
                updateLoadingState(isOlder, isLoading = false)
                completion?.onSuccess()
            }

            override fun onFailure(code: Int, desc: String) {
                updateLoadingState(isOlder, isLoading = false)
                completion?.onFailure(code, desc)
            }
        })
    }

    private fun updateLoadingState(isOlder: Boolean, isLoading: Boolean) {
        _loadingState.value = if (isOlder) {
            _loadingState.value.copy(isLoadingOlder = isLoading)
        } else {
            _loadingState.value.copy(isLoadingNewer = isLoading)
        }
    }
}
