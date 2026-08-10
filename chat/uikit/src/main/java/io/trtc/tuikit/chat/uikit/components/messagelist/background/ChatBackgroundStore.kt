package io.trtc.tuikit.chat.uikit.components.messagelist.background
import android.content.Context
import com.tencent.mmkv.MMKV

internal interface ChatBackgroundStore {
    fun getImageUri(conversationID: String): String?
    fun setImageUri(conversationID: String, imageUri: String?)
    fun clearImageUri(conversationID: String)
}

internal object ChatBackgroundPersistencePolicy {
    private const val KEY_PREFIX = "chat_background::"

    fun storageKey(conversationID: String): String {
        return KEY_PREFIX + conversationID
    }

    fun normalizeImageUri(imageUri: String?): String? {
        return imageUri?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun shouldPersist(currentUri: String?, newUri: String?): Boolean {
        val normalizedCurrentUri = normalizeImageUri(currentUri)
        val normalizedNewUri = normalizeImageUri(newUri)
        return normalizedNewUri != null && normalizedCurrentUri != normalizedNewUri
    }
}

internal class MmkvChatBackgroundStore(context: Context) : ChatBackgroundStore {
    private val mmkv: MMKV

    init {
        MMKV.initialize(context.applicationContext)
        mmkv = MMKV.mmkvWithID(MMKV_ID)
    }

    override fun getImageUri(conversationID: String): String? {
        return ChatBackgroundPersistencePolicy.normalizeImageUri(
            mmkv.decodeString(ChatBackgroundPersistencePolicy.storageKey(conversationID))
        )
    }

    override fun setImageUri(conversationID: String, imageUri: String?) {
        val normalizedUri = ChatBackgroundPersistencePolicy.normalizeImageUri(imageUri)
        if (normalizedUri == null) {
            clearImageUri(conversationID)
            return
        }
        val currentUri = getImageUri(conversationID)
        if (ChatBackgroundPersistencePolicy.shouldPersist(currentUri, normalizedUri)) {
            mmkv.encode(ChatBackgroundPersistencePolicy.storageKey(conversationID), normalizedUri)
        }
    }

    override fun clearImageUri(conversationID: String) {
        mmkv.removeValueForKey(ChatBackgroundPersistencePolicy.storageKey(conversationID))
    }

    private companion object {
        const val MMKV_ID = "atomicx_chat_background"
    }
}
