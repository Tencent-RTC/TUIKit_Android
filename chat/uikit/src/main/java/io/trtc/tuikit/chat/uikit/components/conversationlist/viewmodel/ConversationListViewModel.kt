package io.trtc.tuikit.chat.uikit.components.conversationlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationLoadOption
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationMarkType
import io.trtc.tuikit.atomicxcore.api.conversation.ReceiveMessageOption
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomEditor
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ChatConversationActionConfig
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ConversationActionConfigProtocol
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ConversationActionCustomizer
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.ConversationActionIDs
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.ConversationCustomAction
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.ConversationCustomActionContext
import io.trtc.tuikit.chat.uikit.components.conversationlist.utils.isUnread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal data class ConversationActionCallbacks(
    val onClearUnreadCount: (ConversationInfo) -> Unit = {},
    val onMarkAsUnread: (ConversationInfo) -> Unit = {},
    val onPinConversation: (ConversationInfo) -> Unit = {},
    val onUnpinConversation: (ConversationInfo) -> Unit = {},
    val onMuteConversation: (conversationID: String, mute: Boolean) -> Unit = { _, _ -> },
    val onClearMessage: (ConversationInfo) -> Unit = {},
    val onDeleteConversation: (ConversationInfo) -> Unit = {},
)

internal data class ConversationActionTitleResIDs(
    val markRead: Int,
    val markUnread: Int,
    val mute: Int,
    val unmute: Int,
    val pin: Int,
    val unpin: Int,
    val clear: Int,
    val delete: Int,
) {
    companion object {
        fun defaults(): ConversationActionTitleResIDs {
            return ConversationActionTitleResIDs(
                markRead = R.string.conversation_list_mark_read,
                markUnread = R.string.conversation_list_mark_unread,
                mute = R.string.conversation_list_mute,
                unmute = R.string.conversation_list_unmute,
                pin = R.string.conversation_list_pin,
                unpin = R.string.conversation_list_unpin,
                clear = R.string.conversation_list_clear,
                delete = R.string.conversation_list_delete,
            )
        }
    }
}

internal fun buildDefaultConversationActions(
    isSupportMarkUnread: Boolean,
    isSupportMute: Boolean,
    isSupportPin: Boolean,
    isSupportClearHistory: Boolean,
    isSupportDelete: Boolean,
    isUnread: Boolean,
    isPinned: Boolean,
    isReceiving: Boolean,
    titleResIDs: ConversationActionTitleResIDs,
    callbacks: ConversationActionCallbacks,
): List<ConversationCustomAction> {
    val actions = mutableListOf<ConversationCustomAction>()
    if (isSupportMarkUnread) {
        actions += ConversationCustomAction(
            ID = ConversationActionIDs.MARK_UNREAD,
            titleResID = if (isUnread) titleResIDs.markRead else titleResIDs.markUnread,
            action = if (isUnread) callbacks.onClearUnreadCount else callbacks.onMarkAsUnread,
        )
    }
    if (isSupportMute) {
        actions += ConversationCustomAction(
            ID = ConversationActionIDs.MUTE,
            titleResID = if (isReceiving) titleResIDs.mute else titleResIDs.unmute,
            action = { target ->
                callbacks.onMuteConversation(target.conversationID, isReceiving)
            },
        )
    }
    if (isSupportPin) {
        actions += ConversationCustomAction(
            ID = ConversationActionIDs.PIN,
            titleResID = if (isPinned) titleResIDs.unpin else titleResIDs.pin,
            action = if (isPinned) callbacks.onUnpinConversation else callbacks.onPinConversation,
        )
    }
    if (isSupportClearHistory) {
        actions += ConversationCustomAction(
            ID = ConversationActionIDs.CLEAR_HISTORY,
            titleResID = titleResIDs.clear,
            action = callbacks.onClearMessage,
        )
    }
    if (isSupportDelete) {
        actions += ConversationCustomAction(
            ID = ConversationActionIDs.DELETE,
            titleResID = titleResIDs.delete,
            dangerous = true,
            action = callbacks.onDeleteConversation,
        )
    }
    return actions
}

internal fun applyConversationActionCustomizer(
    actionContext: ConversationCustomActionContext,
    defaults: List<ConversationCustomAction>,
    customizer: ConversationActionCustomizer?,
): List<ConversationCustomAction> {
    if (customizer == null) {
        return defaults
    }
    val editor = CustomEditor(actionContext, defaults)
    customizer.customize(editor)
    return editor.build()
}

class ConversationListViewModel(
    val conversationListStore: ConversationListStore,
    private var conversationActionConfig: ConversationActionConfigProtocol
) : ViewModel() {

    val conversationState = conversationListStore.state
    val hasMoreConversations = conversationState.hasMoreConversations

    val conversationList: StateFlow<List<ConversationInfo>> = conversationState.conversationList
        .map { list ->
            list.filter { item ->
                item.conversationID.isNotEmpty()
            }.distinctBy { item -> item.conversationID }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val selectedConversationMap = LinkedHashMap<String, ConversationInfo>()
    private val _selectedConversations = MutableStateFlow<Set<ConversationInfo>>(emptySet())
    val selectedConversations: StateFlow<Set<ConversationInfo>> = _selectedConversations.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _initialLoadFinished = MutableStateFlow(false)
    val initialLoadFinished: StateFlow<Boolean> = _initialLoadFinished.asStateFlow()

    init {
        conversationListStore.loadConversations(
            ConversationLoadOption(),
            object : CompletionHandler {
                override fun onSuccess() {
                    _initialLoadFinished.value = true
                }
                override fun onFailure(code: Int, desc: String) {
                    _initialLoadFinished.value = true
                }
            }
        )
    }

    fun updateConfig(config: ConversationActionConfigProtocol) {
        conversationActionConfig = config
    }

    fun addSelection(conversation: ConversationInfo) {
        selectedConversationMap[conversation.conversationID] = conversation
        emitSelectedConversations()
    }

    fun removeSelection(conversation: ConversationInfo) {
        selectedConversationMap.remove(conversation.conversationID)
        emitSelectedConversations()
    }

    fun clearSelection() {
        selectedConversationMap.clear()
        emitSelectedConversations()
    }

    fun isSelected(conversation: ConversationInfo): Boolean {
        return selectedConversationMap.containsKey(conversation.conversationID)
    }

    private fun emitSelectedConversations() {
        _selectedConversations.value = selectedConversationMap.values.toSet()
    }

    fun getDefaultActions(conversationInfo: ConversationInfo): List<ConversationCustomAction> {
        return buildDefaultConversationActions(
            isSupportMarkUnread = conversationActionConfig.isSupportMarkUnread,
            isSupportMute = conversationActionConfig.isSupportMute,
            isSupportPin = conversationActionConfig.isSupportPin,
            isSupportClearHistory = conversationActionConfig.isSupportClearHistory,
            isSupportDelete = conversationActionConfig.isSupportDelete,
            isUnread = conversationInfo.isUnread,
            isPinned = conversationInfo.isPinned,
            isReceiving = conversationInfo.receiveOption == ReceiveMessageOption.RECEIVE,
            titleResIDs = ConversationActionTitleResIDs.defaults(),
            callbacks = ConversationActionCallbacks(
                onClearUnreadCount = { clearUnreadCount(it) },
                onMarkAsUnread = { markAsUnRead(it) },
                onPinConversation = { pinConversation(it) },
                onUnpinConversation = { unpinConversation(it) },
                onMuteConversation = { conversationID, mute ->
                    conversationListStore.setReceiveMessageOpt(
                        conversationID,
                        if (mute) ReceiveMessageOption.NOT_NOTIFY else ReceiveMessageOption.RECEIVE
                    )
                },
                onClearMessage = { clearMessage(it) },
                onDeleteConversation = { deleteConversation(it) },
            ),
        )
    }

    fun deleteConversation(conversationInfo: ConversationInfo) {
        conversationListStore.deleteConversation(conversationInfo.conversationID)
    }

    fun pinConversation(conversationInfo: ConversationInfo) {
        conversationListStore.pinConversation(conversationInfo.conversationID, true)
    }

    fun unpinConversation(conversationInfo: ConversationInfo) {
        conversationListStore.pinConversation(conversationInfo.conversationID, false)
    }

    fun clearMessage(conversationInfo: ConversationInfo) {
        conversationListStore.clearConversationMessages(conversationInfo.conversationID)
    }

    fun clearUnreadCount(conversationInfo: ConversationInfo) {
        conversationListStore.clearConversationUnreadCount(conversationInfo.conversationID)
        conversationListStore.markConversation(
            listOf(conversationInfo.conversationID),
            ConversationMarkType.unread,
            false
        )
    }

    fun markAsUnRead(conversationInfo: ConversationInfo) {
        conversationListStore.markConversation(
            listOf(conversationInfo.conversationID),
            ConversationMarkType.unread,
            true
        )
    }

    fun loadMoreConversation() {
        if (_isLoadingMore.value || !hasMoreConversations.value) {
            return
        }
        _isLoadingMore.value = true
        conversationListStore.loadMoreConversations(object : CompletionHandler {
            override fun onSuccess() {
                _isLoadingMore.value = false
            }

            override fun onFailure(code: Int, desc: String) {
                _isLoadingMore.value = false
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
    }
}

class ConversationListViewModelFactory(
    private val conversationListStore: ConversationListStore = ConversationListStore.create(),
    private val conversationActionConfig: ConversationActionConfigProtocol = ChatConversationActionConfig()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConversationListViewModel::class.java)) {
            return ConversationListViewModel(conversationListStore, conversationActionConfig) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
