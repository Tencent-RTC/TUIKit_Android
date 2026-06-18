package com.tencent.rtcube.modules.call.online.guide

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.modules.R
import com.tencent.rtcube.modules.call.online.ui.CallTitleBar
import kotlinx.coroutines.launch

/** Guide copy link (zh). */
private const val GUIDE_COPY_URL = "https://rtcube.cloud.tencent.com/com"

/** Guide copy link (en). */
private const val GUIDE_COPY_URL_EN = "https://trtc.io/demo"

/**
 * Guide home screen with two tabs: SinglePlayer and MultiPlayer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GuideHomeScreen(
    homeModel: GuideHomeModel,
    initialTabIndex: Int = 0,
    onBack: (() -> Unit)? = null,
) {
    val tabs = listOf(
        stringResource(R.string.assembly_call_guide_room_single),
        stringResource(R.string.assembly_call_guide_room_multi),
    )
    val pagerState = rememberPagerState(initialPage = initialTabIndex) { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.call_color_white)),
    ) {
        CallTitleBar(title = stringResource(R.string.assembly_call_guide_title), onBack = onBack)

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = colorResource(R.color.call_color_white),
            contentColor = colorResource(R.color.call_color_tab_select),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    height = 4.dp,
                    color = colorResource(R.color.call_color_tab_select),
                )
            },
            divider = {
                HorizontalDivider(color = colorResource(R.color.call_color_line), thickness = 1.dp)
            },
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                    modifier = Modifier.height(49.dp),
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (pagerState.currentPage == index) {
                            colorResource(R.color.call_color_tab_select)
                        } else {
                            colorResource(R.color.call_color_tab_normal)
                        },
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> GuideScreen(
                    pageType = GuidePageType.SINGLE_PLAYER,
                    homeModel = homeModel,
                    copyUrl = GUIDE_COPY_URL,
                    copyUrlEn = GUIDE_COPY_URL_EN,
                )

                1 -> GuideScreen(
                    pageType = GuidePageType.MULTI_PLAYER_WITH_WEB,
                    homeModel = homeModel,
                    copyUrl = GUIDE_COPY_URL,
                    copyUrlEn = GUIDE_COPY_URL_EN,
                )
            }
        }
    }
}