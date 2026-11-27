package io.trtc.tuikit.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.AvatarSelector
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.TextInputBottomSheet
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.getUserAvatarUrls
import io.trtc.tuikit.atomicxcore.api.login.Gender
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.chat.viewmodels.SelfProfileStore
import io.trtc.tuikit.chat.viewmodels.SettingsViewModel
import io.trtc.tuikit.chat.viewmodels.displayName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfDetailScreen(onDismiss: () -> Unit) {
    val colors = LocalTheme.current.colors
    val settingsViewModel: SettingsViewModel = viewModel()
    val userInfo by settingsViewModel.loginUserInfo.collectAsState()

    val scrollState = rememberScrollState()

    var avatarUrl = userInfo?.avatarURL
    var nickname = userInfo?.nickname ?: ""
    var gender = userInfo?.gender
    var birthdayText = remember(userInfo?.birthday) {
        if (userInfo?.birthday == null) {
            return@remember "1970-01-01"
        }
        return@remember userInfo?.birthday.toString().let {
            try {
                "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)}"
            } catch (e: Exception) {
                "1970-01-01"
            }
        }
    }

    var showAvatarSelector by remember { mutableStateOf(false) }
    var showBirthdaySelector by remember { mutableStateOf(false) }

    var showGenderSelector by remember { mutableStateOf(false) }
    var showNickEditor by remember { mutableStateOf(false) }
    var showStatusEditor by remember { mutableStateOf(false) }
    var signature by remember(userInfo?.selfSignature) {
        mutableStateOf(
            userInfo?.selfSignature ?: ""
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colors.bgColorOperate)
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            SelfDetailHeader(title = stringResource(R.string.compose_demo_self_detail_title)) {
                onDismiss()
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Avatar(
            url = avatarUrl,
            name = userInfo?.displayName ?: "",
            size = AvatarSize.XXL, onClick = {
                showAvatarSelector = true
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = userInfo?.displayName ?: "",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = colors.textColorPrimary
        )
        Spacer(modifier = Modifier.height(36.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsItem(
                title = stringResource(R.string.compose_demo_self_detail_account),
                value = userInfo?.userID ?: "",
                showArrow = false,
                showDivider = true,
            )
            SettingsItem(
                title = stringResource(R.string.compose_demo_self_detail_nickname),
                value = nickname,
                showDivider = true,
                onClick = { showNickEditor = true }
            )

            SettingsItem(
                title = stringResource(R.string.compose_demo_self_detail_status),
                value = signature,
                showDivider = true,
                onClick = { showStatusEditor = true }
            )

            val genderText = when (gender) {
                Gender.MALE -> stringResource(R.string.compose_demo_self_detail_gender_male)
                Gender.FEMALE -> stringResource(R.string.compose_demo_self_detail_gender_female)
                else -> stringResource(R.string.compose_demo_self_detail_gender_secret)
            }
            SettingsItem(
                title = stringResource(R.string.compose_demo_self_detail_gender),
                value = genderText,
                showDivider = true,
                onClick = { showGenderSelector = true }
            )
            SettingsItem(
                title = stringResource(R.string.compose_demo_self_detail_birthday),
                value = birthdayText,
                showDivider = true,
                onClick = { showBirthdaySelector = true }
            )
        }

    }

    ActionSheet(
        isVisible = showGenderSelector,
        options = listOf(
            ActionItem(
                text = stringResource(R.string.compose_demo_self_detail_gender_male),
                value = Gender.MALE
            ),
            ActionItem(
                text = stringResource(R.string.compose_demo_self_detail_gender_female),
                value = Gender.FEMALE
            ),
            ActionItem(
                text = stringResource(R.string.compose_demo_self_detail_gender_secret),
                value = Gender.UNKNOWN
            ),
        ),
        onDismiss = { showGenderSelector = false },
        onActionSelected = { item ->
            showGenderSelector = false
            SelfProfileStore.setSelfInfo(
                UserProfile(gender = item.value as? Gender ?: Gender.UNKNOWN)
            )
        }
    )

    TextInputBottomSheet(
        isVisible = showNickEditor,
        title = stringResource(R.string.compose_demo_self_detail_edit_nickname_title),
        initialText = nickname,
        placeholder = stringResource(R.string.compose_demo_self_detail_edit_nickname_placeholder),
        onDismiss = { showNickEditor = false },
        onConfirm = { text ->
            SelfProfileStore.setSelfInfo(UserProfile(nickname = text))
        }
    )

    TextInputBottomSheet(
        isVisible = showStatusEditor,
        title = stringResource(R.string.compose_demo_self_detail_edit_status_title),
        initialText = signature,
        placeholder = stringResource(R.string.compose_demo_self_detail_edit_status_placeholder),
        maxLength = 100,
        onDismiss = { showStatusEditor = false },
        onConfirm = { text ->
            SelfProfileStore.setSelfInfo(UserProfile(selfSignature = text))
        }
    )

    AvatarSelector(
        showAvatarSelector,
        onDismiss = { showAvatarSelector = false },
        imageUrls = getUserAvatarUrls(),
        onImageSelected = { _, url ->
            SelfProfileStore.setSelfInfo(UserProfile(avatarURL = url))
        })

    if (showBirthdaySelector) {
        FullScreenDialog(onDismissRequest = {
            showBirthdaySelector = false
        }) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = System.currentTimeMillis()
            )
            LaunchedEffect(state.selectedDateMillis) {
                state.selectedDateMillis?.let {
                    val date = Date(it)
                    val formatter = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                    val birthdayStr = formatter.format(date)
                    SelfProfileStore.setSelfInfo(
                        UserProfile(birthday = birthdayStr.toLong())
                    )

                }
            }
            DatePicker(state)
        }
    }
}

@Composable
fun SelfDetailHeader(
    title: String,
    onBackClick: () -> Unit,
) {
    val colors = LocalTheme.current.colors
    Column(modifier = Modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBackClick() }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = colors.textColorLink,
                    modifier = Modifier.size(24.dp)
                )

                Text(
                    text = stringResource(io.trtc.tuikit.chat.uikit.compose.R.string.chat_setting_back),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W400,
                    color = colors.textColorLink
                )
            }

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = colors.textColorPrimary
            )

        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.strokeColorSecondary
        )
    }
}
