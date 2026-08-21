package io.trtc.tuikit.atomicx.theme

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicx.theme.tokens.DesignTokenSet
import io.trtc.tuikit.atomicx.theme.utils.ColorAlgorithm
import io.trtc.tuikit.atomicx.theme.utils.ThemePersistUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Theme(
    val id: String,
    val displayName: String,
    val tokens: DesignTokenSet
) {
    companion object {
        const val SYSTEM_THEME_ID = "system"
        const val LIGHT_THEME_ID = "light"
        const val DARK_THEME_ID = "dark"

        const val SYSTEM_THEME_DISPLAY_NAME = "System"
        const val LIGHT_THEME_DISPLAY_NAME = "Light"
        const val DARK_THEME_DISPLAY_NAME = "Dark"

        fun systemTheme(context: Context): Theme {
            return Theme(
                id = SYSTEM_THEME_ID,
                displayName = SYSTEM_THEME_DISPLAY_NAME,
                tokens = DesignTokenSet.defaultLight(context)
            )
        }

        fun lightTheme(context: Context): Theme {
            return Theme(
                id = LIGHT_THEME_ID,
                displayName = LIGHT_THEME_DISPLAY_NAME,
                tokens = DesignTokenSet.defaultLight(context)
            )
        }

        fun darkTheme(context: Context): Theme {
            return Theme(
                id = DARK_THEME_ID,
                displayName = DARK_THEME_DISPLAY_NAME,
                tokens = DesignTokenSet.defaultDark(context)
            )
        }
    }
}

data class ThemeState(
    val currentTheme: Theme
)

class ThemeStore private constructor(context: Context) {
    private val appContext: Context = context.applicationContext
    private val themePersistUtil: ThemePersistUtil = ThemePersistUtil(appContext)

    private var lastKnownSystemDarkMode: Boolean = isSystemInDarkMode()
    private var followSystemTheme: Boolean = true

    private val _themeState = MutableStateFlow(ThemeState(resolvePersistedTheme()))
    val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()

    init {
        registerSystemAppearanceObserver()
    }

    companion object {
        private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

        @Volatile
        private var instance: ThemeStore? = null

        fun shared(context: Context): ThemeStore {
            return instance ?: synchronized(this) {
                instance ?: ThemeStore(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    fun followSystemTheme(enable: Boolean) {
        followSystemTheme = enable
        updateThemeState(resolvePersistedTheme())
    }

    fun setTheme(theme: Theme) {
        themePersistUtil.setCurrentThemeId(theme.id)
        val resolved = when (theme.id) {
            Theme.SYSTEM_THEME_ID -> resolveSystemTheme()
            Theme.DARK_THEME_ID -> applyPersistedPrimaryColor(theme, isDark = true)
            else -> applyPersistedPrimaryColor(theme, isDark = false)
        }
        updateThemeState(resolved)
    }

    fun setPrimaryColor(hexColor: String) {
        if (!hexColor.matches(HEX_COLOR_REGEX)) {
            return
        }
        val current = _themeState.value.currentTheme
        val newColor = buildPrimaryColorTokens(hexColor, isCurrentThemeDark())
        updateThemeState(current.copy(tokens = current.tokens.copy(color = newColor)))
        themePersistUtil.setCustomPrimaryColor(hexColor)
    }

    private fun resolvePersistedTheme(): Theme {
        if (!followSystemTheme) {
            val theme =  Theme.darkTheme(appContext)
            applyPersistedPrimaryColor(theme, true)
            return theme
        }
        return when (themePersistUtil.getCurrentThemeId()) {
            Theme.LIGHT_THEME_ID -> resolvePresetTheme(isDark = false)
            Theme.DARK_THEME_ID -> resolvePresetTheme(isDark = true)
            else -> resolveSystemTheme()
        }
    }

    private fun resolvePresetTheme(isDark: Boolean): Theme {
        return applyPersistedPrimaryColor(baseTheme(isDark), isDark)
    }

    private fun resolveSystemTheme(): Theme {
        val isDark = isSystemInDarkMode()
        val systemTheme = baseTheme(isDark).copy(
            id = Theme.SYSTEM_THEME_ID,
            displayName = Theme.SYSTEM_THEME_DISPLAY_NAME
        )
        return applyPersistedPrimaryColor(systemTheme, isDark)
    }

    private fun applyPersistedPrimaryColor(theme: Theme, isDark: Boolean): Theme {
        val hexColor = themePersistUtil.getCustomPrimaryColor() ?: return theme
        return theme.copy(tokens = theme.tokens.copy(color = buildPrimaryColorTokens(hexColor, isDark)))
    }

    private fun buildPrimaryColorTokens(hexColor: String, isDark: Boolean): ColorTokens {
        val palette = ColorAlgorithm.generateColorPalette(
            appContext,
            hexColor,
            if (isDark) Theme.DARK_THEME_ID else Theme.LIGHT_THEME_ID
        )
        return if (isDark) {
            ColorTokens.generateDarkTokens(appContext, palette)
        } else {
            ColorTokens.generateLightTokens(appContext, palette)
        }
    }

    private fun baseTheme(isDark: Boolean): Theme {
        return if (isDark) Theme.darkTheme(appContext) else Theme.lightTheme(appContext)
    }

    private fun isCurrentThemeDark(): Boolean {
        val current = _themeState.value.currentTheme
        return if (current.id == Theme.SYSTEM_THEME_ID) {
            isSystemInDarkMode()
        } else {
            current.id == Theme.DARK_THEME_ID
        }
    }

    private fun updateThemeState(theme: Theme) {
        _themeState.value = ThemeState(currentTheme = theme)
    }

    private fun isSystemInDarkMode(): Boolean {
        val uiMode = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun registerSystemAppearanceObserver() {
        // Bound to the application context for the whole process lifetime; intentionally not unregistered.
        appContext.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                if (!followSystemTheme) {
                    return
                }
                val isDark = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
                if (isDark == lastKnownSystemDarkMode) {
                    return
                }
                lastKnownSystemDarkMode = isDark
                if (_themeState.value.currentTheme.id == Theme.SYSTEM_THEME_ID) {
                    updateThemeState(resolveSystemTheme())
                }
            }

            override fun onLowMemory() {}
        })
    }
}
