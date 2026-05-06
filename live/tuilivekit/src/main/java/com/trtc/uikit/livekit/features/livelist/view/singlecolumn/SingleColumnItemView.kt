package com.trtc.uikit.livekit.features.livelist.view.singlecolumn

import android.content.Context
import android.text.TextUtils
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
import io.trtc.tuikit.atomicx.common.imageloader.ImageOptions
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.view.LiveCoreView

class SingleColumnItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private val LOGGER = LiveKitLogger.getComponentLogger("SingleColumnItemView")
    }

    private lateinit var liveCoreView: LiveCoreView
    private lateinit var ivCoverImage: ImageView
    private lateinit var widgetViewGroup: ViewGroup
    private lateinit var liveListViewAdapter: LiveListViewAdapter
    private lateinit var widgetView: View

    private var isPlaying = false
    private var pauseByPictureInPicture = false
    private var liveInfo: LiveInfo? = null

    init {
        initView()
    }

    fun createLiveInfoView(
        adapter: LiveListViewAdapter,
        liveInfo: LiveInfo
    ) {
        liveListViewAdapter = adapter
        setLayoutBackground(liveInfo.coverURL)
        widgetView = liveListViewAdapter.createLiveInfoView(liveInfo)
        widgetViewGroup.addView(widgetView)
        this.liveInfo = liveInfo
    }

    fun updateLiveInfoView(liveInfo: LiveInfo) {
        setLayoutBackground(liveInfo.coverURL)
        stopPreviewLiveStream()
        liveListViewAdapter.updateLiveInfoView(widgetView, liveInfo)
        this.liveInfo = liveInfo
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.livelist_single_column_item, this, true)
        liveCoreView = findViewById(R.id.live_core_view)
        ivCoverImage = findViewById(R.id.cover_image)
        widgetViewGroup = findViewById(R.id.widget_view)
    }

    private fun setLayoutBackground(imageUrl: String?) {
        val url = imageUrl.takeUnless { TextUtils.isEmpty(it) } ?: DEFAULT_COVER_URL
        ImageLoader.load(
            context,
            ivCoverImage,
            url,
            ImageOptions.Builder().setBlurEffect(80f).build()
        )
    }

    fun startPreviewLiveStream(isMuteAudio: Boolean) {
        val currentLiveInfo = liveInfo ?: run {
            LOGGER.error("startPreviewLiveStream failed, roomId is empty")
            return
        }

        val roomId = currentLiveInfo.liveID
        if (TextUtils.isEmpty(roomId)) {
            LOGGER.error("startPreviewLiveStream failed, roomId is empty")
            return
        }

        if (roomId == getPictureInPictureRoomId()) {
            liveCoreView.visibility = GONE
            pauseByPictureInPicture = true
            LOGGER.info("picture in picture view is showing, startPreviewLiveStream ignore, roomId:${currentLiveInfo.liveID}")
            return
        }

        liveCoreView.visibility = VISIBLE
        LOGGER.info("startPreviewLiveStream, roomId:${currentLiveInfo.liveID}")
        setComponent(COMPONENT_LIVE_STREAM)
        liveCoreView.startPreviewLiveStream(currentLiveInfo.liveID, isMuteAudio, null)
        isPlaying = true
    }

    fun stopPreviewLiveStream() {
        val currentLiveInfo = liveInfo ?: run {
            LOGGER.error("stopPreviewLiveStream failed, roomId is empty")
            return
        }

        val roomId = currentLiveInfo.liveID
        if (TextUtils.isEmpty(roomId)) {
            LOGGER.error("stopPreviewLiveStream failed, roomId is empty")
            return
        }

        if (roomId == getPictureInPictureRoomId()) {
            liveCoreView.visibility = GONE
            LOGGER.info("picture in picture view is showing, stopPreviewLiveStream ignore, roomId:${currentLiveInfo.liveID}")
            return
        }

        LOGGER.info("stopPreviewLiveStream, roomId:$roomId, isPlaying:$isPlaying")
        if (isPlaying) {
            liveCoreView.stopPreviewLiveStream(currentLiveInfo.liveID)
            liveCoreView.visibility = GONE
            isPlaying = false
            pauseByPictureInPicture = false
        }
    }

    private fun getPictureInPictureRoomId(): String? {
        return PIPPanelStore.sharedInstance().state.roomId.value
    }

    fun isPauseByPictureInPicture(): Boolean = pauseByPictureInPicture
}
