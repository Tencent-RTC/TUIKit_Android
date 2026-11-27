package io.trtc.tuikit.chat.uikit.pages

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationController
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationControllers
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationPolicy
import io.trtc.tuikit.chat.uikit.components.common.appContext
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.compose.R

class ChatPageController {
    internal var conversationID: String? = null
    internal var hostContext: Context? = null
    private var chatbotController: ChatbotConversationController? = null

    fun clearChatHistory(completion: CompletionHandler? = null): Boolean {
        val conversationID = conversationID ?: return false
        if (!isChatbotConversationID(conversationID)) {
            return false
        }
        val controller = chatbotController ?: return false
        if (controller.isActive) {
            val context = toastContext()
            if (context != null) {
                Toast.warning(
                    context,
                    context.getString(R.string.message_input_chatbot_waiting_tips)
                )
            }
            return false
        }
        ConversationListStore.create().clearConversationMessages(
            conversationID,
            object : CompletionHandler {
                override fun onSuccess() {
                    ChatbotConversationControllers.clear(conversationID)
                    completion?.onSuccess()
                }

                override fun onFailure(code: Int, desc: String) {
                    completion?.onFailure(code, desc)
                }
            }
        )
        return true
    }

    fun isChatbotConversation(): Boolean {
        return isChatbotConversationID(conversationID)
    }

    internal fun bind(conversationID: String, context: Context) {
        hostContext = context
        if (this.conversationID == conversationID) {
            retainChatbotController()
            return
        }
        releaseChatbotController()
        this.conversationID = conversationID
        retainChatbotController()
    }

    internal fun unbind() {
        releaseChatbotController()
        conversationID = null
        hostContext = null
    }

    private fun retainChatbotController() {
        val conversationID = conversationID ?: return
        if (chatbotController != null) {
            return
        }
        if (!isChatbotConversationID(conversationID)) {
            return
        }
        chatbotController = ChatbotConversationControllers.acquire(conversationID)
    }

    private fun releaseChatbotController() {
        val conversationID = conversationID ?: return
        val controller = chatbotController ?: return
        ChatbotConversationControllers.release(conversationID, controller)
        chatbotController = null
    }

    private fun toastContext(): Context? {
        return hostContext ?: runCatching { appContext }.getOrNull()
    }

    companion object {
        @JvmStatic
        fun isChatbotConversationID(conversationID: String?): Boolean {
            return ChatbotConversationPolicy.isChatbotConversation(conversationID)
        }
    }
}

@Composable
fun rememberChatPageController(): ChatPageController {
    return remember { ChatPageController() }
}
