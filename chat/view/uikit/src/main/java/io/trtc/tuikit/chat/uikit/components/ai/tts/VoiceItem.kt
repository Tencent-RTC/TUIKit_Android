package io.trtc.tuikit.chat.uikit.components.ai.tts
import android.content.Context
import androidx.annotation.StringRes
import io.trtc.tuikit.chat.uikit.R

// A TTS voice option. Either a built-in default voice or a user-cloned custom
// voice. Equality is keyed solely on [voiceId] so list selection works.
data class CustomVoiceItem(
    val voiceId: String,
    val name: String,
    val isDefault: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        return other is CustomVoiceItem && other.voiceId == voiceId
    }

    override fun hashCode(): Int {
        return voiceId.hashCode()
    }
}

// Built-in system voice ids, mirroring iOS TUITextToVoiceConfig.systemVoiceList.
const val VOICE_ID_XIAOXU_MALE = "male-kefu-xiaoxu"
const val VOICE_ID_XIAOMEI_FEMALE = "female-kefu-xiaomei"
const val VOICE_ID_XIAOXIN_FEMALE = "female-kefu-xiaoxin"
const val VOICE_ID_XIAOYUE_FEMALE = "female-kefu-xiaoyue"

// Default voice descriptor whose display name is a localized resource so the UI
// layer can resolve it against the current configuration.
data class DefaultVoiceItem(
    val voiceId: String,
    @StringRes val nameResId: Int
)

// The "default" entry (empty voiceId) followed by the system voices. Names point
// to res-voice-message strings.
fun defaultVoiceItems(): List<DefaultVoiceItem> {
    return listOf(
        DefaultVoiceItem("", R.string.voice_message_voice_default),
        DefaultVoiceItem(VOICE_ID_XIAOXU_MALE, R.string.voice_message_voice_xiaoxu),
        DefaultVoiceItem(VOICE_ID_XIAOMEI_FEMALE, R.string.voice_message_voice_xiaomei),
        DefaultVoiceItem(VOICE_ID_XIAOXIN_FEMALE, R.string.voice_message_voice_xiaoxin),
        DefaultVoiceItem(VOICE_ID_XIAOYUE_FEMALE, R.string.voice_message_voice_xiaoyue)
    )
}

// Resolves [defaultVoiceItems] into display-ready [CustomVoiceItem]s.
fun defaultVoiceList(context: Context): List<CustomVoiceItem> {
    return defaultVoiceItems().map { item ->
        CustomVoiceItem(
            voiceId = item.voiceId,
            name = context.getString(item.nameResId),
            isDefault = true
        )
    }
}
