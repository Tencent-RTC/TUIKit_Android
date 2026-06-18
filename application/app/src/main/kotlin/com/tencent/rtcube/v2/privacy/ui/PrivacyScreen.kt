package com.tencent.rtcube.v2.privacy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tencent.rtcube.v2.privacy.PrivacyEntry
import com.tencent.rtcube.v2.privacy.store.PrivacyStore

@Composable
fun PrivacyScreen(
    onBack: () -> Unit = {},
) {
    val store = remember { PrivacyStore() }
    val context = LocalContext.current

    var currentScreen by remember { mutableStateOf<PrivacyRoute>(PrivacyRoute.Main) }

    when (val screen = currentScreen) {
        is PrivacyRoute.Main -> PrivacyMainScreen(
            onBack = onBack,
            onPersonalAuth = { currentScreen = PrivacyRoute.PersonalAuth },
            onDataCollection = { currentScreen = PrivacyRoute.PersonalInfoDetail },
            onDataCollectionList = { store.openUrl(context, PrivacyEntry.dataCollectionListURL) },
            onThirdShare = { store.openUrl(context, PrivacyEntry.thirdShareURL) },
            onPrivacyPolicySummary = { store.openUrl(context, PrivacyEntry.privacySummaryURL) },
            onPrivacyAgreement = { store.openUrl(context, PrivacyEntry.privacyProtectURL) },
            onUserAgreement = { store.openUrl(context, PrivacyEntry.userAgreementURL) },
        )

        is PrivacyRoute.PersonalAuth -> PersonalAuthScreen(
            store = store,
            onBack = { currentScreen = PrivacyRoute.Main },
            onSystemAuth = { currentScreen = PrivacyRoute.SystemAuth },
            onPersonalInfo = { currentScreen = PrivacyRoute.PersonalInfo },
        )

        is PrivacyRoute.SystemAuth -> SystemAuthScreen(
            store = store,
            onBack = { currentScreen = PrivacyRoute.PersonalAuth },
            onPermissionDetail = { title, desc, itemContent ->
                currentScreen = PrivacyRoute.PermissionDetail(title, desc, itemContent)
            },
        )

        is PrivacyRoute.PermissionDetail -> PermissionDetailScreen(
            store = store,
            title = screen.title,
            desc = screen.desc,
            itemContent = screen.itemContent,
            onBack = { currentScreen = PrivacyRoute.SystemAuth },
        )

        is PrivacyRoute.PersonalInfo -> PersonalInfoScreen(
            store = store,
            onBack = { currentScreen = PrivacyRoute.PersonalAuth },
        )

        is PrivacyRoute.PersonalInfoDetail -> PersonalInfoDetailScreen(
            store = store,
            onBack = { currentScreen = PrivacyRoute.Main },
        )
    }
}
