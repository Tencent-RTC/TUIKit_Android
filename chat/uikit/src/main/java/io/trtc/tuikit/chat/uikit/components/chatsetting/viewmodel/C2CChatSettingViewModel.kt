package io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.chat.uikit.components.chatsetting.utils.ChatSettingBackgroundStore
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.GetConversationInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ReceiveMessageOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class C2CChatSettingViewModel(
    val userID: String,
    context: Context
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _contactStore: ContactStore = ContactStore.shared
    private val _contactState = _contactStore.state

    private val userInfo = _contactState.friendList
        .map { list -> list.firstOrNull { it.userID == userID } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _conversationListStore: ConversationListStore = ConversationListStore.create()

    val conversationID = ConversationIDUtil.fromUser(userID)
    private val chatBackgroundStore = ChatSettingBackgroundStore(context.applicationContext)
    private val _chatBackgroundImageUri = MutableStateFlow(chatBackgroundStore.getImageUri(conversationID))
    val chatBackgroundImageUri: StateFlow<String?> = _chatBackgroundImageUri.asStateFlow()

    private val conversationInfo = MutableStateFlow<ConversationInfo?>(null)

    val nickname: StateFlow<String> = userInfo
        .map { it?.nickname ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val avatar: StateFlow<String> = userInfo
        .map { it?.avatarURL ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val aboutMe: StateFlow<String> = userInfo
        .map { it?.aboutMe ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val friendRemark: StateFlow<String> = userInfo
        .map { it?.friendRemark ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val isNotDisturb: StateFlow<Boolean> = conversationInfo
        .map { it?.receiveOption?.let { option -> option != ReceiveMessageOption.RECEIVE } ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isPinned: StateFlow<Boolean> = conversationInfo
        .map { it?.isPinned ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isInBlacklist: StateFlow<Boolean> = _contactState.blackList
        .map { list -> list.any { it.userID == userID } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        _contactStore.loadBlackList()
        _contactStore.loadFriends()
        _conversationListStore.getConversationInfo(
            conversationID = conversationID,
            completion = object : GetConversationInfoCompletionHandler {
                override fun onSuccess(conversationInfo: ConversationInfo) {
                    this@C2CChatSettingViewModel.conversationInfo.value = conversationInfo
                }
                override fun onFailure(code: Int, desc: String) {}
            }
        )
    }

    fun toggleDoNotDisturb() {
        setDoNotDisturb(!isNotDisturb.value)
    }

    fun setDoNotDisturb(enabled: Boolean) {
        val option = if (enabled) ReceiveMessageOption.NOT_NOTIFY else ReceiveMessageOption.RECEIVE
        _conversationListStore.setReceiveMessageOpt(
            conversationID,
            option,
            object : CompletionHandler {
                override fun onSuccess() {
                    updateConversationInfo { it.copy(receiveOption = option) }
                }

                override fun onFailure(code: Int, desc: String) {}
            }
        )
    }

    fun togglePinChat() {
        setPinChat(!isPinned.value)
    }

    fun setPinChat(pinned: Boolean) {
        _conversationListStore.pinConversation(
            conversationID,
            pinned,
            object : CompletionHandler {
                override fun onSuccess() {
                    updateConversationInfo { it.copy(isPinned = pinned) }
                }

                override fun onFailure(code: Int, desc: String) {}
            }
        )
    }

    fun toggleBlacklist() {
        if (isInBlacklist.value) {
            _contactStore.removeFromBlacklist(userID, emptyHandler())
        } else {
            _contactStore.addToBlacklist(userID, emptyHandler())
        }
    }

    fun setFriendRemark(remark: String) {
        _contactStore.setFriendRemark(userID, remark, emptyHandler())
    }

    fun clearChatHistory() {
        _conversationListStore.clearConversationMessages(conversationID, emptyHandler())
    }

    fun setChatBackground(imageUri: String?) {
        chatBackgroundStore.setImageUri(conversationID, imageUri)
        _chatBackgroundImageUri.value = chatBackgroundStore.getImageUri(conversationID)
        postChatBackgroundChangedEvent()
    }

    fun clearChatBackground() {
        chatBackgroundStore.clearImageUri(conversationID)
        _chatBackgroundImageUri.value = null
        postChatBackgroundChangedEvent()
    }

    private fun postChatBackgroundChangedEvent() {
        EventBus.post(
            mapOf(
                "source" to EVENT_SOURCE,
                "event" to EVENT_CHAT_BACKGROUND_CHANGED,
                "conversationID" to conversationID
            )
        )
    }

    private companion object {
        const val EVENT_SOURCE = "ChatSetting"
        const val EVENT_CHAT_BACKGROUND_CHANGED = "onChatBackgroundChanged"
    }

    fun deleteFriend(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int, String) -> Unit)? = null
    ) {
        _contactStore.deleteFriend(
            userID,
            object : CompletionHandler {
                override fun onSuccess() {
                    _conversationListStore.deleteConversation(conversationID)
                    onSuccess?.invoke()
                }

                override fun onFailure(code: Int, desc: String) {
                    onFailure?.invoke(code, desc)
                }
            }
        )
    }

    private fun emptyHandler(): CompletionHandler = object : CompletionHandler {
        override fun onSuccess() {}
        override fun onFailure(code: Int, desc: String) {}
    }

    private fun updateConversationInfo(update: (ConversationInfo) -> ConversationInfo) {
        val current = conversationInfo.value ?: ConversationInfo(conversationID = conversationID)
        conversationInfo.value = update(current)
    }
}

class C2CChatSettingViewModelFactory(
    private val userID: String,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(C2CChatSettingViewModel::class.java)) {
            return C2CChatSettingViewModel(userID, context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
