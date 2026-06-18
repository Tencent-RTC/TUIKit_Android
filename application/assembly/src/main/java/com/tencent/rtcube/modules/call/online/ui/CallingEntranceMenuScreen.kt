package com.tencent.rtcube.modules.call.online.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.modules.R
import com.tencent.rtcube.modules.call.online.model.CallBotType
import com.tencent.rtcube.modules.call.online.model.CallingRobotModel

@Composable
fun CallingEntranceMenuScreen(
    isRobotExpanded: Boolean,
    onRobotExpandedChange: (Boolean) -> Unit,
    onNavigateToBotHesitation: (CallingRobotModel) -> Unit,
    onNavigateToContact: () -> Unit,
    onBack: () -> Unit,
) {
    val robotItems = remember {
        listOf(
            CallingRobotModel(title = "Robot A", callType = CallBotType.INIT_CALL),
            CallingRobotModel(title = "Robot B", callType = CallBotType.HOST_CALL),
        ).filter { it.callType != CallBotType.INIT_CALL }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colorResource(R.color.call_entrance_main),
        topBar = {
            CallTitleBar(title = stringResource(R.string.assembly_call_btn_start_call), onBack = onBack)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.assembly_call_select_call_method),
                    fontSize = 12.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Section 0: bot call
            item {
                CallingMenuSectionHeader(
                    title = stringResource(R.string.assembly_call_call_with_robot),
                    content = stringResource(R.string.assembly_call_call_with_robot_desc),
                    stressContent = listOf(stringResource(R.string.assembly_call_call_with_robot_highlight)),
                    isExpanded = isRobotExpanded,
                    onClick = { onRobotExpandedChange(!isRobotExpanded) },
                )
            }

            item {
                AnimatedVisibility(
                    visible = isRobotExpanded,
                    enter = expandVertically(animationSpec = tween(durationMillis = 250)),
                    exit = shrinkVertically(animationSpec = tween(durationMillis = 200)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White),
                    ) {
                        robotItems.forEachIndexed { index, robot ->
                            CallingRobotRow(
                                model = robot,
                                showTopDivider = index == 0,
                                showBottomDivider = index < robotItems.lastIndex,
                                onDial = { onNavigateToBotHesitation(robot) },
                            )
                        }
                    }
                }
            }

            item {
                if (isRobotExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                            .background(Color.White),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Section 1: real-user call
            item {
                CallingMenuSectionHeader(
                    title = stringResource(R.string.assembly_call_call_other_users),
                    content = stringResource(R.string.assembly_call_call_other_users_desc),
                    stressContent = listOf(stringResource(R.string.assembly_call_call_other_users_highlight)),
                    isExpanded = null,
                    onClick = onNavigateToContact,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CallingMenuSectionHeader(
    title: String,
    content: String,
    stressContent: List<String> = emptyList(),
    isExpanded: Boolean?,
    onClick: () -> Unit,
) {
    val shape = if (isExpanded == true) RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp) else RoundedCornerShape(10.dp)
    val arrowRes = if (isExpanded != null) R.drawable.call_menu_ic_arrow_down else R.drawable.call_common_ic_arrow_right
    val arrowRotation = if (isExpanded == false) -90f else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.82f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF262B32),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = buildAnnotatedString {
                    var lastIndex = 0
                    for (stress in stressContent) {
                        val idx = content.indexOf(stress, lastIndex)
                        if (idx >= 0) {
                            append(content.substring(lastIndex, idx))
                            withStyle(SpanStyle(color = colorResource(R.color.call_color_call_search_text))) { append(stress) }
                            lastIndex = idx + stress.length
                        }
                    }
                    append(content.substring(lastIndex))
                },
                fontSize = 12.sp,
                color = Color(0xFF757575),
            )
        }
        Image(
            painter = painterResource(id = arrowRes),
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.CenterEnd)
                .rotate(arrowRotation),
        )
    }
}

@Composable
private fun CallingRobotRow(
    model: CallingRobotModel,
    showTopDivider: Boolean = true,
    showBottomDivider: Boolean = true,
    onDial: () -> Unit,
) {
    val avatarRes =
        if (model.callType == CallBotType.INIT_CALL) R.drawable.call_menu_ic_robot_a else R.drawable.call_menu_ic_robot_b
    val btnIconRes =
        if (model.callType == CallBotType.INIT_CALL) R.drawable.call_menu_ic_robot_call else R.drawable.call_menu_ic_robot_called

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        if (showTopDivider) {
            HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = avatarRes),
                contentDescription = model.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = model.title,
                fontSize = 15.sp,
                color = Color(0xFF262B32),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onDial,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorResource(R.color.call_color_call_search_text)),
                border = BorderStroke(1.dp, colorResource(R.color.call_color_call_search_text)),
                modifier = Modifier
                    .height(32.dp)
                    .wrapContentWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Image(
                    painter = painterResource(id = btnIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (model.callType == CallBotType.INIT_CALL)
                        stringResource(R.string.assembly_call_simulate_caller)
                    else
                        stringResource(R.string.assembly_call_simulate_called),
                    fontSize = 13.sp,
                    color = colorResource(R.color.call_color_call_search_text),
                )
            }
        }
        if (showBottomDivider) {
            HorizontalDivider(
                color = Color(0xFFF0F0F0),
                thickness = 1.dp,
                modifier = Modifier.padding(start = 70.dp),
            )
        }
    }
}
