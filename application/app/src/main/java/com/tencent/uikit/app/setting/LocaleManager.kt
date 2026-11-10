package com.tencent.uikit.app.setting

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.tencent.mmkv.MMKV
import java.util.Locale

/**
 * 使用 Android 系统 API 管理 App 语言偏好，不依赖任何三方库。
 * 语言偏好持久化到 SharedPreferences，并在 attachBaseContext 中覆写 Context 的 Locale，
 * 使 App 启动后即可生效。
 */
object LocaleManager {
    const val LANGUAGE_ZH = "zh"
    const val LANGUAGE_EN = "en"
    private const val KEY_LANGUAGE = "language"
    private const val DEFAULT_LANGUAGE = LANGUAGE_EN

    fun getLanguage(): String {
        return MMKV.defaultMMKV().getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    fun setLanguage(language: String) {
        MMKV.defaultMMKV().putString(KEY_LANGUAGE, language).apply()
    }

    /** 根据保存的语言构建一个新的 Context，覆写其 Configuration 的 Locale */
    fun attachLanguageContext(context: Context): Context {
        val language = getLanguage()
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            val localeList = android.os.LocaleList(locale)
            config.setLocales(localeList)
            return context.createConfigurationContext(config)
        } else {
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return context
        }
    }
}
