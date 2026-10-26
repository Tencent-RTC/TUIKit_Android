package com.trtc.uikit.roomkit.base.log

import android.util.Log
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.tencent.trtc.TRTCCloud
import org.json.JSONException
import org.json.JSONObject

/**
 * Logger for RoomKit module with support for INFO, WARN, and ERROR levels.
 */
class RoomKitLogger private constructor(private val moduleName: String, private val fileName: String) {

    companion object {
        const val MODULE_NAME = "RoomKit"

        private const val API = "TuikitLog"
        private const val LOG_KEY_API = "api"
        private const val LOG_KEY_PARAMS = "params"
        private const val LOG_KEY_PARAMS_LEVEL = "level"
        private const val LOG_KEY_PARAMS_MESSAGE = "message"
        private const val LOG_KEY_PARAMS_FILE = "file"
        private const val LOG_KEY_PARAMS_MODULE = "module"
        private const val LOG_KEY_PARAMS_LINE = "line"
        private const val LOG_LEVEL_INFO = 0
        private const val LOG_LEVEL_WARNING = 1
        private const val LOG_LEVEL_ERROR = 2

        @JvmStatic
        fun getLogger(file: String) = RoomKitLogger(MODULE_NAME, file)

        private fun log(module: String, file: String, level: Int, message: String) = try {
            JSONObject().apply {
                put(LOG_KEY_API, API)
                put(LOG_KEY_PARAMS, JSONObject().apply {
                    put(LOG_KEY_PARAMS_LEVEL, level)
                    put(LOG_KEY_PARAMS_MESSAGE, message)
                    put(LOG_KEY_PARAMS_MODULE, module)
                    put(LOG_KEY_PARAMS_FILE, file)
                    put(LOG_KEY_PARAMS_LINE, 0)
                })
            }.toString().also { json ->
                TRTCCloud.sharedInstance(ContextProvider.getApplicationContext()).callExperimentalAPI(json)
            }
        } catch (e: JSONException) {
            Log.e("Logger", e.toString())
        }
    }

    fun info(message: String) = log(LOG_LEVEL_INFO, message)
    fun warn(message: String) = log(LOG_LEVEL_WARNING, message)
    fun error(message: String) = log(LOG_LEVEL_ERROR, message)

    private fun log(level: Int, message: String) = log(moduleName, fileName, level, message)
}