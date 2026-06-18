package com.tencent.rtcube.v2.privacy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.privacy.store.PrivacyStore

@Composable
internal fun PersonalInfoDetailScreen(
    store: PrivacyStore,
    onBack: () -> Unit,
) {
    val state by store.state.collectAsState()
    val appName = stringResource(R.string.privacy_app_name)
    val noneText = stringResource(R.string.privacy_none)

    LaunchedEffect(Unit) {
        store.loadUserInfo()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.privacy_color_white))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrivacyTopBar(
            title = stringResource(R.string.privacy_personal_info),
            onBack = onBack,
        )

        Text(
            text = stringResource(R.string.privacy_data_collection),
            color = colorResource(R.color.privacy_color_black),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 15.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.privacy_desc, appName),
            color = colorResource(R.color.privacy_color_main_text),
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )

        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.privacy_avatar),
                color = colorResource(R.color.privacy_color_main_text),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            AsyncImage(
                model = state.userAvatar.ifEmpty { null },
                placeholder = painterResource(id = R.drawable.privacy_ic_head),
                error = painterResource(id = R.drawable.privacy_ic_head),
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        PersonalInfoDetailPurposeRow(
            purpose = stringResource(R.string.privacy_use_by_register_use, appName),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.privacy_name),
                color = colorResource(R.color.privacy_color_main_text),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = state.userName.ifEmpty { noneText },
                color = colorResource(R.color.privacy_color_permission_result),
                fontSize = 14.sp,
            )
        }

        PersonalInfoDetailPurposeRow(
            purpose = stringResource(R.string.privacy_use_by_register_use, appName),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.privacy_phone),
                color = colorResource(R.color.privacy_color_main_text),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = state.phone.ifEmpty { noneText },
                color = colorResource(R.color.privacy_color_permission_result),
                fontSize = 14.sp,
            )
        }

        PersonalInfoDetailPurposeRow(
            purpose = stringResource(R.string.privacy_use_by_register_login),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.privacy_email),
                color = colorResource(R.color.privacy_color_main_text),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = state.email.ifEmpty { noneText },
                color = colorResource(R.color.privacy_color_permission_result),
                fontSize = 14.sp,
            )
        }

        PersonalInfoDetailPurposeRow(
            purpose = stringResource(R.string.privacy_use_by_register_login),
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun PersonalInfoDetailPurposeRow(purpose: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.privacy_purpose),
            color = colorResource(R.color.privacy_color_main_text),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = purpose,
            color = colorResource(R.color.privacy_color_permission_result),
            fontSize = 14.sp,
        )
    }
}
