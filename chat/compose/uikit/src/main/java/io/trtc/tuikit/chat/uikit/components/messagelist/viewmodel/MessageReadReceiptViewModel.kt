package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.message.MessageActionStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MessageReadReceiptViewModel(
    private val message: MessageInfo
) : ViewModel() {
    private val messageActionStore: MessageActionStore = MessageActionStore.create(message)

    val readMemberList: StateFlow<List<GroupMember>> = messageActionStore.state.readMemberList.map {
        it.filter { item -> item.userID.isNotEmpty() }.distinctBy { item -> item.userID }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val unReadMemberList: StateFlow<List<GroupMember>> = messageActionStore.state.unreadMemberList.map {
        it.filter { item -> item.userID.isNotEmpty() }.distinctBy { item -> item.userID }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val hasMoreReadMembers: StateFlow<Boolean> = messageActionStore.state.hasMoreReadMembers
    val hasMoreUnReadMembers: StateFlow<Boolean> = messageActionStore.state.hasMoreUnreadMembers

    fun loadReadMembers(count: Int = 20) {
        messageActionStore.loadReadMembers(count, object : CompletionHandler {
            override fun onSuccess() {
            }

            override fun onFailure(code: Int, desc: String) {
                Log.e(TAG, "load read members failed. code: $code, message: $desc")
            }
        })
    }

    fun loadUnreadMembers(count: Int = 20) {
        messageActionStore.loadUnreadMembers(count, object : CompletionHandler {
            override fun onSuccess() {
            }

            override fun onFailure(code: Int, desc: String) {
                Log.e(TAG, "load unread members failed. code: $code, message: $desc")
            }
        })
    }

    fun loadMoreReadMembers() {
        messageActionStore.loadMoreMembers(
            isRead = true,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                }

                override fun onFailure(code: Int, desc: String) {
                    Log.e(TAG, "load more read members failed. code: $code, message: $desc")
                }
            }
        )
    }

    fun loadMoreUnreadMembers() {
        messageActionStore.loadMoreMembers(
            isRead = false,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                }

                override fun onFailure(code: Int, desc: String) {
                    Log.e(TAG, "load more unread members failed. code: $code, message: $desc")
                }
            }
        )
    }


    companion object {
        private const val TAG = "MessageReadReceipt"
    }

    class Factory(private val message: MessageInfo) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MessageReadReceiptViewModel::class.java)) {
                return MessageReadReceiptViewModel(message) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
