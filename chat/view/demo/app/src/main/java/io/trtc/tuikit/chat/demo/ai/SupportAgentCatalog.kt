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
 *
 * AI 首屏的下拉框在内置助手（公开目录）和用户自定义的机器人（见 [customBotUserId]）之间切换，
 * 选中项记在 [selectedBotUserId]；[fetch] 只回传当前选中的那一个助手。
 * 体验空间创建的机器人不在公开目录里，需要用 `POST /api/support/v1/agents/check` 校验后直接使用。
 */
object SupportAgentCatalog {

    private const val TAG = "SupportAgentCatalog"
    private const val AGENTS_URL = "https://md5.ltd/api/support/v1/agents"
    private const val KEY_CUSTOM_BOT_USER_ID = "ai_custom_bot_user_id"
    private const val KEY_SELECTED_BOT_USER_ID = "ai_selected_bot_user_id"
    const val BOT_USER_ID_PREFIX = "@RBT#"
    private const val HANDOFF_STATUS_PATH = "/api/im/handoff-status"
    private const val AGENT_CHECK_PATH = "/api/support/v1/agents/check"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 服务器内置的助手目录，供下拉框展示。 */
    @Volatile
    var directory: List<AIAssistantAgent> = emptyList()
        private set

    /** 目录是否成功拉取过；网络失败时不据空目录判断需要引导。 */
    @Volatile
    private var directoryLoaded = false

    /** 自定义机器人最近一次校验得到的展示信息，下拉框用来显示昵称。 */
    @Volatile
    var customAgent: AIAssistantAgent? = null
        private set

    /** 最近一次 [fetch] 回传的结果，只含当前选中的助手。 */
    @Volatile
    var agents: List<AIAssistantAgent> = emptyList()
        private set

    /** 用户填写的机器人 UserID，为空表示还没配置过。 */
    var customBotUserId: String
        get() = decode(KEY_CUSTOM_BOT_USER_ID)
        set(value) {
            encode(KEY_CUSTOM_BOT_USER_ID, value)
            customAgent = null
        }

    /** 下拉框当前选中的助手 UserID，为空或已下线时使用目录里的第一个。 */
    var selectedBotUserId: String
        get() = decode(KEY_SELECTED_BOT_USER_ID)
        set(value) = encode(KEY_SELECTED_BOT_USER_ID, value)

    val isCustomSelected: Boolean
        get() = customBotUserId.let { it.isNotEmpty() && it == selectedBotUserId }

    /**
     * 目录按当前登录的 SDKAppID 拉取。登录自己的应用（例如从 GitHub 下载源码后填入）时目录为空，
     * 又还没选中自定义助手，就没有可对话的助手，需要先按官网文档创建并填写自己的助手。
     */
    val needsOwnAssistant: Boolean
        get() = directoryLoaded && directory.isEmpty() && !isCustomSelected

    /** 当前登录的 SDKAppID，未登录时为 null。 */
    private fun loginSdkAppId(): Long? = LoginStore.shared.sdkAppID.toLong().takeIf { it > 0 }

    enum class BotCheckResult { FOUND, NOT_FOUND, NETWORK_ERROR }

    fun displayNameForConversation(conversationID: String): String? {
        return (directory + listOfNotNull(customAgent))
            .firstOrNull { it.conversationID == conversationID }?.displayName
    }

    fun fetch(onResult: (List<AIAssistantAgent>) -> Unit) {
        requestDirectory { fetched ->
            if (fetched != null) {
                directoryLoaded = true
                directory = fetched
            }
            val customId = customBotUserId
            if (!isCustomSelected || directory.any { it.imUserId == customId }) {
                agents = listOfNotNull(selectedBuiltIn())
                onResult(agents)
                return@requestDirectory
            }
            // 体验空间的机器人不在公开目录里，需要单独校验，并用回包里的昵称和欢迎语展示。
            requestBotInfo(customId) { info ->
                when {
                    // 校验失败时保留用户的选择。
                    info == null -> agents = listOf(customAgent ?: buildCustomAgent(customId, null))
                    info.exists -> {
                        val agent = buildCustomAgent(customId, info)
                        customAgent = agent
                        agents = listOf(agent)
                    }
                    // 服务器明确告知机器人已不存在时回退到内置助手。
                    else -> {
                        selectedBotUserId = ""
                        agents = listOfNotNull(selectedBuiltIn())
                    }
                }
                onResult(agents)
            }
        }
    }

    private fun selectedBuiltIn(): AIAssistantAgent? {
        val selected = selectedBotUserId
        return directory.firstOrNull { it.imUserId == selected } ?: directory.firstOrNull()
    }

    private fun decode(key: String): String =
        MMKV.defaultMMKV().decodeString(key, "").orEmpty().trim()

    private fun encode(key: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            MMKV.defaultMMKV().removeValueForKey(key)
        } else {
            MMKV.defaultMMKV().encode(key, trimmed)
        }
    }

    /** 校验 [userId] 是否是服务器上已启用并已连接的机器人，含体验空间创建的机器人。 */
    fun checkBot(userId: String, onResult: (BotCheckResult) -> Unit) {
        requestBotInfo(userId.trim()) { info ->
            onResult(
                when {
                    info == null -> BotCheckResult.NETWORK_ERROR
                    info.exists -> BotCheckResult.FOUND
                    else -> BotCheckResult.NOT_FOUND
                }
            )
        }
    }

    /** `agents/check` 的回包。未找到时只有 [exists]=false。 */
    private data class BotInfo(val exists: Boolean, val nickname: String, val welcome: String)

    /** 公开目录没有该机器人信息，用校验回包里的昵称和欢迎语构造助手；老服务端没有这些字段时回退为 ID。 */
    private fun buildCustomAgent(userId: String, info: BotInfo?) = AIAssistantAgent(
        id = userId,
        name = "",
        welcome = info?.welcome.orEmpty(),
        imUserId = userId,
        imNickname = info?.nickname.orEmpty(),
    )

    /**
     * 调用 `POST /api/support/v1/agents/check`。回传服务器的判断及机器人昵称、欢迎语；
     * 缺少登录凭据、网络或鉴权失败时回传 null。回调始终在主线程。
     */
    private fun requestBotInfo(userId: String, onResult: (BotInfo?) -> Unit) {
        val url = AGENTS_URL.toHttpUrlOrNull()?.resolve(AGENT_CHECK_PATH)?.toString()
        val (loginUserId, userSig) = loginCredentials()
        if (url == null || loginUserId.isEmpty() || userSig.isEmpty()) {
            Log.w(TAG, "check bot skipped: missing url/userId/userSig")
            mainHandler.post { onResult(null) }
            return
        }
        val payload = JSONObject()
            .put("user_id", loginUserId)
            .put("usersig", userSig)
            .put("im_user_id", userId)
            .putSdkAppId()
            .toString()
        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Cache-Control", "no-store")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "check bot failed", e)
                mainHandler.post { onResult(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val info = if (response.isSuccessful) {
                    runCatching { JSONObject(body) }.getOrNull()
                        ?.takeIf { it.has("exists") }
                        ?.let {
                            BotInfo(
                                exists = it.optBoolean("exists", false),
                                nickname = it.optString("im_nickname").trim(),
                                welcome = it.optString("welcome").trim(),
                            )
                        }
                } else {
                    Log.w(TAG, "check bot http ${response.code}")
                    null
                }
                mainHandler.post { onResult(info) }
            }
        })
    }

    /** 当前登录用户的 UserID 与 UserSig，取不到时为空串。 */
    private fun loginCredentials(): Pair<String, String> {
        val userId = LoginStore.shared.loginState.loginUserInfo.value?.userID?.trim().orEmpty()
            .ifEmpty {
                MMKV.defaultMMKV().decodeString(AppConstants.KEY_LOGIN_USER, "").orEmpty().trim()
            }
        val userSig = MMKV.defaultMMKV()
            .decodeString(AppConstants.KEY_LOGIN_USER_SIG, "")
            .orEmpty()
            .trim()
        return userId to userSig
    }

    /** 带上当前登录的 SDKAppID，服务端据此区分平台体验应用与客户自己的应用。 */
    private fun JSONObject.putSdkAppId(): JSONObject = apply {
        loginSdkAppId()?.let { put("sdkappid", it) }
    }

    /** 按当前登录的 SDKAppID 拉取目录，只返回属于该应用的助手；请求失败时回传 null，调用方沿用旧快照。回调始终在主线程。 */
    private fun requestDirectory(onResult: (List<AIAssistantAgent>?) -> Unit) {
        val url = AGENTS_URL.toHttpUrlOrNull()?.newBuilder()?.apply {
            loginSdkAppId()?.let { addQueryParameter("sdkappid", it.toString()) }
        }?.build() ?: run {
            mainHandler.post { onResult(null) }
            return
        }
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Cache-Control", "no-store")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "fetch agents failed", e)
                mainHandler.post { onResult(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val parsed = if (response.isSuccessful) {
                    parse(body)
                } else {
                    Log.w(TAG, "fetch agents http ${response.code}")
                    null
                }
                mainHandler.post { onResult(parsed) }
            }
        })
    }

    fun fetchHandoffStatus(onResult: (AIAssistantHandoffStatus) -> Unit) {
        val url = handoffStatusUrl()
        val (userId, userSig) = loginCredentials()
        if (url == null || userId.isEmpty() || userSig.isEmpty()) {
            Log.w(TAG, "fetch handoff status skipped: missing url/userId/userSig")
            mainHandler.post { onResult(AIAssistantHandoffStatus.INACTIVE) }
            return
        }
        val payload = JSONObject()
            .put("user_id", userId)
            .put("usersig", userSig)
            .putSdkAppId()
            .apply { agents.firstOrNull()?.imUserId?.let { put("im_user_id", it) } }
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
