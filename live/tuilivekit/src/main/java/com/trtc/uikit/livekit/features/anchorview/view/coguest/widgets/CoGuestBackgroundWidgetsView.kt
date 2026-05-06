package com.trtc.uikit.livekit.features.anchorview.view.coguest.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorview.view.BasicView
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CoGuestBackgroundWidgetsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BasicView(context, attrs, defStyleAttr) {
    private val logger = LiveKitLogger.getFeaturesLogger("coGuest-BackgroundWidgetsView")
    private var state = SeatInfo()
    private lateinit var avatar: AtomicAvatar
    private var subscribeStateJob: Job? = null

    fun init(manager: AnchorStore, seatInfo: SeatInfo) {
        logger.info("init userId:" + seatInfo.userInfo.userID)
        state = seatInfo
        super.init(manager)
    }

    override fun initView() {
        LayoutInflater.from(baseContext).inflate(R.layout.livekit_co_guest_background_widgets_view, this, true)
        avatar = findViewById(R.id.iv_avatar)
    }

    override fun refreshView() {
        initUserAvatarView()
    }

    private fun initUserAvatarView() {
        avatar.setContent(
            AvatarContent.URL(
                state.userInfo.avatarURL,
                R.drawable.livekit_ic_avatar
            )
        )
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            onPipModeObserver()
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private suspend fun onPipModeObserver() {
        mediaState?.isPipModeEnabled?.collect {
            visibility = if (it) GONE else VISIBLE
        }
    }
}