package com.tencent.rtcube.modules.call.lab.store

import com.tencent.rtcube.modules.call.lab.ui.LabSettingsConfig

data class LabSettingsState(
    val ringPath: String = LabSettingsConfig.ringPath ?: "",
    val nickname: String = "",
    val avatar: String = "",
    val isMute: Boolean = LabSettingsConfig.isMute,
    val isShowFloatingWindow: Boolean = LabSettingsConfig.isShowFloatingWindow,
    val isShowBlurBackground: Boolean = LabSettingsConfig.isShowBlurBackground,
    val isIncomingBanner: Boolean = LabSettingsConfig.isIncomingBanner,
    val isAISubtitleEnabled: Boolean = false,
    val intRoomId: Int = LabSettingsConfig.intRoomId,
    val strRoomId: String = LabSettingsConfig.strRoomId,
    val callTimeOut: Int = LabSettingsConfig.callTimeOut,
    val userData: String = LabSettingsConfig.userData,
    val offlineParams: String = LabSettingsConfig.offlineParams,
    val resolution: Int = LabSettingsConfig.resolution,
    val resolutionMode: Int = LabSettingsConfig.resolutionMode,
    val fillMode: Int = LabSettingsConfig.fillMode,
    val rotation: Int = LabSettingsConfig.rotation,
    val beautyLevel: Int = LabSettingsConfig.beautyLevel,
    val isMicrophoneDisabled: Boolean = false,
    val isAudioDeviceDisabled: Boolean = false,
    val isCameraDisabled: Boolean = false,
    val isSwitchCameraDisabled: Boolean = false,
    val isInviteUserDisabled: Boolean = false,
    val isAddMainView: Boolean = false,
    val isUseStreamView: Boolean = false,
)
