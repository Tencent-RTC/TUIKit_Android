package com.tencent.qcloud.tuikit.tuicallkit.bridge

import android.content.Context
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitLogger
import io.trtc.tuikit.atomicx.common.util.NotifyParams
import io.trtc.tuikit.atomicx.common.util.TUIEventBus
import io.trtc.tuikit.atomicx.common.util.TUIObserver
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParams

internal object ChatCallEventSubscriber {
    private const val EVENT_START_CALL = "call.startCall"
    private const val EVENT_START_JOIN = "call.startJoin"
    private const val KEY_PARTICIPANT_IDS = "participantIds"
    private const val KEY_MEDIA_TYPE = "mediaType"
    private const val KEY_CHAT_GROUP_ID = "chatGroupId"
    private const val KEY_TIMEOUT = "timeout"
    private const val KEY_CALL_ID = "callId"

    private val logger = CallKitLogger.getLogger("ChatCallEventSubscriber")
    private lateinit var applicationContext: Context

    @Volatile
    private var initialized = false

    private val eventBusObserver = object : TUIObserver() {
        override fun onNotify(event: String, key: String?, params: NotifyParams?) {
            if (params?.data == null) return
            when (event) {
                EVENT_START_CALL -> handleStartCall(params)
                EVENT_START_JOIN -> handleStartJoin(params)
            }
        }
    }

    fun ensureSubscribed(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            applicationContext = context.applicationContext
            TUIEventBus.shared.subscribe(EVENT_START_CALL, null, eventBusObserver)
            TUIEventBus.shared.subscribe(EVENT_START_JOIN, null, eventBusObserver)
            initialized = true
        }
    }

    private fun handleStartCall(params: NotifyParams) {
        val participantIds = params.data?.get(KEY_PARTICIPANT_IDS) as? List<*> ?: return
        val userIdList = participantIds.filterIsInstance<String>()
        if (userIdList.isEmpty()) return

        val mediaType = if (params.data?.get(KEY_MEDIA_TYPE) == "video") CallMediaType.Video else CallMediaType.Audio
        val callParams = CallParams().apply {
            chatGroupId = params.data?.get(KEY_CHAT_GROUP_ID) as? String ?: ""
            timeout = params.data?.get(KEY_TIMEOUT) as? Int ?: 30
        }
        logger.info("handleStartCall, userIdList: $userIdList, mediaType: $mediaType, chatGroupId: ${callParams.chatGroupId}")
        TUICallKit.createInstance(applicationContext).calls(userIdList, mediaType, callParams, null)
    }

    private fun handleStartJoin(params: NotifyParams) {
        val callId = params.data?.get(KEY_CALL_ID) as? String
        if (callId.isNullOrEmpty()) return
        logger.info("handleStartJoin, callId: $callId")
        TUICallKit.createInstance(applicationContext).join(callId, null)
    }
}
