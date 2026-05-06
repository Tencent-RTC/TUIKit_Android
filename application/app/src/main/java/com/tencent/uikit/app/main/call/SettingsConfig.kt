package com.tencent.uikit.app.main.call

import com.tencent.cloud.tuikit.engine.common.TUICommonDefine
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine.VideoRenderParams
import com.tencent.qcloud.tuicore.util.SPUtils
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.CallingBellFeature

object SettingsConfig {
    var ringPath: String? = SPUtils.getInstance(CallingBellFeature.PROFILE_TUICALLKIT)
        .getString(CallingBellFeature.PROFILE_CALL_BELL)
    var isMute: Boolean = false
    var isShowFloatingWindow: Boolean = true
    var isShowBlurBackground: Boolean = true
    var isIncomingBanner: Boolean = true
    var intRoomId: Int = 0
    var strRoomId: String = ""
    var callTimeOut: Int = 60
    var userData: String = ""
    var offlineParams: String = ""
    var resolution: Int = TUICommonDefine.VideoEncoderParams.Resolution.Resolution_640_360.ordinal
    var resolutionMode: Int = TUICommonDefine.VideoEncoderParams.ResolutionMode.Portrait.ordinal
    var fillMode: Int = VideoRenderParams.FillMode.Fill.ordinal
    var rotation: Int = VideoRenderParams.Rotation.Rotation_0.ordinal
    var beautyLevel: Int = 6

    var customUIAdapter: CustomUIAdapter = CustomUIAdapter()
}