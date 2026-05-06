package com.trtc.uikit.livekit.features.audienceview.view.coguest.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import com.trtc.uikit.livekit.features.audienceview.view.BasicView
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CoGuestBackgroundWidgetsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BasicView(context, attrs, defStyleAttr) {

    companion object {
        private val LOGGER = LiveKitLogger.getFeaturesLogger("coGuest-BackgroundWidgetsView")
    }

    private var seatUserInfo: SeatUserInfo = SeatUserInfo()
    private lateinit var imageAvatar: AtomicAvatar

    fun init(manager: AudienceStore, userInfo: SeatUserInfo) {
        LOGGER.info("init userId:" + userInfo.userID)
        seatUserInfo = userInfo
        super.init(manager)
    }

    override fun initView() {
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_co_guest_background_widgets_view, this, true)
        imageAvatar = findViewById(R.id.iv_avatar)
    }

    override fun refreshView() {
        imageAvatar.setContent(
            AvatarContent.URL(
                seatUserInfo.avatarURL,
                R.drawable.livekit_ic_avatar
            )
        )
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            mediaState.isPictureInPictureMode.collect {
                onPictureInPictureObserver(it)
            }
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun onPictureInPictureObserver(isPipMode: Boolean?) {
        visibility = if (isPipMode == true) {
            GONE
        } else {
            VISIBLE
        }
    }
}
