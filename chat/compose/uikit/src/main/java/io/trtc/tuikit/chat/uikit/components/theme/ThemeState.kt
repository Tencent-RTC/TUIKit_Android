package io.trtc.tuikit.chat.uikit.components.theme

import android.os.Parcelable
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.common.appContext
import io.trtc.tuikit.atomicx.theme.Theme
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicx.theme.tokens.FontTokens
import io.trtc.tuikit.atomicx.theme.utils.ThemePersistUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

@Parcelize
@Serializable
data class ThemeConfig(
    val mode: ThemeMode,
    val primaryColor: String?
) : Parcelable

val LocalTheme = compositionLocalOf { ThemeState.shared }

/**
 * Compose-facing facade over [ThemeStore], the theme source shared with the View UIKit.
 * Colors and fonts are projected from the store's token set, while radii and spacings use
 * the Compose UIKit's local schemes.
 */
class ThemeState {
    companion object {
        private const val TAG = "ThemeState"
        private const val LEGACY_MMKV_ID = "BaseComponentID"
        private const val LEGACY_THEME_KEY = "BaseComponentThemeKey"
        private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
        private val json = Json { ignoreUnknownKeys = true }
        val shared = ThemeState()
    }

    private constructor()

    private val themeStore = ThemeStore.shared(appContext)
    private val persistUtil = ThemePersistUtil(appContext)

    /**
     * Snapshot mirror of [ThemeStore.themeState]. Reading it from a composition subscribes
     * that composition to theme changes, which is what keeps the ~120 `LocalTheme.current`
     * call sites recomposing without any of them having to collect the flow themselves.
     */
    private val storeState = mutableStateOf(themeStore.themeState.value)

    /**
     * [ThemeStore.themeState] carries the resolved tokens but not the primary color that
     * produced them, so it is tracked separately and re-read on every store emission to stay
     * in step with changes made from the View side.
     */
    private val primaryColorState = mutableStateOf(persistUtil.getCustomPrimaryColor())

    private var cachedColorTokens: ColorTokens? = null
    private var cachedColorScheme: ColorScheme? = null
    private var cachedFontTokens: FontTokens? = null
    private var cachedFontScheme: FontScheme? = null

    init {
        MMKV.initialize(appContext)
        migrateLegacyPersistenceIfNeeded()
        syncFromStore()
        CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()).launch {
            themeStore.themeState.collect { syncFromStore() }
        }
    }

    private fun syncFromStore() {
        storeState.value = themeStore.themeState.value
        primaryColorState.value = persistUtil.getCustomPrimaryColor()
    }

    val currentTheme: ThemeConfig
        get() = ThemeConfig(currentMode, currentPrimaryColor)

    /**
     * Mirrors how [ThemeStore] itself classifies ids: only the two fixed presets map to a
     * mode, everything else (the system preset and any app-defined theme) reports SYSTEM.
     */
    val currentMode: ThemeMode
        get() = when (storeState.value.currentTheme.id) {
            Theme.LIGHT_THEME_ID -> ThemeMode.LIGHT
            Theme.DARK_THEME_ID -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }

    val currentThemeId: String
        get() = storeState.value.currentTheme.id

    val currentPrimaryColor: String?
        get() = primaryColorState.value

    val hasCustomPrimaryColor: Boolean
        get() = currentPrimaryColor != null

    val isDarkMode: Boolean
        @Composable
        get() = when (currentMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

    fun setThemeMode(mode: ThemeMode) {
        themeStore.setTheme(presetTheme(mode))
    }

    /**
     * Applies an app-defined theme, which is what lifts this module beyond the three presets:
     * [ThemeStore] accepts any [Theme], and its color and font tokens land here through
     * [colors] and [fonts].
     */
    fun setTheme(theme: Theme) {
        themeStore.setTheme(theme)
    }

    fun setPrimaryColor(hexColor: String) {
        if (!hexColor.matches(HEX_COLOR_REGEX)) {
            Log.w(TAG, "Invalid hex color format: $hexColor")
            return
        }
        themeStore.setPrimaryColor(hexColor)
        primaryColorState.value = hexColor
    }

    fun clearPrimaryColor() {
        persistUtil.clearCustomPrimaryColor()
        primaryColorState.value = null
        // ThemeStore exposes no clear entry point, so the current theme is re-applied from a
        // freshly built preset: with nothing persisted its primary color overlay is a no-op,
        // which yields the untinted token set.
        val current = storeState.value.currentTheme
        val refreshed = when (current.id) {
            Theme.LIGHT_THEME_ID -> Theme.lightTheme(appContext)
            Theme.DARK_THEME_ID -> Theme.darkTheme(appContext)
            Theme.SYSTEM_THEME_ID -> Theme.systemTheme(appContext)
            else -> current
        }
        themeStore.setTheme(refreshed)
    }

    private fun presetTheme(mode: ThemeMode): Theme = when (mode) {
        ThemeMode.LIGHT -> Theme.lightTheme(appContext)
        ThemeMode.DARK -> Theme.darkTheme(appContext)
        ThemeMode.SYSTEM -> Theme.systemTheme(appContext)
    }

    val colors: ColorScheme
        @Composable get() = colorSchemeFor(storeState.value.currentTheme.tokens.color)

    val fonts: FontScheme
        @Composable get() = fontSchemeFor(storeState.value.currentTheme.tokens.font)

    val radius: RadiusScheme
        @Composable get() = DefaultRadiusScheme

    val spacings: SpacingScheme
        @Composable get() = DefaultSpacingScheme

    /**
     * Converts once per token set rather than once per call site: every reader sees the same
     * [ColorTokens] instance, so a single-entry cache collapses ~120 conversions into one.
     */
    private fun colorSchemeFor(tokens: ColorTokens): ColorScheme {
        cachedColorScheme?.let { if (cachedColorTokens == tokens) return it }
        val scheme = tokens.toColorScheme()
        cachedColorTokens = tokens
        cachedColorScheme = scheme
        return scheme
    }

    private fun fontSchemeFor(tokens: FontTokens): FontScheme {
        cachedFontScheme?.let { if (cachedFontTokens == tokens) return it }
        val scheme = tokens.toFontScheme()
        cachedFontTokens = tokens
        cachedFontScheme = scheme
        return scheme
    }

    /**
     * Hands the theme picked before this module shared [ThemeStore] over to the store, so
     * that an upgrading user keeps their selection instead of silently falling back to
     * SYSTEM. When nothing was ever persisted, [AppBuilderConfig] seeds the first launch.
     */
    private fun migrateLegacyPersistenceIfNeeded() {
        val mmkv = MMKV.mmkvWithID(LEGACY_MMKV_ID)
        val legacyJson = mmkv.decodeString(LEGACY_THEME_KEY)
        if (!legacyJson.isNullOrEmpty()) {
            try {
                val legacy = json.decodeFromString(ThemeConfig.serializer(), legacyJson)
                setThemeMode(legacy.mode)
                legacy.primaryColor?.let { themeStore.setPrimaryColor(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate legacy theme: ${e.message}")
            }
            mmkv.removeValueForKey(LEGACY_THEME_KEY)
            return
        }

        if (persistUtil.getCurrentThemeId() != null) {
            return
        }
        setThemeMode(AppBuilderConfig.themeMode)
        if (AppBuilderConfig.primaryColor.matches(HEX_COLOR_REGEX)) {
            themeStore.setPrimaryColor(AppBuilderConfig.primaryColor)
        }
    }
}
