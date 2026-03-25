package com.trtc.uikit.livekit.features.anchorprepare.view.function

import android.content.Context
import android.view.View
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.videoquality.LocalMirrorSelectPanel
import com.trtc.uikit.livekit.component.videoquality.VideoQualitySelectPanel
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.MirrorType
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality

class PrepareVideoSettingPanel(context: Context) : AtomicPopover(context) {

    private lateinit var textMirror: TextView
    private lateinit var textVideoQuality: TextView

    init {
        initView()
    }

    private fun initView() {
        val view = View.inflate(context, R.layout.livekit_anchor_prepare_layout_video_setting, null)
        setContent(view)

        textMirror = view.findViewById(R.id.tv_mirror)
        textVideoQuality = view.findViewById(R.id.tv_quality_value)

        textMirror.text = mirrorTypeToString()
        textMirror.setOnClickListener {
            val videoQualityList = listOf(MirrorType.AUTO, MirrorType.ENABLE, MirrorType.DISABLE)
            val mirrorTypePanel = LocalMirrorSelectPanel(context, videoQualityList)
            mirrorTypePanel.setOnMirrorTypeSelectedListener(object :
                LocalMirrorSelectPanel.OnMirrorTypeSelectedListener {
                override fun onVideoQualitySelected(mirrorType: MirrorType) {
                    DeviceStore.shared().switchMirror(mirrorType)
                    textMirror.text = mirrorTypeToString()
                }
            })
            mirrorTypePanel.show()
        }

        textVideoQuality.setOnClickListener {
            val videoQualityList = listOf(VideoQuality.QUALITY_1080P, VideoQuality.QUALITY_720P)
            val videoQualityPanel = VideoQualitySelectPanel(context, videoQualityList)
            videoQualityPanel.setOnVideoQualitySelectedListener(object : VideoQualitySelectPanel
            .OnVideoQualitySelectedListener {
                override fun onVideoQualitySelected(videoQuality: VideoQuality) {
                    DeviceStore.shared().updateVideoQuality(videoQuality)
                    textVideoQuality.text = videoQualityToString(videoQuality)
                }

            })
            videoQualityPanel.show()
        }
        textVideoQuality.text = videoQualityToString(DeviceStore.shared().deviceState.localVideoQuality.value)
    }

    private fun mirrorTypeToString(): String {
        return when (DeviceStore.shared().deviceState.localMirrorType.value) {
            MirrorType.AUTO -> context.getString(R.string.mirror_type_auto)
            MirrorType.ENABLE -> context.getString(R.string.mirror_type_enable)
            MirrorType.DISABLE -> context.getString(R.string.mirror_type_disable)
        }
    }

    private fun videoQualityToString(quality: VideoQuality): String {
        return when (quality) {
            VideoQuality.QUALITY_1080P -> "1080P"
            VideoQuality.QUALITY_720P -> "720P"
            VideoQuality.QUALITY_540P -> "540P"
            VideoQuality.QUALITY_360P -> "360P"
        }
    }
}