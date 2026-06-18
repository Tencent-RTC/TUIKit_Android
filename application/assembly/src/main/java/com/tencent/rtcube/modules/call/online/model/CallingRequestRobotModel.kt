package com.tencent.rtcube.modules.call.online.model

import com.google.gson.annotations.SerializedName

data class CallingRequestRobotModel(
    @SerializedName("errorCode") val errorCode: Int,
    @SerializedName("errorMessage") val errorMessage: String,
    @SerializedName("data") val data: CallingVirtualRobotArrayModel,
)

data class CallingVirtualRobotArrayModel(
    @SerializedName("virtualUsers") val virtualUsers: List<CallingVirtualRobotModel?>?,
)

data class CallingVirtualRobotModel(
    @SerializedName("name") val name: String?,
    @SerializedName("avatar") val avatar: String?,
    @SerializedName("virtualUserId") val virtualUserId: String,
)
