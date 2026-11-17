package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.GetContactInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupInfo
import io.trtc.tuikit.atomicxcore.api.group.GetGroupInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AddType {
    CONTACT,
    GROUP
}

data class AddContactAndGroupUiState(
    val searchKeyword: String = "",
    val addFriendInfo: ContactInfo? = null,
    val joinGroupInfo: ContactInfo? = null,
    val isJoinGroupAlready: Boolean = false,
    val isAddingContact: Boolean = false,
    val currentUserId: String = "",
    val requestResult: RequestResult? = null,
    val isSearching: Boolean = false
)

data class RequestResult(
    val isSuccess: Boolean,
    val message: String,
    val type: AddType
)

class AddContactAndGroupViewModel(
    private val contactStore: ContactStore,
    private val groupStore: GroupStore
) : ViewModel() {

    private val groupStoreState = groupStore.state
    private val _uiState = MutableStateFlow(AddContactAndGroupUiState())
    val uiState: StateFlow<AddContactAndGroupUiState> = _uiState.asStateFlow()

    private val searchRequestTracker = LatestSearchRequestTracker()

    init {
        viewModelScope.launch {
            groupStoreState.joinedGroupList.collect { joinedGroupList ->
                val currentGroupID = _uiState.value.joinGroupInfo?.userID
                val isJoined = currentGroupID?.let { id ->
                    joinedGroupList.any { it.groupID == id }
                } == true
                _uiState.value = _uiState.value.copy(isJoinGroupAlready = isJoined)
            }
        }

        groupStore.loadJoinedGroups()
        getCurrentUserId()
    }

    fun updateSearchKeyword(keyword: String) {
        _uiState.value = _uiState.value.copy(searchKeyword = keyword)
        if (keyword.isEmpty()) {
            searchRequestTracker.invalidate()
            _uiState.value = _uiState.value.copy(
                joinGroupInfo = null,
                addFriendInfo = null,
                isJoinGroupAlready = false,
                isSearching = false
            )
        }
    }

    fun searchGroup() {
        val keyword = _uiState.value.searchKeyword.trim()
        if (keyword.isEmpty()) return
        val requestToken = searchRequestTracker.begin(keyword)
        _uiState.value = _uiState.value.copy(isSearching = true)
        groupStore.getGroupInfo(
            groupID = keyword,
            completion = object : GetGroupInfoCompletionHandler {
                override fun onSuccess(groupInfo: GroupInfo) {
                    if (!searchRequestTracker.accepts(requestToken, _uiState.value.searchKeyword)) {
                        return
                    }
                    val joinGroupInfo = ContactInfo(
                        userID = groupInfo.groupID,
                        avatarURL = groupInfo.avatarURL,
                        nickname = groupInfo.groupName
                    )
                    val isJoined = groupStoreState.joinedGroupList.value.any { it.groupID == groupInfo.groupID }
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        joinGroupInfo = joinGroupInfo,
                        isJoinGroupAlready = isJoined
                    )
                }

                override fun onFailure(code: Int, desc: String) {
                    if (!searchRequestTracker.accepts(requestToken, _uiState.value.searchKeyword)) {
                        return
                    }
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        joinGroupInfo = null,
                        isJoinGroupAlready = false
                    )
                }
            }
        )
    }

    fun searchContact() {
        val keyword = _uiState.value.searchKeyword.trim()
        if (keyword.isEmpty()) return
        val requestToken = searchRequestTracker.begin(keyword)
        _uiState.value = _uiState.value.copy(isSearching = true)
        contactStore.getContactInfo(
            userIDList = listOf(keyword),
            completion = object : GetContactInfoCompletionHandler {
                override fun onSuccess(contactInfoList: List<ContactInfo>) {
                    if (!searchRequestTracker.accepts(requestToken, _uiState.value.searchKeyword)) {
                        return
                    }
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        addFriendInfo = contactInfoList.firstOrNull()
                    )
                }

                override fun onFailure(code: Int, desc: String) {
                    if (!searchRequestTracker.accepts(requestToken, _uiState.value.searchKeyword)) {
                        return
                    }
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        addFriendInfo = null
                    )
                }
            }
        )
    }

    fun addFriend(
        result: ContactInfo,
        addWording: String,
        remark: String,
        successMessage: String,
        failureMessageMapper: (Int, String) -> String
    ) {
        _uiState.value = _uiState.value.copy(isAddingContact = true, requestResult = null)
        val friendRemark = remark.trim().ifEmpty { result.nickname ?: result.userID }
        contactStore.addFriend(
            userID = result.userID,
            remark = friendRemark,
            addWording = addWording,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    _uiState.value = _uiState.value.copy(
                        isAddingContact = false,
                        requestResult = RequestResult(
                            isSuccess = true,
                            message = successMessage,
                            type = AddType.CONTACT
                        )
                    )
                }

                override fun onFailure(errorCode: Int, errorMessage: String) {
                    _uiState.value = _uiState.value.copy(
                        isAddingContact = false,
                        requestResult = RequestResult(
                            isSuccess = ContactRequestResultPolicy.isSuccessfulAddFriendResponse(errorCode),
                            message = failureMessageMapper(errorCode, errorMessage),
                            type = AddType.CONTACT
                        )
                    )
                }
            }
        )
    }

    fun joinGroup(
        result: ContactInfo,
        message: String,
        successMessage: String,
        failureMessageMapper: (Int, String) -> String
    ) {
        _uiState.value = _uiState.value.copy(isAddingContact = true, requestResult = null)
        groupStore.joinGroup(
            groupID = result.userID,
            message = message,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    _uiState.value = _uiState.value.copy(
                        isAddingContact = false,
                        requestResult = RequestResult(
                            isSuccess = true,
                            message = successMessage,
                            type = AddType.GROUP
                        )
                    )
                }

                override fun onFailure(errorCode: Int, errorMessage: String) {
                    _uiState.value = _uiState.value.copy(
                        isAddingContact = false,
                        requestResult = RequestResult(
                            isSuccess = false,
                            message = failureMessageMapper(errorCode, errorMessage),
                            type = AddType.GROUP
                        )
                    )
                }
            }
        )
    }

    private fun getCurrentUserId() {
        val userId = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        _uiState.value = _uiState.value.copy(currentUserId = userId)
    }

    fun clearSearchResults() {
        searchRequestTracker.invalidate()
        _uiState.value = _uiState.value.copy(
            searchKeyword = "",
            joinGroupInfo = null,
            addFriendInfo = null,
            isJoinGroupAlready = false,
            isSearching = false
        )
    }

    fun clearRequestResult() {
        _uiState.value = _uiState.value.copy(requestResult = null)
    }
}

class AddContactAndGroupViewModelFactory(
    private val contactStore: ContactStore = ContactStore.shared,
    private val groupStore: GroupStore = GroupStore.shared
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddContactAndGroupViewModel::class.java)) {
            return AddContactAndGroupViewModel(contactStore, groupStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
