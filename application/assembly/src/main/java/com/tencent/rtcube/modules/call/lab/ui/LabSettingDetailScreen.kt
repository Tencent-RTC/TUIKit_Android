package com.tencent.rtcube.modules.call.lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.rtcube.modules.R
import com.tencent.rtcube.modules.call.lab.store.LabSettingsStore
import io.trtc.tuikit.atomicxcore.api.CompletionHandler

@Composable
fun LabSettingDetailScreen(
    itemType: String,
    onBack: () -> Unit,
    onShowToast: (String) -> Unit,
) {
    val context = LocalContext.current

    val initialValue = when (itemType) {
        LabSettingDetailScreen.ITEM_USER_DATA -> LabSettingsConfig.userData
        LabSettingDetailScreen.ITEM_OFFLINE_MESSAGE -> LabSettingsConfig.offlineParams
        LabSettingDetailScreen.ITEM_AVATAR -> TUILogin.getFaceUrl() ?: ""
        LabSettingDetailScreen.ITEM_RING_PATH -> LabSettingsConfig.ringPath ?: ""
        else -> ""
    }

    val hint = when (itemType) {
        LabSettingDetailScreen.ITEM_USER_DATA -> stringResource(R.string.assembly_call_lab_invite_cmd_extra_info)
        LabSettingDetailScreen.ITEM_OFFLINE_MESSAGE -> stringResource(R.string.assembly_call_lab_offline_message_json_string)
        LabSettingDetailScreen.ITEM_AVATAR -> stringResource(R.string.assembly_call_lab_avatar)
        LabSettingDetailScreen.ITEM_RING_PATH -> stringResource(R.string.assembly_call_lab_set_ring_path)
        else -> ""
    }

    val title = stringResource(R.string.assembly_call_settings_general)

    var content by remember { mutableStateOf(initialValue) }

    fun clickConfirm() {
        when (itemType) {
            LabSettingDetailScreen.ITEM_USER_DATA -> {
                LabSettingsStore.updateUserData(content.trim())
                onShowToast(context.getString(R.string.assembly_call_lab_set_success))
                onBack()
            }

            LabSettingDetailScreen.ITEM_OFFLINE_MESSAGE -> {
                LabSettingsStore.updateOfflineParams(content.trim())
                onShowToast(context.getString(R.string.assembly_call_lab_set_success))
                onBack()
            }

            LabSettingDetailScreen.ITEM_AVATAR -> {
                val avatar = content.trim()
                TUICallKit.createInstance(context).setSelfInfo(
                    TUILogin.getNickName(), avatar,
                    object : CompletionHandler {
                        override fun onSuccess() {
                            onShowToast(context.getString(R.string.assembly_call_lab_set_success))
                            onBack()
                        }

                        override fun onFailure(code: Int, desc: String) {
                            onShowToast(context.getString(R.string.assembly_call_lab_set_fail))
                        }
                    },
                )
            }

            LabSettingDetailScreen.ITEM_RING_PATH -> {
                LabSettingsStore.updateRingPath(content)
                TUICallKit.createInstance(context).setCallingBell(content)
                onShowToast(context.getString(R.string.assembly_call_lab_set_success))
                onBack()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            LabSettingDetailTitleBar(
                title = title,
                onBack = onBack,
                onConfirm = { clickConfirm() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                placeholder = {
                    Text(
                        text = hint,
                        fontSize = 16.sp,
                        color = Color(0xFFBBBBBB),
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { clickConfirm() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                ),
                textStyle = TextStyle(fontSize = 16.sp),
            )
        }
    }
}

@Composable
private fun LabSettingDetailTitleBar(
    title: String,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(62.dp)
            .background(Color.White),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.call_common_ic_back),
                contentDescription = stringResource(R.string.assembly_call_btn_back),
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified,
            )
        }
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.align(Alignment.Center),
        )
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .height(28.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(R.color.call_color_blue),
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.assembly_call_btn_confirm),
                fontSize = 12.sp,
                color = Color.White,
            )
        }
    }
}

object LabSettingDetailScreen {
    const val ITEM_KEY = "settingsItem"
    const val ITEM_AVATAR = "avatar"
    const val ITEM_RING_PATH = "ringPath"
    const val ITEM_USER_DATA = "userData"
    const val ITEM_OFFLINE_MESSAGE = "offlineMessage"
}
