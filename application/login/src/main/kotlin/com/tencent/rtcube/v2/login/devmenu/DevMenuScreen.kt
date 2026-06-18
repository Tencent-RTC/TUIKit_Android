package com.tencent.rtcube.v2.login.devmenu

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.ServerEnvironment
import com.tencent.rtcube.v2.login.components.views.LoginTopBackground

/**
 * Login method selection menu (Dev mode).
 */
@Composable
fun DevMenuScreen(
    versionName: String,
    isAutoLoginEnabled: Boolean,
    currentEnvironment: ServerEnvironment,
    onAutoLoginToggle: () -> Unit,
    onEnvironmentToggle: () -> Unit,
    onPhoneLogin: () -> Unit,
    onEmailLogin: () -> Unit,
    onIOALogin: () -> Unit,
    onInviteLogin: () -> Unit,
    onDebugLogin: () -> Unit,
    onTriggerHiddenConfig: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.login_color_white))
    ) {
        LoginTopBackground(onLogoHiddenTrigger = onTriggerHiddenConfig)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            val isProduction = currentEnvironment == ServerEnvironment.PRODUCTION
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.login_dev_select_mode),
                    color = colorResource(R.color.login_main_text),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                CapsuleButton(
                    label = stringResource(R.string.login_menu_auto_login),
                    dotColor = if (isAutoLoginEnabled) Color(0xFF006EFF) else Color(0xFFBBBBBB),
                    backgroundColor = if (isAutoLoginEnabled) Color(0x1A006EFF) else Color(0xFFFFFFFF),
                    borderColor = if (isAutoLoginEnabled) Color(0x4D006EFF) else Color(0xFFDDDDDD),
                    textColor = if (isAutoLoginEnabled) Color(0xFF006EFF) else Color(0xFF999999),
                    onClick = onAutoLoginToggle
                )

                Spacer(modifier = Modifier.width(8.dp))

                CapsuleButton(
                    label = if (isProduction) stringResource(R.string.login_menu_env_production)
                            else stringResource(R.string.login_menu_env_test),
                    dotColor = if (isProduction) Color(0xFF34C759) else Color(0xFFFF9500),
                    backgroundColor = if (isProduction) Color(0x1A34C759) else Color(0x1AFF9500),
                    borderColor = if (isProduction) Color(0x4D34C759) else Color(0x4DFF9500),
                    textColor = if (isProduction) Color(0xFF2DA44E) else Color(0xFFD4780A),
                    onClick = onEnvironmentToggle
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            DevMenuItem(
                icon = R.drawable.login_ic_menu_phone,
                title = stringResource(R.string.login_menu_phone),
                subtitle = stringResource(R.string.login_menu_phone_desc),
                onClick = onPhoneLogin
            )

            Spacer(modifier = Modifier.height(12.dp))

            DevMenuItem(
                icon = R.drawable.login_ic_menu_email,
                title = stringResource(R.string.login_menu_email),
                subtitle = stringResource(R.string.login_menu_email_desc),
                onClick = onEmailLogin
            )

            Spacer(modifier = Modifier.height(12.dp))

            DevMenuItem(
                icon = R.drawable.login_ic_menu_ioa,
                title = stringResource(R.string.login_menu_ioa),
                subtitle = stringResource(R.string.login_menu_ioa_desc),
                onClick = onIOALogin
            )

            Spacer(modifier = Modifier.height(12.dp))

            DevMenuItem(
                icon = R.drawable.login_ic_menu_invite,
                title = stringResource(R.string.login_menu_invite),
                subtitle = stringResource(R.string.login_menu_invite_desc),
                onClick = onInviteLogin
            )

            Spacer(modifier = Modifier.height(12.dp))

            DevMenuItem(
                icon = R.drawable.login_ic_menu_debug,
                title = stringResource(R.string.login_menu_debug),
                subtitle = stringResource(R.string.login_menu_debug_desc),
                onClick = onDebugLogin
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.login_dev_version, versionName),
                color = colorResource(R.color.login_text_hint),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
private fun CapsuleButton(
    label: String,
    dotColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedBg by animateColorAsState(targetValue = backgroundColor, label = "bg")
    val animatedBorder by animateColorAsState(targetValue = borderColor, label = "border")
    val animatedText by animateColorAsState(targetValue = textColor, label = "text")
    val animatedDot by animateColorAsState(targetValue = dotColor, label = "dot")

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(animatedBg)
            .border(width = 1.dp, color = animatedBorder, shape = RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(animatedDot)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = animatedText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DevMenuItem(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.6f else 1.0f,
        label = "alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF7F8FA))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFEEF3FF)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = title,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colorResource(R.color.login_main_text),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colorResource(R.color.login_text_secondary),
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
