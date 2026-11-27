package io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationControllers
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationPolicy
import io.trtc.tuikit.chat.uikit.components.chatsetting.utils.ChatSettingBackgroundStore
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.chat.uikit.components.common.appContext
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.GetConversationInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ReceiveMessageOption
import io.trtc.tuikit.atomicxcore.api.group.GetMemberInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupInviteOption
import io.trtc.tuikit.atomicxcore.api.group.GroupJoinOption
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberFilterRole
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.group.GroupType
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val CHAT_SETTING_EVENT_SOURCE = "ChatSetting"
private const val EVENT_CHAT_BACKGROUND_CHANGED = "onChatBackgroundChanged"

class C2CChatSettingViewModel(val userID: String) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _contactStore: ContactStore = ContactStore.shared
    private val _contactState = _contactStore.state

    private val _conversationListStore: ConversationListStore = ConversationListStore.create()
    val conversationID = ConversationIDUtil.fromUser(userID)
    private val conversationInfo = MutableStateFlow<ConversationInfo?>(null)

    private val chatBackgroundStore = ChatSettingBackgroundStore(appContext)
    private val _chatBackgroundImageUri = MutableStateFlow(chatBackgroundStore.getImageUri(conversationID))
    val chatBackgroundImageUri: StateFlow<String?> = _chatBackgroundImageUri.asStateFlow()

    private val userInfo = _contactState.friendList
        .map { list -> list.firstOrNull { it.userID == userID } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nickname: StateFlow<String> = userInfo
        .map { it?.nickname ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val avatar: StateFlow<String> = userInfo
        .map { it?.avatarURL ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val signature: StateFlow<String> = userInfo
        .map { it?.aboutMe ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val aboutMe: StateFlow<String>
        get() = signature

    val remark: StateFlow<String> = userInfo
        .map { it?.friendRemark ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val friendRemark: StateFlow<String>
        get() = remark

    val isNotDisturb: StateFlow<Boolean> = conversationInfo
        .map { it?.receiveOption?.let { option -> option != ReceiveMessageOption.RECEIVE } ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isPinned: StateFlow<Boolean> = conversationInfo
        .map { it?.isPinned ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isContact: StateFlow<Boolean> = _contactState.friendList
        .map { list -> list.any { it.userID == userID } }
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

    fun toggleDoNotDisturb() {
        setDoNotDisturb(!isNotDisturb.value)
    }

    fun togglePinChat() {
        setPinChat(!isPinned.value)
    }

    fun toggleBlacklist() {
        if (isInBlacklist.value) {
            _contactStore.removeFromBlacklist(userID, emptyHandler())
        } else {
            _contactStore.addToBlacklist(userID, emptyHandler())
        }
    }

    fun toggleC2CDoNotDisturb() = toggleDoNotDisturb()

    fun toggleC2CTopChat() = togglePinChat()

    fun toggleC2CBlacklist() = toggleBlacklist()

    fun setFriendRemark(remark: String) {
        _contactStore.setFriendRemark(userID, remark, emptyHandler())
    }

    fun clearChatHistory() {
        _conversationListStore.clearConversationMessages(
            conversationID,
            resetChatbotControllerOnHistoryCleared(conversationID)
        )
    }

    fun clearC2CChatHistory() = clearChatHistory()

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

    fun deleteC2CContact() = deleteFriend()

    private fun postChatBackgroundChangedEvent() {
        EventBus.post(
            mapOf(
                "source" to CHAT_SETTING_EVENT_SOURCE,
                "event" to EVENT_CHAT_BACKGROUND_CHANGED,
                "conversationID" to conversationID
            )
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

class GroupChatSettingViewModel(val groupID: String) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _contactStore: ContactStore = ContactStore.shared
    private val _contactState = _contactStore.state
    private val _groupStore: GroupStore = GroupStore.shared
    private val _groupStoreState = _groupStore.state
    private val _groupMemberStore: GroupMemberStore = GroupMemberStore.create(groupID)
    private val _memberState = _groupMemberStore.state
    private val _conversationListStore: ConversationListStore = ConversationListStore.create()

    val conversationID = ConversationIDUtil.fromGroup(groupID)
    private val conversationInfo = MutableStateFlow<ConversationInfo?>(null)
    private val _selfNameCard = MutableStateFlow<String?>(null)
    private val pendingAllMemberCallbacks = mutableListOf<() -> Unit>()
    private var isFetchingAllMembers = false

    private val chatBackgroundStore = ChatSettingBackgroundStore(appContext)
    private val _chatBackgroundImageUri = MutableStateFlow(chatBackgroundStore.getImageUri(conversationID))
    val chatBackgroundImageUri: StateFlow<String?> = _chatBackgroundImageUri.asStateFlow()

    private val currentUserID: String
        get() = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""

    private val groupInfo = _groupStoreState.joinedGroupList
        .map { list -> list.firstOrNull { it.groupID == groupID } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        _contactStore.loadFriends()
        _groupStore.loadJoinedGroups()
        _conversationListStore.getConversationInfo(
            conversationID = conversationID,
            completion = object : GetConversationInfoCompletionHandler {
                override fun onSuccess(conversationInfo: ConversationInfo) {
                    this@GroupChatSettingViewModel.conversationInfo.value = conversationInfo
                }

                override fun onFailure(code: Int, desc: String) {}
            }
        )
        viewModelScope.launch {
            loadMembersInternal(GroupMemberFilterRole.ALL)
        }
        refreshSelfNameCard()
    }

    val friendList: StateFlow<List<ContactInfo>> = _contactState.friendList.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val groupType: StateFlow<GroupType> = groupInfo
        .map { it?.groupType ?: GroupType.WORK }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GroupType.WORK)

    val groupName: StateFlow<String> = groupInfo
        .map { it?.groupName ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val avatar: StateFlow<String> = groupInfo
        .map { it?.avatarURL ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val avatarURL: StateFlow<String>
        get() = avatar

    val notice: StateFlow<String> = groupInfo
        .map { it?.notification ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val notification: StateFlow<String>
        get() = notice

    val isNotDisturb: StateFlow<Boolean> = conversationInfo
        .map { it?.receiveOption?.let { option -> option != ReceiveMessageOption.RECEIVE } ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isPinned: StateFlow<Boolean> = conversationInfo
        .map { it?.isPinned ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isAllMuted: StateFlow<Boolean> = groupInfo
        .map { it?.isAllMuted ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val allMembers: StateFlow<List<GroupMember>>
        get() = _memberState.memberList

    val memberList: StateFlow<List<GroupMember>>
        get() = _memberState.memberList

    val groupOwner: StateFlow<String?> = groupInfo
        .map { it?.groupOwner }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val silencedMembers: StateFlow<List<GroupMember>> = allMembers
        .map { members -> members.filter { it.isMuted } }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = allMembers.value.filter { it.isMuted }
        )

    val adminMembers: StateFlow<List<GroupMember>> = allMembers
        .map { members -> members.filter { it.role == GroupMemberRole.ADMIN } }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = allMembers.value.filter { it.role == GroupMemberRole.ADMIN }
        )

    val currentUserRole: StateFlow<GroupMemberRole> = groupInfo
        .map { it?.selfRole ?: GroupMemberRole.MEMBER }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GroupMemberRole.MEMBER)

    val selfRole: StateFlow<GroupMemberRole>
        get() = currentUserRole

    val selfNameCard: StateFlow<String?> = _selfNameCard.asStateFlow()

    val memberCount: StateFlow<Int> = groupInfo
        .map { it?.memberCount ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val joinGroupApprovalType: StateFlow<GroupJoinOption> = groupInfo
        .map { it?.joinOption ?: GroupJoinOption.ANY }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GroupJoinOption.ANY)

    val inviteToGroupApprovalType: StateFlow<GroupInviteOption> = groupInfo
        .map { it?.inviteOption ?: GroupInviteOption.ANY }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GroupInviteOption.ANY)

    private fun refreshSelfNameCard() {
        val userID = currentUserID
        if (userID.isEmpty()) return
        _groupMemberStore.getMemberInfo(
            listOf(userID),
            object : GetMemberInfoCompletionHandler {
                override fun onSuccess(memberInfoList: List<GroupMember>) {
                    _selfNameCard.value = memberInfoList.firstOrNull()?.nameCard
                }

                override fun onFailure(code: Int, desc: String) {}
            }
        )
    }

    private suspend fun loadAllMembers(role: GroupMemberFilterRole) {
        if (!loadMembersInternal(role)) return
        while (_memberState.hasMoreMembers.value) {
            if (!loadMoreMembersInternal()) return
        }
    }

    private suspend fun loadMembersInternal(role: GroupMemberFilterRole): Boolean {
        return suspendCancellableCoroutine { continuation ->
            _groupMemberStore.loadMembers(
                roleList = listOf(role),
                completion = object : CompletionHandler {
                    override fun onSuccess() {
                        continuation.resume(true)
                    }

                    override fun onFailure(code: Int, desc: String) {
                        continuation.resume(false)
                    }
                }
            )
        }
    }

    private suspend fun loadMoreMembersInternal(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            _groupMemberStore.loadMoreMembers(
                completion = object : CompletionHandler {
                    override fun onSuccess() {
                        continuation.resume(true)
                    }

                    override fun onFailure(code: Int, desc: String) {
                        continuation.resume(false)
                    }
                }
            )
        }
    }

    fun loadAllGroupMembers(onComplete: (() -> Unit)? = null) {
        if (onComplete != null) {
            pendingAllMemberCallbacks.add(onComplete)
        }
        if (!_memberState.hasMoreMembers.value) {
            notifyAllMemberCallbacks()
            return
        }
        if (isFetchingAllMembers) return
        isFetchingAllMembers = true
        viewModelScope.launch {
            loadAllMembers(GroupMemberFilterRole.ALL)
            isFetchingAllMembers = false
            notifyAllMemberCallbacks()
        }
    }

    private fun notifyAllMemberCallbacks() {
        val callbacks = pendingAllMemberCallbacks.toList()
        pendingAllMemberCallbacks.clear()
        callbacks.forEach { it.invoke() }
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

    fun toggleGroupDoNotDisturb() = setDoNotDisturb(!isNotDisturb.value)

    fun toggleGroupTopChat() = setPinChat(!isPinned.value)

    fun setGroupAvatar(avatarUrl: String) {
        _groupStore.updateProfile(GroupInfo(groupID = groupID, avatarURL = avatarUrl), emptyHandler())
    }

    fun setGroupName(name: String) {
        _groupStore.updateProfile(GroupInfo(groupID = groupID, groupName = name), emptyHandler())
    }

    fun setGroupNotice(notice: String) {
        _groupStore.updateProfile(GroupInfo(groupID = groupID, notification = notice), emptyHandler())
    }

    fun setGroupNickname(nickname: String) {
        _groupMemberStore.setSelfNameCard(
            nickname,
            object : CompletionHandler {
                override fun onSuccess() {
                    refreshSelfNameCard()
                }

                override fun onFailure(code: Int, desc: String) {}
            }
        )
    }

    fun setJoinGroupApproveType(type: GroupJoinOption) {
        _groupStore.setJoinOption(groupID, type, emptyHandler())
    }

    fun setInviteGroupApproveType(type: GroupInviteOption) {
        _groupStore.setInviteOption(groupID, type, emptyHandler())
    }

    fun addMember(userIDList: List<String>) {
        if (userIDList.isEmpty()) return
        _groupMemberStore.addMember(userIDList, emptyHandler())
    }

    fun addGroupMember(userIDList: List<String>) = addMember(userIDList)

    fun deleteMember(members: List<GroupMember>) {
        val userIDList = members.map { it.userID }
        _groupMemberStore.deleteMember(userIDList, emptyHandler())
    }

    fun deleteGroupMember(groupMembers: List<GroupMember>) = deleteMember(groupMembers)

    fun setMemberRole(
        userID: String,
        role: GroupMemberRole,
        onFailure: ((Int, String) -> Unit)? = null
    ) {
        _groupMemberStore.setMemberRole(
            userID,
            role,
            object : CompletionHandler {
                override fun onSuccess() {}

                override fun onFailure(code: Int, desc: String) {
                    onFailure?.invoke(code, desc)
                }
            }
        )
    }

    fun setGroupMemberRole(userID: String, role: GroupMemberRole) = setMemberRole(userID, role)

    fun muteGroupMember(
        userID: String,
        muteTimeSeconds: Long,
        onFailure: ((Int, String) -> Unit)? = null
    ) {
        _groupMemberStore.muteMember(
            userID,
            muteTimeSeconds,
            object : CompletionHandler {
                override fun onSuccess() {}

                override fun onFailure(code: Int, desc: String) {
                    onFailure?.invoke(code, desc)
                }
            }
        )
    }

    fun changeOwner(newOwnerID: String) {
        _groupStore.changeOwner(groupID, newOwnerID)
    }

    fun transferGroupOwner(newOwnerID: String) = changeOwner(newOwnerID)

    fun toggleGroupAllMute() {
        _groupStore.muteAllMembers(groupID, !isAllMuted.value, emptyHandler())
    }

    fun quitGroup(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int, String) -> Unit)? = null
    ) {
        _groupStore.quitGroup(
            groupID,
            completionHandler(
                onSuccess = {
                    _conversationListStore.deleteConversation(conversationID)
                    onSuccess?.invoke()
                },
                onFailure = onFailure
            )
        )
    }

    fun dismissGroup(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int, String) -> Unit)? = null
    ) {
        _groupStore.dismissGroup(
            groupID,
            completionHandler(
                onSuccess = {
                    _conversationListStore.deleteConversation(conversationID)
                    onSuccess?.invoke()
                },
                onFailure = onFailure
            )
        )
    }

    fun clearChatHistory() {
        _conversationListStore.clearConversationMessages(
            conversationID,
            resetChatbotControllerOnHistoryCleared(conversationID)
        )
    }

    fun clearGroupChatHistoryConvenience() = clearChatHistory()

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
                "source" to CHAT_SETTING_EVENT_SOURCE,
                "event" to EVENT_CHAT_BACKGROUND_CHANGED,
                "conversationID" to conversationID
            )
        )
    }

    private fun emptyHandler(): CompletionHandler = completionHandler()

    private fun completionHandler(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int, String) -> Unit)? = null
    ): CompletionHandler = object : CompletionHandler {
        override fun onSuccess() {
            onSuccess?.invoke()
        }

        override fun onFailure(code: Int, desc: String) {
            onFailure?.invoke(code, desc)
        }
    }

    private fun updateConversationInfo(update: (ConversationInfo) -> ConversationInfo) {
        val current = conversationInfo.value ?: ConversationInfo(conversationID = conversationID)
        conversationInfo.value = update(current)
    }
}

class C2CChatSettingViewModelFactory(val userID: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(C2CChatSettingViewModel::class.java)) {
            return C2CChatSettingViewModel(userID) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class GroupChatSettingViewModelFactory(val groupID: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupChatSettingViewModel::class.java)) {
            return GroupChatSettingViewModel(groupID) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

private fun resetChatbotControllerOnHistoryCleared(conversationID: String): CompletionHandler {
    return object : CompletionHandler {
        override fun onSuccess() {
            if (ChatbotConversationPolicy.isChatbotConversation(conversationID)) {
                ChatbotConversationControllers.clear(conversationID)
            }
        }

        override fun onFailure(code: Int, desc: String) {}
    }
}

private const val GROUP_AVATAR_URL =
    "https://im.sdk.qcloud.com/download/tuikit-resource/group-avatar/group_avatar_%s.png"
const val GROUP_AVATAR_COUNT = 24
const val CHAT_BACKGROUND_IMAGE =
    "https://im.sdk.qcloud.com/download/tuikit-resource/conversation-backgroundImage/backgroundImage_%s_full.png"
const val CHAT_BACKGROUND_COUNT = 7

const val USER_AVATAR_URL =
    "https://im.sdk.qcloud.com/download/tuikit-resource/avatar/avatar_%s.png"
const val USER_AVATAR_URL_COUNT = 26

fun getUserAvatarUrls(): List<String> {
    return mutableListOf<String>().apply {
        for (i in 1..USER_AVATAR_URL_COUNT) {
            add(String.format(USER_AVATAR_URL, i))
        }
    }
}

fun getGroupAvatarUrls(): List<String> {
    return mutableListOf<String>().apply {
        for (i in 1..GROUP_AVATAR_COUNT) {
            add(String.format(GROUP_AVATAR_URL, i))
        }
    }
}
