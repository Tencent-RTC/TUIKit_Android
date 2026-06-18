package com.tencent.rtcube.v2.login.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.views.FullScreenLoading
import com.tencent.rtcube.v2.login.components.views.LoginButton
import com.tencent.rtcube.v2.login.components.views.LoginTextField
import com.tencent.rtcube.v2.login.profile.store.ProfileState

@Composable
fun ProfileScreen(
    state: ProfileState,
    onNicknameChange: (String) -> Unit,
    onAvatarClick: () -> Unit,
    onAvatarSelect: (String) -> Unit,
    onAvatarConfirm: () -> Unit,
    onAvatarDialogDismiss: () -> Unit,
    onRegister: () -> Unit,
    onToastShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val canRegister = state.nickname.isNotEmpty() && !state.isLoading

    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage.isNotEmpty()) {
            Toast.makeText(context, state.toastMessage, Toast.LENGTH_SHORT).show()
            onToastShown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.login_color_white))
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.login_profile_title),
                color = colorResource(R.color.login_main_text),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = state.avatarUrl.ifEmpty { null },
                    placeholder = painterResource(id = R.drawable.login_ic_head),
                    error = painterResource(id = R.drawable.login_ic_head),
                    contentDescription = stringResource(R.string.login_profile_avatar_title),
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, colorResource(R.color.login_input_border), CircleShape)
                        .clickable { onAvatarClick() },
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                LoginTextField(
                    value = state.nickname,
                    onValueChange = onNicknameChange,
                    placeholder = stringResource(R.string.login_profile_nickname_hint),
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                    onImeAction = { if (canRegister) onRegister() },
                    maxLength = 20,
                )

                Spacer(modifier = Modifier.height(10.dp))

                val tipColor = if (state.isNicknameValid) {
                    colorResource(R.color.login_text_hint)
                } else {
                    colorResource(R.color.login_color_red_light6)
                }
                Text(
                    text = stringResource(R.string.login_profile_nickname_limit),
                    color = tipColor,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(30.dp))

                LoginButton(
                    text = stringResource(R.string.login_profile_title),
                    onClick = onRegister,
                    enabled = canRegister
                )
            }
        }

        if (state.isAvatarDialogVisible) {
            AvatarSelectDialog(
                selectedAvatarUrl = state.avatarUrl,
                onAvatarSelect = onAvatarSelect,
                onConfirm = onAvatarConfirm,
                onDismiss = onAvatarDialogDismiss,
            )
        }

        if (state.isLoading) {
            FullScreenLoading(message = stringResource(R.string.login_status_logging_in))
        }
    }
}