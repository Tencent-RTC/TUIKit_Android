package com.tencent.qcloud.tuikit.tuicallkit.call

import android.content.Context
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow

internal class CallKitConfig private constructor(context: Context) {

    enum class TranscriptFontSize(val storeValue: String, val textSizeSp: Float) {
        SM("sm", 11f),
        MD("md", 12f),
        LG("lg", 14f);

        companion object {
            fun fromStoreValue(value: String): TranscriptFontSize {
                return entries.firstOrNull { it.storeValue == value } ?: MD
            }
        }
    }

    val transcriptionChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var callingBellPath: String = ""
        private set
    var enableFloatWindow: Boolean = true
        private set
    var enableIncomingBanner: Boolean = true
        private set
    var enableVirtualBackground: Boolean = false
        private set
    /** 字幕总开关（控制字幕真实运行与底部按钮态）；app 侧通过 `enableAITranscriber(enable)` 翻转。 */
    var enableAITranscriber: Boolean = true
        private set
    var screenOrientation: Int = 0
        private set

    var enableMuteMode: Boolean = false
        private set
    var showOriginalTranscript: Boolean = false
        private set
    var transcriptFontSize: TranscriptFontSize = TranscriptFontSize.MD
        private set
    var transcriptionSourceLanguage: String = DEFAULT_SOURCE_LANGUAGE
        private set
    var transcriptionTargetLanguage: String = ""
        private set

    var ccPresetEnabled: Boolean = true
        private set

    init {
        enableMuteMode = preferences.getBoolean(KEY_MUTE_MODE, false)
        enableAITranscriber = preferences.getBoolean(KEY_TRANSCRIPTION_ENABLED, true)
        restoreTranscriptionPrefs()
    }

    private fun restoreTranscriptionPrefs() {
        when (preferences.getString(KEY_CC_PRESET_ENABLED, null)) {
            null -> {
                ccPresetEnabled = true
                showOriginalTranscript = preferences.getBoolean(KEY_SHOW_ORIGINAL_TRANSCRIPT, false)
                transcriptFontSize = TranscriptFontSize.fromStoreValue(preferences.getString(KEY_TRANSCRIPT_FONT_SIZE, "").orEmpty())
                transcriptionSourceLanguage = preferences.getString(KEY_TRANSCRIPTION_SOURCE_LANGUAGE, null) ?: DEFAULT_SOURCE_LANGUAGE
                transcriptionTargetLanguage = preferences.getString(KEY_TRANSCRIPTION_TARGET_LANGUAGE, null).orEmpty()
                    .ifEmpty { defaultTargetLanguage() }
            }
            PRESET_ON -> {
                // 用户勾选：按已持久化的快照还原。
                ccPresetEnabled = true
                showOriginalTranscript = preferences.getBoolean(KEY_SHOW_ORIGINAL_TRANSCRIPT, false)
                transcriptFontSize = TranscriptFontSize.fromStoreValue(preferences.getString(KEY_TRANSCRIPT_FONT_SIZE, "").orEmpty())
                transcriptionSourceLanguage = preferences.getString(KEY_TRANSCRIPTION_SOURCE_LANGUAGE, null) ?: DEFAULT_SOURCE_LANGUAGE
                transcriptionTargetLanguage = preferences.getString(KEY_TRANSCRIPTION_TARGET_LANGUAGE, null).orEmpty()
                    .ifEmpty { defaultTargetLanguage() }
            }
            else -> {
                // 用户主动取消：下次启动三项全部工厂默认。
                ccPresetEnabled = false
                showOriginalTranscript = false
                transcriptFontSize = TranscriptFontSize.MD
                transcriptionSourceLanguage = DEFAULT_SOURCE_LANGUAGE
                transcriptionTargetLanguage = defaultTargetLanguage()
            }
        }
    }

    fun updateMuteMode(enable: Boolean) {
        enableMuteMode = enable
        preferences.edit().putBoolean(KEY_MUTE_MODE, enable).apply()
    }

    fun updateCallingBell(filePath: String) {
        callingBellPath = filePath
    }

    fun updateFloatWindow(enable: Boolean) {
        enableFloatWindow = enable
    }

    fun updateIncomingBanner(enable: Boolean) {
        enableIncomingBanner = enable
    }

    fun updateVirtualBackground(enable: Boolean) {
        enableVirtualBackground = enable
    }

    fun updateAITranscriber(enable: Boolean) {
        if (enableAITranscriber == enable) return
        enableAITranscriber = enable
        preferences.edit().putBoolean(KEY_TRANSCRIPTION_ENABLED, enable).apply()
        transcriptionChanged.tryEmit(Unit)
    }

    fun updateScreenOrientation(orientation: Int) {
        screenOrientation = orientation
    }

    fun updateShowOriginalTranscript(show: Boolean) {
        showOriginalTranscript = show
        if (ccPresetEnabled) {
            preferences.edit().putBoolean(KEY_SHOW_ORIGINAL_TRANSCRIPT, show).apply()
        }
        transcriptionChanged.tryEmit(Unit)
    }

    fun updateTranscriptFontSize(size: TranscriptFontSize) {
        transcriptFontSize = size
        if (ccPresetEnabled) {
            preferences.edit().putString(KEY_TRANSCRIPT_FONT_SIZE, size.storeValue).apply()
        }
        transcriptionChanged.tryEmit(Unit)
    }

    fun updateTranscriptionLanguages(source: String, target: String) {
        transcriptionSourceLanguage = source
        transcriptionTargetLanguage = target
        if (ccPresetEnabled) {
            preferences.edit()
                .putString(KEY_TRANSCRIPTION_SOURCE_LANGUAGE, source)
                .putString(KEY_TRANSCRIPTION_TARGET_LANGUAGE, target)
                .apply()
        }
        transcriptionChanged.tryEmit(Unit)
    }

    fun updateCcPresetEnabled(enabled: Boolean) {
        if (ccPresetEnabled == enabled) return
        ccPresetEnabled = enabled
        val editor = preferences.edit()
        editor.putString(KEY_CC_PRESET_ENABLED, if (enabled) PRESET_ON else PRESET_OFF)
        if (enabled) {
            editor.putBoolean(KEY_SHOW_ORIGINAL_TRANSCRIPT, showOriginalTranscript)
            editor.putString(KEY_TRANSCRIPT_FONT_SIZE, transcriptFontSize.storeValue)
            editor.putString(KEY_TRANSCRIPTION_SOURCE_LANGUAGE, transcriptionSourceLanguage)
            editor.putString(KEY_TRANSCRIPTION_TARGET_LANGUAGE, transcriptionTargetLanguage)
        } else {
            editor.remove(KEY_SHOW_ORIGINAL_TRANSCRIPT)
            editor.remove(KEY_TRANSCRIPT_FONT_SIZE)
            editor.remove(KEY_TRANSCRIPTION_SOURCE_LANGUAGE)
            editor.remove(KEY_TRANSCRIPTION_TARGET_LANGUAGE)
        }
        editor.apply()
        transcriptionChanged.tryEmit(Unit)
    }

    private fun defaultTargetLanguage(): String {
        return if (Locale.getDefault().language == Locale.CHINESE.language) "zh" else "en"
    }

    companion object {
        private const val PREFS_NAME = "com.tencent.callkit.config"
        private const val KEY_MUTE_MODE = "com.tencent.callkit.config.muteMode"
        private const val KEY_TRANSCRIPTION_ENABLED = "com.tencent.callkit.config.transcriptionEnabled"
        private const val KEY_SHOW_ORIGINAL_TRANSCRIPT = "com.tencent.callkit.config.showOriginalTranscript"
        private const val KEY_TRANSCRIPT_FONT_SIZE = "com.tencent.callkit.config.transcriptFontSize"
        private const val KEY_TRANSCRIPTION_SOURCE_LANGUAGE = "com.tencent.callkit.config.transcriptionSourceLanguage"
        private const val KEY_TRANSCRIPTION_TARGET_LANGUAGE = "com.tencent.callkit.config.transcriptionTargetLanguage"
        private const val KEY_CC_PRESET_ENABLED = "com.tencent.callkit.config.ccPresetEnabled"
        private const val DEFAULT_SOURCE_LANGUAGE = "auto"
        private const val PRESET_ON = "1"
        private const val PRESET_OFF = "0"

        @Volatile
        private var instance: CallKitConfig? = null

        fun install(context: Context): CallKitConfig {
            return instance ?: synchronized(this) {
                instance ?: CallKitConfig(context.applicationContext).also { instance = it }
            }
        }

        val shared: CallKitConfig
            get() = checkNotNull(instance) { "TUICallKit.createInstance(context) must be called before using the kit" }
    }
}
