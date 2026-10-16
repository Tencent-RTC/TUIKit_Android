package com.trtc.uikit.livekit.common.basic

import android.util.Log
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import org.json.JSONException
import org.json.JSONObject

const val COMPONENT_LIVE_STREAM = 21
const val LIVE_INTEGRATION_SUCCESSFUL = 1120

fun setComponent(component: Int) {
    try {
        val jsonObject = JSONObject().apply {
            put("api", "component")
            put("component", component)
        }
    } catch (e: JSONException) {
        Log.e("DataReporter", "dataReport:${e.message}")
    }
}

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