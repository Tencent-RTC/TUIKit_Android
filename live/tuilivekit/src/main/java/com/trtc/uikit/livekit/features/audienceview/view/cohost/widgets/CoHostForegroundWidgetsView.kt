package com.trtc.uikit.livekit.features.audienceview.view.cohost.widgets

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import com.trtc.uikit.livekit.features.audienceview.view.BasicView
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CoHostForegroundWidgetsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BasicView(context, attrs, defStyleAttr) {

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("CoHostWidgetsView")
    }

    private lateinit var layoutUserInfo: LinearLayout
    private lateinit var textName: TextView
    private lateinit var imageMuteAudio: ImageView
    private var seatUserInfo: SeatUserInfo = SeatUserInfo()

    fun init(manager: AudienceStore, seatInfo: SeatUserInfo) {
        LOGGER.info("init userId:" + seatInfo.userID + ",liveID:" + seatInfo.liveID)
        seatUserInfo = seatInfo
        super.init(manager)
    }

    override fun initView() {
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_co_guest_foreground_widgets_view, this, true)
        layoutUserInfo = findViewById(R.id.ll_user_info)
        imageMuteAudio = findViewById(R.id.iv_mute_audio)
        textName = findViewById(R.id.tv_name)
    }

    override fun refreshView() {
        initUserNameView()
        initMuteAudioView()
    }

    private fun initMuteAudioView() {
        imageMuteAudio.visibility =
            if (seatUserInfo.microphoneStatus == DeviceStatus.ON) GONE else VISIBLE
    }

    private fun initUserNameView() {
        if (isShowUserInfo()) {
            layoutUserInfo.visibility = VISIBLE
        } else {
            layoutUserInfo.visibility = GONE
        }
        textName.text =
            if (TextUtils.isEmpty(seatUserInfo.userName)) seatUserInfo.userID else seatUserInfo.userName
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                audienceStore.getCoGuestState().connected.collect {
                    onCoGuestChange()
                }
            }
            launch {
                audienceStore.getCoHostState().connected.collect {
                    onCoHostChange()
                }
            }
            launch {
                mediaState.isPictureInPictureMode.collect {
                    updateVisibility()
                }
            }
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun onCoGuestChange() {
        initUserNameView()
        updateVisibility()
    }

    private fun onCoHostChange() {
        initUserNameView()
        updateVisibility()
    }

    private fun updateVisibility() {
        visibility = if (mediaState.isPictureInPictureMode.value == true) {
            GONE
        } else {
            VISIBLE
        }
    }

    private fun isShowUserInfo(): Boolean {
        if (audienceStore.getCoHostState().connected.value.size > 1) {
            return true
        }
        if (audienceStore.getCoGuestState().connected.value.size > 1) {
            return true
        }
        return false
    }
}
