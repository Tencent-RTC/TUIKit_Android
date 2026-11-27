package io.trtc.tuikit.chat.viewmodels

import androidx.lifecycle.ViewModel
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val KEY_ENABLE_READ_RECEIPT = "enable_read_receipt"

data class TranslateLanguageOption(
    val code: String,
    val name: String
)

class SettingsViewModel : ViewModel() {
    val loginUserInfo = SelfProfileStore.profile

    private val _enableReadReceipt = MutableStateFlow(AppBuilderConfig.enableReadReceipt)
    val enableReadReceipt: StateFlow<Boolean> = _enableReadReceipt.asStateFlow()

    private val _translateTargetLanguage = MutableStateFlow(AppBuilderConfig.translateTargetLanguage)
    val translateTargetLanguage: StateFlow<String> = _translateTargetLanguage.asStateFlow()

    val translateLanguageOptions: List<TranslateLanguageOption> = listOf(
        TranslateLanguageOption("zh", "简体中文"),
        TranslateLanguageOption("zh-TW", "繁體中文"),
        TranslateLanguageOption("en", "English"),
        TranslateLanguageOption("ja", "日本語"),
        TranslateLanguageOption("ko", "한국어"),
        TranslateLanguageOption("fr", "Français"),
        TranslateLanguageOption("es", "Español"),
        TranslateLanguageOption("it", "Italiano"),
        TranslateLanguageOption("de", "Deutsch"),
        TranslateLanguageOption("tr", "Türkçe"),
        TranslateLanguageOption("ru", "Русский"),
        TranslateLanguageOption("pt", "Português"),
        TranslateLanguageOption("vi", "Tiếng Việt"),
        TranslateLanguageOption("id", "Bahasa Indonesia"),
        TranslateLanguageOption("th", "ภาษาไทย"),
        TranslateLanguageOption("ms", "Bahasa Melayu"),
        TranslateLanguageOption("hi", "हिन्दी")
    )

    fun updateReadReceiptEnabled(enabled: Boolean) {
        AppBuilderConfig.enableReadReceipt = enabled
        _enableReadReceipt.value = enabled
        MMKV.defaultMMKV().encode(KEY_ENABLE_READ_RECEIPT, enabled)
    }

    fun updateTranslateTargetLanguage(languageCode: String) {
        AppBuilderConfig.translateTargetLanguage = languageCode
        _translateTargetLanguage.value = languageCode
    }

    fun getTranslateLanguageDisplayName(code: String): String {
        return translateLanguageOptions.find { it.code == code }?.name ?: code
    }
}

val UserProfile.displayName: String
    get() = when {
        !nickname.isNullOrEmpty() -> nickname
        else -> userID
    }.toString()
