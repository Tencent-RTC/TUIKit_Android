package com.tencent.rtcube.v2.privacy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.privacy.store.PrivacyStore

@Composable
internal fun PermissionDetailScreen(
    store: PrivacyStore,
    title: String,
    desc: String,
    itemContent: String,
    onBack: () -> Unit,
) {
    val state by store.state.collectAsState()
    val context = LocalContext.current

    val beautyTitle = stringResource(R.string.privacy_beauty)
    val avatarTitle = stringResource(R.string.privacy_avatar_title)
    val isBeauty = title == beautyTitle
    val isAvatar = title == avatarTitle
    val showSwitch = isBeauty || isAvatar

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.privacy_color_white))
            .verticalScroll(rememberScrollState()),
    ) {
        PrivacyTopBar(
            title = title,
            onBack = onBack,
        )

        Text(
            text = title,
            color = colorResource(R.color.privacy_color_black),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 15.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = desc,
            color = colorResource(R.color.privacy_color_main_text),
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(modifier = Modifier.height(30.dp))

        HorizontalDivider(thickness = 1.dp, color = colorResource(R.color.privacy_color_line))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .then(
                    if (!showSwitch) Modifier.clickable { store.launchAppSettings(context) }
                    else Modifier
                )
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = itemContent,
                color = colorResource(R.color.privacy_color_main_text),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (showSwitch) {
                val checked = if (isBeauty) state.beautyAllowed else state.avatarAllowed
                Switch(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        if (isBeauty) store.setBeautyAllowed(isChecked)
                        else store.setAvatarAllowed(isChecked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorResource(R.color.privacy_color_white),
                        checkedTrackColor = colorResource(R.color.privacy_color_blue),
                        uncheckedThumbColor = colorResource(R.color.privacy_color_white),
                        uncheckedTrackColor = colorResource(R.color.privacy_color_switch_unchecked_track),
                    ),
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = colorResource(R.color.privacy_color_line))
    }
}
