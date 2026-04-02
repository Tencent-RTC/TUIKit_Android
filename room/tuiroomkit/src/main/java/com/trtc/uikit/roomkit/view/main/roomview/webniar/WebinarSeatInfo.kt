package com.trtc.uikit.roomkit.view.main.roomview

import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine.SeatFullInfo
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import org.json.JSONObject

data class WebinarSeatInfo(
    var index: Int = 0,
    var isLocked: Boolean = false,
    var participant: RoomParticipant = RoomParticipant(),
    var region: WebinarRegionInfo = WebinarRegionInfo()
)

data class WebinarRegionInfo(
    var x: Int = 0,
    var y: Int = 0,
    var w: Int = 0,
    var h: Int = 0,
    var zorder: Int = 0
)

data class WebinarCanvas(
    var w: Int = 0,
    var h: Int = 0,
    var templateID: Int = 201,
    var fillMode: Int = 0
)

fun convertToWebinarSeatInfo(seatFullInfo: SeatFullInfo): WebinarSeatInfo {
    val seatUserInfo = RoomParticipant(
        userID = seatFullInfo.userId,
        userName = seatFullInfo.userName,
        avatarURL = seatFullInfo.userAvatar,
        microphoneStatus = convertToDeviceStatus(seatFullInfo.userMicrophoneStatus),
        cameraStatus = convertToDeviceStatus(seatFullInfo.userCameraStatus),
    )
    val regionInfo = WebinarRegionInfo(
        x = seatFullInfo.x,
        y = seatFullInfo.y,
        w = seatFullInfo.width,
        h = seatFullInfo.height,
        zorder = seatFullInfo.zorder
    )
    return WebinarSeatInfo(
        index = seatFullInfo.seatIndex,
        isLocked = seatFullInfo.isSeatLocked,
        participant = seatUserInfo,
        region = regionInfo
    )
}

fun convertToDeviceStatus(status: TUIRoomDefine.DeviceStatus): DeviceStatus {
    return when (status) {
        TUIRoomDefine.DeviceStatus.OPENED -> DeviceStatus.ON
        else -> DeviceStatus.OFF
    }
}

fun convertJsonToWebinarCanvas(jsonString: String): WebinarCanvas {
    val roomCanvas = WebinarCanvas()
    try {
        val jsonObject = JSONObject(jsonString)
        roomCanvas.h = jsonObject.optInt("canvasHeight", 0)
        roomCanvas.w = jsonObject.optInt("canvasWidth", 0)
        roomCanvas.templateID = jsonObject.optInt("templateId", 0)
        roomCanvas.fillMode = jsonObject.optInt("canvasFillMode", 0)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return roomCanvas
}

fun convertSeatListToString(seatList: List<SeatFullInfo>): String {
    if (seatList.isEmpty()) {
        return "seatList is empty"
    }
    val sb = StringBuilder("seatList:${seatList.size}\n")
    for (seat in seatList) {
        sb.append(seat.printString()).append("\n")
    }
    return sb.toString()
}

fun SeatFullInfo.printString(): String {
    return buildString {
        append("SeatFullInfo {")
        append("  roomId: $roomId")
        append("  seatIndex: $seatIndex")
        append("  isSeatLocked: $isSeatLocked")
        append("  userId: $userId")
        append("  userName: $userName")
        append("  userAvatar: $userAvatar")
        append("  userMicrophoneStatus: $userMicrophoneStatus")
        append("  userCameraStatus: $userCameraStatus")
        append("  userSuspendStatus: $userSuspendStatus")
        append("  x: $x")
        append("  y: $y")
        append("  width: $width")
        append("  height: $height")
        append("  zorder: $zorder")
        append("}")
    }
}