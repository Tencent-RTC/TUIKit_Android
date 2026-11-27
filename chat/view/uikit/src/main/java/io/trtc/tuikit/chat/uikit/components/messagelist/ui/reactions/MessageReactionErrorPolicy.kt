package io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions

import androidx.annotation.StringRes
import io.trtc.tuikit.chat.uikit.R

internal object MessageReactionErrorPolicy {
    private const val ERR_SVR_MSG_REACTION_COUNT_LIMIT = 23005
    private const val ERR_SVR_MSG_REACTION_USER_COUNT_LIMIT = 23006

    @StringRes
    fun resolveAddFailureMessageResId(errorCode: Int): Int {
        return when (errorCode) {
            ERR_SVR_MSG_REACTION_COUNT_LIMIT -> R.string.message_list_reaction_count_limit
            ERR_SVR_MSG_REACTION_USER_COUNT_LIMIT -> R.string.message_list_reaction_user_count_limit
            else -> R.string.message_list_reaction_failed
        }
    }
}
