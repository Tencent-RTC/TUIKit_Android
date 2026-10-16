package com.trtc.uikit.livekit.player.video

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitResources
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.common.basic.imageloader.ImageLoader
import com.trtc.uikit.livekit.common.basic.imageloader.ImageOptions
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.view.AtomicLiveView
import io.trtc.tuikit.atomicxcore.api.view.AtomicLiveViewChangeAdapter
import io.trtc.tuikit.atomicxcore.api.view.UnitInfo

class PlayerVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    internal val coreView = AtomicLiveView(context)
    private var backgroundView = AppCompatImageView(context)
    private var liveInfo: LiveInfo = LiveInfo()
    private val logger = LiveKitLogger.Companion.getComponentLogger("VideoView")

    val unitAdapter = object : AtomicLiveViewChangeAdapter {
        override fun onCreateUnitView(unitInfo: UnitInfo?): View? {
            unitInfo?.let {
                logger.info("onCreateUnitView: $unitInfo")
                return UnitView(context, it, liveInfo)
            }
            return null
        }

        override fun onUpdateUnitView(
            unitInfo: UnitInfo?,
            view: View?
        ) {
            logger.info("onUpdateUnitView: view=$view, unitInfo=$unitInfo")
            if (view is UnitView) {
                view.setUnitInfo(unitInfo ?: UnitInfo())
            }
        }

        override fun onVideoViewFrameChanged(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float
        ) {
        }
    }

    init {
        addView(backgroundView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        coreView.setViewChangeAdapter(unitAdapter)
        addView(coreView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setLiveInfo(liveInfo: LiveInfo) {
        this.liveInfo = liveInfo
        setBackground(liveInfo)
    }

    private fun setBackground(liveInfo: LiveInfo) {
        val url = liveInfo.backgroundURL.ifEmpty {
            liveInfo.coverURL.ifEmpty { LiveKitResources.defaultBackgroundURL }
        }

        val config = ImageOptions.Builder()
            .setBlurEffect(80f)
            .setPlaceImage(R.drawable.livekit_video_background)
            .setErrorImage(R.drawable.livekit_video_background)
            .build()
        ImageLoader.load(context, backgroundView, url, config = config)
    }
}
