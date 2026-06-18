package com.tencent.rtcube.modules.call.online.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.modules.R

@Composable
fun CallTitleBar(
    title: String,
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (onBack != null) {
                Image(
                    painter = painterResource(id = R.drawable.call_common_ic_back),
                    contentDescription = stringResource(R.string.assembly_call_btn_back),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .size(24.dp)
                        .clickable { onBack() },
                )
            }
            Text(
                text = title,
                fontSize = 18.sp,
                color = Color(0xFF262B32),
                textAlign = TextAlign.Center,
            )
        }
    }
}
