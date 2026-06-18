package com.tencent.rtcube.v2.main.domestic.service

import android.os.Bundle
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.interfaces.ITUIService
import com.tencent.qcloud.tuicore.interfaces.TUIServiceCallback
import com.tencent.rtcube.assembly.AppTarget
import com.tencent.rtcube.v2.AppTargetResolver

class ConfigureService : ITUIService {

    companion object {
        const val SERVICE_NAME = "ConfigureService"

        const val METHOD_SHOW_CONTACT_US = "methodGetIsRTCCUbe"
        const val CALL_BACK_CODE_SUCCESS = 0

        fun register() {
            TUICore.registerService(SERVICE_NAME, ConfigureService())
        }
    }

    override fun onCall(method: String, param: Map<String, Any>?): Any? = null

    override fun onCall(method: String, param: Map<String, Any>?, callback: TUIServiceCallback?): Any {
        return when (method) {
            METHOD_SHOW_CONTACT_US -> {
                val bundle = Bundle()
                val isRTCube = AppTargetResolver.getAppTarget() == AppTarget.DOMESTIC
                bundle.putBoolean("paramIsRTCCube", isRTCube)
                callback?.onServiceCallback(CALL_BACK_CODE_SUCCESS, "", bundle)
                true
            }

            else -> false
        }
    }
}