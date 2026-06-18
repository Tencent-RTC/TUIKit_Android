package com.tencent.rtcube.v2.privacy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.privacy.store.PrivacyStore

@Composable
internal fun PersonalAuthScreen(
    store: PrivacyStore,
    onBack: () -> Unit,
    onSystemAuth: () -> Unit,
    onPersonalInfo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.privacy_color_white))
            .verticalScroll(rememberScrollState()),
    ) {
        PrivacyTopBar(
            title = stringResource(R.string.privacy_personal_auth),
            onBack = onBack,
        )
        PrivacyMenuItem(
            text = stringResource(R.string.privacy_system_auth),
            onClick = onSystemAuth,
        )
        PrivacyMenuItem(
            text = stringResource(R.string.privacy_personal_info),
            onClick = onPersonalInfo,
        )
    }
}
