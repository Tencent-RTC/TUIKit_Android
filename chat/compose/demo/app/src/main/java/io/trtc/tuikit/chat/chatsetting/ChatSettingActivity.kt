package io.trtc.tuikit.chat.chatsetting

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomEditor
import io.trtc.tuikit.chat.uikit.components.common.uicustom.EditorContext
import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.chat.uikit.components.common.SetActivitySystemBarAppearance
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.C2CChatSettingConfig
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.GroupChatSettingConfig
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingCustomItem
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingSectionIDs
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.C2CChatSetting
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.GroupChatSetting
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.SettingRowButton
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.SettingRowButtonStyle
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addfriendandgroup.AddContactAndGroupBottomSheet
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.GetContactInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParams
//import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
//import com.tencent.qcloud.tuikit.tuicallkit.common.data.Constants
import io.trtc.tuikit.chat.BaseActivity
import io.trtc.tuikit.chat.Event
import io.trtc.tuikit.chat.chat.ChatActivity
import io.trtc.tuikit.chat.common.AppConstants

private const val REPORT_ITEM_ID = "demo.chatSetting.report"

private fun openReportPage(context: Context) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.REPORT_URL))
        )
    } catch (_: Exception) {
        Toast.error(context, context.getString(io.trtc.tuikit.chat.R.string.compose_demo_open_browser_failed))
    }
}

private fun <C : EditorContext> CustomEditor<C, ChatSettingCustomItem<C>>.addReportItem(
    sectionID: String,
    title: String,
    context: Context
) {
    add(
        ChatSettingCustomItem(
            ID = REPORT_ITEM_ID,
            sectionID = sectionID,
        ) {
            SettingRowButton(
                title = title,
                style = SettingRowButtonStyle.DANGER,
                onClick = { openReportPage(context) }
            )
        }
    )
}

class ChatSettingActivity : BaseActivity() {

    companion object {
        private const val USER_ID = "user_id"
        private const val GROUP_ID = "group_id"
        private const val NEED_NAVIGATE_TO_CHAT = "needNavigateToChat"

        fun start(
            context: Context,
            userID: String? = null,
            groupID: String? = null,
            needNavigateToChat: Boolean = false
        ) {
            val intent = Intent(context, ChatSettingActivity::class.java).apply {
                putExtra(USER_ID, userID)
                putExtra(GROUP_ID, groupID)
                putExtra(NEED_NAVIGATE_TO_CHAT, needNavigateToChat)
            }
            context.startActivity(intent)
        }

    }

    val contactStore = ContactStore.shared

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userID = intent.getStringExtra(USER_ID)
        val groupID = intent.getStringExtra(GROUP_ID)
        val needNavigateToChat = intent.getBooleanExtra(NEED_NAVIGATE_TO_CHAT, false)

        setContent {
            SetActivitySystemBarAppearance()

            var showAddFriendDialog by remember { mutableStateOf(false) }
            var contactInfo by remember { mutableStateOf<ContactInfo?>(null) }

            val colors = LocalTheme.current.colors
            val context = LocalContext.current
            val reportTitle = stringResource(io.trtc.tuikit.chat.R.string.compose_demo_report)
            val c2cChatSettingConfig = remember(reportTitle) {
                C2CChatSettingConfig().customizeItems {
                    addReportItem(ChatSettingSectionIDs.C2C_ACTIONS, reportTitle, context)
                    // also available: replace / moveBefore / moveAfter / clear
                }
            }
            val groupChatSettingConfig = remember(reportTitle) {
                GroupChatSettingConfig().customizeItems {
                    addReportItem(ChatSettingSectionIDs.GROUP_ACTIONS, reportTitle, context)
                    // also available: replace / moveBefore / moveAfter / clear
                }
            }
            if (!userID.isNullOrEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.bgColorOperate)
                        .systemBarsPadding()
                ) {
                    ChatSettingHeader(
                        title = stringResource(io.trtc.tuikit.chat.R.string.compose_demo_chat_setting_contact_info),
                        onBackClick = { finish() },
                    )
                    C2CChatSetting(
                        userID = userID,
                        onSendMessageClick = {
                            if (needNavigateToChat) {
                                startActivity(Intent(this@ChatSettingActivity, ChatActivity::class.java).apply {
                                    putExtra("conversationID", "c2c_${userID}")
                                })
                            }
                            finish()
                        },
                        onContactDeleted = {
                            EventBus.post(Event.ContactDeleted(userID))
                            finish()
                        },
                        config = c2cChatSettingConfig,
                    )
                }
            } else if (!groupID.isNullOrEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.bgColorOperate)
                        .systemBarsPadding()
                ) {
                    ChatSettingHeader(
                        title = stringResource(io.trtc.tuikit.chat.R.string.compose_demo_chat_setting_group_info),
                        onBackClick = { finish() },
                    )
                    GroupChatSetting(
                        groupID = groupID,
                        onGroupMemberClick = { groupMember ->
                            contactStore.getContactInfo(
                                listOf(groupMember.userID),
                                object : GetContactInfoCompletionHandler {
                                    override fun onSuccess(contactInfoList: List<ContactInfo>) {
                                        val info = contactInfoList.firstOrNull() ?: return
                                        if (info.isFriend) {
                                            start(this@ChatSettingActivity, userID = info.userID, groupID = "", true)
                                        } else {
                                            showAddFriendDialog = true
                                            contactInfo = info
                                        }
                                    }

                                    override fun onFailure(code: Int, desc: String) {
                                    }
                                })
                        },
                        onGroupDeleted = {
                            EventBus.post(Event.GroupDeleted(groupID))
                            finish()
                        },
                        config = groupChatSettingConfig,
                    )
                }
            }
            if (showAddFriendDialog && contactInfo != null) {
                AddContactAndGroupBottomSheet(
                    addType = AddType.CONTACT,
                    initialContactInfo = contactInfo,
                    onDismiss = { showAddFriendDialog = false }
                )
            }
        }
    }
}

@Composable
fun ChatSettingHeader(
    title: String,
    onBackClick: () -> Unit,
) {
    val colors = LocalTheme.current.colors
    Column(modifier = Modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBackClick() },
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    painter = painterResource(R.drawable.uikit_ic_back),
                    contentDescription = "Back",
                    tint = colors.textColorSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.strokeColorPrimary
        )
    }
}