package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat
import io.trtc.tuikit.atomicxcore.api.group.GroupType

internal object CreateGroupSubmissionPolicy {
    private const val RESERVED_GROUP_ID_PREFIX = "@TGS#"
    private const val COMMUNITY_GROUP_ID_PREFIX = "@TGS#_"

    enum class Decision {
        ALLOW,
        BLOCKED_CREATING,
        BLOCKED_RESERVED_GROUP_ID,
        BLOCKED_COMMUNITY_GROUP_ID
    }

    fun evaluate(isCreating: Boolean, groupID: String?, groupType: GroupType): Decision {
        return when {
            isCreating -> Decision.BLOCKED_CREATING
            groupType == GroupType.COMMUNITY &&
                !groupID.isNullOrEmpty() &&
                !groupID.startsWith(COMMUNITY_GROUP_ID_PREFIX) ->
                Decision.BLOCKED_COMMUNITY_GROUP_ID
            groupType != GroupType.COMMUNITY &&
                !groupID.isNullOrEmpty() &&
                groupID.startsWith(RESERVED_GROUP_ID_PREFIX) ->
                Decision.BLOCKED_RESERVED_GROUP_ID
            else -> Decision.ALLOW
        }
    }
}
