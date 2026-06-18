package com.tencent.rtcube.v2.privacy.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.R

sealed class PrivacyRoute {
    object Main : PrivacyRoute()
    object PersonalAuth : PrivacyRoute()
    object SystemAuth : PrivacyRoute()
    data class PermissionDetail(val title: String, val desc: String, val itemContent: String) : PrivacyRoute()
    object PersonalInfo : PrivacyRoute()
    object PersonalInfoDetail : PrivacyRoute()
}

@Composable
internal fun PrivacyTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Image(
            painter = painterResource(id = R.drawable.privacy_ic_back),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 17.dp, bottom = 9.dp)
                .size(24.dp)
                .clickable { onBack() },
        )
        Text(
            text = title,
            color = colorResource(R.color.privacy_color_black),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 9.dp),
        )
    }
}

@Composable
internal fun PrivacyMenuItem(
    text: String,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .clickable { onClick() }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = colorResource(R.color.privacy_color_main_text),
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            if (trailingContent != null) {
                trailingContent()
            } else {
                Image(
                    painter = painterResource(id = R.drawable.privacy_ic_details),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 1.dp,
            color = colorResource(R.color.privacy_color_line),
        )
    }
}

@Composable
internal fun PrivacyPermissionMenuItem(
    text: String,
    isGranted: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .clickable { onClick() }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = colorResource(R.color.privacy_color_main_text),
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isGranted) stringResource(R.string.privacy_allow) else stringResource(R.string.privacy_deny),
                color = colorResource(R.color.privacy_color_permission_result),
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Image(
                painter = painterResource(id = R.drawable.privacy_ic_details),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 1.dp,
            color = colorResource(R.color.privacy_color_line),
        )
    }
}
