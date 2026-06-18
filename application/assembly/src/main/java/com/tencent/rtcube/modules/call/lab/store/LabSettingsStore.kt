package com.tencent.rtcube.modules.call.lab.store

import com.tencent.rtcube.modules.call.lab.ui.LabSettingsConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object LabSettingsStore {
    private val _state = MutableStateFlow(LabSettingsState())
    val state: StateFlow<LabSettingsState> = _state.asStateFlow()

    fun updateNickname(nickname: String) {
        _state.update { it.copy(nickname = nickname) }
    }

    fun updateAvatar(avatar: String) {
        _state.update { it.copy(avatar = avatar) }
    }

    fun updateRingPath(ringPath: String) {
        LabSettingsConfig.ringPath = ringPath
        _state.update { it.copy(ringPath = ringPath) }
    }

    fun updateMute(isMute: Boolean) {
        LabSettingsConfig.isMute = isMute
        _state.update { it.copy(isMute = isMute) }
    }

    fun updateFloatingWindow(isShow: Boolean) {
        LabSettingsConfig.isShowFloatingWindow = isShow
        _state.update { it.copy(isShowFloatingWindow = isShow) }
    }

    fun updateBlurBackground(isShow: Boolean) {
        LabSettingsConfig.isShowBlurBackground = isShow
        _state.update { it.copy(isShowBlurBackground = isShow) }
    }

    fun updateIncomingBanner(isShow: Boolean) {
        LabSettingsConfig.isIncomingBanner = isShow
        _state.update { it.copy(isIncomingBanner = isShow) }
    }

    fun updateAISubtitle(isEnabled: Boolean) {
        _state.update { it.copy(isAISubtitleEnabled = isEnabled) }
    }

    fun updateIntRoomId(intRoomId: Int) {
        LabSettingsConfig.intRoomId = intRoomId
        _state.update { it.copy(intRoomId = intRoomId) }
    }

    fun updateStrRoomId(strRoomId: String) {
        LabSettingsConfig.strRoomId = strRoomId
        _state.update { it.copy(strRoomId = strRoomId) }
    }

    fun updateCallTimeOut(timeout: Int) {
        LabSettingsConfig.callTimeOut = timeout
        _state.update { it.copy(callTimeOut = timeout) }
    }

    fun updateUserData(userData: String) {
        LabSettingsConfig.userData = userData
        _state.update { it.copy(userData = userData) }
    }

    fun updateOfflineParams(offlineParams: String) {
        LabSettingsConfig.offlineParams = offlineParams
        _state.update { it.copy(offlineParams = offlineParams) }
    }

    fun updateResolution(resolution: Int) {
        LabSettingsConfig.resolution = resolution
        _state.update { it.copy(resolution = resolution) }
    }

    fun updateResolutionMode(resolutionMode: Int) {
        LabSettingsConfig.resolutionMode = resolutionMode
        _state.update { it.copy(resolutionMode = resolutionMode) }
    }

    fun updateFillMode(fillMode: Int) {
        LabSettingsConfig.fillMode = fillMode
        _state.update { it.copy(fillMode = fillMode) }
    }

    fun updateRotation(rotation: Int) {
        LabSettingsConfig.rotation = rotation
        _state.update { it.copy(rotation = rotation) }
    }

    fun updateBeautyLevel(beautyLevel: Int) {
        LabSettingsConfig.beautyLevel = beautyLevel
        _state.update { it.copy(beautyLevel = beautyLevel) }
    }

    fun updateMicrophoneDisabled(disabled: Boolean) {
        _state.update { it.copy(isMicrophoneDisabled = disabled) }
    }

    fun updateAudioDeviceDisabled(disabled: Boolean) {
        _state.update { it.copy(isAudioDeviceDisabled = disabled) }
    }

    fun updateCameraDisabled(disabled: Boolean) {
        _state.update { it.copy(isCameraDisabled = disabled) }
    }

    fun updateSwitchCameraDisabled(disabled: Boolean) {
        _state.update { it.copy(isSwitchCameraDisabled = disabled) }
    }

    fun updateInviteUserDisabled(disabled: Boolean) {
        _state.update { it.copy(isInviteUserDisabled = disabled) }
    }

    fun updateAddMainView(isAdd: Boolean) {
        _state.update { it.copy(isAddMainView = isAdd, isUseStreamView = if (isAdd) false else it.isUseStreamView) }
    }

    fun updateUseStreamView(isUse: Boolean) {
        _state.update { it.copy(isUseStreamView = isUse, isAddMainView = if (isUse) false else it.isAddMainView) }
    }

    fun refreshFromConfig(nickname: String, avatar: String, isAISubtitleEnabled: Boolean) {
        _state.update {
            it.copy(
                nickname = nickname,
                avatar = avatar,
                ringPath = LabSettingsConfig.ringPath ?: "",
                isMute = LabSettingsConfig.isMute,
                isShowFloatingWindow = LabSettingsConfig.isShowFloatingWindow,
                isShowBlurBackground = LabSettingsConfig.isShowBlurBackground,
                isIncomingBanner = LabSettingsConfig.isIncomingBanner,
                isAISubtitleEnabled = isAISubtitleEnabled,
                intRoomId = LabSettingsConfig.intRoomId,
                strRoomId = LabSettingsConfig.strRoomId,
                callTimeOut = LabSettingsConfig.callTimeOut,
                userData = LabSettingsConfig.userData,
                offlineParams = LabSettingsConfig.offlineParams,
                resolution = LabSettingsConfig.resolution,
                resolutionMode = LabSettingsConfig.resolutionMode,
                fillMode = LabSettingsConfig.fillMode,
                rotation = LabSettingsConfig.rotation,
                beautyLevel = LabSettingsConfig.beautyLevel,
            )
        }
    }
}
