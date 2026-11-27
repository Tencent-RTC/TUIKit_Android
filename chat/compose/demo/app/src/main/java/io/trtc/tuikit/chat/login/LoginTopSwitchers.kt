package io.trtc.tuikit.chat.login

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.theme.ThemeMode
import io.trtc.tuikit.chat.uikit.components.common.getActivityWindow
import io.trtc.tuikit.chat.R
import io.trtc.tuikit.chat.common.AppConstants

@Composable
internal fun LoginTopSwitchers(modifier: Modifier = Modifier) {
    var showThemeSelector by remember { mutableStateOf(false) }
    var showLanguageSelector by remember { mutableStateOf(false) }
    val themeState = LocalTheme.current

    Row(modifier = modifier) {
        SwitcherEntry(
            iconRes = R.drawable.demo_ic_login_theme,
            text = when (themeState.currentMode) {
                ThemeMode.SYSTEM -> stringResource(R.string.compose_demo_theme_system)
                ThemeMode.LIGHT -> stringResource(R.string.compose_demo_theme_light)
                ThemeMode.DARK -> stringResource(R.string.compose_demo_theme_dark)
            },
            contentDescription = stringResource(R.string.compose_demo_theme),
            onClick = { showThemeSelector = true }
        )
        SwitcherEntry(
            iconRes = R.drawable.demo_ic_login_language,
            text = currentLanguageDisplayName(),
            contentDescription = stringResource(R.string.compose_demo_language),
            onClick = { showLanguageSelector = true }
        )
    }

    ActionSheet(
        showThemeSelector, options = listOf(
            ActionItem(text = stringResource(R.string.compose_demo_theme_system), value = ThemeMode.SYSTEM),
            ActionItem(text = stringResource(R.string.compose_demo_theme_light), value = ThemeMode.LIGHT),
            ActionItem(text = stringResource(R.string.compose_demo_theme_dark), value = ThemeMode.DARK)
        ), onDismiss = { showThemeSelector = false }) {
        themeState.setThemeMode(it.value as ThemeMode)
    }

    ActionSheet(
        showLanguageSelector, options = listOf(
            ActionItem(text = stringResource(R.string.compose_demo_zh_hans), value = "zh"),
            ActionItem(text = stringResource(R.string.compose_demo_zh_hant), value = "zh-Hant"),
            ActionItem(text = stringResource(R.string.compose_demo_en), value = "en"),
            ActionItem(text = stringResource(R.string.compose_demo_ar), value = "ar")
        ), onDismiss = { showLanguageSelector = false }) {
        val tag = it.value as String
        MMKV.defaultMMKV().encode(AppConstants.KEY_APP_LANGUAGE, tag)
        val targetLocales = LocaleListCompat.forLanguageTags(tag)
        if (AppCompatDelegate.getApplicationLocales() != targetLocales) {
            AppCompatDelegate.setApplicationLocales(targetLocales)
        }
    }
}

/**
 * Login pages draw a dark hero image behind the status bar, so status bar icons stay
 * light regardless of the theme, while navigation bar icons follow the theme because the
 * bottom of the page is painted with bgColorOperate. Mirrors the View demo's
 * BaseLoginActivity plus appearanceLightStatusBarsOverride() behavior.
 */
@Composable
internal fun SetLoginSystemBarAppearance() {
    val window = LocalContext.current.getActivityWindow() ?: return
    val isDarkMode = LocalTheme.current.isDarkMode
    SideEffect {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = !isDarkMode
    }
}

@Composable
internal fun PrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 17.sp,
    fontWeight: FontWeight? = null,
    horizontalPadding: Dp = 0.dp
) {
    val colors = LocalTheme.current.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) colors.buttonColorPrimaryDefault else colors.buttonColorPrimaryDisabled
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = if (enabled) colors.textColorButton else colors.textColorButtonDisabled,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )
    }
}

@Composable
private fun SwitcherEntry(
    iconRes: Int,
    text: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color.White,
            modifier = Modifier.padding(start = 6.dp)
        )
        Icon(
            painter = painterResource(R.drawable.demo_ic_login_arrow_down),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(10.dp)
        )
    }
}

@Composable
private fun currentLanguageDisplayName(): String {
    val persistedTag = MMKV.defaultMMKV()
        .decodeString(AppConstants.KEY_APP_LANGUAGE, "").orEmpty()
    val currentTag = persistedTag.ifBlank {
        AppCompatDelegate.getApplicationLocales().toLanguageTags()
    }
    return when {
        currentTag.isBlank() -> stringResource(R.string.compose_demo_current_language)
        isTraditionalChinese(currentTag) -> stringResource(R.string.compose_demo_zh_hant)
        currentTag.startsWith("zh", ignoreCase = true) -> stringResource(R.string.compose_demo_zh_hans)
        currentTag.startsWith("en", ignoreCase = true) -> stringResource(R.string.compose_demo_en)
        currentTag.startsWith("ar", ignoreCase = true) -> stringResource(R.string.compose_demo_ar)
        else -> stringResource(R.string.compose_demo_current_language)
    }
}

private fun isTraditionalChinese(languageTag: String): Boolean {
    val normalizedTag = languageTag.lowercase()
    return normalizedTag.contains("hant") ||
        normalizedTag.contains("zh-hk") ||
        normalizedTag.contains("zh-tw") ||
        normalizedTag.contains("zh-mo")
}
