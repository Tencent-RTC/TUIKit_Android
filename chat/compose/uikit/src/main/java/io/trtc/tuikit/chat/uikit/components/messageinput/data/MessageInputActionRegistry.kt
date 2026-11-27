package io.trtc.tuikit.chat.uikit.components.messageinput.data

import android.content.Context

fun interface MessageInputCustomActionProvider {
    fun provide(context: Context, conversationID: String): MessageInputMenuAction?
}

object MessageInputActionRegistry {
    private val lock = Any()
    private val providers = mutableListOf<MessageInputCustomActionProvider>()

    @JvmStatic
    fun registerCustomAction(provider: MessageInputCustomActionProvider) {
        synchronized(lock) {
            if (!providers.contains(provider)) {
                providers.add(provider)
            }
        }
    }

    @JvmStatic
    fun unregisterCustomAction(provider: MessageInputCustomActionProvider) {
        synchronized(lock) {
            providers.remove(provider)
        }
    }

    internal fun resolveCustomActions(context: Context, conversationID: String): List<MessageInputMenuAction> {
        return synchronized(lock) { providers.toList() }
            .mapNotNull { provider ->
                runCatching { provider.provide(context, conversationID) }.getOrNull()
            }
    }
}
