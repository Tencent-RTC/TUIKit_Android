package io.trtc.tuikit.chat.uikit.components.aiassistant

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMMessage
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore

/**
 * 会话还是空的时候，把目录里的欢迎语插成一条本地文本消息，看起来像机器人发的，但不上行。
 *
 * 接口文档写明 `welcome` 不是已经发出的 IM 消息。只有首次使用、或者用户清空了聊天记录之后
 * 才补这一条：只要能拉到任何一条历史消息，就说明这个会话已经聊过，不再插。
 *
 * 判空时忽略机器人控制信令（登录激活 ping、语音通话进出），那些不展示给用户，
 * 有它们不代表会话真的聊过。
 */
internal object AgentWelcomeInserter {

    private const val TAG = "AgentWelcome"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackLock = Any()
    private val pendingCallbacks = mutableMapOf<String, MutableList<() -> Unit>>()

    fun insertIfConversationEmpty(agent: AIAssistantAgent, onDone: () -> Unit) {
        val welcome = agent.welcome
        if (welcome.isEmpty()) {
            finish(onDone)
            return
        }
        val conversationID = agent.conversationID
        val shouldStart = synchronized(callbackLock) {
            val callbacks = pendingCallbacks.getOrPut(conversationID) { mutableListOf() }
            callbacks += onDone
            callbacks.size == 1
        }
        if (!shouldStart) {
            return
        }
        val store = MessageListStore.create(agent.conversationID)
        store.loadMessages(
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    val hasHistory = store.state.messageList.value.any { message ->
                        !AIAssistantControlMessages.isControlMessage(message)
                    }
                    if (hasHistory) {
                        finish(conversationID)
                    } else {
                        insertLocal(agent.imUserId, welcome, conversationID)
                    }
                }

                /**
                 * 拉不到历史就无从判断会话聊没聊过，插进去很可能和已有的欢迎语重复。
                 * 重复是永久污染，少一条只是这次没看到，下次能拉到历史时会重新判断。
                 */
                override fun onFailure(code: Int, desc: String) {
                    Log.w(TAG, "load history failed, skip welcome: $code $desc")
                    finish(conversationID)
                }
            },
        )
    }

    private fun insertLocal(robotUserId: String, welcome: String, conversationID: String) {
        val message = V2TIMManager.getMessageManager().createTextMessage(welcome)
        if (message == null) {
            finish(conversationID)
            return
        }
        V2TIMManager.getMessageManager().insertC2CMessageToLocalStorage(
            message,
            robotUserId,
            robotUserId,
            object : V2TIMValueCallback<V2TIMMessage> {
                override fun onSuccess(value: V2TIMMessage?) {
                    finish(conversationID)
                }

                override fun onError(code: Int, desc: String?) {
                    Log.w(TAG, "insert welcome failed: $code $desc")
                    finish(conversationID)
                }
            },
        )
    }

    private fun finish(conversationID: String) {
        val callbacks = synchronized(callbackLock) {
            pendingCallbacks.remove(conversationID).orEmpty()
        }
        callbacks.forEach(::finish)
    }

    private fun finish(onDone: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onDone()
        } else {
            mainHandler.post(onDone)
        }
    }
}
