package com.trtc.uikit.livekit.features.anchorprepare.store

import android.text.TextUtils
import com.trtc.tuikit.common.system.ContextProvider
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.PermissionRequest
import com.trtc.uikit.livekit.features.anchorprepare.AnchorPrepareViewListener
import com.trtc.uikit.livekit.features.anchorprepare.LiveStreamPrivacyStatus
import com.trtc.uikit.livekit.features.anchorprepare.PrepareState
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class AnchorPrepareStore() {
    companion object {
        private val LOGGER = LiveKitLogger.getFeaturesLogger("AnchorPrepareManager")
        private const val CAMERA_NOT_AUTHORIZED = -1101
        fun disableFeatureMenu(enable: Boolean) {
            if (AnchorPrepareConfig.disableFeatureMenu.value == enable) {
                return
            }
            AnchorPrepareConfig.disableFeatureMenu.value = enable
        }

        fun disableMenuSwitchButton(enable: Boolean) {
            if (AnchorPrepareConfig.disableMenuSwitchButton.value == enable) {
                return
            }
            AnchorPrepareConfig.disableMenuSwitchButton.value = enable
        }

        fun disableMenuBeautyButton(enable: Boolean) {
            if (AnchorPrepareConfig.disableMenuBeautyButton.value == enable) {
                return
            }
            AnchorPrepareConfig.disableMenuBeautyButton.value = enable
        }

        fun disableMenuAudioEffectButton(enable: Boolean) {
            if (AnchorPrepareConfig.disableMenuAudioEffectButton.value == enable) {
                return
            }
            AnchorPrepareConfig.disableMenuAudioEffectButton.value = enable
        }
    }

    private val internalState: AnchorPrepareState = AnchorPrepareState().apply {
        selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        selfUserName = LoginStore.shared.loginState.loginUserInfo.value?.nickname ?: ""
    }

    private val externalState: PrepareState = PrepareState(
        coverURL = internalState.coverURL,
        liveMode = internalState.liveMode,
        roomName = internalState.roomName,
        coGuestTemplateId = internalState.coGuestTemplateId,
        coHostTemplateId = internalState.coHostTemplateId
    )
    private val listenerManager: AnchorPrepareListenerDispatch = AnchorPrepareListenerDispatch()

    fun getState(): AnchorPrepareState = internalState

    fun setCoverURL(coverURL: String) {
        internalState.coverURL.value = coverURL
    }

    fun setRoomName(roomName: String) {
        internalState.roomName.value = roomName
    }

    fun setLiveMode(value: LiveStreamPrivacyStatus) {
        internalState.liveMode.value = value
    }

    fun setCoGuestTemplate(template: Int) {
        internalState.coGuestTemplateId.value = template
    }

    fun setCoHostTemplate(template: Int) {
        internalState.coHostTemplateId.value = template
    }

    fun startLive() {
        listenerManager.notifyAnchorPrepareViewListener { it.onClickStartButton() }
    }

    fun getDefaultRoomName(): String {
        return if (TextUtils.isEmpty(internalState.selfUserName)) {
            internalState.selfUserId
        } else {
            internalState.selfUserName
        }
    }

    fun destroy() {
        listenerManager.clearAnchorPrepareViewListeners()
    }

    fun getExternalState(): PrepareState = externalState

    fun startPreview(callback: CompletionHandler?) {
        LOGGER.info("requestCameraPermissions:[]")
        PermissionRequest.requestCameraPermissions(ContextProvider.getApplicationContext(), object :
            PermissionCallback() {
            override fun onRequesting() {
                LOGGER.info("requestCameraPermissions:[onRequesting]")
            }

            override fun onGranted() {
                LOGGER.info("requestCameraPermissions:[onGranted]")
                DeviceStore.shared().openLocalCamera(internalState.useFrontCamera.value == true, object :
                    CompletionHandler {
                    override fun onSuccess() {
                        LOGGER.info("startCamera success, requestMicrophonePermissions")
                        PermissionRequest.requestMicrophonePermissions(
                            ContextProvider.getApplicationContext(),
                            object : PermissionCallback() {
                                override fun onGranted() {
                                    LOGGER.info("requestMicrophonePermissions success")
                                    DeviceStore.shared().openLocalMicrophone(object : CompletionHandler {
                                        override fun onSuccess() {
                                            callback?.onSuccess()
                                        }

                                        override fun onFailure(code: Int, desc: String) {
                                            LOGGER.error("startMicrophone failed:code:$code,desc:$desc")
                                            callback?.onFailure(code, desc)
                                        }

                                    })
                                }

                                override fun onDenied() {
                                    LOGGER.error("requestCameraPermissions:[onDenied]")
                                    callback?.onFailure(CAMERA_NOT_AUTHORIZED, "requestCameraPermissions:[onDenied]")
                                }
                            })
                    }

                    override fun onFailure(code: Int, desc: String) {
                        LOGGER.error("startCamera failed:code:$code,desc:$desc")
                        callback?.onFailure(code, desc)
                    }

                })
            }

            override fun onDenied() {
                LOGGER.error("requestCameraPermissions:[onDenied]")
                callback?.onFailure(CAMERA_NOT_AUTHORIZED, "requestCameraPermissions:[onDenied]")
            }
        })
    }

    fun stopPreview() {
        DeviceStore.shared().closeLocalCamera()
        DeviceStore.shared().closeLocalMicrophone()
        listenerManager.notifyAnchorPrepareViewListener { it.onClickBackButton() }
    }

    fun addAnchorPrepareViewListener(listener: AnchorPrepareViewListener) {
        listenerManager.addAnchorPrepareViewListener(listener)
    }

    fun removeAnchorPrepareViewListener(listener: AnchorPrepareViewListener) {
        listenerManager.removeAnchorPrepareViewListener(listener)
    }
}