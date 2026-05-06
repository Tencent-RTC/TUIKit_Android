package com.trtc.uikit.livekit.features.livelist.view.doublecolumn

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.COMPONENT_LIVE_STREAM
import com.trtc.uikit.livekit.common.DEFAULT_COVER_URL
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.setComponent
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore
import com.trtc.uikit.livekit.features.livelist.LiveListViewAdapter
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.view.LiveCoreView

class DoubleColumnItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private val LOGGER = LiveKitLogger.getComponentLogger("DoubleColumnItemView")

        private const val START_PLAY_VIDEO_STREAM = 10001
        private const val START_PLAY_DELAY_MILLIS = 500L
    }

    private var liveCoreView: LiveCoreView
    private var ivCoverImage: ImageView
    private var widgetViewGroup: ViewGroup
    private var liveInfo: LiveInfo? = null
    private var liveListViewAdapter: LiveListViewAdapter? = null
    private lateinit var widgetView: View
    private var isPlaying = false
    private var pauseByPictureInPicture = false

    init {
        LayoutInflater.from(context).inflate(R.layout.livelist_double_column_item, this, true)
        liveCoreView = findViewById(R.id.live_core_view)
        ivCoverImage = findViewById(R.id.cover_image)
        widgetViewGroup = findViewById(R.id.widget_view)
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                START_PLAY_VIDEO_STREAM -> {
                    removeMessages(START_PLAY_VIDEO_STREAM)
                    (msg.obj as? String)?.let { roomId ->
                        startPreviewLiveStream(roomId)
                    }
                }
            }
        }
    }

    fun createLiveInfoView(adapter: LiveListViewAdapter, info: LiveInfo) {
        liveListViewAdapter = adapter
        setLayoutBackground(info.coverURL)
        widgetView = adapter.createLiveInfoView(info)
        widgetViewGroup.addView(widgetView)
        liveInfo = info
    }

    fun updateLiveInfoView(info: LiveInfo) {
        setLayoutBackground(info.coverURL)
        stopPreviewLiveStream()
        liveListViewAdapter?.updateLiveInfoView(widgetView, info)
        liveInfo = info
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }

    private fun setLayoutBackground(imageUrl: String?) {
        ImageLoader.load(context, ivCoverImage, imageUrl ?: DEFAULT_COVER_URL, R.drawable.livelist_default_cover)
    }

    fun startPreviewLiveStreamDelay() {
        val roomId = liveInfo?.liveID ?: ""
        if (roomId.isEmpty()) {
            return
        }
        if (roomId == getPictureInPictureRoomId()) {
            LOGGER.info("picture in picture view is showing, startPreviewLiveStream ignore, roomId: $roomId")
            pauseByPictureInPicture = true
            return
        }
        handler.removeMessages(START_PLAY_VIDEO_STREAM)
        val message = handler.obtainMessage(START_PLAY_VIDEO_STREAM, roomId)
        handler.sendMessageDelayed(message, START_PLAY_DELAY_MILLIS)
    }

    private fun startPreviewLiveStream(roomId: String) {
        if (roomId != liveInfo?.liveID) return

        if (roomId == getPictureInPictureRoomId()) {
            liveCoreView.visibility = GONE
            pauseByPictureInPicture = true
            return
        }

        liveCoreView.visibility = VISIBLE
        setComponent(COMPONENT_LIVE_STREAM)
        liveCoreView.startPreviewLiveStream(roomId, true, null)
        isPlaying = true
    }

    fun stopPreviewLiveStream() {
        liveInfo?.liveID?.let { roomId ->
            handler.removeMessages(START_PLAY_VIDEO_STREAM)
            if (roomId == getPictureInPictureRoomId()) {
                liveCoreView.visibility = GONE
                return
            }
            if (isPlaying) {
                liveCoreView.stopPreviewLiveStream(roomId)
                liveCoreView.visibility = GONE
                isPlaying = false
                pauseByPictureInPicture = false
            }
        }
    }

    private fun getPictureInPictureRoomId() = PIPPanelStore.sharedInstance().state.roomId.value

    fun isPauseByPictureInPicture() = pauseByPictureInPicture
}
