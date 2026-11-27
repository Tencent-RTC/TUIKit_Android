package io.trtc.tuikit.chat.uikit.components.messagelist.utils

import android.content.Context
import com.tencent.imsdk.v2.V2TIMManager
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus

internal data class RecalledMessageDisplaySpec(
    val textResId: Int,
    val formatArg: String? = null,
)

internal object RecalledMessageDisplayPolicy {
    fun format(context: Context, message: MessageInfo): String? {
        val spec = createSpec(message) ?: return null
        return spec.formatArg?.let { context.getString(spec.textResId, it) }
            ?: context.getString(spec.textResId)
    }

    fun createSpec(
        message: MessageInfo,
        currentUserID: String? = V2TIMManager.getInstance().loginUser,
    ): RecalledMessageDisplaySpec? {
        if (message.status != MessageStatus.REVOKED) {
            return null
        }
        val revokerUserID = message.revokerInfo?.userID.takeUnless { it.isNullOrBlank() }
        val selfUserID = currentUserID.takeUnless { it.isNullOrBlank() }
        if (!revokerUserID.isNullOrBlank() && revokerUserID == selfUserID) {
            return RecalledMessageDisplaySpec(R.string.message_list_message_tips_you_recall_message)
        }
        val revokerName = message.revokerInfo?.nickname.takeUnless { it.isNullOrBlank() }
            ?: revokerUserID
        if (!revokerName.isNullOrBlank()) {
            return RecalledMessageDisplaySpec(
                textResId = R.string.message_list_message_tips_recall_message_format,
                formatArg = revokerName,
            )
        }
        val textResId = if (message.conversationType == ConversationType.C2C) {
            R.string.message_list_message_tips_others_recall_message
        } else {
            R.string.message_list_message_tips_normal_recall_message
        }
        return RecalledMessageDisplaySpec(textResId)
    }
}
