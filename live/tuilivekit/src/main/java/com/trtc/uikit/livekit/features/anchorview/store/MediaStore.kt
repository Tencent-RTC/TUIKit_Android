package com.trtc.uikit.livekit.features.anchorview.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.TypedValue
import com.tencent.qcloud.tuicore.TUICore
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.component.beauty.tebeauty.TEBeautyManager
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.DeviceControlPolicy
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class MediaState(
    val isAudioLocked: StateFlow<Boolean>,
    val isVideoLocked: StateFlow<Boolean>,
    val isPipModeEnabled: StateFlow<Boolean>,
    var bigMuteBitmap: Bitmap?,
    var smallMuteBitmap: Bitmap?,
)

class MediaStore() {
    private val logger = LiveKitLogger.getFeaturesLogger("MediaManager")
    private val _isAudioLocked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _isVideoLocked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _isPipModeEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val mediaState = MediaState(
        isAudioLocked = _isAudioLocked,
        isVideoLocked = _isVideoLocked,
        isPipModeEnabled = _isPipModeEnabled,
        bigMuteBitmap = null,
        smallMuteBitmap = null,
    )

    enum class MediaDevice {
        MICROPHONE,
        CAMERA
    }

    fun destroy() {
        logger.info("destroy")
        releaseVideoMuteBitmap()
        enableMultiPlaybackQuality(false)
    }

    fun enableMultiPlaybackQuality(enable: Boolean) {
        val params = hashMapOf<String, Any>("enable" to enable)
        TUICore.callService("AdvanceSettingManager", "enableMultiPlaybackQuality", params)
    }

    fun createVideoMuteBitmap(context: Context, bigResId: Int, smallResId: Int) {
        if (mediaState.bigMuteBitmap == null) {
            mediaState.bigMuteBitmap = createMuteBitmap(context, bigResId)
        }
        if (mediaState.smallMuteBitmap == null) {
            mediaState.smallMuteBitmap = createMuteBitmap(context, smallResId)
        }
    }

    private fun releaseVideoMuteBitmap() {
        mediaState.bigMuteBitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        mediaState.smallMuteBitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        mediaState.bigMuteBitmap = null
        mediaState.smallMuteBitmap = null
    }

    private fun createMuteBitmap(context: Context, resId: Int): Bitmap {
        val tv = TypedValue()
        context.resources.openRawResource(resId, tv)
        val opt = BitmapFactory.Options().apply {
            inDensity = tv.density
            inScaled = false
        }
        return BitmapFactory.decodeResource(context.resources, resId, opt)
    }

    fun setCustomVideoProcess() {
        TEBeautyManager.setCustomVideoProcess()
    }

    fun enablePipMode(enable: Boolean) {
        _isPipModeEnabled.update { enable }
    }

    fun disableUserMediaDevice(userId: String, device: MediaDevice, isDisable: Boolean) {
        val liveId = LiveListStore.shared().liveState.currentLive.value.liveID
        if (liveId.isEmpty()) {
            return
        }
        if (device == MediaDevice.CAMERA) {
            if (isDisable) {
                LiveSeatStore.create(liveId).openRemoteCamera(
                    userId, DeviceControlPolicy.UNLOCK_ONLY,
                    object : CompletionHandler {
                        override fun onSuccess() {
                            logger.info("openRemoteCamera:[success]")
                        }

                        override fun onFailure(code: Int, desc: String) {
                            logger.error("openRemoteCamera failed:code:$code,desc:$desc")
                            ErrorLocalized.onError(code)
                        }

                    })
            } else {
                LiveSeatStore.create(liveId).closeRemoteCamera(
                    userId,
                    object : CompletionHandler {
                        override fun onSuccess() {
                            logger.info("closeRemoteCamera:[success]")
                        }

                        override fun onFailure(code: Int, desc: String) {
                            logger.error("closeRemoteCamera failed:code:$code,desc:$desc")
                            ErrorLocalized.onError(code)
                        }

                    })
            }
        } else {
            if (isDisable) {
                LiveSeatStore.create(liveId).openRemoteMicrophone(
                    userId, DeviceControlPolicy.UNLOCK_ONLY,
                    object : CompletionHandler {
                        override fun onSuccess() {
                            logger.info("openRemoteMicrophone:[success]")
                        }

                        override fun onFailure(code: Int, desc: String) {
                            logger.error("openRemoteMicrophone failed:code:$code,desc:$desc")
                            ErrorLocalized.onError(code)
                        }

                    })
            } else {
                LiveSeatStore.create(liveId).closeRemoteMicrophone(
                    userId,
                    object : CompletionHandler {
                        override fun onSuccess() {
                            logger.info("closeRemoteMicrophone:[success]")
                        }

                        override fun onFailure(code: Int, desc: String) {
                            logger.error("closeRemoteMicrophone failed:code:$code,desc:$desc")
                            ErrorLocalized.onError(code)
                        }

                    })
            }
        }

    }
}
