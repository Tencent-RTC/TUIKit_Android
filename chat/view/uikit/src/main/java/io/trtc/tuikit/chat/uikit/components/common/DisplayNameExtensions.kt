package io.trtc.tuikit.chat.uikit.components.common
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.FriendApplicationInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.atomicxcore.api.search.FriendSearchInfo

val ContactInfo.displayName: String
    get() = friendRemark?.takeIf { it.isNotEmpty() }
        ?: nickname?.takeIf { it.isNotEmpty() }
        ?: userID

val GroupMember.displayName: String
    get() = nameCard?.takeIf { it.isNotBlank() }
        ?: friendRemark?.takeIf { it.isNotBlank() }
        ?: nickname?.takeIf { it.isNotBlank() }
        ?: userID

val FriendApplicationInfo.displayName
    get() = title ?: userID

val UserProfile.displayName
    get() = nickname?.takeIf { it.isNotEmpty() }
        ?: userID

val FriendSearchInfo.displayName: String
    get() = friendRemark?.takeIf { it.isNotEmpty() }
        ?: userInfo?.nickname?.takeIf { it.isNotEmpty() }
        ?: userID
