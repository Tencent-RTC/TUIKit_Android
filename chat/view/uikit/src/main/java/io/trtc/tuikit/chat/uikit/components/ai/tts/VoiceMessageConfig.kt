package io.trtc.tuikit.chat.uikit.components.ai.tts
import android.content.Context
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

// Per-user persisted configuration for the chat TTS suite.
//
// Stores the record-translation target language (independent from the global
// message translate target) and the selected TTS voice. Keys are namespaced by
// the current login user id so two accounts on the same device never overwrite
// each other. An empty selected voice id means the built-in "default" voice.
object VoiceMessageConfig {
    private var mmkv: MMKV? = null

    fun getSelectedVoiceId(context: Context): String {
        return storage(context).decodeString(key(NAME_SELECTED_VOICE_ID), "").orEmpty()
    }

    fun getSelectedVoiceName(context: Context): String {
        return storage(context).decodeString(key(NAME_SELECTED_VOICE_NAME), "").orEmpty()
    }

    fun setSelectedVoice(context: Context, id: String, name: String) {
        val storage = storage(context)
        storage.encode(key(NAME_SELECTED_VOICE_ID), id)
        storage.encode(key(NAME_SELECTED_VOICE_NAME), name)
    }

    fun getRecordTranslateTargetLanguage(context: Context): String {
        return storage(context).decodeString(key(NAME_RECORD_TRANSLATE_LANGUAGE), "").orEmpty()
    }

    fun setRecordTranslateTargetLanguage(context: Context, lang: String) {
        storage(context).encode(key(NAME_RECORD_TRANSLATE_LANGUAGE), lang)
    }

    private fun storage(context: Context): MMKV {
        var instance = mmkv
        if (instance == null) {
            MMKV.initialize(context.applicationContext)
            instance = MMKV.mmkvWithID(MMKV_ID)
            mmkv = instance
        }
        return instance
    }

    private fun key(name: String): String {
        val userId = currentUserId()
        return if (userId.isEmpty()) "$KEY_PREFIX.$name" else "$KEY_PREFIX.$userId.$name"
    }

    private fun currentUserId(): String {
        return try {
            LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        } catch (exception: Exception) {
            ""
        }
    }

    private const val MMKV_ID = "atomicx_voice_message_config"
    private const val KEY_PREFIX = "voice_message_config"
    private const val NAME_SELECTED_VOICE_ID = "selectedVoiceId"
    private const val NAME_SELECTED_VOICE_NAME = "selectedVoiceName"
    private const val NAME_RECORD_TRANSLATE_LANGUAGE = "recordTranslateTargetLanguage"
}
