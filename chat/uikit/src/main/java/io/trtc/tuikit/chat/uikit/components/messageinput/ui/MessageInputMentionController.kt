package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.content.Context
import androidx.fragment.app.FragmentActivity
import io.trtc.tuikit.chat.uikit.components.messageinput.model.MentionInfo
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputCoordinator
import io.trtc.tuikit.chat.uikit.components.messageinput.state.PanelEvent
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.MessageInputViewModel
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.senderDisplayName
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

internal class MessageInputMentionController(
    private val context: Context,
    private val textController: MessageInputTextController,
    private val coordinatorProvider: () -> InputCoordinator,
    private val viewModelProvider: () -> MessageInputViewModel?
) {
    fun handleMessageListEvent(event: Map<*, *>): Boolean {
        val source = event["source"] as? String
        val eventType = event["event"] as? String
        if (source != MESSAGE_LIST_SOURCE || eventType != USER_LONG_PRESS_EVENT) return false

        val isGroupChat = viewModelProvider()?.conversationID?.let { ConversationIDUtil.isGroup(it) } == true
        if (!isGroupChat) return true
        val userID = event["userID"] as? String ?: return true
        val message = event["message"] as? MessageInfo
        if (message?.isSentBySelf == true) return true
        val displayName = message?.senderDisplayName?.takeIf { it.isNotBlank() } ?: userID
        textController.insertMention(MentionInfo(userID = userID, displayName = displayName))
        return true
    }

    fun showMentionMemberDialog() {
        val conversationID = viewModelProvider()?.conversationID ?: return
        val groupID = ConversationIDUtil.groupIdOrNull(conversationID) ?: return
        val activity = context as? FragmentActivity ?: return

        coordinatorProvider().dispatch(PanelEvent.RequestCollapse)

        MentionMemberDialogFragment.show(
            activity = activity,
            groupID = groupID,
            onConfirm = { selectedMentions ->
                textController.insertMentionsReplacingTrigger(selectedMentions)
            }
        )
    }

    private companion object {
        private const val MESSAGE_LIST_SOURCE = "MessageList"
        private const val USER_LONG_PRESS_EVENT = "onUserLongPress"
    }
}
