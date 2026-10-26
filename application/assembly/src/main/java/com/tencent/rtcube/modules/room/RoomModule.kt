package com.tencent.rtcube.modules.room

import android.content.Context
import android.content.Intent
import com.tencent.rtcube.assembly.CallGuard
import com.tencent.rtcube.assembly.EntranceCardStyle
import com.tencent.rtcube.assembly.ModuleConfig
import com.tencent.rtcube.assembly.ModuleEnvironment
import com.tencent.rtcube.assembly.ModuleProvider
import com.tencent.rtcube.modules.AtomicXCoreLogin
import com.tencent.rtcube.modules.R
import com.trtc.uikit.roomkit.RoomHomeActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RoomModule private constructor(config: ModuleConfig) : ModuleProvider {

    override val config: ModuleConfig = config

    override fun setup(environment: ModuleEnvironment) {
        AtomicXCoreLogin.startAutoLogin(environment.context)
    }

    companion object {
        fun standard(context: Context): RoomModule {
            val config = ModuleConfig(
                identifier = "conference",
                title = context.getString(R.string.assembly_room_card_title),
                description = context.getString(R.string.assembly_room_card_description),
                iconResId = R.drawable.module_ic_room,
                iconUrl = "",
                cardStyle = EntranceCardStyle.UI_COMPONENT,
                gradientColors = intArrayOf(0xFFD9E8FE.toInt(), 0xFFFFFFFF.toInt()),
                isHot = false,
                targetProvider = { ctx ->
                    if (!CallGuard.canStartNewRoom) {
                        CallGuard.showCannotStartRoomToast(ctx)
                        return@ModuleConfig null
                    }
                    Intent(ctx, RoomHomeActivity::class.java)
                },
                analyticsEvent = "conference",
                iconName = "main_entrance_room",
                iconImage = null,
            )
            return RoomModule(config)
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
