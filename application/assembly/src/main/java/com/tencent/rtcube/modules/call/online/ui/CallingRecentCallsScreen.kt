package com.tencent.rtcube.modules.call.online.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.modules.R

@Composable
fun CallingRecentCallsScreen(
    onBack: () -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFEBEDF5),
        topBar = {
            CallTitleBar(title = stringResource(R.string.assembly_call_btn_start_call), onBack = onBack)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFEBEDF5)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.call_contact_ic_guidance),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.assembly_call_no_recent_calls),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF262B32),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.assembly_call_no_recent_calls_desc),
                    fontSize = 13.sp,
                    color = Color(0xFF9E9E9E),
                )
            }
        }
    }
}
