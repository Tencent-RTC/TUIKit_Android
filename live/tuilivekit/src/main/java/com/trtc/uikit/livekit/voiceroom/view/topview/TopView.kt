package com.trtc.uikit.livekit.voiceroom.view.topview

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.qcloud.tuicore.TUICore
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_CLOSE_VOICE_ROOM
import com.trtc.uikit.livekit.common.PackageService.isRTCubeOrTencentRTC
import com.trtc.uikit.livekit.component.audiencelist.AudienceListView
import com.trtc.uikit.livekit.component.roominfo.LiveInfoView
import com.trtc.uikit.livekit.voiceroom.manager.VoiceRoomManager
import com.trtc.uikit.livekit.voiceroom.view.basic.BasicView
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore

class TopView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BasicView(context, attrs, defStyleAttr) {

    private var liveInfoView: LiveInfoView

    private lateinit var liveListStore: LiveListStore

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_voiceroom_top_view, this, true)
        liveInfoView = findViewById(R.id.rl_room_info)
    }

    override fun init(liveID: String, voiceRoomManager: VoiceRoomManager) {
        super.init(liveID, voiceRoomManager)
        val audienceListView = findViewById<AudienceListView>(R.id.rl_audience_list)
        audienceListView.init(liveListStore.liveState.currentLive.value)
        initReportView()
        initCloseView()
    }

    override fun initStore() {
        liveListStore = LiveListStore.shared()
    }

    private fun initReportView() {
        if (!TextUtils.equals(
                TUIRoomEngine.getSelfInfo().userId,
                liveListStore.liveState.currentLive.value.liveOwner.userID
            )
        ) {
            if (TUICore.getService("ReportViolatingService") == null) return
            val imageReport = findViewById<ImageView>(R.id.iv_report)
            imageReport.visibility = if (isRTCubeOrTencentRTC) VISIBLE else GONE
            imageReport.setOnClickListener {
                val param = HashMap<String, Any>()
                param["paramContext"] = context
                param["paramRoomId"] = liveID
                param["paramOwnerId"] = liveListStore.liveState.currentLive.value.liveOwner.userID
                TUICore.callService("ReportViolatingService", "methodDisplayReportDialog", param)
            }
        }
    }

    override fun addObserver() {
        liveInfoView.init(liveListStore.liveState.currentLive.value)
    }

    override fun removeObserver() {
        liveInfoView.unInit()
    }

    private fun initCloseView() {
        val mImageClose = findViewById<ImageView>(R.id.iv_exit)
        if (TextUtils.equals(
                liveListStore.liveState.currentLive.value.liveOwner.userID,
                TUIRoomEngine.getSelfInfo().userId
            )
        ) {
            mImageClose.setImageResource(R.drawable.livekit_anchor_icon_end_stream)
        } else {
            mImageClose.setImageResource(R.drawable.livekit_ic_audience_exit)
        }
        mImageClose.setOnClickListener {
            TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_CLOSE_VOICE_ROOM, null)
        }
    }
}