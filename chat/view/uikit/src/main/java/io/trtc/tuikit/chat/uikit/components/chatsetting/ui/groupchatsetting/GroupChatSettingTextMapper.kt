package io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting
import androidx.annotation.StringRes
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicxcore.api.group.GroupInviteOption
import io.trtc.tuikit.atomicxcore.api.group.GroupJoinOption
import io.trtc.tuikit.atomicxcore.api.group.GroupType

internal object GroupChatSettingTextMapper {

    @StringRes
    fun groupTypeTextRes(groupType: GroupType): Int {
        return when (groupType) {
            GroupType.WORK -> R.string.chat_setting_group_type_work
            GroupType.PUBLIC_GROUP -> R.string.chat_setting_group_type_public
            GroupType.MEETING -> R.string.chat_setting_group_type_meeting
            GroupType.AV_CHAT_ROOM -> R.string.chat_setting_group_type_avchatroom
            GroupType.COMMUNITY -> R.string.chat_setting_group_type_community
            else -> R.string.chat_setting_group_type_work
        }
    }

    @StringRes
    fun joinOptionTextRes(option: GroupJoinOption): Int? {
        return when (option) {
            GroupJoinOption.FORBID -> R.string.chat_setting_join_method_forbid
            GroupJoinOption.AUTH -> R.string.chat_setting_method_auth
            GroupJoinOption.ANY -> R.string.chat_setting_method_auto
            else -> R.string.chat_setting_method_auto
        }
    }

    @StringRes
    fun inviteOptionTextRes(option: GroupInviteOption): Int? {
        return when (option) {
            GroupInviteOption.FORBID -> R.string.chat_setting_invite_method_forbid
            GroupInviteOption.AUTH -> R.string.chat_setting_method_auth
            GroupInviteOption.ANY -> R.string.chat_setting_method_auto
            else -> R.string.chat_setting_method_auto
        }
    }
}
