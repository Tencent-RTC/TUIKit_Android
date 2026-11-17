package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupType
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.atomicxcore.impl.common.ContextProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val BUSINESS_ID_GROUP_CREATE = "group_create"
private const val GROUP_CREATE_MESSAGE_DELAY = 500L

internal class GroupCreateMessageSender(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    fun schedule(conversationId: String, groupType: GroupType) {
        scope.launch {
            delay(GROUP_CREATE_MESSAGE_DELAY)
            send(conversationId, groupType)
        }
    }

    private fun send(conversationId: String, groupType: GroupType) {
        val appContext = ContextProvider.getApplicationContext() ?: return
        val userId = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val customData = JSONObject().apply {
            put("version", 1)
            put("businessID", BUSINESS_ID_GROUP_CREATE)
            put("opUser", userId)
            put("content", appContext.getString(R.string.contact_list_create_group))
            put("cmd", if (groupType == GroupType.COMMUNITY) 1 else 0)
        }.toString()
        val payload = SendMessagePayload.CustomSendMessagePayload(customData = customData)
        MessageInputStore.create(conversationId).sendMessage(
            payload = payload,
            completion = object : CompletionHandler {
                override fun onSuccess() = Unit
                override fun onFailure(code: Int, desc: String) = Unit
            }
        )
    }
}
