package io.trtc.tuikit.chat.uikit.components.aiassistant

/**
 * Host-provided AI assistant metadata.
 *
 * UIKit deliberately does not own the directory endpoint or any assistant account.
 */
data class AIAssistantAgent(
    val id: String,
    val name: String,
    val welcome: String,
    val imUserId: String,
    val imNickname: String,
) {
    val conversationID: String
        get() = "c2c_$imUserId"

    val displayName: String
        get() = imNickname.ifBlank { name }.ifBlank { imUserId }
}

fun interface AIAssistantAgentLoader {
    fun load(onResult: (List<AIAssistantAgent>) -> Unit)
}

/**
 * Host-provided human seat handoff status for the current visitor.
 *
 * UIKit deliberately does not own the handoff status endpoint.
 */
data class AIAssistantHandoffStatus(
    val callable: Boolean = false,
    val state: String = "",
    val seatImUserId: String = "",
) {
    /**
     * Both `callable` and a non-blank `seatImUserId` are required to initiate a CallKit call to
     * the human seat.
     */
    val isSeatCallable: Boolean
        get() = callable && seatImUserId.isNotBlank()

    /**
     * The visitor is waiting for a human seat (`queued` / `offered`, or `assigned` without a
     * bound seat IM account), so neither CallKit nor AI voice call should start yet.
     */
    val isWaitingForSeat: Boolean
        get() = !isSeatCallable && (
            state == STATE_QUEUED ||
                state == STATE_OFFERED ||
                state == STATE_ASSIGNED
            )

    companion object {
        const val STATE_QUEUED = "queued"
        const val STATE_OFFERED = "offered"
        const val STATE_ASSIGNED = "assigned"
        const val STATE_CLOSED = "closed"

        val INACTIVE = AIAssistantHandoffStatus()
    }
}

fun interface AIAssistantHandoffStatusLoader {
    fun load(onResult: (AIAssistantHandoffStatus) -> Unit)
}
