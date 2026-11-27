package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel

import com.tencent.imsdk.BaseConstants
import com.tencent.qcloud.tuicore.util.ErrorMessageConverter

private const val GROUP_NAME_PREVIEW_COUNT = 3

internal data class SearchRequestToken(
    val sequence: Long,
    val keyword: String
)

internal class LatestSearchRequestTracker {
    private var nextSequence = 0L
    private var latestToken: SearchRequestToken? = null

    fun begin(keyword: String): SearchRequestToken {
        val token = SearchRequestToken(
            sequence = ++nextSequence,
            keyword = keyword.trim()
        )
        latestToken = token
        return token
    }

    fun invalidate() {
        latestToken = null
        nextSequence++
    }

    fun accepts(token: SearchRequestToken, currentKeyword: String): Boolean {
        return latestToken == token && token.keyword == currentKeyword.trim()
    }
}

internal object ContactRequestResultPolicy {
    fun shouldDismissAfterRequest(isSuccess: Boolean): Boolean = isSuccess

    fun isSuccessfulAddFriendResponse(errorCode: Int): Boolean =
        errorCode == BaseConstants.ERR_SVR_FRIENDSHIP_ALREADY_FRIENDS ||
            errorCode == BaseConstants.ERR_SVR_FRIENDSHIP_ALLOW_TYPE_NEED_CONFIRM

    fun getErrorMessage(errorCode: Int, errorMessage: String, getString: (Int) -> String): String =
        ErrorMessageConverter.ERROR_CODE_MAP[errorCode]?.let(getString) ?: errorMessage
}

internal object ContactListGroupNameFormatter {
    fun generate(
        names: List<String>,
        separator: String,
        suffix: (remainingCount: Int) -> String
    ): String {
        val displayNames = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (displayNames.isEmpty()) {
            return ""
        }
        val previewNames = displayNames
            .take(GROUP_NAME_PREVIEW_COUNT)
            .joinToString(separator = separator)
        val remainingCount = displayNames.size - GROUP_NAME_PREVIEW_COUNT
        return if (remainingCount > 0) {
            previewNames + suffix(remainingCount)
        } else {
            previewNames
        }
    }
}
