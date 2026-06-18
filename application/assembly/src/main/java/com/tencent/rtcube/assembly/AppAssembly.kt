package com.tencent.rtcube.assembly

import android.app.Activity
import android.content.Context
import com.tencent.rtcube.modules.call.CallModule
import com.tencent.rtcube.modules.live.LiveModule
import com.tencent.rtcube.modules.room.RoomModule
import com.tencent.rtcube.modules.voiceroom.VoiceRoomModule

enum class AppTarget {
    DOMESTIC, // RTCube
    OVERSEAS, // TencentRTC
    LAB,      // RTCubeLab
}

sealed class PrivacyAction {
    /** Call antifraud reminder (red banner + gentle reminder popup) */
    object ShowAntifraudReminder : PrivacyAction()

    /** Real-name verification check, completion: (whether verified, message) */
    data class CheckRealNameAuth(
        val context: Activity,
        val userId: String,
        val token: String,
        val completion: (Boolean, String) -> Unit,
    ) : PrivacyAction()

    /** Face verification token fetch, completion: (success, faceToken) */
    data class ShowFaceIdTokenVerify(
        val userId: String,
        val token: String,
        val completion: (Boolean, String) -> Unit,
    ) : PrivacyAction()

    /** Live duration limit alert popup */
    object ShowLiveTimeLimitAlert : PrivacyAction()

    /** Live remaining 1-minute Toast prompt */
    object ShowLiveRemainingOneMinToast : PrivacyAction()
}

sealed class AnalyticEvent {
    data class LiveEvent(val name: String, val params: Map<String, Any?>) : AnalyticEvent()
    data class VoiceRoomEvent(val name: String, val params: Map<String, Any?>) : AnalyticEvent()
}

object AppAssembly {
    var privacyActionHandler: ((PrivacyAction) -> Unit)? = null
    var analyticEventHandler: ((AnalyticEvent) -> Unit)? = null

    fun allModuleProviders(context: Context, target: AppTarget = AppTarget.DOMESTIC): List<ModuleProvider> {
        return when (target) {
            AppTarget.OVERSEAS -> {
                listOf(
                    CallModule.standard(context, target),
                    RoomModule.standard(context),
                    LiveModule.standard(context, target), 
                    VoiceRoomModule.standard(context, target),  
                )
            }

            AppTarget.DOMESTIC, AppTarget.LAB -> {
                listOf(
                    CallModule.standard(context, target),
                    LiveModule.standard(context, target),
                    RoomModule.standard(context),
                    VoiceRoomModule.standard(context, target),
                )
            }
        }
    }
}
