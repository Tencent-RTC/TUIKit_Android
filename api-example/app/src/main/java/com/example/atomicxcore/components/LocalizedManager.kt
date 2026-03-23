package com.example.atomicxcore.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import com.example.atomicxcore.R
import com.example.atomicxcore.scenes.login.LoginActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * Localization manager
 * Supports switching between Simplified Chinese and English
 * Follows the system language by default: zh-CN for Chinese systems, English for others
 * Corresponds to iOS's LocalizedManager
 */
object LocalizedManager {

    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    /**
     * Current language, follows the system language by default
     */
    fun getCurrentLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, null) ?: getDefaultLanguage()
    }

    /**
     * Default language: zh for Simplified Chinese systems, English for others
     */
    private fun getDefaultLanguage(): String {
        val systemLang = Locale.getDefault().language
        return if (systemLang == "zh") "zh" else "en"
    }

    val isChinese: Boolean
        get() {
            val systemLang = Locale.getDefault().language
            return systemLang == "zh"
        }

    /**
     * Switch language and restart the Activity
     */
    fun switchLanguage(context: Context) {
        val current = getCurrentLanguage(context)
        val newLang = if (current == "zh") "en" else "zh"
        setLanguage(context, newLang)
    }

    private fun setLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()

        // Restart to the login page
        restartApp(context)
    }

    /**
     * Apply language configuration to Context (called in attachBaseContext)
     */
    fun applyLanguage(context: Context): Context {
        val language = getCurrentLanguage(context)
        val locale = if (language == "zh") Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * Restart the app after switching language (navigate to the login page)
     */
    private fun restartApp(context: Context) {
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
        if (context is Activity) {
            context.finish()
        }
    }

    /**
     * Show the language switch dialog
     * Corresponds to iOS's showLanguageSwitchAlert
     */
    fun showLanguageSwitchDialog(activity: Activity) {
        val items = arrayOf("简体中文", "English")
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.featureList_language))
            .setItems(items) { _, which ->
                val newLang = if (which == 0) "zh" else "en"
                setLanguage(activity, newLang)
            }
            .setNegativeButton(activity.getString(R.string.common_cancel), null)
            .show()
    }
}
