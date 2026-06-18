package com.tencent.rtcube.modules.live

import android.content.Context
import android.content.Intent
import com.tencent.rtcube.assembly.AnalyticEvent
import com.tencent.rtcube.assembly.AppAssembly
import com.tencent.rtcube.assembly.AppTarget
import com.tencent.rtcube.assembly.EntranceCardStyle
import com.tencent.rtcube.assembly.ModuleConfig
import com.tencent.rtcube.assembly.ModuleEnvironment
import com.tencent.rtcube.assembly.ModuleProvider
import com.tencent.rtcube.modules.AtomicXCoreLogin
import com.tencent.rtcube.modules.R
import com.tencent.rtcube.modules.live.service.LiveTimeLimitHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LiveModule private constructor(config: ModuleConfig) : ModuleProvider {

    override val config: ModuleConfig = config

    private var environment: ModuleEnvironment? = null

    override fun setup(environment: ModuleEnvironment) {
        this.environment = environment
        AtomicXCoreLogin.startAutoLogin(environment.context)
        if (environment.appTarget == AppTarget.DOMESTIC) {
            LiveTimeLimitHandler.register()
        }
    }

    companion object {
        private const val TAG = "LiveModule"
        fun standard(context: Context, target: AppTarget = AppTarget.DOMESTIC): LiveModule {
            val isOverseas = target == AppTarget.OVERSEAS
            val desResId = if (isOverseas) R.string.assembly_over_seas_live_subtitle else R.string.assembly_live_card_description
            val config = ModuleConfig(
                identifier = "live",
                title = context.getString(R.string.assembly_live_card_title),
                description = context.getString(desResId),
                iconResId = R.drawable.module_ic_live,
                iconUrl = "",
                cardStyle = EntranceCardStyle.UI_COMPONENT,
                gradientColors = intArrayOf(0xFFD9E8FE.toInt(), 0xFFFFFFFF.toInt()),
                isHot = false,
                targetProvider = { ctx ->
                    if (isOverseas) {
                        Intent(ctx, LiveOverSeasActivity::class.java)
                    } else {
                        AppAssembly.analyticEventHandler?.invoke(AnalyticEvent.LiveEvent("live_show_live_list", emptyMap()))
                        Intent(ctx, LiveListActivity::class.java).apply { putExtra(LiveListActivity.EXTRA_TARGET, target.name) }
                    }
                },
                analyticsEvent = "live",
                iconName = "main_entrance_tuilivekit",
                iconImage = null,
            )
            return LiveModule(config)
        }
    }

    override val badgeCountFlow: StateFlow<Long> = MutableStateFlow(0L)

    override val isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true)

    fun updateBadgeCount(count: Long) {
        (badgeCountFlow as MutableStateFlow).value = count
    }

    fun setVisible(visible: Boolean) {
        (isVisibleFlow as MutableStateFlow).value = visible
    }
}