package com.tencent.rtcube.v2.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tencent.rtcube.v2.R

@Composable
fun ShowTipDialog(
    message: String,
    confirmText: String = stringResource(R.string.mine_common_btn_confirm),
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .width(270.dp)
                .background(Color.White, RoundedCornerShape(12.dp)),
        ) {
            Text(
                text = message,
                color = Color(0xFF333333),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 20.dp, bottom = 20.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFEEEEEE)),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = confirmText,
                    color = Color(0xFF006EFF),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}