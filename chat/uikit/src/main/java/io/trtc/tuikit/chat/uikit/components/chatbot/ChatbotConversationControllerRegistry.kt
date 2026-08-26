package io.trtc.tuikit.chat.uikit.components.chatbot

internal class ChatbotConversationControllerRegistry(
    private val controllerFactory: (String) -> ChatbotConversationController = {
        ChatbotConversationController(it)
    }
) {
    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun acquire(
        conversationID: String,
        preferredController: ChatbotConversationController? = null
    ): ChatbotConversationController {
        val entry = entries.getOrPut(conversationID) {
            Entry(preferredController ?: controllerFactory(conversationID))
        }
        entry.referenceCount += 1
        return entry.controller
    }

    @Synchronized
    fun release(
        conversationID: String,
        controller: ChatbotConversationController
    ) {
        val entry = entries[conversationID] ?: return
        if (entry.controller !== controller) {
            return
        }
        entry.referenceCount = (entry.referenceCount - 1).coerceAtLeast(0)
        if (entry.referenceCount == 0 && !entry.controller.isActive) {
            entries.remove(conversationID)
        }
    }

    @Synchronized
    fun clear(conversationID: String) {
        val entry = entries[conversationID] ?: return
        entry.controller.clear()
        if (entry.referenceCount == 0) {
            entries.remove(conversationID)
        }
    }

    private data class Entry(
        val controller: ChatbotConversationController,
        var referenceCount: Int = 0
    )
}

internal object ChatbotConversationControllers {
    private val registry = ChatbotConversationControllerRegistry()

    fun acquire(conversationID: String): ChatbotConversationController {
        return registry.acquire(conversationID)
    }

    fun retain(
        conversationID: String,
        controller: ChatbotConversationController
    ): ChatbotConversationController {
        return registry.acquire(conversationID, controller)
    }

    fun release(
        conversationID: String,
        controller: ChatbotConversationController
    ) {
        registry.release(conversationID, controller)
    }

    fun clear(conversationID: String) {
        registry.clear(conversationID)
    }
}
