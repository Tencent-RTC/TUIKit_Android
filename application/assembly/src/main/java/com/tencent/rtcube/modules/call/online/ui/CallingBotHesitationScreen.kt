package com.tencent.rtcube.modules.call.online.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.modules.R
import com.tencent.rtcube.modules.call.online.model.CallBotType
import com.tencent.rtcube.modules.call.online.model.CallingRobotModel
import com.tencent.rtcube.modules.call.online.service.HttpRequestBotService
import kotlinx.coroutines.delay

@Composable
fun CallingBotHesitationScreen(
    robot: CallingRobotModel,
    onRequestBot: (botId: String) -> Unit,
    onBotBusy: () -> Unit,
    onFailed: (String) -> Unit,
    onWaitingFailed: (String) -> Unit,
    onShowToast: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showCancel by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2_000)
        showCancel = false
        if (robot.callType == CallBotType.INIT_CALL) {
            HttpRequestBotService.requestInitCallBot(
                onSuccess = { model ->
                    if (model.errorCode == 0) {
                        val users = model.data.virtualUsers?.filterNotNull() ?: emptyList()
                        if (users.isNotEmpty()) {
                            onRequestBot(users.random().virtualUserId)
                        } else {
                            onBotBusy()
                        }
                    } else {
                        onShowToast(context.getString(R.string.assembly_call_unexpected_error))
                    }
                    onBack()
                },
                onFailed = { msg ->
                    onFailed(msg)
                    onBack()
                },
            )
        } else {
            val lang = ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language ?: "zh"
            HttpRequestBotService.requestWaitingCall(
                language = lang,
                onSuccess = { onBack() },
                onFailed = { msg ->
                    onWaitingFailed(msg)
                    onBack()
                },
            )
        }
    }

    val avatarRes = if (robot.callType == CallBotType.INIT_CALL) {
        R.drawable.call_menu_ic_robot_a
    } else {
        R.drawable.call_menu_ic_robot_b
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colorResource(R.color.call_entrance_main),
        topBar = {
            CallTitleBar(title = stringResource(R.string.assembly_call_btn_start_call), onBack = onBack)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 80.dp)
                    .padding(horizontal = 40.dp)
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Spacer(modifier = Modifier.height(48.dp))

                    PulsingAvatarBox(avatarRes = avatarRes, title = robot.title)

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = stringResource(R.string.assembly_call_activating_robot),
                        fontSize = 16.sp,
                        color = Color(0xFF262B32),
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (showCancel) {
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorResource(R.color.call_color_cancel),
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = colorResource(R.color.call_color_cancel),
                            ),
                            modifier = Modifier
                                .height(40.dp)
                                .width(160.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.assembly_call_btn_cancel),
                                fontSize = 15.sp,
                                color = colorResource(R.color.call_color_cancel),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(40.dp))
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun PulsingAvatarBox(avatarRes: Int, title: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val outerScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "outerPulse",
    )
    val innerScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "innerPulse",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(108.dp),
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .scale(outerScale)
                .clip(RoundedCornerShape(54.dp))
                .background(Color(0x1A1C66E5)),
        )
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(innerScale)
                .clip(RoundedCornerShape(44.dp))
                .background(Color(0x331C66E5)),
        )
        Image(
            painter = painterResource(id = avatarRes),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(34.dp)),
        )
    }
}
