package com.tencent.rtcube.modules.call.lab.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.cloud.tuikit.engine.call.TUICallEngine
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine.VideoRenderParams
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.qcloud.tuikit.tuicallkit.common.data.Constants
import com.tencent.qcloud.tuikit.tuicallkit.state.GlobalState
import com.tencent.rtcube.modules.R
import com.tencent.rtcube.modules.call.lab.store.LabSettingsState
import com.tencent.rtcube.modules.call.lab.store.LabSettingsStore
import io.trtc.tuikit.atomicxcore.api.CompletionHandler

@Composable
fun LabSettingsScreen(
    onNavigateToDetail: (String) -> Unit,
    onBack: () -> Unit,
    onShowToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val state by LabSettingsStore.state.collectAsState()

    LaunchedEffect(Unit) {
        val nickname = TUILogin.getNickName() ?: ""
        val avatar = TUILogin.getFaceUrl() ?: ""
        val isAISubtitleEnabled = GlobalState.instance.enableAITranscriber
        LabSettingsStore.refreshFromConfig(nickname, avatar, isAISubtitleEnabled)
        val buttonSet = GlobalState.instance.disableControlButtonSet
        LabSettingsStore.updateMicrophoneDisabled(buttonSet.contains(Constants.ControlButton.Microphone))
        LabSettingsStore.updateAudioDeviceDisabled(buttonSet.contains(Constants.ControlButton.AudioPlaybackDevice))
        LabSettingsStore.updateCameraDisabled(buttonSet.contains(Constants.ControlButton.Camera))
        LabSettingsStore.updateSwitchCameraDisabled(buttonSet.contains(Constants.ControlButton.SwitchCamera))
        LabSettingsStore.updateInviteUserDisabled(buttonSet.contains(Constants.ControlButton.InviteUser))
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            LabSettingsTitleBar(
                title = stringResource(R.string.assembly_call_settings_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            LabSettingsSectionLabel(text = stringResource(R.string.assembly_call_settings_basic))

            LabSettingsNavigationRow(
                label = stringResource(R.string.assembly_call_lab_avatar),
                value = state.avatar,
                onClick = { onNavigateToDetail(LabSettingDetailScreen.ITEM_AVATAR) },
            )

            LabSettingsEditRow(
                label = stringResource(R.string.assembly_call_lab_nickname),
                value = state.nickname,
                hint = stringResource(R.string.assembly_call_lab_nickname),
                onValueChange = { LabSettingsStore.updateNickname(it) },
                onDone = {
                    TUICallKit.createInstance(context).setSelfInfo(
                        state.nickname, TUILogin.getFaceUrl(),
                        object : CompletionHandler {
                            override fun onSuccess() {
                                onShowToast(context.getString(R.string.assembly_call_lab_set_success))
                            }

                            override fun onFailure(code: Int, desc: String) {
                                LabSettingsStore.updateNickname(TUILogin.getNickName() ?: "")
                                onShowToast(context.getString(R.string.assembly_call_lab_set_fail))
                            }
                        },
                    )
                },
            )

            LabSettingsNavigationRow(
                label = stringResource(R.string.assembly_call_lab_set_ring),
                value = state.ringPath,
                onClick = { onNavigateToDetail(LabSettingDetailScreen.ITEM_RING_PATH) },
            )

            LabSettingsSwitchRow(
                label = stringResource(R.string.assembly_call_lab_mute_mode),
                checked = state.isMute,
                onCheckedChange = { isChecked ->
                    LabSettingsStore.updateMute(isChecked)
                    TUICallKit.createInstance(context).enableMuteMode(isChecked)
                },
            )

            LabSettingsSwitchRow(
                label = stringResource(R.string.assembly_call_lab_show_float_button),
                checked = state.isShowFloatingWindow,
                onCheckedChange = { isChecked ->
                    LabSettingsStore.updateFloatingWindow(isChecked)
                    TUICallKit.createInstance(context).enableFloatWindow(isChecked)
                },
            )

            LabSettingsSwitchRow(
                label = stringResource(R.string.assembly_call_lab_blur_background),
                checked = state.isShowBlurBackground,
                onCheckedChange = { isChecked ->
                    LabSettingsStore.updateBlurBackground(isChecked)
                    TUICallKit.createInstance(context).enableVirtualBackground(isChecked)
                },
            )

            LabSettingsSwitchRow(
                label = stringResource(R.string.assembly_call_lab_incoming_banner),
                checked = state.isIncomingBanner,
                onCheckedChange = { isChecked ->
                    LabSettingsStore.updateIncomingBanner(isChecked)
                    TUICallKit.createInstance(context).enableIncomingBanner(isChecked)
                },
            )

            LabSettingsSwitchRow(
                label = stringResource(R.string.assembly_call_lab_ai_subtitles),
                checked = state.isAISubtitleEnabled,
                onCheckedChange = { isChecked ->
                    LabSettingsStore.updateAISubtitle(isChecked)
                    TUICallKit.createInstance(context).enableAITranscriber(isChecked)
                },
            )

            LabSettingsSectionLabel(text = stringResource(R.string.assembly_call_settings_call_params))

            LabSettingsEditRow(
                label = stringResource(R.string.assembly_call_lab_int_room_id),
                value = if (state.intRoomId == 0) "" else state.intRoomId.toString(),
                hint = stringResource(R.string.assembly_call_lab_not_setting),
                keyboardType = KeyboardType.Number,
                onValueChange = {},
                onDone = { text ->
                    val id = if (text.isBlank()) 0 else text.toIntOrNull() ?: 0
                    LabSettingsStore.updateIntRoomId(id)
                    onShowToast(context.getString(R.string.assembly_call_lab_set_success))
                },
                initialValue = if (state.intRoomId == 0) "0" else state.intRoomId.toString(),
            )

            LabSettingsEditRow(
                label = stringResource(R.string.assembly_call_lab_string_room_id),
                value = state.strRoomId,
                hint = stringResource(R.string.assembly_call_lab_not_setting),
                onValueChange = {},
                onDone = { text ->
                    LabSettingsStore.updateStrRoomId(text.trim())
                    onShowToast(context.getString(R.string.assembly_call_lab_set_success))
                },
            )

            LabSettingsEditRow(
                label = stringResource(R.string.assembly_call_settings_timeout),
                value = state.callTimeOut.toString(),
                hint = stringResource(R.string.assembly_call_lab_not_setting),
                keyboardType = KeyboardType.Number,
                onValueChange = {},
                onDone = { text ->
                    if (text.isBlank()) {
                        onShowToast(context.getString(R.string.assembly_call_lab_please_set_call_waiting_timeout))
                        return@LabSettingsEditRow
                    }
                    val timeout = text.toIntOrNull()
                    if (timeout != null) {
                        LabSettingsStore.updateCallTimeOut(timeout)
                        onShowToast(context.getString(R.string.assembly_call_lab_set_success))
                    }
                },
            )

            LabSettingsNavigationRow(
                label = stringResource(R.string.assembly_call_lab_user_data),
                value = state.userData,
                onClick = { onNavigateToDetail(LabSettingDetailScreen.ITEM_USER_DATA) },
            )

            LabSettingsNavigationRow(
                label = stringResource(R.string.assembly_call_lab_offline_message),
                value = state.offlineParams,
                onClick = { onNavigateToDetail(LabSettingDetailScreen.ITEM_OFFLINE_MESSAGE) },
            )

            LabSettingsSectionLabel(text = stringResource(R.string.assembly_call_lab_view_setting))

            LabSettingsCheckboxGroupRow(
                label = stringResource(R.string.assembly_call_lab_disable_button),
                items = listOf(
                    CheckboxItem("microphone", state.isMicrophoneDisabled) { isChecked ->
                        LabSettingsStore.updateMicrophoneDisabled(isChecked)
                        if (isChecked) {
                            TUICallKit.createInstance(context).disableControlButton(Constants.ControlButton.Microphone)
                        } else {
                            GlobalState.instance.disableControlButtonSet.remove(Constants.ControlButton.Microphone)
                        }
                    },
                    CheckboxItem("audioDevice", state.isAudioDeviceDisabled) { isChecked ->
                        LabSettingsStore.updateAudioDeviceDisabled(isChecked)
                        if (isChecked) {
                            TUICallKit.createInstance(context).disableControlButton(Constants.ControlButton.AudioPlaybackDevice)
                        } else {
                            GlobalState.instance.disableControlButtonSet.remove(Constants.ControlButton.AudioPlaybackDevice)
                        }
                    },
                    CheckboxItem("camera", state.isCameraDisabled) { isChecked ->
                        LabSettingsStore.updateCameraDisabled(isChecked)
                        if (isChecked) {
                            TUICallKit.createInstance(context).disableControlButton(Constants.ControlButton.Camera)
                        } else {
                            GlobalState.instance.disableControlButtonSet.remove(Constants.ControlButton.Camera)
                        }
                    },
                    CheckboxItem("switchCamera", state.isSwitchCameraDisabled) { isChecked ->
                        LabSettingsStore.updateSwitchCameraDisabled(isChecked)
                        if (isChecked) {
                            TUICallKit.createInstance(context).disableControlButton(Constants.ControlButton.SwitchCamera)
                        } else {
                            GlobalState.instance.disableControlButtonSet.remove(Constants.ControlButton.SwitchCamera)
                        }
                    },
                    CheckboxItem("inviteUser", state.isInviteUserDisabled) { isChecked ->
                        LabSettingsStore.updateInviteUserDisabled(isChecked)
                        if (isChecked) {
                            TUICallKit.createInstance(context).disableControlButton(Constants.ControlButton.InviteUser)
                        } else {
                            GlobalState.instance.disableControlButtonSet.remove(Constants.ControlButton.InviteUser)
                        }
                    },
                ),
            )

            LabSettingsSwitchRow(
                label = stringResource(R.string.assembly_call_lab_custom_main_view),
                checked = state.isAddMainView,
                onCheckedChange = { isChecked ->
                    LabSettingsStore.updateAddMainView(isChecked)
                    val adapter = if (isChecked) LabCustomUIAdapter().mainViewAdapter else null
                    TUICallKit.createInstance(context).setAdapter(adapter)
                },
            )

            LabSettingsSwitchRow(
                label = stringResource(R.string.assembly_call_lab_custom_stream_view),
                checked = state.isUseStreamView,
                onCheckedChange = { isChecked ->
                    LabSettingsStore.updateUseStreamView(isChecked)
                    val adapter = if (isChecked) LabCustomUIAdapter().streamViewAdapter else null
                    TUICallKit.createInstance(context).setAdapter(adapter)
                },
            )

            LabSettingsSectionLabel(text = stringResource(R.string.assembly_call_settings_video_setting))

            LabSettingsDropdownRow(
                label = stringResource(R.string.assembly_call_settings_resolution),
                selectedIndex = state.resolution,
                options = listOf("360p", "480p", "720p", "1080p"),
                onSelected = { index ->
                    LabSettingsStore.updateResolution(index)
                    setVideoEncoderParams(context, state.copy(resolution = index), onShowToast)
                },
            )

            LabSettingsDropdownRow(
                label = stringResource(R.string.assembly_call_lab_screen_orientation),
                selectedIndex = state.resolutionMode,
                options = listOf(
                    stringResource(R.string.assembly_call_settings_vertical),
                    stringResource(R.string.assembly_call_settings_horizontal),
                ),
                onSelected = { index ->
                    LabSettingsStore.updateResolutionMode(index)
                    setVideoEncoderParams(context, state.copy(resolutionMode = index), onShowToast)
                },
            )

            LabSettingsDropdownRow(
                label = stringResource(R.string.assembly_call_settings_fill_mode),
                selectedIndex = state.fillMode,
                options = listOf(
                    stringResource(R.string.assembly_call_settings_fill),
                    stringResource(R.string.assembly_call_settings_fit),
                ),
                onSelected = { index ->
                    LabSettingsStore.updateFillMode(index)
                    setVideoRenderParams(context, state.copy(fillMode = index), onShowToast)
                },
            )

            LabSettingsDropdownRow(
                label = stringResource(R.string.assembly_call_settings_rotation),
                selectedIndex = state.rotation,
                options = listOf("0°", "90°", "180°", "270°"),
                onSelected = { index ->
                    LabSettingsStore.updateRotation(index)
                    setVideoRenderParams(context, state.copy(rotation = index), onShowToast)
                },
            )

            LabSettingsEditRow(
                label = stringResource(R.string.assembly_call_settings_beauty_level),
                value = state.beautyLevel.toString(),
                hint = stringResource(R.string.assembly_call_lab_not_setting),
                keyboardType = KeyboardType.Number,
                onValueChange = {},
                onDone = { text ->
                    if (text.isBlank()) {
                        onShowToast(context.getString(R.string.assembly_call_lab_please_set_beauty_level))
                        return@LabSettingsEditRow
                    }
                    val level = text.toIntOrNull() ?: return@LabSettingsEditRow
                    LabSettingsStore.updateBeautyLevel(level)
                    TUICallEngine.createInstance(context).setBeautyLevel(
                        level.toFloat(),
                        object : TUICommonDefine.Callback {
                            override fun onSuccess() {
                                onShowToast(context.getString(R.string.assembly_call_lab_set_success))
                            }

                            override fun onError(errCode: Int, errMsg: String?) {
                                onShowToast(context.getString(R.string.assembly_call_lab_set_fail) + "| errorCode:$errCode, errMsg:$errMsg")
                            }
                        },
                    )
                },
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private fun setVideoEncoderParams(
    context: Context,
    state: LabSettingsState,
    onShowToast: (String) -> Unit,
) {
    val params = TUICommonDefine.VideoEncoderParams()
    params.resolutionMode = TUICommonDefine.VideoEncoderParams.ResolutionMode.fromInt(state.resolutionMode)
    params.resolution = TUICommonDefine.VideoEncoderParams.Resolution.fromInt(state.resolution)
    TUICallEngine.createInstance(context).setVideoEncoderParams(
        params,
        object : TUICommonDefine.Callback {
            override fun onSuccess() {
                onShowToast(context.getString(R.string.assembly_call_lab_set_success))
            }

            override fun onError(errCode: Int, errMsg: String?) {
                onShowToast(context.getString(R.string.assembly_call_lab_set_fail) + "| errorCode:$errCode, errMsg:$errMsg")
            }
        },
    )
}

private fun setVideoRenderParams(
    context: Context,
    state: LabSettingsState,
    onShowToast: (String) -> Unit,
) {
    val params = VideoRenderParams()
    params.rotation = VideoRenderParams.Rotation.entries.toTypedArray()[state.rotation]
    params.fillMode = VideoRenderParams.FillMode.entries.toTypedArray()[state.fillMode]
    TUICallEngine.createInstance(context).setVideoRenderParams(
        TUILogin.getLoginUser(), params,
        object : TUICommonDefine.Callback {
            override fun onSuccess() {
                onShowToast(context.getString(R.string.assembly_call_lab_set_success))
            }

            override fun onError(errCode: Int, errMsg: String?) {
                onShowToast(context.getString(R.string.assembly_call_lab_set_fail) + "| errorCode:$errCode, errMsg:$errMsg")
            }
        },
    )
}

@Composable
private fun LabSettingsTitleBar(title: String, onBack: () -> Unit) {
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
    }
}

@Composable
private fun LabSettingsSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = Color(0xFF9C9C9C),
        modifier = Modifier
            .padding(start = 16.dp, top = 8.dp)
            .height(30.dp),
    )
}

@Composable
private fun LabSettingsNavigationRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = Color.Black,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 16.sp,
            color = Color.Black,
            maxLines = 1,
            modifier = Modifier
                .width(200.dp)
                .padding(end = 10.dp),
            textAlign = TextAlign.End,
        )
        Icon(
            painter = painterResource(R.drawable.call_common_ic_back),
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .rotate(180f),
            tint = Color(0xFFACB6C4),
        )
    }
}

@Composable
private fun LabSettingsEditRow(
    label: String,
    value: String,
    hint: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
    onDone: (String) -> Unit,
    initialValue: String = value,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = Color.Black,
        )
        Spacer(modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .width(200.dp),
            placeholder = {
                Text(
                    text = hint,
                    fontSize = 16.sp,
                    color = Color(0xFFBBBBBB),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone(text) },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
            ),
            textStyle = TextStyle(
                fontSize = 16.sp,
                textAlign = TextAlign.End,
            ),
        )
    }
}

@Composable
private fun LabSettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = Color.Black,
        )
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.7f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colorResource(R.color.call_color_blue),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF999999),
                uncheckedBorderColor = Color(0xFF999999),
            ),
        )
    }
}

data class CheckboxItem(
    val label: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
)

@Composable
private fun LabSettingsCheckboxGroupRow(
    label: String,
    items: List<CheckboxItem>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = Color.Black,
        )
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        text = item.label,
                        fontSize = 14.sp,
                        color = Color.Black,
                    )
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = item.onCheckedChange,
                        modifier = Modifier.scale(0.7f),
                        colors = CheckboxDefaults.colors(
                            checkedColor = colorResource(R.color.call_color_blue),
                            uncheckedColor = Color(0xFFBBBBBB),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LabSettingsDropdownRow(
    label: String,
    selectedIndex: Int,
    options: List<String>,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = options.getOrElse(selectedIndex) { options.firstOrNull() ?: "" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = Color.Black,
        )
        Spacer(modifier = Modifier.weight(1f))
        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedText,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Icon(
                    painter = painterResource(R.drawable.call_menu_ic_arrow_down),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(8.dp),
                    tint = Color.Black,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White),
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                fontSize = 14.sp,
                                color = Color.Black,
                            )
                        },
                        onClick = {
                            onSelected(index)
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
