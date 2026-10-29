package io.trtc.tuikit.chat.demo.ai

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantAgent
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantHandoffStatus
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 拉取 customer-server 的公开客服目录与人工转接状态。
 *
 * `GET /api/support/v1/agents` 无需鉴权。文档要求每次打开客服入口都重新拉，
 * 不长期缓存停用状态；[agents] 只作为当前这次展示和标题回退的快照。
 */
object SupportAgentCatalog {

    private const val TAG = "SupportAgentCatalog"
    private const val PLACEHOLDER_HOST = "your-business-server.example.com"
    private const val AGENTS_URL =
        "https://$PLACEHOLDER_HOST/api/support/v1/agents"
    private const val HANDOFF_STATUS_PATH = "/api/im/handoff-status"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var agents: List<AIAssistantAgent> = emptyList()
        private set

    fun displayNameForConversation(conversationID: String): String? {
        return agents.firstOrNull { it.conversationID == conversationID }?.displayName
    }

    fun fetch(onResult: (List<AIAssistantAgent>) -> Unit) {
        if (AGENTS_URL.contains(PLACEHOLDER_HOST)) {
            Log.w(TAG, "Replace the AGENTS_URL placeholder with your business server domain")
            mainHandler.post { onResult(emptyList()) }
            return
        }
        val request = Request.Builder()
            .url(AGENTS_URL)
            .get()
            .header("Cache-Control", "no-store")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "fetch agents failed", e)
                mainHandler.post { onResult(agents) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    agents = parse(body)
                } else {
                    Log.w(TAG, "fetch agents http ${response.code}")
                }
                mainHandler.post { onResult(agents) }
            }
        })
    }

    fun fetchHandoffStatus(onResult: (AIAssistantHandoffStatus) -> Unit) {
        if (AGENTS_URL.contains(PLACEHOLDER_HOST)) {
            Log.w(TAG, "Replace the AGENTS_URL placeholder with your business server domain")
            mainHandler.post { onResult(AIAssistantHandoffStatus.INACTIVE) }
            return
        }
        val url = handoffStatusUrl()
        val userId = LoginStore.shared.loginState.loginUserInfo.value?.userID?.trim().orEmpty()
            .ifEmpty {
                MMKV.defaultMMKV().decodeString(AppConstants.KEY_LOGIN_USER, "").orEmpty().trim()
            }
        val userSig = MMKV.defaultMMKV()
            .decodeString(AppConstants.KEY_LOGIN_USER_SIG, "")
            .orEmpty()
            .trim()
        if (url == null || userId.isEmpty() || userSig.isEmpty()) {
            Log.w(TAG, "fetch handoff status skipped: missing url/userId/userSig")
            mainHandler.post { onResult(AIAssistantHandoffStatus.INACTIVE) }
            return
        }
        val payload = JSONObject()
            .put("user_id", userId)
            .put("usersig", userSig)
            .toString()
        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Cache-Control", "no-store")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "fetch handoff status failed", e)
                mainHandler.post { onResult(AIAssistantHandoffStatus.INACTIVE) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val status = if (response.isSuccessful) {
                    parseHandoffStatus(body)
                } else {
                    Log.w(TAG, "fetch handoff status http ${response.code}")
                    AIAssistantHandoffStatus.INACTIVE
                }
                mainHandler.post { onResult(status) }
            }
        })
    }

    private fun handoffStatusUrl(): String? {
        return AGENTS_URL.toHttpUrlOrNull()?.resolve(HANDOFF_STATUS_PATH)?.toString()
    }

    private fun parse(body: String): List<AIAssistantAgent> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("agents") ?: return emptyList()
        val result = ArrayList<AIAssistantAgent>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val imUserId = item.optString("im_user_id").trim()
            if (imUserId.isEmpty()) {
                continue
            }
            result += AIAssistantAgent(
                id = item.optString("id").trim(),
                name = item.optString("name").trim(),
                welcome = item.optString("welcome").trim(),
                imUserId = imUserId,
                imNickname = item.optString("im_nickname").trim(),
            )
        }
        return result
    }

    internal fun parseHandoffStatus(body: String): AIAssistantHandoffStatus {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return AIAssistantHandoffStatus.INACTIVE
        return AIAssistantHandoffStatus(
            callable = root.optBoolean("callable", false),
            state = root.optString("state", "").trim(),
            seatImUserId = root.optString("seat_im_user_id", "").trim(),
        )
    }
}
