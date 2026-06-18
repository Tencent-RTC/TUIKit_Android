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

@Composable
internal fun PrivacyMainScreen(
    onBack: () -> Unit,
    onPersonalAuth: () -> Unit,
    onDataCollection: () -> Unit,
    onDataCollectionList: () -> Unit,
    onThirdShare: () -> Unit,
    onPrivacyPolicySummary: () -> Unit,
    onPrivacyAgreement: () -> Unit,
    onUserAgreement: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.privacy_color_white))
            .verticalScroll(rememberScrollState()),
    ) {
        PrivacyTopBar(
            title = stringResource(R.string.privacy_title),
            onBack = onBack,
        )
        PrivacyMenuItem(
            text = stringResource(R.string.privacy_personal_auth),
            onClick = onPersonalAuth,
        )
        PrivacyMenuItem(
            text = stringResource(R.string.privacy_data_collection),
            onClick = onDataCollection,
        )
        PrivacyMenuItem(
            text = stringResource(R.string.privacy_data_collection_list),
            onClick = onDataCollectionList,
        )
        PrivacyMenuItem(
            text = stringResource(R.string.privacy_third_share),
            onClick = onThirdShare,
        )
        PrivacyMenuItem(
            text = stringResource(R.string.privacy_policy_summary),
            onClick = onPrivacyPolicySummary,
        )
        PrivacyMenuItem(
            text = stringResource(R.string.privacy_agreement),
            onClick = onPrivacyAgreement,
        )
        PrivacyMenuItem(
            text = stringResource(R.string.privacy_user_agreement),
            onClick = onUserAgreement,
        )
    }
}
