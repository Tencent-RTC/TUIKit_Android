package com.tencent.rtcube.v2.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ConfirmDialog(
    message: String,
    negativeText: String,
    positiveText: String,
    negativeTextColor: Color = Color(0xFF006EFF),
    positiveTextColor: Color = Color(0xFFFA585E),
    onNegative: () -> Unit,
    onPositive: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .width(270.dp)
                .wrapContentHeight()
                .background(Color.White, RoundedCornerShape(12.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 99.dp)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message,
                    color = Color(0xFF030303),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFEEEEEE)),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(43.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(43.dp)
                        .clickable { onNegative() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = negativeText,
                        color = negativeTextColor,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(43.dp)
                        .background(Color(0xFFEEEEEE)),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(43.dp)
                        .clickable { onPositive() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = positiveText,
                        color = positiveTextColor,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}