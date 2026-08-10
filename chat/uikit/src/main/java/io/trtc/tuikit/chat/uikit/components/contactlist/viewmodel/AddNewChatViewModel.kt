package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.CreateGroupCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupCreateParams
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.group.GroupType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ChatType {
    SINGLE,
    GROUP
}

enum class GroupFlowStep {
    CONTACT_SELECTION,
    GROUP_SETTINGS,
    GROUP_TYPE_SELECTION
}

data class GroupTypeOption(
    val displayNameResID: Int,
    val descriptionResID: Int,
    val type: GroupType
)

data class AddNewChatUiState(
    val chatType: ChatType = ChatType.SINGLE,
    val selectedContacts: List<ContactInfo> = emptyList(),
    val isCreating: Boolean = false,
    val error: String? = null,
    val createdConversationId: String? = null,
    val groupFlowStep: GroupFlowStep = GroupFlowStep.CONTACT_SELECTION,
    val groupName: String = "",
    val groupID: String? = null,
    val groupAvatarUrl: String? = null
)

class AddNewChatViewModel(
    private val contactStore: ContactStore,
    private val groupStore: GroupStore
) : ViewModel() {

    companion object {
        const val GROUP_AVATAR_URL =
            "https://im.sdk.qcloud.com/download/tuikit-resource/group-avatar/group_avatar_%s.png"
        const val GROUP_AVATAR_COUNT = 24

        val WORK_TYPE_OPTION = GroupTypeOption(
            displayNameResID = R.string.contact_list_group_work_type,
            descriptionResID = R.string.contact_list_group_work_des,
            type = GroupType.WORK
        )
        val PUBLIC_TYPE_OPTION = GroupTypeOption(
            displayNameResID = R.string.contact_list_group_public_type,
            descriptionResID = R.string.contact_list_group_public_des,
            type = GroupType.PUBLIC_GROUP
        )
        val MEETING_TYPE_OPTION = GroupTypeOption(
            displayNameResID = R.string.contact_list_group_meeting_type,
            descriptionResID = R.string.contact_list_group_meeting_des,
            type = GroupType.MEETING
        )
        val COMMUNITY_TYPE_OPTION = GroupTypeOption(
            displayNameResID = R.string.contact_list_group_community_type,
            descriptionResID = R.string.contact_list_group_community_des,
            type = GroupType.COMMUNITY
        )

        fun getGroupTypeOptionList(): List<GroupTypeOption> {
            return listOf(
                WORK_TYPE_OPTION,
                PUBLIC_TYPE_OPTION,
                MEETING_TYPE_OPTION,
                COMMUNITY_TYPE_OPTION
            )
        }

        fun getGroupAvatarUrls(): List<String> {
            return (1..GROUP_AVATAR_COUNT).map { String.format(GROUP_AVATAR_URL, it) }
        }
    }

    private val contactState = contactStore.state

    private val groupCreateMessageSender = GroupCreateMessageSender()
    private var lastCreatedGroupID: String? = null

    val contactDataSource: StateFlow<List<UserPickerData<ContactInfo>>> = contactState.friendList.map { list ->
        list.map {
            UserPickerData(
                key = it.userID,
                label = it.displayName,
                avatarUrl = it.avatarURL,
                extraData = it
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _uiState = MutableStateFlow(AddNewChatUiState())
    val uiState: StateFlow<AddNewChatUiState> = _uiState.asStateFlow()

    private val _currentSelectedGroupType = MutableStateFlow(WORK_TYPE_OPTION)
    val currentSelectedGroupType: StateFlow<GroupTypeOption> = _currentSelectedGroupType.asStateFlow()

    init {
        loadFriends()
    }

    fun setChatType(chatType: ChatType) {
        _uiState.value = _uiState.value.copy(
            chatType = chatType,
            selectedContacts = emptyList(),
            error = null
        )
    }

    fun setSelectedContacts(contacts: List<UserPickerData<ContactInfo>>) {
        _uiState.value = _uiState.value.copy(
            selectedContacts = contacts.map { it.extraData },
            error = null
        )
    }

    fun removeSelectedContact(contact: ContactInfo) {
        val selectedContacts = _uiState.value.selectedContacts.toMutableList()
        selectedContacts.remove(contact)
        _uiState.value = _uiState.value.copy(selectedContacts = selectedContacts)
    }

    fun clearState() {
        _uiState.value = AddNewChatUiState()
    }

    fun getCreatedGroupID(): String? = lastCreatedGroupID

    fun consumeCreatedConversationId() {
        _uiState.value = _uiState.value.copy(createdConversationId = null)
    }

    fun startChat() {
        val currentState = _uiState.value
        if (currentState.selectedContacts.isEmpty()) {
            _uiState.value = currentState.copy(error = "")
            return
        }
        if (currentState.chatType == ChatType.SINGLE) {
            val contact = currentState.selectedContacts.first()
            _uiState.value = currentState.copy(
                createdConversationId = ConversationIDUtil.fromUser(contact.userID),
                isCreating = false
            )
        } else {
            val ensuredGroupName = if (currentState.groupName.isBlank()) {
                generateGroupName(currentState.selectedContacts)
            } else {
                currentState.groupName
            }
            _uiState.value = currentState.copy(
                groupFlowStep = GroupFlowStep.GROUP_SETTINGS,
                groupName = ensuredGroupName
            )
        }
    }

    fun generateGroupName(contacts: List<ContactInfo>): String {
        val appContext = ContextProvider.getApplicationContext()
        val separator = appContext
            ?.getString(R.string.contact_list_group_name_separator)
            .orEmpty()
            .ifBlank { ", " }
        return ContactListGroupNameFormatter.generate(
            names = contacts.map { it.displayName },
            separator = separator,
            suffix = { remainingCount ->
                appContext
                    ?.getString(R.string.contact_list_group_name_suffix, remainingCount)
                    .orEmpty()
                    .ifBlank { " and $remainingCount others" }
            }
        )
    }

    fun showGroupTypeSelectionScreen() {
        _uiState.value = _uiState.value.copy(groupFlowStep = GroupFlowStep.GROUP_TYPE_SELECTION)
    }

    fun clearGroupSettingsScreen() {
        _uiState.value = _uiState.value.copy(groupFlowStep = GroupFlowStep.CONTACT_SELECTION)
    }

    fun clearGroupTypeSelectionScreen() {
        _uiState.value = _uiState.value.copy(groupFlowStep = GroupFlowStep.GROUP_SETTINGS)
    }

    fun updateSelectedGroupType(groupTypeOption: GroupTypeOption) {
        _currentSelectedGroupType.value = groupTypeOption
        _uiState.value = _uiState.value.copy(groupFlowStep = GroupFlowStep.GROUP_SETTINGS)
    }

    fun updateGroupName(name: String) {
        _uiState.value = _uiState.value.copy(groupName = name)
    }

    fun updateGroupID(id: String?) {
        _uiState.value = _uiState.value.copy(groupID = id)
    }

    fun updateGroupAvatarUrl(url: String?) {
        _uiState.value = _uiState.value.copy(groupAvatarUrl = url)
    }

    fun createGroupChatWithSettings(
        groupName: String,
        groupID: String? = null,
        groupAvatarUrl: String? = null,
        onSuccess: () -> Unit,
        onFailure: (code: Int, desc: String) -> Unit
    ) {
        val currentState = _uiState.value
        if (currentState.isCreating) {
            return
        }
        if (currentState.selectedContacts.isEmpty()) {
            _uiState.value = currentState.copy(error = "")
            return
        }

        val ensuredGroupName = groupName.ifBlank {
            generateGroupName(currentState.selectedContacts)
        }
        _uiState.value = currentState.copy(
            isCreating = true,
            error = null,
            createdConversationId = null,
            groupName = ensuredGroupName
        )

        groupStore.createGroup(
            params = GroupCreateParams(
                groupType = currentSelectedGroupType.value.type,
                groupName = ensuredGroupName,
                groupID = groupID,
                avatarURL = groupAvatarUrl,
                memberList = currentState.selectedContacts.map { it.userID }
            ),
            completion = object : CreateGroupCompletionHandler {
                override fun onSuccess(groupID: String) {
                    lastCreatedGroupID = groupID
                    val conversationId = ConversationIDUtil.fromGroup(groupID)
                    val createdGroupType = currentSelectedGroupType.value.type
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        createdConversationId = conversationId,
                        groupFlowStep = GroupFlowStep.CONTACT_SELECTION
                    )
                    onSuccess()
                    groupCreateMessageSender.schedule(conversationId, createdGroupType)
                }

                override fun onFailure(code: Int, desc: String) {
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        groupFlowStep = GroupFlowStep.GROUP_SETTINGS
                    )
                    onFailure(code, desc)
                }
            }
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun loadFriends() {
        contactStore.loadFriends(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {
                _uiState.value = _uiState.value.copy(error = desc)
            }
        })
    }
}

class AddNewChatViewModelFactory(
    private val contactStore: ContactStore = ContactStore.shared,
    private val groupStore: GroupStore = GroupStore.shared
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddNewChatViewModel::class.java)) {
            return AddNewChatViewModel(contactStore, groupStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
