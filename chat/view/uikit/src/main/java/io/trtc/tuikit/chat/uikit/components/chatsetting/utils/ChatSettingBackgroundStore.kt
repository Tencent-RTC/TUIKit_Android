package io.trtc.tuikit.chat.uikit.components.chatsetting.utils
import android.content.Context
import com.tencent.mmkv.MMKV

internal class ChatSettingBackgroundStore(context: Context) {
    private val mmkv: MMKV

    init {
        MMKV.initialize(context.applicationContext)
        mmkv = MMKV.mmkvWithID(MMKV_ID)
    }

    fun getImageUri(conversationID: String): String? {
        return normalizeImageUri(mmkv.decodeString(storageKey(conversationID)))
    }

    fun setImageUri(conversationID: String, imageUri: String?) {
        val normalizedUri = normalizeImageUri(imageUri)
        if (normalizedUri == null) {
            clearImageUri(conversationID)
            return
        }
        if (getImageUri(conversationID) != normalizedUri) {
            mmkv.encode(storageKey(conversationID), normalizedUri)
        }
    }

    fun clearImageUri(conversationID: String) {
        mmkv.removeValueForKey(storageKey(conversationID))
    }

    private fun storageKey(conversationID: String): String {
        return KEY_PREFIX + conversationID
    }

    private fun normalizeImageUri(imageUri: String?): String? {
        return imageUri?.trim()?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val MMKV_ID = "atomicx_chat_background"
        const val KEY_PREFIX = "chat_background::"
    }
}
