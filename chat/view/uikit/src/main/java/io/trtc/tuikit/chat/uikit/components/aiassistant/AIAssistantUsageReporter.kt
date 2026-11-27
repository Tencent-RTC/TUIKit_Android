package io.trtc.tuikit.chat.uikit.components.aiassistant

import android.util.Log
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import org.json.JSONObject

/**
 * AI 助手页面实例的使用上报。
 *
 * 文字消息每个页面实例只报一次；进入实时语音每次都报。上报失败不影响业务流程。
 */
internal class AIAssistantUsageReporter(
    private val report: (Int) -> Unit = ::reportFeatureUsage
) {
    private var hasReportedMessageUsage = false

    @Synchronized
    fun onMessageSent() {
        if (hasReportedMessageUsage) {
            return
        }
        hasReportedMessageUsage = true
        report(MESSAGE_USAGE)
    }

    fun onVoiceEntered() {
        report(VOICE_ENTRY)
    }

    private companion object {
        const val TAG = "AIAssistantReporter"
        const val MESSAGE_USAGE = 1022
        const val VOICE_ENTRY = 1023

        fun reportFeatureUsage(type: Int) {
            val param = JSONObject().put("UIComponentType", type).toString()
            V2TIMManager.getInstance().callExperimentalAPI(
                "reportTUIFeatureUsage",
                param,
                object : V2TIMValueCallback<Any> {
                    override fun onSuccess(value: Any?) = Unit

                    override fun onError(code: Int, desc: String?) {
                        Log.w(TAG, "report $type failed: $code $desc")
                    }
                }
            )
        }
    }
}
