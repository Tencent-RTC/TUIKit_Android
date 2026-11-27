package io.trtc.tuikit.chat.demo.ai

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import org.json.JSONObject

/**
 * 统计用户在 AI 助手 tab 的停留时长：连续停留超过 5s / 10s / 30s 时分别上报 10221 / 10222 / 10223。
 *
 * 切走 tab 或应用退到后台会取消未触发的计时，不会把多次进入累加。
 * 每次进入都单独计时：同一次停留内每个值最多上报一次，下次进入重新计。上报失败不影响业务。
 */
class AIStayReporter(
    private val scheduler: Scheduler = HandlerScheduler(),
    private val report: (Int) -> Unit = ::reportFeatureUsage,
) {

    interface Scheduler {
        fun postDelayed(task: Runnable, delayMs: Long)
        fun cancel(task: Runnable)
    }

    private class HandlerScheduler : Scheduler {
        private val handler = Handler(Looper.getMainLooper())
        override fun postDelayed(task: Runnable, delayMs: Long) {
            handler.postDelayed(task, delayMs)
        }

        override fun cancel(task: Runnable) {
            handler.removeCallbacks(task)
        }
    }

    private val tasks = mutableListOf<Runnable>()
    private var staying = false

    /** AI tab 对用户可见了。 */
    fun onEnter() {
        if (staying) return
        staying = true
        THRESHOLDS.forEach { (delayMs, type) ->
            val task = Runnable { report(type) }
            tasks += task
            scheduler.postDelayed(task, delayMs)
        }
    }

    /** AI tab 被切走，或应用离开前台。 */
    fun onLeave() {
        staying = false
        tasks.forEach { scheduler.cancel(it) }
        tasks.clear()
    }

    companion object {
        private const val TAG = "AIStayReporter"
        internal val THRESHOLDS = listOf(
            5_000L to 10221,
            10_000L to 10222,
            30_000L to 10223,
        )

        private fun reportFeatureUsage(type: Int) {
            val param = JSONObject().put("UIComponentType", type).toString()
            V2TIMManager.getInstance().callExperimentalAPI(
                "reportTUIFeatureUsage",
                param,
                object : V2TIMValueCallback<Any> {
                    override fun onSuccess(value: Any?) = Unit

                    override fun onError(code: Int, desc: String?) {
                        Log.w(TAG, "report $type failed: $code $desc")
                    }
                },
            )
        }
    }
}
