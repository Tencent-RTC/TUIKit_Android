package com.tencent.uikit.app.common.utils

import android.util.Log
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import org.json.JSONObject

const val DEMO_LOGIN_SUCCESS = 1302
const val DEMO_CLICK_CALL = 1303
const val DEMO_CLICK_LIVE = 1119
const val DEMO_CLICK_ROOM = 1205

object KeyMetrics {
    @JvmStatic
    fun reportAtomicMetrics(platform: Int) {
        val param = JSONObject().apply {
            put("UIComponentType", platform.toLong())
        }.toString()
        V2TIMManager.getInstance()
            .callExperimentalAPI("reportTUIFeatureUsage", param, object : V2TIMValueCallback<Any> {
                override fun onSuccess(t: Any?) {
                }

                override fun onError(code: Int, desc: String?) {
                    Log.e("TUILiveKit-DataReporter", "reportFeatureUsage failed: $code $desc")
                }
            })
    }
}