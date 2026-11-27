package io.trtc.tuikit.chat.uikit.components.conversationlist.utils

import android.util.Log
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationLoadOption
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationMarkType

private const val TAG = "ConversationUnreadClearer"
private const val PAGE_SIZE = 100
private const val UNMARK_BATCH_SIZE = 100

object ConversationUnreadClearer {

    private val markedConversationQueryStore: ConversationListStore by lazy {
        ConversationListStore.create()
    }

    fun clearAllUnread(store: ConversationListStore, completion: CompletionHandler?) {
        loadAllMarkedConversationIDs { conversationIDs ->
            Log.d(TAG, "clear all unread, marked=${conversationIDs.size}")
            unmarkConversations(store, conversationIDs, 0) {
                store.clearConversationUnreadCount("", completion)
            }
        }
    }

    private fun loadAllMarkedConversationIDs(onFinished: (List<String>) -> Unit) {
        markedConversationQueryStore.loadConversations(
            ConversationLoadOption(count = PAGE_SIZE, markType = ConversationMarkType.unread),
            object : CompletionHandler {
                override fun onSuccess() {
                    loadRemainingPages { onFinished(collectMarkedConversationIDs()) }
                }

                override fun onFailure(code: Int, desc: String) {
                    Log.w(TAG, "load unread marked conversations failed, code=$code, desc=$desc")
                    onFinished(emptyList())
                }
            }
        )
    }

    private fun loadRemainingPages(onFinished: () -> Unit) {
        if (!markedConversationQueryStore.state.hasMoreConversations.value) {
            onFinished()
            return
        }
        markedConversationQueryStore.loadMoreConversations(object : CompletionHandler {
            override fun onSuccess() {
                loadRemainingPages(onFinished)
            }

            override fun onFailure(code: Int, desc: String) {
                Log.w(TAG, "load more unread marked conversations failed, code=$code, desc=$desc")
                onFinished()
            }
        })
    }

    private fun collectMarkedConversationIDs(): List<String> {
        return markedConversationQueryStore.state.conversationList.value
            .map { it.conversationID }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun unmarkConversations(
        store: ConversationListStore,
        conversationIDs: List<String>,
        start: Int,
        onFinished: () -> Unit
    ) {
        if (start >= conversationIDs.size) {
            onFinished()
            return
        }
        val end = minOf(start + UNMARK_BATCH_SIZE, conversationIDs.size)
        store.markConversation(
            conversationIDs.subList(start, end),
            ConversationMarkType.unread,
            false,
            object : CompletionHandler {
                override fun onSuccess() {
                    unmarkConversations(store, conversationIDs, end, onFinished)
                }

                override fun onFailure(code: Int, desc: String) {
                    Log.w(TAG, "unmark unread conversations failed, code=$code, desc=$desc")
                    onFinished()
                }
            }
        )
    }
}
