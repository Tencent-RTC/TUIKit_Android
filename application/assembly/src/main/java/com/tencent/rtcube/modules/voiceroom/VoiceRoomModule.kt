package com.tencent.rtcube.modules.voiceroom

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
import com.tencent.rtcube.modules.voiceroom.karaoke.OnlineMusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoiceRoomModule private constructor(config: ModuleConfig) : ModuleProvider {
    override val config: ModuleConfig = config

    override fun setup(environment: ModuleEnvironment) {
        AtomicXCoreLogin.startAutoLogin(environment.context)
        OnlineMusicService.setLicense(key = environment.karaokeLicenseKey, url = environment.karaokeLicenseUrl)
    }

    companion object {
        fun standard(context: Context, target: AppTarget = AppTarget.DOMESTIC): VoiceRoomModule {
            val config = ModuleConfig(
                identifier = "voice_chat",
                title = context.getString(R.string.assembly_voiceroom_card_title),
                description = context.getString(R.string.assembly_voiceroom_card_description),
                iconResId = R.drawable.module_ic_voiceroom,
                iconUrl = "",
                cardStyle = EntranceCardStyle.STANDARD,
                gradientColors = intArrayOf(),
                isHot = false,
                targetProvider = { ctx ->
                    val map = emptyMap<String, String>()
                    AppAssembly.analyticEventHandler?.invoke(AnalyticEvent.VoiceRoomEvent("voice_room_show_live_list", map))
                    Intent(ctx, VoiceRoomListActivity::class.java).apply {
                        putExtra(VoiceRoomListActivity.EXTRA_TARGET, target.name)
                    }
                },
                analyticsEvent = "voice_chat",
                iconName = "main_entrance_voice_room",
                iconImage = null,
            )
            return VoiceRoomModule(config)
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