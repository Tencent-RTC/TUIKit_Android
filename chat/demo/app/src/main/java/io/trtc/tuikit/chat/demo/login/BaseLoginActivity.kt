package io.trtc.tuikit.chat.demo.login

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tencent.cloud.tuikit.engine.call.TUICallEngine
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine
import com.tencent.mmkv.MMKV
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.CallingBellFeature
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.CallingVibratorFeature
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.NotificationFeature
import io.trtc.tuikit.atomicx.theme.Theme
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.common.WelcomeMessageSender
import io.trtc.tuikit.chat.demo.customerservice.CustomerServiceManager
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class BaseLoginActivity : BaseActivity() {

    override val requiresLogin: Boolean = false

    protected val themeStore by lazy { ThemeStore.shared(this) }
    private var loginScope: CoroutineScope? = null
    protected var contentReady = false

    private lateinit var themeSwitcher: LinearLayout
    private lateinit var tvThemeValue: TextView
    private lateinit var languageSwitcher: LinearLayout
    private lateinit var tvLanguageValue: TextView

    private val appLanguageOptions by lazy {
        listOf(
            ActionItem(text = getString(R.string.demo_settings_zh_hans), value = "zh"),
            ActionItem(text = getString(R.string.demo_settings_zh_hant), value = "zh-Hant"),
            ActionItem(text = getString(R.string.demo_settings_en), value = "en"),
            ActionItem(text = getString(R.string.demo_settings_ar), value = "ar")
        )
    }

    protected fun setupCommonLoginViews() {
        themeSwitcher = findViewById(R.id.demo_themeSwitcher)
        tvThemeValue = findViewById(R.id.demo_tvThemeValue)
        languageSwitcher = findViewById(R.id.demo_languageSwitcher)
        tvLanguageValue = findViewById(R.id.demo_tvLanguageValue)

        themeSwitcher.setOnClickListener { showThemeSelector() }
        languageSwitcher.setOnClickListener { showLanguageSelector() }
        updateThemeSwitcherLabel()
        updateLanguageSwitcherLabel()
        contentReady = true
    }

    override fun onStart() {
        super.onStart()
        if (!contentReady) {
            return
        }
        loginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        loginScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyThemeColors(state.currentTheme.tokens.color)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        loginScope?.cancel()
        loginScope = null
    }

    abstract fun applyThemeColors(colors: ColorTokens)

    protected fun performLogin(
        sdkAppId: Int,
        userId: String,
        userSig: String,
        loginType: String,
        token: String? = null,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int, String) -> Unit)? = null
    ) {
        LoginStore.shared.login(
            this, sdkAppId, userId, userSig,
            object : CompletionHandler {
                override fun onSuccess() {
                    initCall(sdkAppId, userId, userSig)
                    CustomerServiceManager.initAndStart(this@BaseLoginActivity, sdkAppId, userId, userSig)
                    WelcomeMessageSender.scheduleWelcomeMessage(this@BaseLoginActivity)
                    MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, userId)
                    MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TYPE, loginType)
                    if (token != null) {
                        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TOKEN, token)
                    }
                    onSuccess?.invoke()
                    startActivity(Intent(this@BaseLoginActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    })
                }

                override fun onFailure(code: Int, desc: String) {
                    if (onFailure != null) {
                        onFailure(code, desc)
                    } else {
                        Toast.makeText(
                            this@BaseLoginActivity,
                            getString(R.string.demo_login_failed, desc),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    protected fun createButtonBackground(colors: ColorTokens): StateListDrawable {
        return StateListDrawable().apply {
            addState(
                intArrayOf(-android.R.attr.state_enabled),
                createButtonShape(colors.buttonColorPrimaryDisabled)
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                createButtonShape(colors.buttonColorPrimaryActive)
            )
            addState(intArrayOf(), createButtonShape(colors.buttonColorPrimaryDefault))
        }
    }

    private fun createButtonShape(fillColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = dpToPx(14).toFloat()
        }
    }

    protected fun createButtonTextColors(colors: ColorTokens): ColorStateList {
        return ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(colors.textColorButtonDisabled, colors.textColorButton)
        )
    }

    protected fun updateBackgroundPreservingPadding(view: View, background: Drawable) {
        val paddingStart = view.paddingStart
        val paddingTop = view.paddingTop
        val paddingEnd = view.paddingEnd
        val paddingBottom = view.paddingBottom
        view.background = background
        view.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom)
    }

    protected fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun initCall(sdkAppId: Int, userId: String, userSig: String?) {
        TUICallEngine.createInstance(this).init(
            sdkAppId, userId, userSig,
            object : TUICommonDefine.Callback {
                override fun onSuccess() {
                    Log.i(TAG, "callEngine init success")
                    NotificationFeature(this@BaseLoginActivity).registerNotificationBannerChannel()
                    CallingBellFeature(this@BaseLoginActivity)
                    CallingVibratorFeature(this@BaseLoginActivity)
                    TUICallEngine.createInstance(this@BaseLoginActivity).enableMultiDeviceAbility(true, null)
                    TUICallKit.createInstance(this@BaseLoginActivity).enableIncomingBanner(true)
                }

                override fun onError(errCode: Int, errMsg: String) {
                    Log.e(TAG, "callEngine init failed, errCode: $errCode, errMsg: $errMsg")
                }
            })
    }

    private fun showThemeSelector() {
        val options = listOf(
            ActionItem(text = getString(R.string.demo_settings_theme_system), value = AppConstants.THEME_MODE_SYSTEM),
            ActionItem(text = getString(R.string.demo_settings_theme_light), value = AppConstants.THEME_MODE_LIGHT),
            ActionItem(text = getString(R.string.demo_settings_theme_dark), value = AppConstants.THEME_MODE_DARK)
        )
        ActionSheet.show(this, options) { selected ->
            val mode = selected.value as Int
            when (mode) {
                AppConstants.THEME_MODE_SYSTEM -> themeStore.setTheme(Theme.systemTheme(this))
                AppConstants.THEME_MODE_LIGHT -> themeStore.setTheme(Theme.lightTheme(this))
                AppConstants.THEME_MODE_DARK -> themeStore.setTheme(Theme.darkTheme(this))
            }
            updateThemeSwitcherLabel()
        }
    }

    private fun showLanguageSelector() {
        ActionSheet.show(this, appLanguageOptions) { selected ->
            val tag = selected.value as String
            val targetLocales = LocaleListCompat.forLanguageTags(tag)
            MMKV.defaultMMKV().encode(AppConstants.KEY_APP_LANGUAGE, tag)
            if (AppCompatDelegate.getApplicationLocales() == targetLocales) {
                return@show
            }
            AppCompatDelegate.setApplicationLocales(targetLocales)
        }
    }

    private fun updateThemeSwitcherLabel() {
        tvThemeValue.text = when (themeStore.themeState.value.currentTheme.id) {
            "dark" -> getString(R.string.demo_settings_theme_dark)
            Theme.SYSTEM_THEME_ID -> getString(R.string.demo_settings_theme_system)
            else -> getString(R.string.demo_settings_theme_light)
        }
    }

    private fun updateLanguageSwitcherLabel() {
        tvLanguageValue.text = getCurrentLanguageDisplayName()
    }

    private fun getCurrentLanguageDisplayName(): String {
        val persistedTag = MMKV.defaultMMKV()
            .decodeString(AppConstants.KEY_APP_LANGUAGE, "").orEmpty()
        val currentTag = if (persistedTag.isNotBlank()) {
            persistedTag
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
        return when {
            currentTag.isBlank() -> getString(R.string.demo_settings_current_language)
            isTraditionalChinese(currentTag) -> getString(R.string.demo_settings_zh_hant)
            currentTag.startsWith("zh", ignoreCase = true) -> getString(R.string.demo_settings_zh_hans)
            currentTag.startsWith("en", ignoreCase = true) -> getString(R.string.demo_settings_en)
            currentTag.startsWith("ar", ignoreCase = true) -> getString(R.string.demo_settings_ar)
            else -> getString(R.string.demo_settings_current_language)
        }
    }

    private fun isTraditionalChinese(languageTag: String): Boolean {
        val normalizedTag = languageTag.lowercase()
        return normalizedTag.contains("hant") ||
            normalizedTag.contains("zh-hk") ||
            normalizedTag.contains("zh-tw") ||
            normalizedTag.contains("zh-mo")
    }

    companion object {
        private const val TAG = "BaseLoginActivity"
    }
}
