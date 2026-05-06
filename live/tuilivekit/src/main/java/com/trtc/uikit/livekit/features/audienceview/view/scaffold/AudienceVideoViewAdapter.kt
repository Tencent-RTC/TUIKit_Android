package com.trtc.uikit.livekit.features.audienceview.view.scaffold

import android.content.Context
import android.text.TextUtils
import android.view.View
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import com.trtc.uikit.livekit.features.audienceview.view.battle.widgets.BattleInfoView
import com.trtc.uikit.livekit.features.audienceview.view.battle.widgets.BattleMemberInfoView
import com.trtc.uikit.livekit.features.audienceview.view.coguest.panel.TypeSelectDialog
import com.trtc.uikit.livekit.features.audienceview.view.coguest.widgets.AudienceEmptySeatView
import com.trtc.uikit.livekit.features.audienceview.view.coguest.widgets.CoGuestBackgroundWidgetsView
import com.trtc.uikit.livekit.features.audienceview.view.coguest.widgets.CoGuestForegroundWidgetsView
import com.trtc.uikit.livekit.features.audienceview.view.cohost.widgets.CoHostBackgroundWidgetsView
import com.trtc.uikit.livekit.features.audienceview.view.cohost.widgets.CoHostForegroundWidgetsView
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.view.VideoViewAdapter
import io.trtc.tuikit.atomicxcore.api.view.ViewLayer
import java.lang.ref.WeakReference

class AudienceVideoViewAdapter(
    context: Context,
    private val audienceStore: AudienceStore,
    private val callback: Callback,
) : VideoViewAdapter {

    interface Callback {
        fun showCoGuestManageDialog(userInfo: LiveUserInfo)
    }

    private val weakContext = WeakReference(context)

    override fun createCoGuestView(
        seatInfo: SeatInfo?,
        viewLayer: ViewLayer?,
    ): View? {
        val context = weakContext.get()
        if (context == null) {
            LOGGER.error("createCoGuestView: context is null")
            return null
        }
        if (TextUtils.isEmpty(seatInfo?.userInfo?.userID)) {
            return if (viewLayer == ViewLayer.BACKGROUND) {
                val emptySeatView = AudienceEmptySeatView(context)
                emptySeatView.init(audienceStore)
                emptySeatView.tag = seatInfo
                emptySeatView.setOnClickListener { v ->
                    val coGuestState = audienceStore.getCoGuestState()
                    val viewState = audienceStore.getViewState()
                    if (viewState.isApplyingToTakeSeat.value
                        || !coGuestState.connected.value.none { it.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID }
                        || viewState.isTakeSeatDialogShowing.value
                    ) {
                        return@setOnClickListener
                    }
                    viewState.isTakeSeatDialogShowing.value = true
                    val seat = v.tag as SeatInfo
                    val typeSelectDialog = TypeSelectDialog(context, audienceStore, seat.index)
                    typeSelectDialog.setOnDismissListener { viewState.isTakeSeatDialogShowing.value = false }
                    typeSelectDialog.show()
                }
                emptySeatView
            } else {
                null
            }
        }
        return if (ViewLayer.BACKGROUND == viewLayer) {
            val backgroundWidgetsView = CoGuestBackgroundWidgetsView(context)
            backgroundWidgetsView.init(audienceStore, seatInfo?.userInfo ?: SeatUserInfo())
            backgroundWidgetsView
        } else {
            val foregroundWidgetsView = CoGuestForegroundWidgetsView(context)
            foregroundWidgetsView.init(audienceStore, seatInfo?.userInfo ?: SeatUserInfo())
            foregroundWidgetsView.setOnClickListener {
                callback.showCoGuestManageDialog(
                    LiveUserInfo(
                        userID = seatInfo?.userInfo?.userID ?: "",
                        userName = seatInfo?.userInfo?.userName ?: "",
                        avatarURL = seatInfo?.userInfo?.avatarURL ?: "",
                    )
                )
            }
            foregroundWidgetsView
        }
    }

    override fun createCoHostView(
        seatInfo: SeatInfo?,
        viewLayer: ViewLayer?,
    ): View? {
        val context = weakContext.get()
        if (context == null) {
            LOGGER.error("createCoHostView: context is null")
            return null
        }
        return if (ViewLayer.BACKGROUND == viewLayer) {
            val backgroundWidgetsView = CoHostBackgroundWidgetsView(context)
            backgroundWidgetsView.init(audienceStore, seatInfo?.userInfo ?: SeatUserInfo())
            backgroundWidgetsView
        } else {
            val foregroundWidgetsView = CoHostForegroundWidgetsView(context)
            foregroundWidgetsView.init(audienceStore, seatInfo?.userInfo ?: SeatUserInfo())
            foregroundWidgetsView
        }
    }

    override fun createBattleView(seatInfo: SeatInfo?): View? {
        val context = weakContext.get()
        if (context == null) {
            LOGGER.error("createBattleView: context is null")
            return null
        }
        val battleMemberInfoView = BattleMemberInfoView(context)
        battleMemberInfoView.init(audienceStore, seatInfo?.userInfo?.userID ?: "")
        return battleMemberInfoView
    }

    override fun createBattleContainerView(): View? {
        val context = weakContext.get()
        if (context == null) {
            LOGGER.error("createBattleContainerView: context is null")
            return null
        }
        val battleInfoView = BattleInfoView(context)
        battleInfoView.init(audienceStore)
        return battleInfoView
    }

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("AudienceVideoViewAdapter")
    }
}
