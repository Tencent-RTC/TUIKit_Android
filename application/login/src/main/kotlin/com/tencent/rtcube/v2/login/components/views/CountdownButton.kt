package com.tencent.rtcube.v2.login.components.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.login.R

@Composable
fun CountdownButton(
    countdownSeconds: Int,
    canSend: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCountingDown = countdownSeconds > 0
    val enabled = canSend && !isCountingDown

    val text = if (isCountingDown) {
        stringResource(R.string.login_countdown, countdownSeconds)
    } else {
        stringResource(R.string.login_get_verify_code)
    }

    val textColor = if (enabled) Color(0xFF006EFF) else Color(0xFFBBBBBB)

    val interactionSource = remember { MutableInteractionSource() }

    Text(
        text = text,
        color = textColor,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .padding(start = 8.dp)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = interactionSource,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
    )
}