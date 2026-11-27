package io.trtc.tuikit.chat.uikit.components.common

import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputActionIDs
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.MessageInputMenuActionCallbacks
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.MessageInputMenuActionIcons
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.MessageInputMenuActionLabels
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.buildDefaultMessageInputMenuActions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupCallAttributeParserTest {

    @Test
    fun `hasOngoingGroupCall returns true for active multi-user group call`() {
        val raw = """
            {
                "business_type": "callkit",
                "call_id": "call_123",
                "call_media_type": "video",
                "user_list": [
                    {"userid": "alice", "nickname": "Alice"},
                    {"userID": "bob", "name": "Bob"}
                ]
            }
        """.trimIndent()

        assertTrue(GroupCallAttributeParser.hasOngoingGroupCall(raw))
    }

    @Test
    fun `hasOngoingGroupCall returns false when users count is at most one or invalid`() {
        val singleUser = """
            {
                "business_type": "callkit",
                "call_id": "call_123",
                "call_media_type": "audio",
                "user_list": [{"userid": "alice"}]
            }
        """.trimIndent()
        val emptyUserId = """
            {
                "business_type": "callkit",
                "call_id": "call_123",
                "call_media_type": "audio",
                "user_list": [{"userid": "alice"}, {"userid": ""}]
            }
        """.trimIndent()
        val wrongBusiness = """
            {
                "business_type": "other",
                "call_id": "call_123",
                "call_media_type": "audio",
                "user_list": [{"userid": "alice"}, {"userid": "bob"}]
            }
        """.trimIndent()

        assertFalse(GroupCallAttributeParser.hasOngoingGroupCall(null))
        assertFalse(GroupCallAttributeParser.hasOngoingGroupCall(""))
        assertFalse(GroupCallAttributeParser.hasOngoingGroupCall("{invalid"))
        assertFalse(GroupCallAttributeParser.hasOngoingGroupCall(singleUser))
        assertFalse(GroupCallAttributeParser.hasOngoingGroupCall(emptyUserId))
        assertFalse(GroupCallAttributeParser.hasOngoingGroupCall(wrongBusiness))
    }

    @Test
    fun `buildDefaultMessageInputMenuActions only disables audio and video call actions`() {
        val labels = MessageInputMenuActionLabels(
            album = "Album",
            takePhoto = "Photo",
            recordVideo = "Video",
            file = "File",
            audioCall = "Audio",
            videoCall = "Video Call"
        )
        val icons = MessageInputMenuActionIcons(1, 2, 3, 4, 5, 6)

        val disabledActions = buildDefaultMessageInputMenuActions(
            isShowPhotoTaker = true,
            isShowVideoCall = true,
            isShowAudioCall = true,
            labels = labels,
            callbacks = MessageInputMenuActionCallbacks(),
            icons = icons,
            isCallActionEnabled = false
        ).associateBy { it.ID }

        assertTrue(disabledActions.getValue(MessageInputActionIDs.ALBUM).isEnabled)
        assertTrue(disabledActions.getValue(MessageInputActionIDs.TAKE_PHOTO).isEnabled)
        assertTrue(disabledActions.getValue(MessageInputActionIDs.RECORD_VIDEO).isEnabled)
        assertTrue(disabledActions.getValue(MessageInputActionIDs.FILE).isEnabled)
        assertFalse(disabledActions.getValue(MessageInputActionIDs.VIDEO_CALL).isEnabled)
        assertFalse(disabledActions.getValue(MessageInputActionIDs.AUDIO_CALL).isEnabled)
    }
}
