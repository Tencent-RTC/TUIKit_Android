package io.trtc.tuikit.chat.uikit.components.aiassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AIAssistantAgentTest {

    @Test
    fun `display name falls back from nickname to name then user id`() {
        assertEquals("Nickname", agent(name = "Name", nickname = "Nickname").displayName)
        assertEquals("Name", agent(name = "Name", nickname = " ").displayName)
        assertEquals("@RBT#assistant", agent(name = "", nickname = "").displayName)
    }

    @Test
    fun `conversation id uses c2c prefix`() {
        assertEquals("c2c_@RBT#assistant", agent().conversationID)
    }

    @Test
    fun `voice controls match action or session description`() {
        assertTrue(
            AIAssistantControlMessages.isControlPayload(
                customData = """{"action":"enterVoiceCall"}""",
                description = null,
            )
        )
        assertTrue(
            AIAssistantControlMessages.isControlPayload(
                customData = """{"action":"leaveVoiceCall"}""",
                description = null,
            )
        )
        assertTrue(
            AIAssistantControlMessages.isControlPayload(
                customData = "{}",
                description = " voice call session ",
            )
        )
    }

    @Test
    fun `unrelated custom payload is not a voice control`() {
        assertFalse(
            AIAssistantControlMessages.isControlPayload(
                customData = """{"action":"other"}""",
                description = "other",
            )
        )
        assertFalse(
            AIAssistantControlMessages.isControlPayload(
                customData = "not-json",
                description = null,
            )
        )
    }

    @Test
    fun `handoff status is callable only when callable flag and seat user id are both present`() {
        val callableStatus = AIAssistantHandoffStatus(
            callable = true,
            state = AIAssistantHandoffStatus.STATE_ASSIGNED,
            seatImUserId = "@customer_seat#1",
        )
        assertTrue(callableStatus.isSeatCallable)
        assertFalse(callableStatus.isWaitingForSeat)

        val assignedWithoutSeat = AIAssistantHandoffStatus(
            callable = true,
            state = AIAssistantHandoffStatus.STATE_ASSIGNED,
            seatImUserId = " ",
        )
        assertFalse(assignedWithoutSeat.isSeatCallable)
        assertTrue(assignedWithoutSeat.isWaitingForSeat)
    }

    @Test
    fun `handoff status distinguishes waiting states from inactive states`() {
        val queued = AIAssistantHandoffStatus(
            callable = false,
            state = AIAssistantHandoffStatus.STATE_QUEUED,
            seatImUserId = "",
        )
        assertFalse(queued.isSeatCallable)
        assertTrue(queued.isWaitingForSeat)

        val offered = AIAssistantHandoffStatus(
            callable = false,
            state = AIAssistantHandoffStatus.STATE_OFFERED,
            seatImUserId = "",
        )
        assertFalse(offered.isSeatCallable)
        assertTrue(offered.isWaitingForSeat)

        val closed = AIAssistantHandoffStatus(
            callable = false,
            state = AIAssistantHandoffStatus.STATE_CLOSED,
            seatImUserId = "",
        )
        assertFalse(closed.isSeatCallable)
        assertFalse(closed.isWaitingForSeat)

        assertFalse(AIAssistantHandoffStatus.INACTIVE.isSeatCallable)
        assertFalse(AIAssistantHandoffStatus.INACTIVE.isWaitingForSeat)
    }

    private fun agent(
        name: String = "Name",
        nickname: String = "Nickname",
    ) = AIAssistantAgent(
        id = "assistant-id",
        name = name,
        welcome = "Welcome",
        imUserId = "@RBT#assistant",
        imNickname = nickname,
    )
}
