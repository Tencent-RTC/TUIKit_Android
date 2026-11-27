package io.trtc.tuikit.chat.uikit.components.messagelist.ui.forward
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationLoadOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class ForwardConversationStore(
    scope: CoroutineScope,
    private val conversationListStore: ConversationListStore = ConversationListStore.create()
) {

    val conversationList: StateFlow<List<ConversationInfo>> = conversationListStore.state.conversationList
        .map { list ->
            list.filter { item ->
                item.conversationID.isNotEmpty()
            }.distinctBy { item -> item.conversationID }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val hasMoreConversations = conversationListStore.state.hasMoreConversations

    private val selectedConversationMap = LinkedHashMap<String, ConversationInfo>()
    private val _selectedConversations = MutableStateFlow<Set<ConversationInfo>>(emptySet())
    val selectedConversations: StateFlow<Set<ConversationInfo>> = _selectedConversations.asStateFlow()

    private val isLoadingMore = MutableStateFlow(false)

    init {
        conversationListStore.loadConversations(
            ConversationLoadOption(),
            object : CompletionHandler {
                override fun onSuccess() {}
                override fun onFailure(code: Int, desc: String) {}
            }
        )
    }

    fun loadMoreConversation() {
        if (isLoadingMore.value || !hasMoreConversations.value) {
            return
        }
        isLoadingMore.value = true
        conversationListStore.loadMoreConversations(object : CompletionHandler {
            override fun onSuccess() {
                isLoadingMore.value = false
            }

            override fun onFailure(code: Int, desc: String) {
                isLoadingMore.value = false
            }
        })
    }

    fun addSelection(conversation: ConversationInfo) {
        selectedConversationMap[conversation.conversationID] = conversation
        emitSelectedConversations()
    }

    fun removeSelection(conversation: ConversationInfo) {
        selectedConversationMap.remove(conversation.conversationID)
        emitSelectedConversations()
    }

    fun isSelected(conversation: ConversationInfo): Boolean {
        return selectedConversationMap.containsKey(conversation.conversationID)
    }

    private fun emitSelectedConversations() {
        _selectedConversations.value = selectedConversationMap.values.toSet()
    }
}
