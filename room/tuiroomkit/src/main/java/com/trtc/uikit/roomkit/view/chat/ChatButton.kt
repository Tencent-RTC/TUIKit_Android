package com.trtc.uikit.roomkit.view.chat

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.ui.BaseView
import io.trtc.tuikit.atomicxcore.api.message.MessageEvent
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ChatButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val tvUnreadCount: TextView
    private var subscribeJob: Job? = null
    private var messageListStore: MessageListStore? = null
    private var unreadCount: Int = 0

    var onClick: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_chat_button, this, true)
        tvUnreadCount = findViewById(R.id.tv_unread_count)
        setOnClickListener { onClick?.invoke() }
    }

    public override fun init(roomID: String) {
        super.init(roomID)
    }

    override fun initStore(roomID: String) {
        messageListStore = MessageListStore.create("group_$roomID")
    }

    override fun addObserver() {
        val store = messageListStore ?: return
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            store.messageEventFlow.collect { event ->
                when (event) {
                    is MessageEvent.OnReceiveNewMessage -> handleNewMessage(event.message)
                }
            }
        }
    }

    override fun removeObserver() {
        subscribeJob?.cancel()
        subscribeJob = null
        messageListStore = null
        unreadCount = 0
    }

    fun clearUnreadCount() {
        unreadCount = 0
        tvUnreadCount.visibility = GONE
    }

    private fun handleNewMessage(message: MessageInfo) {
        if (message.isSentBySelf) {
            return
        }
        unreadCount++
        tvUnreadCount.visibility = VISIBLE
        tvUnreadCount.text = if (unreadCount > 99) "99" else unreadCount.toString()
    }
}
