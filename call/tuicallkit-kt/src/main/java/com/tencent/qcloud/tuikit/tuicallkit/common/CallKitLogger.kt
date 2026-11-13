package com.tencent.qcloud.tuikit.tuicallkit.common

import android.util.Log
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import org.json.JSONObject

internal class CallKitLogger private constructor(private val fileName: String) {

    companion object {
        private const val MODULE = "TUICallKit"
        private const val LOG_LEVEL_INFO = 0
        private const val LOG_LEVEL_WARNING = 1
        private const val LOG_LEVEL_ERROR = 2

        @JvmStatic
        fun getLogger(file: String) = CallKitLogger(file)

        private fun log(file: String, level: Int, message: String) {
            try {
                val payload = JSONObject().apply {
                    put("api", "TuikitLog")
                    put("params", JSONObject().apply {
                        put("level", level)
                        put("message", message)
                        put("module", MODULE)
                        put("file", file)
                        put("line", 0)
                    })
                }.toString()
                CallStore.shared.callExperimentalAPI(payload)
            } catch (e: Exception) {
                Log.e("CallKitLogger", e.toString())
            }
        }
    }

    fun info(message: String) = log(LOG_LEVEL_INFO, message)
    fun warn(message: String) = log(LOG_LEVEL_WARNING, message)
    fun error(message: String) = log(LOG_LEVEL_ERROR, message)

    private fun log(level: Int, message: String) = log(fileName, level, message)
}
