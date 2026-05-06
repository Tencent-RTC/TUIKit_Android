package com.trtc.uikit.livekit.features.anchorview.view

import android.content.Context
import android.text.TextUtils
import android.view.View
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorview.view.battle.widgets.BattleInfoView
import com.trtc.uikit.livekit.features.anchorview.view.battle.widgets.BattleMemberInfoView
import com.trtc.uikit.livekit.features.anchorview.view.coguest.widgets.AnchorEmptySeatView
import com.trtc.uikit.livekit.features.anchorview.view.coguest.widgets.CoGuestBackgroundWidgetsView
import com.trtc.uikit.livekit.features.anchorview.view.coguest.widgets.CoGuestForegroundWidgetsView
import com.trtc.uikit.livekit.features.anchorview.view.cohost.widgets.CoHostBackgroundWidgetsView
import com.trtc.uikit.livekit.features.anchorview.view.cohost.widgets.CoHostForegroundWidgetsView
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.view.VideoViewAdapter
import io.trtc.tuikit.atomicxcore.api.view.ViewLayer

class AnchorVideoViewAdapter(
    private val context: Context,
    private val anchorStore: AnchorStore,
    private val onCoGuestForegroundClick: (SeatInfo?) -> Unit,
) : VideoViewAdapter {

    override fun createCoGuestView(seatInfo: SeatInfo?, viewLayer: ViewLayer?): View? {
        if (TextUtils.isEmpty(seatInfo?.userInfo?.userID)) {
            if (viewLayer == ViewLayer.BACKGROUND) {
                return AnchorEmptySeatView(context).apply {
                    if (seatInfo != null) {
                        init(anchorStore, seatInfo)
                    }
                }
            }
            return null
        }

        return if (viewLayer == ViewLayer.BACKGROUND) {
            CoGuestBackgroundWidgetsView(context).apply {
                if (seatInfo != null) {
                    init(anchorStore, seatInfo)
                }
            }
        } else {
            CoGuestForegroundWidgetsView(context).apply {
                if (seatInfo != null) {
                    init(anchorStore, seatInfo)
                }
                setOnClickListener { onCoGuestForegroundClick(seatInfo) }
            }
        }
    }

    override fun createCoHostView(seatInfo: SeatInfo?, viewLayer: ViewLayer?): View? {
        if (seatInfo == null) {
            return null
        }
        return if (viewLayer == ViewLayer.BACKGROUND) {
            CoHostBackgroundWidgetsView(context).apply {
                init(anchorStore, seatInfo)
            }
        } else {
            CoHostForegroundWidgetsView(context).apply {
                init(anchorStore, seatInfo)
            }
        }
    }

    override fun createBattleView(seatInfo: SeatInfo?): View? {
        if (seatInfo == null) {
            return null
        }
        return BattleMemberInfoView(context).apply {
            init(anchorStore, seatInfo.userInfo.userID)
        }
    }

    override fun createBattleContainerView(): View? {
        return BattleInfoView(context).apply {
            init(anchorStore)
        }
    }
}
