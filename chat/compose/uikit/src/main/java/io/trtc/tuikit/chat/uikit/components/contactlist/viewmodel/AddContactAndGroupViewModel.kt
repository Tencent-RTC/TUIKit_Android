package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tencent.imsdk.BaseConstants
import com.tencent.qcloud.tuicore.util.ErrorMessageConverter
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.common.appContext
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.GetContactInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GetGroupInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupInfo
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
    private val groupStore: GroupStore,
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
        groupStore.getGroupInfo(keyword, object : GetGroupInfoCompletionHandler {
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
        })
    }

    fun searchContact() {
        val keyword = _uiState.value.searchKeyword.trim()
        if (keyword.isEmpty()) return
        val requestToken = searchRequestTracker.begin(keyword)
        _uiState.value = _uiState.value.copy(isSearching = true)
        contactStore.getContactInfo(listOf(keyword), object : GetContactInfoCompletionHandler {
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
        })
    }

    fun addFriend(
        result: ContactInfo,
        addWording: String,
        remark: String,
        getString: (Int) -> String = appContext::getString
    ) {
        _uiState.value = _uiState.value.copy(isAddingContact = true, requestResult = null)

        val friendRemark = remark.trim().ifEmpty { result.nickname ?: result.userID }
        contactStore.addFriend(
            result.userID,
            friendRemark,
            addWording,
            object : CompletionHandler {
                override fun onSuccess() {
                    _uiState.value = _uiState.value.copy(
                        isAddingContact = false,
                        requestResult = RequestResult(
                            isSuccess = true,
                            message = getString(R.string.contact_list_add_friend_success),
                            type = AddType.CONTACT
                        )
                    )
                }

                override fun onFailure(errorCode: Int, errorMessage: String) {
                    _uiState.value = _uiState.value.copy(
                        isAddingContact = false,
                        requestResult = RequestResult(
                            isSuccess = ContactRequestResultPolicy.isSuccessfulAddFriendResponse(errorCode),
                            message = ContactRequestResultPolicy.getErrorMessage(
                                errorCode,
                                errorMessage,
                                getString
                            ),
                            type = AddType.CONTACT
                        )
                    )
                }
            })
    }

    fun joinGroup(result: ContactInfo, message: String) {
        _uiState.value = _uiState.value.copy(isAddingContact = true, requestResult = null)

        groupStore.joinGroup(result.userID, message, object : CompletionHandler {
            override fun onSuccess() {
                _uiState.value = _uiState.value.copy(
                    isAddingContact = false,
                    requestResult = RequestResult(
                        isSuccess = true,
                        message = appContext.getString(R.string.contact_list_join_group_request_sent),
                        type = AddType.GROUP
                    )
                )
            }

            override fun onFailure(errorCode: Int, errorMessage: String) {
                _uiState.value = _uiState.value.copy(
                    isAddingContact = false,
                    requestResult = RequestResult(
                        isSuccess = false,
                        message = errorMessage,
                        type = AddType.GROUP
                    )
                )
            }
        })
    }

    private fun getCurrentUserId() {
        val currentUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        _uiState.value = _uiState.value.copy(currentUserId = currentUserId)
    }


    fun clearSearchResults() {
        searchRequestTracker.invalidate()
        _uiState.value =
            _uiState.value.copy(
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
    private val groupStore: GroupStore = GroupStore.shared,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddContactAndGroupViewModel::class.java)) {
            return AddContactAndGroupViewModel(contactStore, groupStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

internal object ContactRequestResultPolicy {
    fun isSuccessfulAddFriendResponse(errorCode: Int): Boolean =
        errorCode == BaseConstants.ERR_SVR_FRIENDSHIP_ALREADY_FRIENDS ||
            errorCode == BaseConstants.ERR_SVR_FRIENDSHIP_ALLOW_TYPE_NEED_CONFIRM

    fun getErrorMessage(errorCode: Int, errorMessage: String, getString: (Int) -> String): String =
        ErrorMessageConverter.ERROR_CODE_MAP[errorCode]?.let(getString) ?: errorMessage
}

internal data class SearchRequestToken(
    val sequence: Long,
    val keyword: String
)

internal class LatestSearchRequestTracker {
    private var nextSequence = 0L
    private var latestToken: SearchRequestToken? = null

    fun begin(keyword: String): SearchRequestToken {
        val token = SearchRequestToken(
            sequence = ++nextSequence,
            keyword = keyword.trim()
        )
        latestToken = token
        return token
    }

    fun invalidate() {
        latestToken = null
        nextSequence++
    }

    fun accepts(token: SearchRequestToken, currentKeyword: String): Boolean {
        return latestToken == token && token.keyword == currentKeyword.trim()
    }
}
