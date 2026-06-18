package com.tencent.rtcube.v2.mine.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.mine.store.MineStore

@Composable
internal fun AboutScreen(
    store: MineStore,
    onLogOffClick: () -> Unit,
) {
    val context = LocalContext.current
    val sdkVersion by remember { mutableStateOf(store.getSDKVersion()) }
    val appVersion by remember { mutableStateOf(store.getAppVersion(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F5F9)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            Image(
                painter = painterResource(id = R.drawable.mine_info_ic_back),
                contentDescription = stringResource(R.string.mine_info_back_desc),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 17.dp)
                    .size(38.dp)
                    .padding(10.dp)
                    .clickable { store.hideAbout() },
            )
            Text(
                text = stringResource(R.string.mine_about_title),
                color = Color(0xFF000000),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            AboutRow(
                label = stringResource(R.string.mine_about_sdk_version),
                value = sdkVersion,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                thickness = 1.dp,
                color = Color(0xFFEEEEEE),
            )
            AboutRow(
                label = stringResource(R.string.mine_about_app_version),
                value = appVersion,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                thickness = 1.dp,
                color = Color(0xFFEEEEEE),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .clickable { onLogOffClick() }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.mine_logoff_title),
                    color = Color(0xFF333333),
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Image(
                    painter = painterResource(id = R.drawable.mine_info_ic_arrow),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                thickness = 1.dp,
                color = Color(0xFFEEEEEE),
            )
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFF333333),
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = Color(0xFF333333),
            fontSize = 16.sp,
        )
    }
}