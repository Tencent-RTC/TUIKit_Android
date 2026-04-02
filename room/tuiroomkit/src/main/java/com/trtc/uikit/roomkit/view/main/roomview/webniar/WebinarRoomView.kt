package com.trtc.uikit.roomkit.view.main.roomview

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.contains
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine
import com.tencent.cloud.tuikit.engine.common.TUIVideoView
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine.SeatFullInfo
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine.UserInfo
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine.VideoStreamType
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.cloud.tuikit.engine.room.TUIRoomObserver
import com.tencent.trtc.TRTCCloudDef
import com.tencent.trtc.TRTCCloudDef.TRTC_VIDEO_RENDER_MODE_FILL
import com.tencent.trtc.TRTCCloudDef.TRTC_VIDEO_RENDER_MODE_FIT
import com.tencent.trtc.TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.utils.dpToPx
import com.trtc.uikit.roomkit.base.utils.getScreenWidth
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import org.json.JSONObject

data class VideoView(
    var userId: String = "",
    var videoView: TUIVideoView
)

class WebinarRoomView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val logger = RoomKitLogger.getLogger("WebinarRoomView")
    private val oid: Int = hashCode()

    private val videoLandscapeTemplateID = 201
    private var roomID: String = ""
    private var mixVideoViewLayout = VideoView(videoView = TUIVideoView(context.applicationContext))
    private var multiStreamViewLayout = ConstraintLayout(context.applicationContext)
    private var widgetViewLayout = ConstraintLayout(context.applicationContext)

    private val roomEngine = TUIRoomEngine.sharedInstance()
    private val roomEngineObserver = RoomEngineObserver()
    private val tuiVideoViewMap = HashMap<String, TUIVideoView>()
    internal val widgetViewLayoutMap = HashMap<String, View>()
    private var lastSeatList: List<WebinarSeatInfo>? = null
    private var videoViewAdapter: VideoViewAdapter? = null
    private var canvasTemplateID: Int? = null
    private var canvasFillMode: Int? = null

    init {
        addView(mixVideoViewLayout.videoView, createLayoutParams())
        addView(multiStreamViewLayout, createLayoutParams())
        addView(widgetViewLayout, createLayoutParams())
    }

    interface VideoViewAdapter {
        fun createWidgetView(participant: RoomParticipant): View?
    }

    fun setVideoViewAdapter(adapter: VideoViewAdapter?) {
        videoViewAdapter = adapter
    }

    private fun createLayoutParams(): LayoutParams {
        val params = LayoutParams(
            LayoutParams.MATCH_CONSTRAINT,
            LayoutParams.MATCH_CONSTRAINT
        )
        params.startToStart = LayoutParams.PARENT_ID
        params.topToTop = LayoutParams.PARENT_ID
        params.endToEnd = LayoutParams.PARENT_ID
        params.bottomToBottom = LayoutParams.PARENT_ID
        params.dimensionRatio = ""
        return params
    }

    fun init(roomID: String) {
        this.roomID = roomID
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        roomEngine.addObserver(roomEngineObserver)
    }

    override fun onDetachedFromWindow() {
        roomEngine.removeObserver(roomEngineObserver)
        clearVideoView()
        super.onDetachedFromWindow()
    }

    private fun clearVideoView() {
        lastSeatList = null
        if (mixVideoViewLayout.userId.isNotEmpty()) {
            TUIRoomEngine.sharedInstance()
                .setRemoteVideoView(mixVideoViewLayout.userId, VideoStreamType.CAMERA_STREAM, null)
        }
        if (tuiVideoViewMap.isNotEmpty()) {
            for (userId in tuiVideoViewMap.keys) {
                if (userId == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                    TUIRoomEngine.sharedInstance().setLocalVideoView(null)
                } else {
                    TUIRoomEngine.sharedInstance().setRemoteVideoView(userId, VideoStreamType.CAMERA_STREAM, null)
                }
            }
        }
        multiStreamViewLayout.removeAllViews()
        widgetViewLayout.removeAllViews()
        tuiVideoViewMap.clear()
        widgetViewLayoutMap.clear()
    }

    private fun compareSeatRegionIsSame(seatRegionOld: WebinarSeatInfo?, seatRegionNew: WebinarSeatInfo?): Boolean {
        if (seatRegionOld == null || seatRegionNew == null) {
            return false
        }
        return seatRegionNew == seatRegionOld
    }

    private fun getSeatRegionBySeatIndex(seatList: List<WebinarSeatInfo>, seatInfo: WebinarSeatInfo): WebinarSeatInfo? {
        for (item in seatList) {
            if (item.index == seatInfo.index) {
                return item
            }
        }
        return null
    }

    private fun getTUIVideoViewByUserId(userId: String): TUIVideoView {
        logger.info("oid:$oid, createTUIVideoViewByUserId userId:$userId")
        var liveView = tuiVideoViewMap.get(userId)
        if (liveView == null) {
            liveView = TUIVideoView(context.applicationContext)
            tuiVideoViewMap.put(userId, liveView)
        }
        return liveView
    }

    private fun getWidgetViewByUserId(participant: RoomParticipant): View? {
        logger.info("oid:$oid, getWidgetViewByUserId userId:${participant.userID}")
        var widgetView = widgetViewLayoutMap.get(participant.userID)
        if (widgetView == null) {
            widgetView = videoViewAdapter?.createWidgetView(participant)
            if (widgetView != null) {
                widgetViewLayoutMap.put(participant.userID, widgetView)
            }
        }
        return widgetView
    }

    private fun addSeatRegionView(region: WebinarSeatInfo) {
        addVideoView(region)
        addWidgetsView(region)
    }

    private fun addVideoView(seatInfo: WebinarSeatInfo) {
        logger.info("oid:$oid, addVideoView:region$seatInfo")
        if (seatInfo.region.w <= 0 || seatInfo.region.h <= 0) {
            return
        }
        val userID = seatInfo.participant.userID
        val videoView = getTUIVideoViewByUserId(userID)
        if (LoginStore.shared.loginState.loginUserInfo.value?.userID == userID) {
            setLocalVideoView(seatInfo.participant.cameraStatus, videoView)
        } else if (!TextUtils.isEmpty(userID)) {
            TUIRoomEngine.sharedInstance()
                .setRemoteVideoView(userID, VideoStreamType.CAMERA_STREAM, videoView)
            setVideoRenderMode(userID, canvasFillMode)
        }
        if (videoView.parent === multiStreamViewLayout) {
            return
        }
        multiStreamViewLayout.addView(videoView, createLayoutParams())
    }

    private fun addWidgetsView(seatInfo: WebinarSeatInfo) {
        logger.info("oid:$oid, addWidgetsView:region$seatInfo")
        if (seatInfo.region.w <= 0 || seatInfo.region.h <= 0) {
            return
        }
        val widgetView = getWidgetViewByUserId(seatInfo.participant)
        widgetView?.let {
            widgetViewLayout.addView(it, createLayoutParams())
        }
    }

    private fun removeSeatRegionView(region: WebinarSeatInfo) {
        removeVideoView(region)
        removeWidgetsView(region)
    }

    private fun removeVideoView(region: WebinarSeatInfo) {
        logger.info("oid:$oid, removeVideoView:region$region")
        val videoView: TUIVideoView? = tuiVideoViewMap[region.participant.userID]
        if (videoView != null && multiStreamViewLayout.contains(videoView)) {
            multiStreamViewLayout.removeView(videoView)
        }
        tuiVideoViewMap.remove(region.participant.userID)
    }

    private fun removeWidgetsView(region: WebinarSeatInfo) {
        logger.info("oid:$oid, removeWidgetsView:region$region")
        val widgetView = widgetViewLayoutMap.get(region.participant.userID)
        if (widgetView != null && widgetViewLayout.contains(widgetView)) {
            widgetViewLayout.removeView(widgetView)
        }
        widgetViewLayoutMap.remove(region.participant.userID)
    }

    private fun updateSeatRegionView(oldRegion: WebinarSeatInfo, newRegion: WebinarSeatInfo) {
        logger.info("oid:$oid, updateSeatRegionView:oldRegion$oldRegion,newRegion:$newRegion")
        updateVideoView(oldRegion, newRegion)
        updateWidgetsView(oldRegion, newRegion)
    }

    private fun updateVideoView(oldSeatInfo: WebinarSeatInfo, newSeatInfo: WebinarSeatInfo) {
        logger.info("oid:$oid, updateVideoView:oldSeatInfo$oldSeatInfo,newSeatInfo:$newSeatInfo")
        val videoView = getTUIVideoViewByUserId(newSeatInfo.participant.userID)
        if (newSeatInfo.region.w <= 0 || newSeatInfo.region.h <= 0) {
            if (multiStreamViewLayout.contains(videoView)) {
                multiStreamViewLayout.removeView(videoView)
            }
            return
        }
        if (LoginStore.shared.loginState.loginUserInfo.value?.userID == newSeatInfo.participant.userID) {
            setLocalVideoView(newSeatInfo.participant.cameraStatus, videoView)
        } else {
            val userID = newSeatInfo.participant.userID
            TUIRoomEngine.sharedInstance()
                .setRemoteVideoView(userID, VideoStreamType.CAMERA_STREAM, videoView)
            setVideoRenderMode(userID, canvasFillMode)
        }
        if (oldSeatInfo.region != newSeatInfo.region) {
            if (multiStreamViewLayout.contains(videoView)) {
                multiStreamViewLayout.removeView(videoView)
            }
            multiStreamViewLayout.addView(videoView)
        }
    }

    private fun updateWidgetsView(oldRegion: WebinarSeatInfo, newRegion: WebinarSeatInfo) {
        logger.info("oid:$oid, updateWidgetsView:region$oldRegion,newRegion:$newRegion")
        removeWidgetsView(oldRegion)
        addWidgetsView(newRegion)
    }

    private fun startPlayRemoteVideo(userID: String) {
        TUIRoomEngine.sharedInstance().startPlayRemoteVideo(
            userID,
            VideoStreamType.CAMERA_STREAM,
            object : TUIRoomDefine.PlayCallback {
                override fun onPlaying(userId: String?) {
                    logger.info("oid:$oid, onPlaying:userId$userId")
                }

                override fun onLoading(userId: String?) {
                    logger.info("oid:$oid, onLoading:userId$userId")
                }

                override fun onPlayError(
                    userId: String?,
                    error: TUICommonDefine.Error?,
                    message: String?
                ) {
                    logger.error("oid:$oid, onPlayError:userID$userId, error$error, message$message")
                }
            })
    }

    private fun setLocalVideoView(deviceStatus: DeviceStatus, videoView: TUIVideoView) {
        if (deviceStatus == DeviceStatus.ON) {
            TUIRoomEngine.sharedInstance().setLocalVideoView(videoView)
        } else {
            TUIRoomEngine.sharedInstance().setLocalVideoView(null)
        }
    }

    private fun queryRoomCanvas(): WebinarCanvas? {
        val api = JSONObject()
        val params = JSONObject()
        params.put("roomId", roomID)
        api.put("api", "querySeatLayout")
        api.put("params", params)
        try {
            val result =
                roomEngine.callExperimentalAPI(api.toString()) { jsonData -> logger.info("callExperimentalAPI querySeatLayout response: $jsonData") }
            logger.info("callExperimentalAPI querySeatLayout response: $result")
            if (result != null && result is String) {
                return convertJsonToWebinarCanvas(result)
            }
        } catch (e: Exception) {
            logger.error("callExperimentalAPI querySeatLayout failed, ${e.message}")
        }
        return null
    }

    private fun setVideoRenderMode(userID: String, fillMode: Int?) {
        val params = TRTCCloudDef.TRTCRenderParams()
        params.fillMode = if (fillMode == 0) TRTC_VIDEO_RENDER_MODE_FIT else TRTC_VIDEO_RENDER_MODE_FILL
        roomEngine.trtcCloud.setRemoteRenderParams(userID, TRTC_VIDEO_STREAM_TYPE_BIG, params)
    }

    private fun updateRoomViewLayoutSize() {
        val canvas = queryRoomCanvas() ?: return
        val templateID = canvas.templateID
        canvasFillMode = canvas.fillMode
        if (templateID == canvasTemplateID) {
            return
        }
        val marginLayoutParams = layoutParams as? MarginLayoutParams ?: return
        val margin = dpToPx(30)
        if (templateID == videoLandscapeTemplateID) {
            marginLayoutParams.setMargins(0, margin, 0, 0)
            marginLayoutParams.height = getScreenWidth(context) * canvas.h / canvas.w
        } else {
            marginLayoutParams.setMargins(0, 0, 0, 0)
            marginLayoutParams.height = LayoutParams.MATCH_PARENT
        }
        layoutParams = marginLayoutParams
        canvasTemplateID = canvas.templateID
    }

    private inner class RoomEngineObserver : TUIRoomObserver() {
        override fun onUserVideoStateChanged(
            userID: String,
            streamType: VideoStreamType,
            hasVideo: Boolean,
            reason: TUIRoomDefine.ChangeReason
        ) {
            logger.info("onUserVideoStateChanged:$oid, userID:$userID, streamType:$streamType,hasVideo:$hasVideo,reason:$reason")
            if (userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                return
            }
            val isMixUser: Boolean = userID.contains("_feedback_")
            if (hasVideo) {
                if (isMixUser) {
                    mixVideoViewLayout.userId = userID
                    TUIRoomEngine.sharedInstance()
                        .setRemoteVideoView(userID, VideoStreamType.CAMERA_STREAM, mixVideoViewLayout.videoView)
                    startPlayRemoteVideo(userID)
                } else {
                    val tuiVideoView = getTUIVideoViewByUserId(userID)
                    TUIRoomEngine.sharedInstance()
                        .setRemoteVideoView(userID, VideoStreamType.CAMERA_STREAM, tuiVideoView)
                    setVideoRenderMode(userID, canvasFillMode)
                    startPlayRemoteVideo(userID)
                }
            } else {
                TUIRoomEngine.sharedInstance().stopPlayRemoteVideo(userID, VideoStreamType.CAMERA_STREAM)
            }
        }

        override fun onSeatListChanged(
            roomId: String,
            seatList: List<SeatFullInfo>,
            newlySeatedUsers: List<UserInfo>,
            newlyLeftUsers: List<UserInfo>
        ) {
            logger.info("onSeatListChanged:$roomId, seatList: ${convertSeatListToString(seatList)}")
            updateRoomViewLayoutSize()
            val seatList = seatList.map { convertToWebinarSeatInfo(it) }
            lastSeatList?.let {
                for (oldRegion in it) {
                    val newRegion: WebinarSeatInfo? = getSeatRegionBySeatIndex(seatList, oldRegion)
                    if (newRegion == null) {
                        removeSeatRegionView(oldRegion)
                    }
                }
            }

            for (newRegion in seatList) {
                if (lastSeatList == null) {
                    addSeatRegionView(newRegion)
                    continue
                }
                val oldRegion = getSeatRegionBySeatIndex(lastSeatList!!, newRegion)
                logger.info("oid:$oid, updateSeatLayout,isSame:${oldRegion == newRegion},oldRegion:$oldRegion,newRegion:$newRegion")
                if (oldRegion == null) {
                    addSeatRegionView(newRegion)
                } else if (!compareSeatRegionIsSame(newRegion, oldRegion)) {
                    updateSeatRegionView(oldRegion, newRegion)
                }
            }
            lastSeatList = seatList
        }
    }
}