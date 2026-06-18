package com.tencent.rtcube.modules.call.online.model

/**
 * Robot call type.
 *
 * - INIT_CALL: actively call the robot (Robot A)
 * - HOST_CALL: wait for the robot to call back (Robot B)
 */
enum class CallBotType {
    INIT_CALL,
    HOST_CALL,
}
data class CallingRobotModel(
    val title: String,
    val callType: CallBotType,
)
