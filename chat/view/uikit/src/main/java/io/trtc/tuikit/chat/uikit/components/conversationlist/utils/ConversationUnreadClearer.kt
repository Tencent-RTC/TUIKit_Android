package io.trtc.tuikit.chat.uikit.components.conversationlist.utils

import android.util.Log
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationLoadOption
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationMarkType
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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

    fun clearConversationsUnread(
        store: ConversationListStore,
        conversations: List<ConversationInfo>,
        completion: CompletionHandler? = null
    ) {
        clearUnread(
            store = store,
            conversationIDs = conversations.map { it.conversationID },
            markedConversationIDs = conversations
                .filter { it.conversationMarkList.contains(ConversationMarkType.unread) }
                .map { it.conversationID },
            completion = completion
        )
    }

    fun clearUnread(
        store: ConversationListStore,
        conversationIDs: List<String>,
        markedConversationIDs: Collection<String> = conversationIDs,
        completion: CompletionHandler? = null
    ) {
        val ids = conversationIDs.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) {
            completion?.onSuccess()
            return
        }
        val marked = markedConversationIDs.filter { it.isNotEmpty() }.distinct()
        unmarkConversations(store, marked, 0) {
            clearUnreadCounts(store, ids, completion)
        }
    }

    fun clearMessages(
        store: ConversationListStore,
        conversationID: String,
        hasUnreadMark: Boolean = true,
        completion: CompletionHandler? = null
    ) {
        store.clearConversationMessages(conversationID, object : CompletionHandler {
            override fun onSuccess() {
                completion?.onSuccess()
                clearUnread(
                    store = store,
                    conversationIDs = listOf(conversationID),
                    markedConversationIDs = if (hasUnreadMark) listOf(conversationID) else emptyList()
                )
            }

            override fun onFailure(code: Int, desc: String) {
                completion?.onFailure(code, desc)
            }
        })
    }

    private fun clearUnreadCounts(
        store: ConversationListStore,
        conversationIDs: List<String>,
        completion: CompletionHandler?
    ) {
        val remaining = AtomicInteger(conversationIDs.size)
        val failure = AtomicReference<Pair<Int, String>?>(null)
        val finishOne = {
            if (remaining.decrementAndGet() == 0) {
                val error = failure.get()
                if (error == null) {
                    completion?.onSuccess()
                } else {
                    completion?.onFailure(error.first, error.second)
                }
            }
        }
        conversationIDs.forEach { conversationID ->
            store.clearConversationUnreadCount(conversationID, object : CompletionHandler {
                override fun onSuccess() {
                    finishOne()
                }

                override fun onFailure(code: Int, desc: String) {
                    Log.w(TAG, "clear unread count failed, code=$code, desc=$desc")
                    failure.compareAndSet(null, code to desc)
                    finishOne()
                }
            })
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
