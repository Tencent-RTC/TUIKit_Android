package io.trtc.tuikit.chat

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.AllowType
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.chat.common.AppConstants
import io.trtc.tuikit.chat.common.DemoTabState
import io.trtc.tuikit.chat.login.LoginActivity
import io.trtc.tuikit.chat.settings.AboutActivity
import io.trtc.tuikit.chat.settings.PrimaryColorPickerDialog
import io.trtc.tuikit.chat.settings.VoiceMessageSettingActivity
import io.trtc.tuikit.chat.settings.normalizeHex
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.theme.ThemeMode
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog
import io.trtc.tuikit.chat.uikit.components.widgets.Switch
import io.trtc.tuikit.chat.viewmodels.SelfProfileStore
import io.trtc.tuikit.chat.viewmodels.SettingsViewModel
import io.trtc.tuikit.chat.viewmodels.displayName

private const val DEFAULT_PRIMARY_COLOR = "#1C66E5"
private val PRIMARY_COLOR_PREVIEW_SIZE = 22.dp
private val PRIMARY_COLOR_PREVIEW_STROKE = 1.5.dp
private val SETTINGS_GROUP_SPACER = 10.dp
private val SETTINGS_DIVIDER_THICKNESS = 0.5.dp
private val SETTINGS_ITEM_MIN_HEIGHT = 48.dp
private val LOGOUT_CORNER_RADIUS = 8.dp

@Composable
fun SettingsScreen() {
    val themeState = LocalTheme.current
    val colors = themeState.colors
    val activity = LocalActivity.current
    var showSelfDetailDialog by remember { mutableStateOf(false) }
    val settingsViewModel: SettingsViewModel = viewModel()
    val userInfo by settingsViewModel.loginUserInfo.collectAsState()
    val enableReadReceipt by settingsViewModel.enableReadReceipt.collectAsState()
    val translateTargetLanguage by settingsViewModel.translateTargetLanguage.collectAsState()
    var showFriendAddOpt by remember { mutableStateOf(false) }
    var showThemeSelector by remember { mutableStateOf(false) }
    var showLanguageSelector by remember { mutableStateOf(false) }
    var showTranslateLanguageSelector by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showCallsTab by remember {
        mutableStateOf(MMKV.defaultMMKV().decodeBool(AppConstants.KEY_SHOW_CALLS_TAB, true))
    }
    val displayName = userInfo?.displayName ?: ""
    val userId = userInfo?.userID ?: ""
    val selfSignature = userInfo?.selfSignature ?: ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colors.bgColorTopBar)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = colors.bgColorOperate)
                        .clickable { showSelfDetailDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(
                        url = userInfo?.avatarURL,
                        name = displayName,
                        size = AvatarSize.L,
                        onClick = { showSelfDetailDialog = true }
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            color = colors.textColorPrimary
                        )
                        Text(
                            text = "ID：$userId",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Start,
                            color = colors.textColorTertiary
                        )
                        Text(
                            text = "${stringResource(R.string.compose_demo_self_detail_status)}：$selfSignature",
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            color = colors.textColorTertiary
                        )
                    }
                }

                SettingsGroupSpacer()

                SettingsGroup {
                    SettingsItem(
                        title = stringResource(R.string.compose_demo_theme),
                        value = getThemeString(themeState.currentMode),
                        showDivider = true,
                        onClick = { showThemeSelector = true }
                    )
                    SettingsItem(
                        title = stringResource(R.string.compose_demo_primary_color),
                        value = "",
                        showDivider = true,
                        onClick = { showColorPicker = true },
                        trailingContent = {
                            PrimaryColorPreview(
                                hex = themeState.currentPrimaryColor ?: DEFAULT_PRIMARY_COLOR
                            )
                        }
                    )
                    SettingsItem(
                        title = stringResource(R.string.compose_demo_language),
                        value = currentLanguageDisplayName(),
                        showDivider = false,
                        onClick = { showLanguageSelector = true }
                    )
                }

                SettingsGroupSpacer()

                SettingsGroup {
                    SettingsItem(
                        title = stringResource(R.string.compose_demo_add_rule),
                        value = getFriendAddOptString(userInfo?.allowType),
                        showDivider = true,
                        onClick = { showFriendAddOpt = true }
                    )
                    ReadReceiptToggleItem(
                        enabled = enableReadReceipt,
                        showDivider = true,
                        onToggle = { newValue ->
                            settingsViewModel.updateReadReceiptEnabled(newValue)
                        }
                    )
                    SettingsItem(
                        title = stringResource(R.string.compose_demo_translate_target_language),
                        value = settingsViewModel.getTranslateLanguageDisplayName(translateTargetLanguage),
                        showDivider = false,
                        onClick = { showTranslateLanguageSelector = true }
                    )
                }

                SettingsGroupSpacer()

                SettingsGroup {
                    SettingsItem(
                        title = stringResource(R.string.compose_demo_voice_message_settings),
                        value = "",
                        showDivider = false,
                        onClick = {
                            activity?.let { VoiceMessageSettingActivity.start(it) }
                        }
                    )
                }

                SettingsGroupSpacer()

                SettingsGroup {
                    SettingsItem(
                        title = stringResource(R.string.compose_demo_about_im),
                        value = "",
                        showDivider = false,
                        onClick = {
                            activity?.let { AboutActivity.start(it) }
                        }
                    )
                }

                SettingsGroupSpacer()

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LOGOUT_CORNER_RADIUS))
                        .background(color = colors.bgColorInput)
                        .clickable {
                            LoginStore.shared.logout(object : CompletionHandler {
                                override fun onSuccess() {
                                    MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, "")
                                    MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TOKEN, "")
                                    MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TYPE, "")
                                    activity?.startActivity(Intent(activity, LoginActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    })
                                    activity?.finish()
                                }

                                override fun onFailure(code: Int, desc: String) {
                                }
                            })
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.compose_demo_logout),
                        fontSize = 16.sp,
                        color = colors.textColorError
                    )
                }
            }
        }

        ActionSheet(
            showFriendAddOpt, options = listOf(
                ActionItem(
                    text = stringResource(R.string.compose_demo_allow_type_allow_any),
                    value = AllowType.ALLOW_ANY
                ),
                ActionItem(
                    text = stringResource(R.string.compose_demo_allow_type_deny_any),
                    value = AllowType.DENY_ANY
                ),
                ActionItem(
                    text = stringResource(R.string.compose_demo_allow_type_need_confirm),
                    value = AllowType.NEED_CONFIRM
                ),
            ), onDismiss = { showFriendAddOpt = false }) {
            val userProfile = UserProfile().apply {
                allowType = it.value as? AllowType ?: AllowType.NEED_CONFIRM
            }
            SelfProfileStore.setSelfInfo(userProfile, object : CompletionHandler {
                override fun onSuccess() {
                }

                override fun onFailure(code: Int, desc: String) {
                }
            })
        }

        ActionSheet(
            isVisible = showThemeSelector,
            options = listOf(
                ActionItem(text = getThemeString(ThemeMode.SYSTEM), value = ThemeMode.SYSTEM),
                ActionItem(text = getThemeString(ThemeMode.LIGHT), value = ThemeMode.LIGHT),
                ActionItem(text = getThemeString(ThemeMode.DARK), value = ThemeMode.DARK),
            ),
            onDismiss = { showThemeSelector = false }
        ) {
            themeState.setThemeMode(it.value as ThemeMode)
        }

        if (showColorPicker) {
            PrimaryColorPickerDialog(
                selectedHex = themeState.currentPrimaryColor ?: DEFAULT_PRIMARY_COLOR,
                onDismiss = { showColorPicker = false },
                onColorSelected = { hex ->
                    themeState.setPrimaryColor(hex)
                    AppBuilderConfig.primaryColor = hex
                }
            )
        }

        ActionSheet(
            showLanguageSelector, options = listOf(
                ActionItem(text = stringResource(R.string.compose_demo_zh_hans), value = "zh"),
                ActionItem(text = stringResource(R.string.compose_demo_zh_hant), value = "zh-Hant"),
                ActionItem(text = stringResource(R.string.compose_demo_en), value = "en"),
                ActionItem(text = stringResource(R.string.compose_demo_ar), value = "ar"),
            ), onDismiss = { showLanguageSelector = false }) {
            val tag = it.value.toString()
            val targetLocales = LocaleListCompat.forLanguageTags(tag)
            MMKV.defaultMMKV().encode(AppConstants.KEY_APP_LANGUAGE, tag)
            if (AppCompatDelegate.getApplicationLocales() == targetLocales) {
                return@ActionSheet
            }
            AppCompatDelegate.setApplicationLocales(targetLocales)
            val viewModelStore = (activity as AppCompatActivity).viewModelStore
            viewModelStore.clear()
            activity.recreate()
        }

        ActionSheet(
            showTranslateLanguageSelector,
            options = settingsViewModel.translateLanguageOptions.map { option ->
                ActionItem(text = option.name, value = option.code)
            },
            onDismiss = { showTranslateLanguageSelector = false }
        ) {
            settingsViewModel.updateTranslateTargetLanguage(it.value.toString())
        }
    }

    if (showSelfDetailDialog) {
        FullScreenDialog(onDismissRequest = { showSelfDetailDialog = false }) {
            SelfDetailScreen(onDismiss = { showSelfDetailDialog = false })
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalTheme.current.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colors.bgColorOperate),
        content = content
    )
}

@Composable
private fun SettingsGroupSpacer() {
    val colors = LocalTheme.current.colors
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(SETTINGS_GROUP_SPACER)
            .background(color = colors.bgColorTopBar)
    )
}

@Composable
private fun SettingsDivider() {
    val colors = LocalTheme.current.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SETTINGS_DIVIDER_THICKNESS)
            .background(color = colors.strokeColorPrimary)
    )
}

@Composable
fun SettingsItem(
    title: String,
    value: String,
    showDivider: Boolean,
    showArrow: Boolean = true,
    onClick: () -> Unit = {},
    trailingContent: (@Composable () -> Unit)? = null
) {
    val colors = LocalTheme.current.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SETTINGS_ITEM_MIN_HEIGHT)
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                maxLines = 1,
                textAlign = TextAlign.Start,
                color = colors.textColorSecondary
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (trailingContent != null) {
                    trailingContent()
                } else if (value.isNotEmpty()) {
                    Text(
                        text = value,
                        fontSize = 16.sp,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.textColorPrimary
                    )
                }
            }
            if (showArrow) {
                Icon(
                    painter = painterResource(id = R.drawable.demo_ic_arrow_right),
                    contentDescription = null,
                    tint = colors.textColorTertiary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .width(7.dp)
                        .height(12.dp)
                )
            }
        }
        if (showDivider) {
            SettingsDivider()
        }
    }
}

@Composable
private fun PrimaryColorPreview(hex: String) {
    val colors = LocalTheme.current.colors
    val previewColor = remember(hex) {
        val parsed = runCatching {
            android.graphics.Color.parseColor(normalizeHex(hex))
        }.getOrElse {
            android.graphics.Color.parseColor(DEFAULT_PRIMARY_COLOR)
        }
        Color(parsed)
    }
    Box(
        modifier = Modifier
            .size(PRIMARY_COLOR_PREVIEW_SIZE)
            .clip(CircleShape)
            .background(previewColor)
            .border(PRIMARY_COLOR_PREVIEW_STROKE, colors.strokeColorPrimary, CircleShape)
    )
}

@Composable
fun getFriendAddOptString(allowType: AllowType?): String {
    return when (allowType) {
        AllowType.ALLOW_ANY -> stringResource(R.string.compose_demo_allow_type_allow_any)
        AllowType.DENY_ANY -> stringResource(R.string.compose_demo_allow_type_deny_any)
        AllowType.NEED_CONFIRM -> stringResource(R.string.compose_demo_allow_type_need_confirm)
        else -> stringResource(R.string.compose_demo_allow_type_need_confirm)
    }
}

@Composable
fun getThemeString(themeScheme: ThemeMode): String {
    return when (themeScheme) {
        ThemeMode.SYSTEM -> stringResource(R.string.compose_demo_theme_system)
        ThemeMode.DARK -> stringResource(R.string.compose_demo_theme_dark)
        ThemeMode.LIGHT -> stringResource(R.string.compose_demo_theme_light)
    }
}

@Composable
private fun currentLanguageDisplayName(): String {
    val persistedTag = MMKV.defaultMMKV().decodeString(AppConstants.KEY_APP_LANGUAGE, "").orEmpty()
    val currentTag = if (persistedTag.isNotBlank()) {
        persistedTag
    } else {
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

@Composable
fun ReadReceiptToggleItem(
    enabled: Boolean,
    showDivider: Boolean = false,
    onToggle: (Boolean) -> Unit
) {
    val colors = LocalTheme.current.colors
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.compose_demo_message_read_receipt),
                    fontSize = 16.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                    color = colors.textColorSecondary,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = getReadReceiptDescription(enabled),
                fontSize = 12.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Start,
                color = colors.textColorTertiary
            )
        }
        if (showDivider) {
            SettingsDivider()
        }
    }
}

@Composable
fun getReadReceiptDescription(enabled: Boolean): String {
    return if (enabled) {
        stringResource(R.string.compose_demo_message_read_receipt_enabled_desc)
    } else {
        stringResource(R.string.compose_demo_message_read_receipt_disabled_desc)
    }
}

@Composable
fun ShowCallsTabToggleItem(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val colors = LocalTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ITEM_MIN_HEIGHT)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.compose_demo_settings_show_calls),
            fontSize = 16.sp,
            maxLines = 1,
            textAlign = TextAlign.Start,
            color = colors.textColorSecondary,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
