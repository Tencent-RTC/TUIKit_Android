package io.trtc.tuikit.chat.demo.common

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.chat.app.R

object WelcomeMessageSender {

    private const val TAG = "WelcomeMessageSender"
    private const val ADMINISTRATOR_CONVERSATION_ID = "c2c_administrator"
    private const val SEND_DELAY_MS = 1000L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun scheduleWelcomeMessage(context: Context) {
        val appContext = context.applicationContext
        mainHandler.postDelayed({ sendWelcomeMessage(appContext) }, SEND_DELAY_MS)
    }

    private fun sendWelcomeMessage(context: Context) {
        val inputStore = MessageInputStore.create(ADMINISTRATOR_CONVERSATION_ID)
        inputStore.sendMessage(
            SendMessagePayload.TextSendMessagePayload(context.getString(R.string.demo_welcome_message)),
            null,
            object : CompletionHandler {
                override fun onSuccess() {
                    Log.i(TAG, "send welcome message success")
                }

                override fun onFailure(code: Int, desc: String) {
                    Log.e(TAG, "send welcome message failed: $code $desc")
                }
            }
        )
    }
}
