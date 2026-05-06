package com.trtc.uikit.livekit.common

import android.util.Log
import com.google.gson.Gson
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.atomicxcore.api.live.deprecated.LiveCoreViewDeprecated
import org.json.JSONException
import org.json.JSONObject

const val LIVEKIT_METRICS_PANEL_SHOW_SEAT_GRID_VIEW = 191026
const val LIVEKIT_METRICS_PANEL_HIDE_SEAT_GRID_VIEW = 191027
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_START_MICROPHONE = 191028
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_STOP_MICROPHONE = 191029
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_MUTE_MICROPHONE = 191030
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_UNMUTE_MICROPHONE = 191031
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_START_ROOM = 191032
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_STOP_ROOM = 191033
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_JOIN_ROOM = 191034
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_LEAVE_ROOM = 191035
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_UPDATE_SEAT_MODE = 191036
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_RESPONSE_REQUEST = 191037
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_CANCEL_REQUEST = 191038
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_TAKE_SEAT = 191039
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_MOVE_TO_SEAT = 191040
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_LEAVE_SEAT = 191041
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_TAKE_USER_ON_SEAT = 191042
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_KICK_USER_OFF_SEAT = 191043
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_LOCK_SEAT = 191044
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_SET_LAYOUT_MODE = 191045
const val LIVEKIT_METRICS_METHOD_CALL_SEAT_GRID_VIEW_SET_SEAT_VIEW_ADAPTER = 191046
const val COMPONENT_VOICE_ROOM = 22
const val COMPONENT_LIVE_STREAM = 21
const val LIVE_INTEGRATION_SUCCESSFUL = 1120

fun reportEventData(eventKey: Int) {
    val gson = Gson()
    val json = gson.toJson(
        mapOf(
            "api" to "KeyMetricsStats",
            "params" to mapOf(
                "key" to eventKey
            )
        )
    )
    TUIRoomEngine.sharedInstance().callExperimentalAPI(json, null)
    Log.i("DataReporter", "reportEventData:[json:$json]")
}

fun setComponent(component: Int) {
    try {
        val jsonObject = JSONObject().apply {
            put("api", "component")
            put("component", component)
        }
        LiveCoreViewDeprecated.callExperimentalAPI(jsonObject.toString())
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