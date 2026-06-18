package com.tencent.rtcube.modules.live

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.rtcube.assembly.AnalyticEvent
import com.tencent.rtcube.assembly.AppAssembly
import com.tencent.rtcube.assembly.AppTarget
import com.tencent.rtcube.assembly.CallGuard
import com.tencent.rtcube.assembly.PrivacyAction
import com.tencent.rtcube.modules.R
import com.tencent.rtcube.modules.live.service.LiveTimeLimitHandler
import com.tencent.rtcube.modules.live.ui.LiveListScreen
import com.tencent.rtcube.v2.login.LoginEntry
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_DESTROY_LIVE_VIEW
import com.trtc.uikit.livekit.common.LiveIdentityGenerator
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore
import com.trtc.uikit.livekit.livestream.VideoLiveKit
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast

class LiveListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET = "extra_app_target"
    }

    private val appTarget: AppTarget by lazy {
        val name = intent.getStringExtra(EXTRA_TARGET)
        AppTarget.entries.find { it.name == name } ?: AppTarget.DOMESTIC
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LiveListScreen(
                onBack = { finish() },
                onCreateLive = { handleCreateLive() },
                onToggleColumnClick = { style ->
                    val map = mapOf("column_style" to style)
                    AppAssembly.analyticEventHandler?.invoke(AnalyticEvent.LiveEvent("live_toggle_column", map))
                }
            )
        }
    }

    private fun handleCreateLive() {
        if (!CallGuard.canStartNewRoom) {
            CallGuard.showCannotStartRoomToast(this)
            return
        }

        if (PIPPanelStore.sharedInstance().state.isAnchorStreaming) {
            AtomicToast.show(this, getString(R.string.assembly_live_list_exit_float_window_tip), AtomicToast.Style.ERROR)
            return
        }
        val currentUser = LoginEntry.currentUser.value ?: return
        val userId = currentUser.userId
        val token = currentUser.token
        if (appTarget == AppTarget.DOMESTIC) {
            AppAssembly.privacyActionHandler?.invoke(PrivacyAction.CheckRealNameAuth(this, userId, token) { isAuth, msg ->
                if (isAuth) {
                    startLive(userId)
                } else {
                    AtomicToast.show(this, msg, AtomicToast.Style.ERROR)
                }
            })
        } else {
            startLive(userId)
        }
    }

    private fun startLive(userId: String) {
        TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, null)
        val liveId = LiveIdentityGenerator.generateId(userId, LiveIdentityGenerator.RoomType.LIVE)
        LiveTimeLimitHandler.updateLiveID(liveId)

        VideoLiveKit.createInstance(applicationContext).startLive(liveId)
    }
}
