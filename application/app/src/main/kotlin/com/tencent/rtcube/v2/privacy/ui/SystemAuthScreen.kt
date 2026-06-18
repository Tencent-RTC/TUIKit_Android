package com.tencent.rtcube.v2.privacy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
internal fun SystemAuthScreen(
    store: PrivacyStore,
    onBack: () -> Unit,
    onPermissionDetail: (title: String, desc: String, itemContent: String) -> Unit,
) {
    val state by store.state.collectAsState()
    val context = LocalContext.current
    val appName = stringResource(R.string.privacy_app_name)

    LaunchedEffect(Unit) {
        store.refreshPermissions(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.privacy_color_white))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrivacyTopBar(
            title = stringResource(R.string.privacy_system_auth),
            onBack = onBack,
        )

        val cameraTitle = stringResource(R.string.privacy_camera)
        val cameraDesc = stringResource(R.string.privacy_camera_des, appName)
        val cameraManage = stringResource(R.string.privacy_manage, cameraTitle)
        PrivacyPermissionMenuItem(
            text = cameraTitle,
            isGranted = state.cameraGranted,
            onClick = { onPermissionDetail(cameraTitle, cameraDesc, cameraManage) },
        )

        val storageTitle = stringResource(R.string.privacy_storage)
        val storageDesc = stringResource(R.string.privacy_storage_des, appName)
        val storageManage = stringResource(R.string.privacy_manage, storageTitle)
        PrivacyPermissionMenuItem(
            text = storageTitle,
            isGranted = state.storageGranted,
            onClick = { onPermissionDetail(storageTitle, storageDesc, storageManage) },
        )

        val micTitle = stringResource(R.string.privacy_microphone)
        val micDesc = stringResource(R.string.privacy_mic_des, appName)
        val micManage = stringResource(R.string.privacy_manage, micTitle)
        PrivacyPermissionMenuItem(
            text = micTitle,
            isGranted = state.micGranted,
            onClick = { onPermissionDetail(micTitle, micDesc, micManage) },
        )

        val beautyTitle = stringResource(R.string.privacy_beauty)
        val beautyDesc = stringResource(R.string.privacy_beauty_face_request)
        val beautyManage = stringResource(R.string.privacy_manage, beautyTitle)
        PrivacyPermissionMenuItem(
            text = beautyTitle,
            isGranted = state.beautyAllowed,
            onClick = { onPermissionDetail(beautyTitle, beautyDesc, beautyManage) },
        )

        val avatarTitle = stringResource(R.string.privacy_avatar_title)
        val avatarDesc = stringResource(R.string.privacy_tencent_effect_avatar_face_request)
        val avatarManage = stringResource(R.string.privacy_manage, avatarTitle)
        PrivacyPermissionMenuItem(
            text = avatarTitle,
            isGranted = state.avatarAllowed,
            onClick = { onPermissionDetail(avatarTitle, avatarDesc, avatarManage) },
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.privacy_system_setting),
            color = colorResource(R.color.privacy_color_blue),
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { store.launchAppSettings(context) },
        )
    }
}
