package com.tencent.rtcube.modules.call.lab.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.rtcube.modules.R
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParams

@Composable
fun LabCallScreen(
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var userIdListInput by remember { mutableStateOf("") }
    var groupIdInput by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf(CallMediaType.Audio) }
    var isOptionalExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            LabCallTitleBar(
                title = stringResource(R.string.assembly_call_lab_group_call),
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
            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.assembly_call_lab_user_id_list),
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedTextField(
                    value = userIdListInput,
                    onValueChange = { userIdListInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.assembly_call_lab_please_input_user_id_list),
                            fontSize = 16.sp,
                            color = Color(0xFFBBBBBB),
                        )
                    },
                    singleLine = true,
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

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.assembly_call_settings_media_type),
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(modifier = Modifier.selectableGroup()) {
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = mediaType == CallMediaType.Video,
                                onClick = { mediaType = CallMediaType.Video },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mediaType == CallMediaType.Video,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorResource(R.color.call_color_blue),
                            ),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.assembly_call_settings_video_call),
                            fontSize = 16.sp,
                            color = Color.Black,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = mediaType == CallMediaType.Audio,
                                onClick = { mediaType = CallMediaType.Audio },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mediaType == CallMediaType.Audio,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorResource(R.color.call_color_blue),
                            ),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.assembly_call_settings_audio_call),
                            fontSize = 16.sp,
                            color = Color.Black,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 10.dp),
                color = Color(0xFFCCCCCC),
                thickness = 1.dp,
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp)
                    .clickable { isOptionalExpanded = !isOptionalExpanded }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.assembly_call_settings_optional_parameters),
                    fontSize = 16.sp,
                    color = colorResource(R.color.call_color_blue),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(
                        if (isOptionalExpanded) R.drawable.call_menu_ic_arrow_down
                        else R.drawable.call_common_ic_arrow_right
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorResource(R.color.call_color_blue),
                )
            }

            if (isOptionalExpanded) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.assembly_call_settings_group_id),
                        fontSize = 16.sp,
                        color = Color.Black,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = groupIdInput,
                        onValueChange = { groupIdInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.assembly_call_lab_please_input_group_id),
                                fontSize = 16.sp,
                                color = Color(0xFFBBBBBB),
                            )
                        },
                        singleLine = true,
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

            Spacer(modifier = Modifier.height(45.dp))

            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { onNavigateToSettings() }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.assembly_call_settings_title),
                    fontSize = 16.sp,
                    color = colorResource(R.color.call_color_blue),
                )
                Spacer(modifier = Modifier.width(9.5.dp))
                Icon(
                    painter = painterResource(R.drawable.call_common_ic_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorResource(R.color.call_color_blue),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val context = LocalContext.current
            Button(
                onClick = {
                    startCall(
                        context = context,
                        userIdListInput = userIdListInput,
                        groupIdInput = groupIdInput,
                        mediaType = mediaType,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 46.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.call_color_blue),
                ),
            ) {
                Text(
                    text = stringResource(R.string.assembly_call_lab_start_call),
                    fontSize = 16.sp,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun LabCallTitleBar(
    title: String,
    onBack: () -> Unit,
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
    }
}

private fun startCall(
    context: Context,
    userIdListInput: String,
    groupIdInput: String,
    mediaType: CallMediaType,
) {
    if (userIdListInput.isBlank()) {
        return
    }
    val userIdList: List<String> = when {
        userIdListInput.contains(",") -> userIdListInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        userIdListInput.contains("，") -> userIdListInput.split("，").map { it.trim() }.filter { it.isNotEmpty() }
        else -> listOf(userIdListInput.trim())
    }

    val callParams = buildCallParams()
    callParams.chatGroupId = groupIdInput

    TUICallKit.createInstance(context).calls(userIdList, mediaType, callParams, null)
}

private fun buildCallParams(): CallParams {
    val callParams = CallParams()
    callParams.timeout = LabSettingsConfig.callTimeOut
    callParams.userData = LabSettingsConfig.userData
    if (LabSettingsConfig.strRoomId.isNotEmpty()) {
        callParams.roomId = LabSettingsConfig.strRoomId
    }
    return callParams
}
