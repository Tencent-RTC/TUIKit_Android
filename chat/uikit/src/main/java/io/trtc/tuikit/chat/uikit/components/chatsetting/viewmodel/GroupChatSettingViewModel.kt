package io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.chat.uikit.components.chatsetting.utils.ChatSettingBackgroundStore
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.GetConversationInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ReceiveMessageOption
import io.trtc.tuikit.atomicxcore.api.group.GroupInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupInviteOption
import io.trtc.tuikit.atomicxcore.api.group.GroupJoinOption
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberFilterRole
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.group.GroupType
import io.trtc.tuikit.atomicxcore.api.group.GetMemberInfoCompletionHandler
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

class GroupChatSettingViewModel(
    val groupID: String,
    context: Context
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _contactStore: ContactStore = ContactStore.shared
    private val _contactState = _contactStore.state
    private val _groupStore: GroupStore = GroupStore.shared
    private val _groupMemberStore: GroupMemberStore = GroupMemberStore.create(groupID)
    private val _state = _groupMemberStore.state
    private val _groupStoreState = _groupStore.state
    private val _conversationListStore: ConversationListStore = ConversationListStore.create()

    val conversationID = ConversationIDUtil.fromGroup(groupID)
    private val chatBackgroundStore = ChatSettingBackgroundStore(context.applicationContext)
    private val _chatBackgroundImageUri = MutableStateFlow(chatBackgroundStore.getImageUri(conversationID))
    val chatBackgroundImageUri: StateFlow<String?> = _chatBackgroundImageUri.asStateFlow()

    private val currentUserID: String
        get() = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""

    private val _selfNameCard = MutableStateFlow<String?>(null)
    private val pendingAllMemberCallbacks = mutableListOf<() -> Unit>()
    private var isFetchingAllMembers = false

    private val groupInfo = _groupStoreState.joinedGroupList
        .map { list -> list.firstOrNull { it.groupID == groupID } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val conversationInfo = MutableStateFlow<ConversationInfo?>(null)

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
            loadMembersInternal(role = GroupMemberFilterRole.ALL)
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

    val avatarURL: StateFlow<String> = groupInfo
        .map { it?.avatarURL ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val notification: StateFlow<String> = groupInfo
        .map { it?.notification ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val isNotDisturb: StateFlow<Boolean> = conversationInfo
        .map { it?.receiveOption?.let { option -> option != ReceiveMessageOption.RECEIVE } ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isPinned: StateFlow<Boolean> = conversationInfo
        .map { it?.isPinned ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isAllMuted: StateFlow<Boolean> = groupInfo
        .map { it?.isAllMuted ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val memberList: StateFlow<List<GroupMember>>
        get() = _state.memberList

    val silencedMembers: StateFlow<List<GroupMember>> = memberList
        .map { members -> members.filter { it.isMuted } }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = memberList.value.filter { it.isMuted }
        )

    val adminMembers: StateFlow<List<GroupMember>> = memberList
        .map { members -> members.filter { it.role == GroupMemberRole.ADMIN } }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = memberList.value.filter { it.role == GroupMemberRole.ADMIN }
        )

    val selfRole: StateFlow<GroupMemberRole> = groupInfo
        .map { it?.selfRole ?: GroupMemberRole.MEMBER }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GroupMemberRole.MEMBER)

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
                override fun onSuccess(membersInfo: List<GroupMember>) {
                    _selfNameCard.value = membersInfo.firstOrNull()?.nameCard
                }

                override fun onFailure(code: Int, desc: String) {}
            }
        )
    }

    private suspend fun loadAllMembers(role: GroupMemberFilterRole) {
        if (!loadMembersInternal(role)) return
        while (_state.hasMoreMembers.value) {
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
        if (!_state.hasMoreMembers.value) {
            notifyAllMemberCallbacks()
            return
        }
        if (isFetchingAllMembers) return
        isFetchingAllMembers = true
        viewModelScope.launch {
            loadAllMembers(role = GroupMemberFilterRole.ALL)
            isFetchingAllMembers = false
            notifyAllMemberCallbacks()
        }
    }

    private fun notifyAllMemberCallbacks() {
        val callbacks = pendingAllMemberCallbacks.toList()
        pendingAllMemberCallbacks.clear()
        callbacks.forEach { it.invoke() }
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

    fun setGroupName(name: String) {
        val info = GroupInfo(groupID = groupID, groupName = name)
        _groupStore.updateProfile(info, emptyHandler())
    }

    fun setGroupAvatar(avatarUrl: String) {
        val info = GroupInfo(groupID = groupID, avatarURL = avatarUrl)
        _groupStore.updateProfile(info, emptyHandler())
    }

    fun setGroupNotice(notice: String) {
        val info = GroupInfo(groupID = groupID, notification = notice)
        _groupStore.updateProfile(info, emptyHandler())
    }

    fun setJoinGroupApproveType(type: GroupJoinOption) {
        _groupStore.setJoinOption(groupID, type, emptyHandler())
    }

    fun setInviteGroupApproveType(type: GroupInviteOption) {
        _groupStore.setInviteOption(groupID, type, emptyHandler())
    }

    fun setGroupNickname(nickname: String) {
        _groupMemberStore.setSelfNameCard(nickname, object : CompletionHandler {
            override fun onSuccess() {
                refreshSelfNameCard()
            }

            override fun onFailure(code: Int, desc: String) {}
        })
    }

    fun addMember(userIDList: List<String>) {
        if (userIDList.isEmpty()) return
        _groupMemberStore.addMember(userIDList, emptyHandler())
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

    fun changeOwner(newOwnerID: String) {
        _groupStore.changeOwner(groupID, newOwnerID)
    }

    fun toggleGroupAllMute() {
        val mute = !isAllMuted.value
        _groupStore.muteAllMembers(groupID, mute, emptyHandler())
    }

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

    fun deleteMember(members: List<GroupMember>) {
        val userIDList = members.map { it.userID }
        _groupMemberStore.deleteMember(userIDList, emptyHandler())
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

class GroupChatSettingViewModelFactory(
    private val groupID: String,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupChatSettingViewModel::class.java)) {
            return GroupChatSettingViewModel(groupID, context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

private const val GROUP_AVATAR_URL_TEMPLATE =
    "https://im.sdk.qcloud.com/download/tuikit-resource/group-avatar/group_avatar_%s.png"
private const val GROUP_AVATAR_URL_COUNT = 24

fun getGroupAvatarUrls(): List<String> {
    val list = ArrayList<String>(GROUP_AVATAR_URL_COUNT)
    for (i in 1..GROUP_AVATAR_URL_COUNT) {
        list.add(String.format(GROUP_AVATAR_URL_TEMPLATE, i))
    }
    return list
}
