package com.trtc.uikit.livekit.voicelounge.settings

import android.content.Context
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitResources
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeSignal
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeBackground
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.vlString
import io.trtc.tuikit.atomicxcore.api.ai.TranslationLanguage
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

internal class VoiceLoungeSettings {
    val changes = VoiceLoungeSignal()

    var config = VoiceLoungeConfig()
        set(value) {
            if (value == field) return
            field = value
            changes.send()
        }

    var hostName = ""
        private set
    var hostAvatarURL = ""
        private set
    var hostUserID = ""
        private set

    val seatLayout: VoiceLoungeSeatLayout get() = config.seatLayout
    val topic: String get() = config.topic
    val isTranscriberEnabled: Boolean get() = config.isTranscriberEnabled
    val transcriberShowOriginal: Boolean get() = config.transcriberShowOriginal
    val transcriberScale: VoiceLoungeTranscriberScale get() = config.transcriberScale
    val transcriberLanguage: TranslationLanguage get() = config.transcriberLanguage
    val skin: VoiceLoungeSkin get() = config.skin
    val background: VoiceLoungeBackground get() = config.background
    val coverURL: String get() = config.coverURL

    val displayRoomName: String
        get() = config.roomName.ifEmpty { VoiceLoungeConfig.defaultRoomName(hostName) }

    fun updateHost(name: String, avatarURL: String, userID: String) {
        if (name == hostName && avatarURL == hostAvatarURL && userID == hostUserID) return
        hostName = name
        hostAvatarURL = avatarURL
        hostUserID = userID
        changes.send()
    }
}

internal data class VoiceLoungeConfig(
    val roomName: String = "",
    val coverURL: String = LiveKitResources.defaultCoverURL,
    val topic: String = defaultTopic(),
    val seatLayout: VoiceLoungeSeatLayout = VoiceLoungeSeatLayout.ONE_V8,
    val skin: VoiceLoungeSkin = VoiceLoungeSkin.defaultSkin,
    val background: VoiceLoungeBackground = VoiceLoungeBackground.defaultBackground,
    val isTranscriberEnabled: Boolean = false,
    val transcriberShowOriginal: Boolean = false,
    val transcriberScale: VoiceLoungeTranscriberScale = VoiceLoungeTranscriberScale.STANDARD,
    val transcriberLanguage: TranslationLanguage = TranslationLanguage.ENGLISH,
) {
    val metaData: Map<String, String>
        get() = mapOf(
            META_LAYOUT to seatLayout.rawValue,
            META_BACKGROUND to background.id,
            META_SKIN to skin.id,
            META_TOPIC to topic,
            META_TRANSCRIBER to if (isTranscriberEnabled) "1" else "0",
        )

    fun applyingMeta(metaData: Map<String, String>, includeTranscriber: Boolean = true): VoiceLoungeConfig {
        val defaults = VoiceLoungeConfig()
        return copy(
            seatLayout = metaData[META_LAYOUT]?.let(VoiceLoungeSeatLayout::fromRawValue) ?: defaults.seatLayout,
            background = metaData[META_BACKGROUND]?.let(VoiceLoungeBackground::background) ?: defaults.background,
            skin = metaData[META_SKIN]?.let(VoiceLoungeSkin::skin) ?: defaults.skin,
            topic = metaData[META_TOPIC] ?: defaults.topic,
            isTranscriberEnabled = if (includeTranscriber) {
                metaData[META_TRANSCRIBER]?.let { it == "1" } ?: defaults.isTranscriberEnabled
            } else {
                isTranscriberEnabled
            },
        )
    }

    companion object {
        private const val META_LAYOUT = "vl_layout"
        private const val META_BACKGROUND = "vl_bg"
        private const val META_SKIN = "vl_skin"
        private const val META_TOPIC = "vl_topic"
        private const val META_TRANSCRIBER = "vl_caption"

        val metaKeys = listOf(META_LAYOUT, META_BACKGROUND, META_SKIN, META_TOPIC, META_TRANSCRIBER)
        const val PAUSED_META_KEY = "vl_paused"

        fun hasMeta(metaData: Map<String, String>): Boolean = metaKeys.any { metaData[it] != null }

        fun defaultTopic(): String = vlString(R.string.livekit_voiceroom_topic_default)

        fun defaultRoomName(hostName: String): String {
            val trimmed = hostName.trim()
            if (trimmed.isEmpty()) return vlString(R.string.livekit_voiceroom_prepare_room_name_fallback)
            return vlString(R.string.livekit_voiceroom_prepare_room_name_voice, trimmed)
        }
    }
}

internal enum class VoiceLoungeTranscriberScale(val sizeSp: Float) {
    SMALL(11f), STANDARD(13f), LARGE(18f),
}

internal enum class VoiceLoungeSeatLayout(val rawValue: String) {
    ONE_V8("oneV8"), ONE_V8X("oneV8x"), TWO_V8("twoV8"), DUO("duo");

    val displayName: String
        get() = vlString(
            when (this) {
                ONE_V8 -> R.string.livekit_voiceroom_layout_one_v8
                ONE_V8X -> R.string.livekit_voiceroom_layout_one_v8x
                TWO_V8 -> R.string.livekit_voiceroom_layout_two_v8
                DUO -> R.string.livekit_voiceroom_layout_duo
            },
        )

    companion object {
        private const val PREFERENCES_NAME = "tuilivekit_voicelounge"

        fun fromRawValue(value: String): VoiceLoungeSeatLayout? = values().firstOrNull { it.rawValue == value }

        fun savedDefault(context: Context): VoiceLoungeSeatLayout? {
            val key = defaultKey() ?: return null
            return preferences(context).getString(key, null)?.let(::fromRawValue)
        }

        fun saveDefault(context: Context, layout: VoiceLoungeSeatLayout?) {
            val key = defaultKey() ?: return
            preferences(context).edit().apply {
                if (layout != null) putString(key, layout.rawValue) else remove(key)
            }.apply()
        }

        private fun preferences(context: Context) =
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

        private fun defaultKey(): String? {
            val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID
            if (userID.isNullOrEmpty()) return null
            return "TUILiveKit.voiceLounge.defaultLayout.v1.${LoginStore.shared.sdkAppID}.$userID.voice"
        }
    }
}

internal data class VoiceLoungeTranscriberDefaults(
    val isEnabled: Boolean,
    val showOriginal: Boolean,
    val scale: VoiceLoungeTranscriberScale,
    val language: TranslationLanguage,
) {
    companion object {
        private const val PREFERENCES_NAME = "tuilivekit_voicelounge"

        fun load(context: Context): VoiceLoungeTranscriberDefaults? {
            val key = defaultsKey() ?: return null
            val prefs = preferences(context)
            if (!prefs.contains("${key}.enabled")) return null
            val scaleName = prefs.getString("${key}.scale", null)
            val languageName = prefs.getString("${key}.lang", null)
            return VoiceLoungeTranscriberDefaults(
                isEnabled = prefs.getBoolean("${key}.enabled", false),
                showOriginal = prefs.getBoolean("${key}.original", false),
                scale = scaleName?.let { runCatching { VoiceLoungeTranscriberScale.valueOf(it) }.getOrNull() }
                    ?: VoiceLoungeTranscriberScale.STANDARD,
                language = languageName?.let { runCatching { TranslationLanguage.valueOf(it) }.getOrNull() }
                    ?: TranslationLanguage.ENGLISH,
            )
        }

        fun save(context: Context, defaults: VoiceLoungeTranscriberDefaults) {
            val key = defaultsKey() ?: return
            preferences(context).edit().putBoolean("${key}.enabled", defaults.isEnabled)
                .putBoolean("${key}.original", defaults.showOriginal).putString("${key}.scale", defaults.scale.name)
                .putString("${key}.lang", defaults.language.name).apply()
        }

        private fun preferences(context: Context) =
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

        private fun defaultsKey(): String? {
            val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID
            if (userID.isNullOrEmpty()) return null
            return "TUILiveKit.voiceLounge.transcriber.v1.${LoginStore.shared.sdkAppID}.$userID"
        }
    }
}
