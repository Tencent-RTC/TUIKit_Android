package io.trtc.tuikit.chat.demo.customerservice

import android.content.Context
import android.util.Log
import com.tencentcloud.tencentcloudcustomer.Callbacks.AIDeskCallback
import com.tencentcloud.tencentcloudcustomer.TencentAiDeskCustomer
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageMatcher
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageSummaryProvider
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageListMessageSummaryRegistry
import org.json.JSONObject
import java.util.Locale

object CustomerServiceManager {

    private const val TAG = "CustomerService"

    private const val KEY_CUSTOMER_SERVICE_PLUGIN = "customerServicePlugin"

    private const val KEY_SRC = "src"
    private const val SRC_WELCOME = "7"

    @Volatile
    private var summaryRegistered = false

    fun initAndStart(context: Context, sdkAppId: Int, userId: String, userSig: String) {
        TencentAiDeskCustomer.getInstance().initWithProfile(
            context.applicationContext,
            sdkAppId,
            userId,
            userSig,
            null,
            null,
            object : AIDeskCallback() {
                override fun onSuccess() {
                    TencentAiDeskCustomer.getInstance().setShowHumanService(true)
                    sendWelcomeMessageAndPin()
                }

                override fun onError(code: Int, desc: String?) {
                    Log.e(TAG, "customer service login failed: $code $desc")
                }
            }
        )
    }

    fun openCustomerServiceChat(context: Context) {
        val intent = TencentAiDeskCustomer.getInstance()
            .getCustomerServiceChatIntent(context, AppConstants.CUSTOMER_SERVICE_USER_ID)
        context.startActivity(intent)
    }

    fun registerSummary() {
        if (summaryRegistered) {
            return
        }
        summaryRegistered = true
        MessageListMessageSummaryRegistry.addCustomMessageSummary(
            matcher = MessageMatcher { message ->
                message.messageType == MessageType.CUSTOM && isCustomerServiceMessage(message)
            },
            summaryProvider = MessageSummaryProvider { summaryContext ->
                summaryContext.context.getString(R.string.demo_customer_service_summary)
            }
        )
    }

    private fun sendWelcomeMessageAndPin() {
        val language = currentLanguageTag()
        val payload = JSONObject().apply {
            put(KEY_SRC, SRC_WELCOME)
            put(KEY_CUSTOMER_SERVICE_PLUGIN, 0)
            put("triggeredContent", JSONObject().apply { put("language", language) })
        }.toString()

        val inputStore = MessageInputStore.create(AppConstants.CUSTOMER_SERVICE_CONVERSATION_ID)
        inputStore.sendMessage(
            SendMessagePayload.CustomSendMessagePayload(customData = payload),
            null,
            object : CompletionHandler {
                override fun onSuccess() {
                    pinCustomerServiceConversation()
                }

                override fun onFailure(code: Int, desc: String) {
                    Log.e(TAG, "send welcome message failed: $code $desc")
                }
            }
        )
    }

    private fun pinCustomerServiceConversation() {
        ConversationListStore.create().pinConversation(
            AppConstants.CUSTOMER_SERVICE_CONVERSATION_ID,
            true,
            object : CompletionHandler {
                override fun onSuccess() {}

                override fun onFailure(code: Int, desc: String) {
                    Log.e(TAG, "pin conversation failed: $code $desc")
                }
            }
        )
    }

    private fun isCustomerServiceMessage(message: io.trtc.tuikit.atomicxcore.api.message.MessageInfo): Boolean {
        val customData = (message.messagePayload as? CustomMessagePayload)?.customData ?: return false
        return runCatching {
            JSONObject(customData).has(KEY_CUSTOMER_SERVICE_PLUGIN)
        }.getOrDefault(false)
    }

    private fun currentLanguageTag(): String {
        val locale = Locale.getDefault()
        return if (locale.language.equals("zh", ignoreCase = true)) {
            if (isTraditionalChinese(locale)) "zh-Hant" else "zh-Hans"
        } else {
            locale.language.ifEmpty { "zh-Hans" }
        }
    }

    private fun isTraditionalChinese(locale: Locale): Boolean {
        val tag = locale.toLanguageTag().lowercase()
        return tag.contains("hant") || tag.contains("tw") || tag.contains("hk") || tag.contains("mo")
    }
}
