package com.tencent.qcloud.tuikit.tuicallkit

import android.app.Application
import android.content.Context
import com.tencent.qcloud.tuikit.tuicallkit.call.CallKitConfig
import com.tencent.qcloud.tuikit.tuicallkit.call.CallLauncher
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitContext
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitLogger
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitToast
import com.tencent.qcloud.tuikit.tuicallkit.common.data.Constants
import com.tencent.qcloud.tuikit.tuicallkit.bridge.ChatCallEventSubscriber
import com.tencent.qcloud.tuikit.tuicallkit.view.CallAdapter
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParams
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile

open class TUICallKit {

    private val config: CallKitConfig
        get() = CallKitConfig.shared
    private val logger = CallKitLogger.getLogger("TUICallKit")

    companion object {
        @Volatile
        private var instance: TUICallKit? = null

        @JvmStatic
        fun createInstance(context: Context): TUICallKit {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val applicationContext = context.applicationContext
                    CallKitContext.install(applicationContext)
                    CallKitConfig.install(applicationContext)
                    (applicationContext as? Application)?.let { CallKitToast.install(it) }
                    CallLauncher.init()
                    ChatCallEventSubscriber.ensureSubscribed(applicationContext)
                    TUICallKit().also { instance = it }
                }
            }
        }
    }

    open fun setSelfInfo(nickname: String?, avatar: String?, completion: CompletionHandler?) {
        val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val profile = UserProfile(userID = userID, nickname = nickname.orEmpty(), avatarURL = avatar.orEmpty())
        LoginStore.shared.setSelfInfo(profile, completion)
    }

    open fun calls(
        userIdList: List<String>, mediaType: CallMediaType,
        params: CallParams?, completion: CompletionHandler?
    ) {
        CallStore.shared.calls(userIdList, mediaType, params, completion)
    }

    open fun join(callId: String?, completion: CompletionHandler?) {
        CallStore.shared.join(callId.orEmpty(), completion)
    }

    open fun setCallingBell(filePath: String?) {
        config.updateCallingBell(filePath.orEmpty())
    }

    open fun enableMuteMode(enable: Boolean) {
        config.updateMuteMode(enable)
    }

    open fun enableFloatWindow(enable: Boolean) {
        config.updateFloatWindow(enable)
    }

    open fun enableVirtualBackground(enable: Boolean) {
        config.updateVirtualBackground(enable)
    }

    open fun enableAITranscriber(enable: Boolean) {
        config.updateAITranscriber(enable)
    }

    open fun enableIncomingBanner(enable: Boolean) {
        config.updateIncomingBanner(enable)
    }

    open fun callExperimentalAPI(jsonStr: String) {
        CallStore.shared.callExperimentalAPI(jsonStr)
    }

    open fun setScreenOrientation(orientation: Int) {
        if (orientation !in 0..2) {
            logger.warn("setScreenOrientation invalid value: $orientation")
            return
        }
        config.updateScreenOrientation(orientation)
    }

    @Deprecated("Deprecated compatibility API; no longer supported.")
    open fun disableControlButton(button: Constants.ControlButton?) {}

    @Deprecated("Deprecated compatibility API; no longer supported.")
    open fun setAdapter(adapter: CallAdapter?) {}
}
