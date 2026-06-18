package com.tencent.rtcube.modules.call

import android.content.Context
import android.content.Intent
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.rtcube.assembly.AppTarget
import com.tencent.rtcube.assembly.EntranceCardStyle
import com.tencent.rtcube.assembly.ModuleConfig
import com.tencent.rtcube.assembly.ModuleEnvironment
import com.tencent.rtcube.assembly.ModuleProvider
import com.tencent.rtcube.modules.AtomicXCoreLogin
import com.tencent.rtcube.modules.R
import com.tencent.rtcube.modules.call.service.CallAntifraudHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CallModule private constructor(config: ModuleConfig) : ModuleProvider {

    override val config: ModuleConfig = config

    override fun setup(environment: ModuleEnvironment) {
        AtomicXCoreLogin.startAutoLogin(environment.context)

        val callKit = TUICallKit.createInstance(environment.context)
        callKit.enableFloatWindow(true)
        callKit.enableVirtualBackground(true)
        callKit.enableIncomingBanner(true)
        callKit.enableAITranscriber(true)

        if (environment.appTarget == AppTarget.DOMESTIC) {
            CallAntifraudHandler.register()
        }
    }

    companion object {
        fun standard(context: Context, target: AppTarget = AppTarget.DOMESTIC): CallModule {
            val config = ModuleConfig(
                identifier = "call",
                title = context.getString(R.string.assembly_call_card_title),
                description = context.getString(R.string.assembly_call_card_description),
                iconResId = R.drawable.module_ic_call,
                iconUrl = "",
                cardStyle = EntranceCardStyle.UI_COMPONENT,
                gradientColors = intArrayOf(0xFFD9E8FE.toInt(), 0xFFFFFFFF.toInt()),
                isHot = false,
                targetProvider = { ctx ->
                    Intent(ctx, CallActivity::class.java).apply {
                        putExtra(CallActivity.EXTRA_TARGET, target.name)
                    }
                },
                analyticsEvent = "call",
                iconName = "main_entrance_tuicallkit",
                iconImage = null,
            )
            return CallModule(config)
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
