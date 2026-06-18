package com.tencent.rtcube.v2.main.overseas

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.component.CommonNavigationBar
import com.tencent.rtcube.v2.main.model.ResolvedModule
import com.tencent.rtcube.v2.main.overseas.store.OverSeasEntranceStore
import com.tencent.rtcube.v2.main.overseas.views.OverSeasDiscoveryCard
import com.tencent.rtcube.v2.main.overseas.views.OverSeasModuleCard

private const val CONTACT_US_URL = "https://trtc.io/contact"

@Composable
fun OverSeasEntranceScreen(
    onAvatarClick: () -> Unit = {},
    onTrackAnalytics: ((String) -> Unit)? = null,
) {
    OverSeasEntranceContent(onAvatarClick = onAvatarClick, onTrackAnalytics = onTrackAnalytics)
}

@Composable
fun OverSeasEntranceContent(
    onAvatarClick: () -> Unit = {},
    onTrackAnalytics: ((String) -> Unit)? = null,
) {
    val store = remember { OverSeasEntranceStore(onTrackAnalytics = onTrackAnalytics) }
    val state by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val onContactClick: () -> Unit = {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, CONTACT_US_URL.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { e ->
            Log.w("OverseasEntrance", "Failed to open contact url", e)
        }
    }

    LaunchedEffect(Unit) {
        store.loadModules()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            CommonNavigationBar(
                logoRes = R.drawable.main_english_logo,
                avatarUrl = state.userAvatarUrl,
                onAvatarClick = onAvatarClick,
                backgroundColor = colorResource(R.color.main_navbar_bg),
            )

            TabBar(
                selectedIndex = state.selectedTabIndex,
                onTabSelected = { store.selectTab(it) },
            )

            Crossfade(
                targetState = state.selectedTabIndex,
                label = "tab_content",
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF1F3F8)),
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> ProductsContent(
                        modules = state.productsModules.filter { it.isVisible },
                        onModuleClick = { module ->
                            handleModuleClick(store, context, module)
                        },
                    )

                    1 -> DiscoveryContent(
                        modules = state.discoveryModules.filter { it.isVisible },
                        onModuleClick = { module ->
                            handleModuleClick(store, context, module)
                        },
                        onContactClick = onContactClick,
                    )
                }
            }
        }

        ContactFloatButton(
            onClick = onContactClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 80.dp),
        )
    }

    DisposableEffect(Unit) {
        onDispose { store.destroy() }
    }
}

private fun handleModuleClick(
    store: OverSeasEntranceStore,
    context: android.content.Context,
    module: ResolvedModule,
) {
    val intent = store.selectModule(context, module)
    runCatching { context.startActivity(intent) }.onFailure { e ->
        Log.w("OverseasEntrance", "Failed to launch module: ${module.config.identifier}", e)
        Toast.makeText(context, "Coming Soon", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun TabBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF1F3F8))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFECEFF6))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(
                text = stringResource(R.string.main_overseas_tab_products),
                isSelected = selectedIndex == 0,
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(1f),
            )
            TabItem(
                text = stringResource(R.string.main_overseas_tab_discovery),
                isSelected = selectedIndex == 1,
                onClick = { onTabSelected(1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isSelected) Color.White else Color.Transparent
    val textColor = if (isSelected) Color(0xFF1C66E5) else Color(0xFF4E5461)
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

    Box(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = textColor,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProductsContent(
    modules: List<ResolvedModule>,
    onModuleClick: (ResolvedModule) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        itemsIndexed(
            items = modules,
            key = { _, module -> module.config.identifier },
        ) { _, module ->
            OverSeasModuleCard(
                module = module,
                onClick = { onModuleClick(module) },
            )
        }
    }
}

@Composable
private fun DiscoveryContent(
    modules: List<ResolvedModule>,
    onModuleClick: (ResolvedModule) -> Unit,
    onContactClick: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        item(key = "discovery_tip") {
            DiscoveryTipText(
                onContactClick = onContactClick,
                modifier = Modifier.padding(top = 0.dp, bottom = 8.dp),
            )
        }
        itemsIndexed(
            items = modules,
            key = { _, module -> module.config.identifier },
        ) { _, module ->
            OverSeasDiscoveryCard(
                module = module,
                onClick = { onModuleClick(module) },
            )
        }
    }
}

@Composable
private fun DiscoveryTipText(
    onContactClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tipText = stringResource(R.string.main_overseas_discovery_tips)
    val contactText = stringResource(R.string.main_overseas_contact_us)
    val startIndex = tipText.indexOf(contactText)

    if (startIndex >= 0) {
        val annotatedString = buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFF727A8A), fontSize = 10.sp)) {
                append(tipText.substring(0, startIndex))
            }
            pushStringAnnotation(tag = "contact", annotation = "contact")
            withStyle(SpanStyle(color = Color(0xFF1C66E5), fontSize = 10.sp)) {
                append(contactText)
            }
            pop()
        }

        ClickableText(
            text = annotatedString,
            onClick = { offset ->
                annotatedString.getStringAnnotations("contact", offset, offset)
                    .firstOrNull()?.let { onContactClick() }
            },
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        Text(
            text = tipText,
            fontSize = 10.sp,
            color = Color(0xFF727A8A),
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ContactFloatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = androidx.compose.ui.res.painterResource(R.drawable.overseas_contact_float_button),
        contentDescription = null,
        modifier = modifier
            .size(80.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() },
    )
}