package com.trtc.uikit.livekit.livelist.viewpager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.player.PlayerView
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveInfoCompletionHandler

internal class PageCell() : Fragment() {
    companion object {
        private val LOGGER = LiveKitLogger.getComponentLogger("PageCell")
    }

    private var liveInfo: LiveInfo? = null
    private var getSwipeBehavior: (() -> SwipeBehavior)? = null
    private var onCloseClicked: (() -> Unit)? = null
    private var onJoinClicked: (() -> Unit)? = null
    private var onDidSlideInCallback: ((PlayerView) -> Unit)? = null
    private var onDidSlideOutCallback: ((PlayerView) -> Unit)? = null
    private var onJoinChannelStart: (() -> Unit)? = null
    private var onJoinChannelResult: ((Boolean) -> Unit)? = null

    private var playerView: PlayerView? = null

    constructor(
        liveInfo: LiveInfo,
        getSwipeBehavior: () -> SwipeBehavior,
        onCloseClicked: () -> Unit,
        onJoinClicked: () -> Unit,
        onDidSlideIn: (PlayerView) -> Unit,
        onDidSlideOut: (PlayerView) -> Unit,
        onJoinChannelStart: () -> Unit,
        onJoinChannelResult: (Boolean) -> Unit,
    ) : this() {
        this.liveInfo = liveInfo
        this.getSwipeBehavior = getSwipeBehavior
        this.onCloseClicked = onCloseClicked
        this.onJoinClicked = onJoinClicked
        this.onDidSlideInCallback = onDidSlideIn
        this.onDidSlideOutCallback = onDidSlideOut
        this.onJoinChannelStart = onJoinChannelStart
        this.onJoinChannelResult = onJoinChannelResult
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val info = liveInfo ?: return null
        val ctx = inflater.context
        LOGGER.info("createPlayerView: ${info.liveID}, this: ${hashCode()}")
        val view = object : PlayerView(ctx, liveID = info.liveID) {
            override fun onCloseClicked() {
                this@PageCell.onCloseClicked?.invoke()
            }

            override fun onJoinLiveClicked() {
                this@PageCell.onJoinClicked?.invoke()
            }
        }.apply {
            setLiveInfo(info)
            setJoinLiveEntranceEnabled(getSwipeBehavior?.invoke() == SwipeBehavior.PREVIEW_ONLY)
        }
        playerView = view
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playerView = null
    }

    fun updateData(info: LiveInfo) {
        this.liveInfo = info
        playerView?.setLiveInfo(info)
    }

    fun startPreview() {
        val player = playerView ?: return
        LOGGER.info("startPreview: ${player.liveID}, this: ${hashCode()}")
        player.setVolume(0)
        player.startPlay()
    }

    fun stopPreview() {
        val player = playerView ?: return
        LOGGER.info("stopPreview: ${player.liveID}, this: ${hashCode()}")
        player.stopPlay()
    }

    fun joinChannel() {
        val player = playerView ?: return
        LOGGER.info("onDidSlideIn: ${player.liveID}, this: ${hashCode()}")
        player.setVolume(100)
        if (getSwipeBehavior?.invoke() == SwipeBehavior.AUTO_ENTER_ROOM) {
            onJoinChannelStart?.invoke()
            player.joinChannel(object : LiveInfoCompletionHandler {
                override fun onFailure(code: Int, desc: String) {
                    onJoinChannelResult?.invoke(false)
                }

                override fun onSuccess(liveInfo: LiveInfo) {
                    onJoinChannelResult?.invoke(true)
                }
            })
        }
        onDidSlideInCallback?.invoke(player)
    }

    fun leaveChannel() {
        val player = playerView ?: return
        LOGGER.info("onDidSlideOut: ${player.liveID}, this: ${hashCode()}")
        player.stopPlay()
        if (getSwipeBehavior?.invoke() == SwipeBehavior.AUTO_ENTER_ROOM) {
            player.leaveChannel()
        }
        onDidSlideOutCallback?.invoke(player)
    }

    fun setJoinLiveEntranceEnabled(enabled: Boolean) {
        playerView?.setJoinLiveEntranceEnabled(enabled)
    }
}
