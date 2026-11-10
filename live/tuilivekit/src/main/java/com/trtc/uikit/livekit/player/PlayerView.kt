package com.trtc.uikit.livekit.player

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveInfoCompletionHandler
import kotlinx.coroutines.flow.StateFlow

enum class PlayerStatus {
    IDLE,
    PLAYING,
    IN_ROOM,
    CO_GUEST,
    END
}

@SuppressLint("ViewConstructor")
open class PlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    val liveID: String,
) : FrameLayout(context, attrs, defStyleAttr) {

    val status: StateFlow<PlayerStatus> get() = impl.status

    fun setLiveInfo(liveInfo: LiveInfo) = impl.setLiveInfo(liveInfo)
    fun startPlay() = impl.startPlay()
    fun stopPlay() = impl.stopPlay()
    fun joinChannel(callback: LiveInfoCompletionHandler?) = impl.joinChannel(callback)
    fun leaveChannel() = impl.leaveChannel()
    fun setVolume(volume: Int) = impl.setVolume(volume)
    fun setJoinLiveEntranceEnabled(enabled: Boolean) = impl.setJoinLiveEntranceEnabled(enabled)
    open fun onJoinLiveClicked() {}
    open fun onCloseClicked() {}

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        impl.onAttached()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        impl.onDetached()
    }

    private val impl: PlayerViewImpl = PlayerViewImpl(host = this, liveID = liveID)
}
