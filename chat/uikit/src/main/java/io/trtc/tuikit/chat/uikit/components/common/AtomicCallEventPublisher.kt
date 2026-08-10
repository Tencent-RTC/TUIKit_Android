package io.trtc.tuikit.chat.uikit.components.common

import io.trtc.tuikit.atomicx.common.util.PublishParams
import io.trtc.tuikit.atomicx.common.util.TUIEventBus
import io.trtc.tuikit.atomicxcore.impl.common.DataReport
import io.trtc.tuikit.atomicxcore.impl.common.InteractionMetrics

internal object AtomicCallEventPublisher {
    const val MEDIA_TYPE_AUDIO = "audio"
    const val MEDIA_TYPE_VIDEO = "video"

    private const val EVENT_START_CALL = "call.startCall"
    private const val EVENT_START_JOIN = "call.startJoin"
    private const val KEY_PARTICIPANT_IDS = "participantIds"
    private const val KEY_MEDIA_TYPE = "mediaType"
    private const val KEY_CHAT_GROUP_ID = "chatGroupId"
    private const val KEY_TIMEOUT = "timeout"
    private const val KEY_CALL_ID = "callId"
    private const val DEFAULT_TIMEOUT_SECONDS = 30

    fun publishStartCall(
        participantIds: List<String>,
        mediaType: String,
        chatGroupId: String? = null
    ) {
        if (participantIds.isEmpty()) {
            return
        }
        val data = mutableMapOf<String, Any?>(
            KEY_PARTICIPANT_IDS to participantIds,
            KEY_MEDIA_TYPE to mediaType,
            KEY_TIMEOUT to DEFAULT_TIMEOUT_SECONDS
        )
        if (!chatGroupId.isNullOrEmpty()) {
            data[KEY_CHAT_GROUP_ID] = chatGroupId
        }
        // CallKit subscribes to the start-call event once integrated, so a live
        // subscriber means the host app has CallKit. Unlike reflection on class
        // names, this survives R8/ProGuard obfuscation.
        if (TUIEventBus.shared.hasSubscriber(EVENT_START_CALL, null)) {
            DataReport.reportInteractionMetrics(InteractionMetrics.CHAT_INVOKE_CALL)
        }
        TUIEventBus.shared.publish(
            EVENT_START_CALL,
            null,
            PublishParams(isSticky = false, data = data)
        )
    }

    fun publishStartJoin(callId: String) {
        if (callId.isEmpty()) {
            return
        }
        TUIEventBus.shared.publish(
            EVENT_START_JOIN,
            null,
            PublishParams(
                isSticky = false,
                data = mapOf(KEY_CALL_ID to callId)
            )
        )
    }
}
