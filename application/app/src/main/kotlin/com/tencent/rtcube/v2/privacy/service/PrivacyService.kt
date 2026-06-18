package com.tencent.rtcube.v2.privacy.service

import android.content.Context
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.interfaces.ITUIService
import com.tencent.qcloud.tuicore.interfaces.TUIServiceCallback

/**
 * TUICore service of the privacy module; invoked by Beauty / UGSV modules via [TUICore.getService]
 * to show the authorization dialog. Status is synced with the SP switches on the privacy settings page.
 */
class PrivacyService : ITUIService {

    companion object {
        const val SERVICE_NAME = "PrivacyService"

        const val METHOD_SHOW_BEAUTY_AUTH_DIALOG = "methodShowBeautyAuthDialog"

        // Context the dialog attaches to (must be an Activity to actually show).
        const val PARAM_NAME_AUTH_DIALOG = "param_name_auth_dialog_context"

        fun register() {
            TUICore.registerService(SERVICE_NAME, PrivacyService())
        }
    }

    override fun onCall(method: String, param: Map<String, Any>?): Any? = null

    override fun onCall(method: String, param: Map<String, Any>?, callback: TUIServiceCallback?): Any {
        val context = param?.get(PARAM_NAME_AUTH_DIALOG) as? Context
        return when (method) {
            METHOD_SHOW_BEAUTY_AUTH_DIALOG -> PrivacyBeautyAuthDialog.showBeautyAuthDialog(context, callback)
            else -> false
        }
    }
}