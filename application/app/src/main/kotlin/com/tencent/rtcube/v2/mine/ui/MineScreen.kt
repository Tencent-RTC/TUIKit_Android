package com.tencent.rtcube.v2.mine.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tencent.rtcube.assembly.AppTarget
import com.tencent.rtcube.v2.AppTargetResolver
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.component.ConfirmDialog
import com.tencent.rtcube.v2.component.ShowTipDialog
import com.tencent.rtcube.v2.mine.store.MineStore
import com.tencent.rtcube.v2.privacy.PrivacyEntry
import com.tencent.rtcube.v2.privacy.PrivacyPageType

@Composable
internal fun MineScreen(
    onBack: () -> Unit = {},
    onLogoutSuccess: ((Result<Unit>) -> Unit) = {},
    modifier: Modifier = Modifier,
) {
    val store = remember { MineStore() }
    val state by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSelfDetail by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        store.loadUserInfo()
        store.onLogoutResult = onLogoutSuccess
        store.onLogOffResult = {
            Toast.makeText(context, context.getString(R.string.mine_logoff_ok), Toast.LENGTH_SHORT).show()
            onLogoutSuccess(Result.success(Unit))
        }
    }

    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage.isNotEmpty()) {
            Toast.makeText(context, state.toastMessage, Toast.LENGTH_LONG).show()
            store.clearToast()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MineInfoScreen(
            store = store,
            state = state,
            onBack = onBack,
            onAvatarClick = { showSelfDetail = true },
        )

        if (state.isLogoutDialogVisible) {
            ConfirmDialog(
                message = stringResource(R.string.mine_info_dialog_logout),
                negativeText = stringResource(R.string.mine_common_btn_cancel),
                positiveText = stringResource(R.string.mine_common_btn_determine),
                onNegative = { store.hideLogoutDialog() },
                onPositive = { store.logout() },
            )
        }

        if (state.isStatementDialogVisible) {
            val appName = stringResource(R.string.mine_info_app_name)
            ShowTipDialog(
                message = stringResource(R.string.mine_info_statement_detail, appName),
                onDismiss = { store.hideStatementDialog() },
            )
        }

        if (state.isAboutVisible) {
            AboutScreen(
                store = store,
                onLogOffClick = { store.showLogOff() },
            )
        }

        if (state.isLogOffVisible) {
            LogOffScreen(
                store = store,
                state = state,
            )
        }

        if (state.isExperienceVisible) {
            ExperienceScreen(
                store = store,
            )
        }

        if (showSelfDetail) {
            SelfDetailScreen(
                store = store,
                userId = state.userId,
                onBack = { showSelfDetail = false },
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { store.destroy() }
    }
}

@Composable
private fun MineInfoScreen(
    store: MineStore,
    state: com.tencent.rtcube.v2.mine.store.MineState,
    onBack: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    val context = LocalContext.current
    val isOverseas = AppTargetResolver.getAppTarget() == AppTarget.OVERSEAS

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.mine_info_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(51.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mine_info_ic_back),
                    contentDescription = stringResource(R.string.mine_info_back_desc),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 19.dp)
                        .size(38.dp)
                        .padding(10.dp)
                        .clickable { onBack() },
                )
                Text(
                    text = stringResource(R.string.mine_info_title),
                    color = Color(0xFF000000),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Spacer(modifier = Modifier.height(58.dp))

            AsyncImage(
                model = state.avatarUrl.ifEmpty { null },
                placeholder = painterResource(id = R.drawable.mine_profile_ic_head),
                error = painterResource(id = R.drawable.mine_profile_ic_head),
                contentDescription = "mine_profile_avatar_desc",
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .clickable { onAvatarClick() },
                contentScale = ContentScale.Crop,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.nickname,
                    color = Color(0xFF000000),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Image(
                    painter = painterResource(id = R.drawable.mine_info_ic_edit),
                    contentDescription = "mine_info_edit",
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onAvatarClick() },
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = state.userId,
                color = Color(0xFF999999),
                fontSize = 16.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
            ) {
                MineMenuItem(
                    iconRes = R.drawable.mine_info_ic_privacy,
                    text = stringResource(R.string.mine_info_privacy),
                    onClick = { PrivacyEntry.pushPrivacyPage(PrivacyPageType.PrivacyCenter, context) },
                )
                if (!isOverseas) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MineMenuItem(
                        iconRes = R.drawable.mine_info_ic_statement,
                        text = stringResource(R.string.mine_info_statement),
                        onClick = { store.showStatementDialog() },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MineIcpMenuItem(
                        icpNumber = stringResource(R.string.mine_info_icp_value),
                        onClick = { store.openIcpUrl(context) },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                MineMenuItem(
                    iconRes = R.drawable.mine_info_ic_about,
                    text = stringResource(R.string.mine_info_about),
                    onClick = { store.showAbout() },
                )
            }

            if (!isOverseas) {
                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(Color.White, RoundedCornerShape(12.dp)),
                ) {
                    MineMenuItem(
                        iconRes = R.drawable.mine_info_ic_fast_debug,
                        text = stringResource(R.string.mine_info_fast_debug),
                        onClick = { store.showExperience() },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .clickable { store.showLogoutDialog() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.mine_info_logout),
                    color = Color(0xFFF33A50),
                    fontSize = 16.sp,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun MineMenuItem(
    iconRes: Int,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = Color(0xFF333333),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Image(
            painter = painterResource(id = R.drawable.mine_info_ic_arrow),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MineIcpMenuItem(
    icpNumber: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.mine_info_ic_icp),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.mine_info_icp_number),
            color = Color(0xFF333333),
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = icpNumber,
            color = Color(0xFF727A8A),
            fontSize = 12.sp,
            modifier = Modifier
                .weight(1f)
                .wrapContentWidth(Alignment.End),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Image(
            painter = painterResource(id = R.drawable.mine_info_ic_arrow),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}