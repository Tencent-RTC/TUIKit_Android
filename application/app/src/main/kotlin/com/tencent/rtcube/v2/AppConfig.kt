package com.tencent.rtcube.v2

import android.app.Activity
import android.content.Context
import com.tencent.rtcube.v2.login.components.model.UserModel

object AppConfig {

    fun init(context: Context) {
    }

    fun isMiniProgramProcess(context: Context): Boolean {
        return false
    }

    fun performInitialRiskCheckOnce(activity: Activity, userModel: UserModel?) {

    }

    fun performRiskCheck(
        activity: Activity,
        userModel: UserModel?,
        onFaceAuthDialogShowed: ((Boolean) -> Unit)? = null,
    ) {

    }

    fun trackAnalytics(moduleName: String = "") {

    }
}
