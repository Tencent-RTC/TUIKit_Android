package com.tencent.rtcube.v2.main.domestic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import com.tencent.rtcube.assembly.AppTarget
import com.tencent.rtcube.assembly.EntranceCardStyle
import com.tencent.rtcube.v2.AppTargetResolver
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.component.CommonNavigationBar
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.LoginMode
import com.tencent.rtcube.v2.main.domestic.service.ModulePermissionService
import com.tencent.rtcube.v2.main.domestic.store.EntranceStore
import com.tencent.rtcube.v2.main.domestic.views.ModuleCard
import com.tencent.rtcube.v2.main.model.ResolvedModule

@Composable
fun DomesticEntranceScreen(
    onAvatarClick: () -> Unit = {},
    onTriggerFaceAuthDialog: ((onFaceAuthDialogShowed: (Boolean) -> Unit) -> Unit)? = null,
    onTrackAnalytics: ((String) -> Unit)? = null,
) {
    val store = remember { EntranceStore(onTrackAnalytics = onTrackAnalytics) }
    val state by store.state.collectAsState()
    val currentUser by LoginEntry.currentUser.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        store.loadModules()
    }

    LaunchedEffect(currentUser?.userId) {
        if (currentUser == null) {
            ModulePermissionService.clearUserBlackList()
            return@LaunchedEffect
        }
        if (AppTarget.DOMESTIC == AppTargetResolver.getAppTarget()) {
            ModulePermissionService.loadUserBlackList(context)
        }
        // Report bar is shown only for non-debug and non-MOA users.
        val mode = LoginEntry.loggedInMode
        store.updateReportViewVisible(mode != LoginMode.DEBUG_AUTH && mode != LoginMode.IOA_AUTH)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CommonNavigationBar(
            logoRes = resolveLogoRes(context),
            avatarUrl = state.userAvatarUrl,
            onAvatarClick = onAvatarClick,
            backgroundColor = colorResource(R.color.main_navbar_bg),
        )

        if (state.isReportViewVisible) {
            Text(
                text = stringResource(id = R.string.main_report_hint),
                fontSize = 12.sp,
                color = colorResource(R.color.main_report_bar_text),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://cloud.tencent.com/act/event/report-platform"),
                            )
                            context.startActivity(intent)
                        }
                    }
                    .background(colorResource(R.color.main_report_bar_bg))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        ModuleGrid(
            modules = state.modules,
            onModuleClick = { index ->
                val intent = store.selectModule(
                    context = context,
                    index = index,
                    onTriggerFaceAuthDialog = onTriggerFaceAuthDialog,
                )
                if (intent != null) {
                    runCatching { context.startActivity(intent) }.onFailure { e ->
                        Log.w("EntranceScreen", "Failed to launch module at $index", e)
                    }
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose { store.destroy() }
    }
}

@Composable
private fun ModuleGrid(
    modules: List<ResolvedModule>,
    onModuleClick: (index: Int) -> Unit,
) {
    val rows = remember(modules) {
        buildModuleRows(modules.withIndex().filter { it.value.isVisible })
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = rows,
            key = { row -> row.joinToString("|") { it.value.config.identifier } },
        ) { row ->
            ModuleRow(row = row, onModuleClick = onModuleClick)
        }

        item {
            Text(
                text = stringResource(id = R.string.main_trial_hint),
                fontSize = 12.sp,
                color = colorResource(R.color.main_footer_text),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

private fun buildModuleRows(visible: List<IndexedValue<ResolvedModule>>): List<List<IndexedValue<ResolvedModule>>> {
    val rows = mutableListOf<List<IndexedValue<ResolvedModule>>>()
    var pending: IndexedValue<ResolvedModule>? = null
    visible.forEach { item ->
        if (item.value.config.cardStyle == EntranceCardStyle.BANNER) {
            // Flush any pending half-row first so a banner always sits on its own row.
            pending?.let { rows.add(listOf(it)) }
            pending = null
            rows.add(listOf(item))
        } else if (pending == null) {
            pending = item
        } else {
            rows.add(listOf(pending!!, item))
            pending = null
        }
    }
    pending?.let { rows.add(listOf(it)) }
    return rows
}

@Composable
private fun ModuleRow(
    row: List<IndexedValue<ResolvedModule>>,
    onModuleClick: (index: Int) -> Unit,
) {
    if (row.size == 1 && row.first().value.config.cardStyle == EntranceCardStyle.BANNER) {
        val item = row.first()
        ModuleCard(
            module = item.value,
            onClick = { onModuleClick(item.index) },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        row.forEach { item ->
            ModuleCard(
                module = item.value,
                onClick = { onModuleClick(item.index) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        if (row.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun resolveLogoRes(context: Context): Int {
    val language = ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language
    return if (language == "zh") {
        R.drawable.main_simplified_chinese_logo
    } else {
        R.drawable.main_english_logo
    }
}
