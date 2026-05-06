package com.trtc.uikit.livekit.features.anchorview.view.cohost.widgets

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorview.view.BasicView
import com.trtc.uikit.livekit.features.anchorview.view.cohost.panel.AnchorCoHostOperateDialog
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CoHostForegroundWidgetsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BasicView(context, attrs, defStyleAttr) {

    private val logger = LiveKitLogger.getFeaturesLogger("CoHost- ForegroundWidgetsView")
    private lateinit var layoutUserInfo: LinearLayout
    private lateinit var textName: TextView
    private lateinit var imageMuteAudio: ImageView
    private var state = SeatInfo()
    private var subscribeStateJob: Job? = null

    fun init(manager: AnchorStore, seatInfo: SeatInfo) {
        logger.info("init userId:${seatInfo.userInfo.userID},roomId:${seatInfo.userInfo.liveID}")
        state = seatInfo
        super.init(manager)

        if (LoginStore.shared.loginState.loginUserInfo.value?.userID != seatInfo.userInfo.userID) {
            setOnClickListener { showCoHostOperateDialog() }
        }
    }

    override fun initView() {
        LayoutInflater.from(baseContext).inflate(R.layout.livekit_co_guest_foreground_widgets_view, this, true)
        layoutUserInfo = findViewById(R.id.ll_user_info)
        imageMuteAudio = findViewById(R.id.iv_mute_audio)
        textName = findViewById(R.id.tv_name)
    }

    override fun refreshView() {
        initUserNameView()
        initMuteAudioView()
    }

    private fun initMuteAudioView() {
        imageMuteAudio.visibility = if (state.userInfo.microphoneStatus == DeviceStatus.ON) GONE else VISIBLE
    }

    private fun initUserNameView() {
        if (isShowUserInfo()) {
            layoutUserInfo.visibility = VISIBLE
        } else {
            layoutUserInfo.visibility = GONE
        }
        textName.text = if (TextUtils.isEmpty(state.userInfo.userName)) state.userInfo.userID else state.userInfo.userName
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                onCoGuestChange()
            }

            launch {
                onCoHostChange()
            }
            launch {
                onPipModeObserver()
            }
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private suspend fun onPipModeObserver() {
        mediaState?.isPipModeEnabled?.collect {
            updateVisibility()
        }
    }

    private fun updateVisibility() {
        visibility = if (mediaState?.isPipModeEnabled?.value == true) GONE else VISIBLE
    }

    private suspend fun onCoGuestChange() {
        val coGuestStore = CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
        coGuestStore.coGuestState.connected.collect {
            initUserNameView()
            updateVisibility()
        }
    }

    private suspend fun onCoHostChange() {
        val coHostStore = CoHostStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
        coHostStore.coHostState.connected.collect { userList ->
            initUserNameView()
            updateVisibility()
        }
    }

    private fun isShowUserInfo(): Boolean {
        val currentLiveId = LiveListStore.shared().liveState.currentLive.value.liveID
        if (currentLiveId.isEmpty()) {
            return false
        }
        if (CoHostStore.create(currentLiveId).coHostState.connected.value.size > 1) {
            return true
        }

        if (CoGuestStore.create(currentLiveId).coGuestState.connected.value.filterNot { it.liveID != currentLiveId }.size > 1) {
            return true
        }

        return false
    }

    private fun showCoHostOperateDialog() {
        anchorStore?.let {
            val anchorManagerDialog = AnchorCoHostOperateDialog(baseContext, it)
            anchorManagerDialog.init(state)
            anchorManagerDialog.show()
        }
    }
}