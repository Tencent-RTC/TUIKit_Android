package io.trtc.tuikit.chat.uikit.components.contactlist.utils

import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.common.ContextProvider
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.FriendApplicationInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationHandledResult
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationHandledStatus
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationType
import io.trtc.tuikit.atomicxcore.api.login.UserProfile

val ContactInfo.displayName
    get() = friendRemark?.takeIf { it.isNotEmpty() }
        ?: nickname?.takeIf { it.isNotEmpty() }
        ?: userID

fun ContactInfo.matchesSearchQuery(query: String): Boolean {
    val keyword = query.trim()
    if (keyword.isEmpty()) {
        return true
    }
    return listOf(displayName, userID, nickname, friendRemark)
        .filterNotNull()
        .distinct()
        .any { value ->
            value.contains(keyword, ignoreCase = true)
        }
}

val FriendApplicationInfo.displayName
    get() = title ?: userID

val UserProfile.displayName
    get() = nickname?.takeIf { it.isNotEmpty() }
        ?: userID

val GroupApplicationInfo.fromUserDisplayName
    get() = when {
        !fromUserNickname.isNullOrEmpty() -> fromUserNickname
        else -> fromUser
    } ?: ""

val GroupApplicationInfo.toUserDisplayName
    get() = toUser ?: ""

val GroupApplicationInfo.groupDisplayName: String
    get() {
        val groupName = readStringProperty("groupName")?.takeIf { it.isNotBlank() }
        return groupName ?: groupID.orEmpty()
    }

val GroupApplicationInfo.isJoinRequest
    get() = type == GroupApplicationType.JOIN_APPROVED_BY_ADMIN

val GroupApplicationInfo.isInviteRequest
    get() = type == GroupApplicationType.INVITE_APPROVED_BY_INVITEE ||
            type == GroupApplicationType.INVITE_APPROVED_BY_ADMIN

val GroupApplicationInfo.applicationTypeText
    get() = when {
        isJoinRequest -> ContextProvider.appContext.getString(R.string.contact_list_apply_to_join_group)
        isInviteRequest -> ContextProvider.appContext.getString(R.string.contact_list_invite_to_join_group)
        else -> ContextProvider.appContext.getString(R.string.contact_list_unknown)
    }

val GroupApplicationInfo.statusText
    get() = when (handledStatus) {
        GroupApplicationHandledStatus.UNHANDLED -> ContextProvider.appContext.getString(R.string.contact_list_handle_status_unhandle)
        GroupApplicationHandledStatus.BY_OTHER -> ContextProvider.appContext.getString(R.string.contact_list_handle_status_handled_by_ohter)
        GroupApplicationHandledStatus.BY_MYSELF -> when (handledResult) {
            GroupApplicationHandledResult.REFUSED -> ContextProvider.appContext.getString(R.string.contact_list_refused)
            GroupApplicationHandledResult.AGREED -> ContextProvider.appContext.getString(R.string.contact_list_agreed)
            else -> ContextProvider.appContext.getString(R.string.contact_list_unknown)
        }

        else -> ContextProvider.appContext.getString(R.string.contact_list_unknown)
    }

val GroupApplicationInfo.canHandle
    get() = handledStatus == GroupApplicationHandledStatus.UNHANDLED

private fun Any.readStringProperty(name: String): String? {
    val getterName = "get" + name.replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }
    val getterValue = runCatching {
        javaClass.methods
            .firstOrNull { it.name == getterName && it.parameterCount == 0 }
            ?.invoke(this) as? String
    }.getOrNull()
    if (!getterValue.isNullOrBlank()) {
        return getterValue
    }
    return runCatching {
        javaClass.getDeclaredField(name)
            .apply { isAccessible = true }
            .get(this@readStringProperty) as? String
    }.getOrNull()
}
