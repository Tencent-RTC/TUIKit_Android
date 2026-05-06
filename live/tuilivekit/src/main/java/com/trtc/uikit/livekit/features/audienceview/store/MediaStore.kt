package com.trtc.uikit.livekit.features.audienceview.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.TextUtils
import android.util.TypedValue
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.cloud.tuikit.engine.room.TUIRoomObserver
import com.tencent.qcloud.tuicore.TUICore
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.component.beauty.tebeauty.TEBeautyManager
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONException
import org.json.JSONObject

data class MediaState(
    val isPictureInPictureMode: MutableStateFlow<Boolean>,
    val playbackQuality: MutableStateFlow<VideoQuality?>,
    val playbackQualityList: MutableStateFlow<List<VideoQuality>>,
    val bigMuteBitmap: MutableStateFlow<Bitmap?>,
    val smallMuteBitmap: MutableStateFlow<Bitmap?>
)

class MediaStore(liveID: String) {
    private val _isPictureInPictureMode = MutableStateFlow<Boolean>(false)
    private val _playbackQuality = MutableStateFlow<VideoQuality?>(null)
    private val _playbackQualityList = MutableStateFlow<List<VideoQuality>>(emptyList())
    private val _bigMuteBitmap = MutableStateFlow<Bitmap?>(null)
    private val _smallMuteBitmap = MutableStateFlow<Bitmap?>(null)

    private val roomEngine = TUIRoomEngine.sharedInstance()
    private val roomEngineObserver = RoomEngineObserver()
    private val coGuestState = CoGuestStore.create(liveID).coGuestState

    val mediaState = MediaState(
        isPictureInPictureMode = _isPictureInPictureMode,
        playbackQuality = _playbackQuality,
        playbackQualityList = _playbackQualityList,
        bigMuteBitmap = _bigMuteBitmap,
        smallMuteBitmap = _smallMuteBitmap
    )

    init {
        roomEngine.addObserver(roomEngineObserver)
        enableSwitchPlaybackQuality(true)
    }

    fun destroy() {
        LOGGER.info("destroy")
        roomEngine.removeObserver(roomEngineObserver)
        enableSwitchPlaybackQuality(false)
        _isPictureInPictureMode.value = false
        _playbackQuality.value = null
        _playbackQualityList.value = emptyList()
        _bigMuteBitmap.value = null
        _smallMuteBitmap.value = null
    }

    fun createVideoMuteBitmap(context: Context, bigResId: Int, smallResId: Int) {
        if (_bigMuteBitmap.value == null)
            _bigMuteBitmap.value = createMuteBitmap(context, bigResId)
        if (_smallMuteBitmap.value == null)
            _smallMuteBitmap.value = createMuteBitmap(context, smallResId)
    }

    fun releaseVideoMuteBitmap() {
        if (_bigMuteBitmap.value != null && _bigMuteBitmap.value?.isRecycled == false)
            _bigMuteBitmap.value?.recycle()
        if (_smallMuteBitmap.value != null && !_smallMuteBitmap.value!!.isRecycled)
            _smallMuteBitmap.value!!.recycle()
        _bigMuteBitmap.value = null
        _smallMuteBitmap.value = null
    }

    private fun createMuteBitmap(context: Context, resId: Int): Bitmap {
        val tv = TypedValue()
        context.resources.openRawResource(resId, tv)
        val opt = BitmapFactory.Options()
        opt.inDensity = tv.density
        opt.inScaled = false
        return BitmapFactory.decodeResource(context.resources, resId, opt)
    }

    fun setCustomVideoProcess() {
        TEBeautyManager.setCustomVideoProcess()
    }

    fun enablePictureInPictureMode(enable: Boolean) {
        LOGGER.info("enablePictureInPictureMode enable:$enable")
        _isPictureInPictureMode.value = enable
    }

    private fun onUserVideoSizeChanged(
        width: Int,
        height: Int
    ) {
        val playbackQuality = getVideoQuality(width, height)
        if (playbackQuality == _playbackQuality.value) {
            return
        }
        if (_playbackQualityList.value.size <= 1) {
            return
        }
        _playbackQualityList.value.contains(playbackQuality).let {
            if (!it) {
                return
            }
        }
        if (coGuestState.connected.value.size > 1 || coGuestState.applicants.value.isNotEmpty() || coGuestState.invitees.value.isNotEmpty()) {
            return
        }
        _playbackQuality.value = playbackQuality
    }

    fun enableSwitchPlaybackQuality(enable: Boolean) {
        val params = HashMap<String, Any>()
        params["enable"] = enable
        TUICore.callService("AdvanceSettingManager", "enableSwitchPlaybackQuality", params)
    }

    fun getMultiPlaybackQuality(roomId: String) {
        val jsonStr = "{\"api\":\"queryPlaybackQualityList\", \"params\":{\"roomId\": \"$roomId\"}}"
        TUIRoomEngine.sharedInstance().callExperimentalAPI(jsonStr) { jsonData ->
            if (TextUtils.isEmpty(jsonData)) {
                return@callExperimentalAPI
            }
            parseQualityJsonString(jsonData)
        }
    }

    fun switchPlaybackQuality(quality: VideoQuality) {
        try {
            val params = JSONObject()
            params.put("quality", quality.value)
            params.put("autoSwitch", false)
            val jsonObject = JSONObject()
            jsonObject.put("api", "switchPlaybackQuality")
            jsonObject.put("params", params)
            TUIRoomEngine.sharedInstance().callExperimentalAPI(jsonObject.toString(), null)
        } catch (e: JSONException) {
            LOGGER.error("Failed to build JSON for switchPlaybackQuality: ${e.message}")
        }
    }

    fun parseQualityJsonString(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)
            val dataArray = jsonObject.getJSONArray("data")

            val qualityList = ArrayList<VideoQuality>()
            for (i in 0 until dataArray.length()) {
                val quality = dataArray.getInt(i)
                if (quality == VideoQuality.QUALITY_1080P.value) {
                    qualityList.add(VideoQuality.QUALITY_1080P)
                }
                if (quality == VideoQuality.QUALITY_720P.value) {
                    qualityList.add(VideoQuality.QUALITY_720P)
                }
                if (quality == VideoQuality.QUALITY_540P.value) {
                    qualityList.add(VideoQuality.QUALITY_540P)
                }
                if (quality == VideoQuality.QUALITY_360P.value) {
                    qualityList.add(VideoQuality.QUALITY_360P)
                }
            }
            _playbackQualityList.value = qualityList
            if (qualityList.isNotEmpty()) {
                _playbackQuality.value = qualityList[0]
            }
        } catch (e: JSONException) {
            LOGGER.error("Failed to decode JSON: $jsonString,${e.message}")
        }
    }

    private fun getVideoQuality(width: Int, height: Int): VideoQuality {
        val resolution = width * height
        if (resolution <= (360 * 640)) {
            return VideoQuality.QUALITY_360P
        }
        if (resolution <= (540 * 960)) {
            return VideoQuality.QUALITY_540P
        }
        if (resolution <= (720 * 1280)) {
            return VideoQuality.QUALITY_720P
        }
        return VideoQuality.QUALITY_1080P
    }

    private inner class RoomEngineObserver : TUIRoomObserver() {
        override fun onUserVideoSizeChanged(
            roomId: String,
            userId: String,
            streamType: TUIRoomDefine.VideoStreamType,
            width: Int,
            height: Int
        ) {
            this@MediaStore.onUserVideoSizeChanged(width, height)
        }
    }

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("MediaManager")
    }
}
