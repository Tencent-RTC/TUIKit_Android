package com.tencent.rtcube.v2.login.hiddenconfig

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.views.FullScreenLoading
import com.tencent.rtcube.v2.login.components.views.LoginButton
import com.tencent.rtcube.v2.login.components.views.LoginTextField
import com.tencent.rtcube.v2.login.components.views.LoginTopBackground
import com.tencent.rtcube.v2.login.hiddenconfig.store.HiddenConfigState

@Composable
fun HiddenConfigScreen(
    state: HiddenConfigState,
    onSdkAppIdChange: (String) -> Unit,
    onUserIdChange: (String) -> Unit,
    onUserSigChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onRestoreDefault: () -> Unit,
    onBack: () -> Unit,
    onScanQRCode: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.login_color_white))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LoginTopBackground()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                QrScanCard(onClick = onScanQRCode)

                Spacer(modifier = Modifier.height(24.dp))

                LoginTextField(
                    value = state.sdkAppId,
                    onValueChange = onSdkAppIdChange,
                    placeholder = "SDKAppID",
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.login_ic_sdkappid),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                LoginTextField(
                    value = state.userId,
                    onValueChange = onUserIdChange,
                    placeholder = "UserID",
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.login_ic_user_id),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                LoginTextField(
                    value = state.userSig,
                    onValueChange = onUserSigChange,
                    placeholder = "UserSig",
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.login_ic_user_sig),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                )

                Spacer(modifier = Modifier.height(40.dp))

                LoginButton(
                    text = stringResource(R.string.login_hidden_config_confirm_switch),
                    onClick = onConfirm,
                    enabled = state.isConfirmEnabled
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.login_hidden_config_restore_default),
                        color = colorResource(R.color.login_color_blue),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onRestoreDefault() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 8.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "‹",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (isLoading) {
            FullScreenLoading(message = stringResource(R.string.login_status_logging_in))
        }
    }
}

@Composable
private fun QrScanCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F8FA), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.login_ic_qrcode),
            contentDescription = null,
            modifier = Modifier.size(36.dp)
        )

        Spacer(modifier = Modifier.size(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.login_hidden_config_scan_qr),
                color = colorResource(R.color.login_main_text),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = stringResource(R.string.login_hidden_config_scan_qr_desc),
                color = colorResource(R.color.login_text_hint),
                fontSize = 12.sp
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.login_ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colorResource(R.color.login_text_hint)
        )
    }
}
