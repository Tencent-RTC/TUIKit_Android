package com.trtc.uikit.livekit.common

import com.tencent.cloud.tuikit.engine.common.ContextProvider


object PackageService {

    private const val PACKAGE_RT_CUBE = "com.tencent.trtc"
    private const val PACKAGE_TENCENT_RTC = "com.tencent.rtc.app"

    private val currentPackageName: String
        get() = ContextProvider.getApplicationContext()?.packageName?:""

    val isRTCube: Boolean
        get() = PACKAGE_RT_CUBE == currentPackageName

    val isTencentRTC: Boolean
        get() = PACKAGE_TENCENT_RTC == currentPackageName

    val isRTCubeOrTencentRTC: Boolean
        get() = isRTCube || isTencentRTC
}