package io.trtc.tuikit.chat.uikit.components.chatsetting.ui
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.GroupChatSettingConfig
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.GroupChatSettingConfigProtocol
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingCustomItem
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.GroupChatSettingItemContext
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingItemIDs
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.buildChatSettingItems
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupMemberActionPolicy
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermission
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermissionManager
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting.GroupChatSettingActionSection
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting.GroupChatSettingHeaderSection
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting.GroupChatSettingRowsController
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting.createGroupChatSettingUiState
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupChatSettingViewModel
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupChatSettingViewModelFactory
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.getGroupAvatarUrls
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupInviteOption
import io.trtc.tuikit.atomicxcore.api.group.GroupJoinOption
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import io.trtc.tuikit.atomicxcore.api.group.GroupType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GroupChatSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var onGroupMemberClick: ((GroupMember) -> Unit)? = null
    private var onGroupDeleted: (() -> Unit)? = null

    private var viewModel: GroupChatSettingViewModel? = null
    private var viewScope: CoroutineScope? = null

    private lateinit var scrollView: ScrollView
    private lateinit var contentLayout: LinearLayout
    private lateinit var headerSection: GroupChatSettingHeaderSection
    private lateinit var rowsController: GroupChatSettingRowsController
    private lateinit var actionSectionController: GroupChatSettingActionSection
    private lateinit var itemRenderer: ChatSettingItemLayoutRenderer<GroupChatSettingItemContext>

    private lateinit var memberPreviewSection: GroupMemberPreviewSection

    private var config: GroupChatSettingConfigProtocol = GroupChatSettingConfig()

    fun setup(
        groupID: String,
        onGroupMemberClick: ((GroupMember) -> Unit)? = null,
        onGroupDeleted: (() -> Unit)? = null,
        config: GroupChatSettingConfigProtocol = GroupChatSettingConfig(),
    ) {
        this.onGroupMemberClick = onGroupMemberClick
        this.onGroupDeleted = onGroupDeleted
        this.config = config

        val owner = context.findViewModelStoreOwner() ?: return

        cleanupBinding()
        val viewModelKey = "${GroupChatSettingViewModel::class.java.name}:$groupID"
        viewModel = ViewModelProvider(owner, GroupChatSettingViewModelFactory(groupID, context))
            .get(viewModelKey, GroupChatSettingViewModel::class.java)

        buildUI()

        if (isAttachedToWindow) {
            bindViewModel()
        }
    }

    private fun buildUI() {
        val vm = viewModel ?: return
        layoutDirection = LAYOUT_DIRECTION_LOCALE
        removeAllViews()

        scrollView = ScrollView(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isVerticalScrollBarEnabled = false
            setBackgroundColor(getColors().bgColorInput)
        }

        contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setBackgroundColor(getColors().bgColorInput)
        }

        headerSection = GroupChatSettingHeaderSection(context)
        memberPreviewSection = GroupMemberPreviewSection(context).apply {
            onHeaderClick = { showMemberList() }
            onMemberClick = { member -> onGroupMemberClick?.invoke(member) }
            onAddClick = { showAddMemberDialog() }
            onRemoveClick = { showRemoveMemberDialog() }
        }

        rowsController = GroupChatSettingRowsController(
            context = context,
            viewModelProvider = { viewModel },
            onOpenGroupManagement = ::showGroupManagement,
            onShowJoinMethod = ::showJoinMethodActionSheet,
            onShowInviteMethod = ::showInviteMethodActionSheet,
            onShowChatBackgroundPicker = ::showChatBackgroundPicker,
        )
        val rowItems = rowsController.buildItems()
        actionSectionController = GroupChatSettingActionSection(
            context = context,
            onGroupDeletedProvider = { onGroupDeleted },
        )
        val actionItems = actionSectionController.buildItems(vm)

        val itemContext = GroupChatSettingItemContext(
            androidContext = context,
            groupID = vm.groupID,
        )
        val candidates = buildList<ChatSettingCustomItem<GroupChatSettingItemContext>> {
            add(ChatSettingCustomItem(ChatSettingItemIDs.GROUP_HEADER) { headerSection.rootView })
            add(ChatSettingCustomItem(ChatSettingItemIDs.GROUP_MEMBER_PREVIEW) { memberPreviewSection })
            addAll(rowItems)
            addAll(actionItems)
        }
        val items = buildChatSettingItems(
            itemContext = itemContext,
            defaults = candidates.filter { isItemEnabled(it.ID) },
            customizer = config.itemCustomizer,
        )
        itemRenderer = ChatSettingItemLayoutRenderer(context, contentLayout)
        itemRenderer.setItems(itemContext, items)

        scrollView.addView(contentLayout)
        addView(scrollView)
        applyThemeColors(getColors())
        refreshViewState()
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }

    private fun isItemEnabled(ID: String): Boolean {
        return when (ID) {
            ChatSettingItemIDs.GROUP_HEADER -> config.isShowHeader
            ChatSettingItemIDs.GROUP_MEMBER_PREVIEW -> config.isShowMemberPreview
            ChatSettingItemIDs.GROUP_NOTICE -> config.isShowNotice
            ChatSettingItemIDs.GROUP_MANAGEMENT -> config.isShowManagement
            ChatSettingItemIDs.GROUP_TYPE -> config.isShowGroupType
            ChatSettingItemIDs.GROUP_JOIN_METHOD -> config.isShowJoinMethod
            ChatSettingItemIDs.GROUP_INVITE_METHOD -> config.isShowInviteMethod
            ChatSettingItemIDs.GROUP_ALIAS -> config.isShowAlias
            ChatSettingItemIDs.GROUP_DO_NOT_DISTURB -> config.isShowDoNotDisturb
            ChatSettingItemIDs.GROUP_PIN -> config.isShowPin
            ChatSettingItemIDs.GROUP_CHAT_BACKGROUND -> config.isShowChatBackground
            ChatSettingItemIDs.GROUP_TRANSFER_OWNER -> config.isShowTransferOwner
            ChatSettingItemIDs.GROUP_CLEAR_HISTORY -> config.isShowClearHistory
            ChatSettingItemIDs.GROUP_DELETE_AND_QUIT -> config.isShowDeleteAndQuit
            ChatSettingItemIDs.GROUP_DISMISS -> config.isShowDismiss
            else -> true
        }
    }

    private fun bindViewModel() {
        val vm = viewModel ?: return
        if (viewScope != null) return
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope

        scope.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                applyThemeColors(it.currentTheme.tokens.color)
                refreshViewState()
            }
        }

        scope.launch {
            combine(
                vm.groupName,
                vm.avatarURL,
                vm.notification,
                vm.groupType,
                vm.selfRole,
                vm.joinGroupApprovalType,
                vm.inviteToGroupApprovalType,
                vm.selfNameCard,
                vm.memberCount,
                vm.memberList,
                vm.isNotDisturb,
                vm.isPinned,
                vm.chatBackgroundImageUri
            ) { _ ->
                Unit
            }.collectLatest {
                refreshViewState()
            }
        }
    }

    private fun cleanupBinding() {
        viewScope?.cancel()
        viewScope = null
    }

    private fun refreshViewState() {
        val vm = viewModel ?: return
        val state = createGroupChatSettingUiState(
            groupID = vm.groupID,
            groupName = vm.groupName.value,
            avatarURL = vm.avatarURL.value,
            notification = vm.notification.value,
            groupType = vm.groupType.value,
            selfRole = vm.selfRole.value,
            joinOption = vm.joinGroupApprovalType.value,
            inviteOption = vm.inviteToGroupApprovalType.value,
            nameCard = vm.selfNameCard.value,
            memberCount = vm.memberCount.value,
            groupMembers = vm.memberList.value,
            isNotDisturb = vm.isNotDisturb.value,
            isPinned = vm.isPinned.value,
            chatBackgroundImageUri = vm.chatBackgroundImageUri.value,
            canPerformAction = ::canPerformAction
        )
        val permissions = state.permissions

        headerSection.update(
            state = state,
            onAvatarClick = ::showGroupAvatarPicker,
            onGroupNameConfirmed = vm::setGroupName,
            onGroupIdClick = ::copyGroupID
        )

        memberPreviewSection.updateContent(
            members = state.groupMembers,
            memberCount = state.memberCount,
            showAddButton = permissions.canAddMember,
            showRemoveButton = permissions.canRemoveMember
        )

        rowsController.refresh(state, vm)
        itemRenderer.setItemAvailability(
            mapOf(
                ChatSettingItemIDs.GROUP_MANAGEMENT to permissions.canOpenGroupManagement,
                ChatSettingItemIDs.GROUP_DO_NOT_DISTURB to permissions.canToggleDoNotDisturb,
                ChatSettingItemIDs.GROUP_PIN to permissions.canTogglePinned,
                ChatSettingItemIDs.GROUP_TRANSFER_OWNER to canPerformAction(
                    state.groupType,
                    state.selfRole,
                    GroupPermission.TRANSFER_OWNER,
                ),
                ChatSettingItemIDs.GROUP_CLEAR_HISTORY to canPerformAction(
                    state.groupType,
                    state.selfRole,
                    GroupPermission.CLEAR_HISTORY_MESSAGES,
                ),
                ChatSettingItemIDs.GROUP_DELETE_AND_QUIT to canPerformAction(
                    state.groupType,
                    state.selfRole,
                    GroupPermission.DELETE_AND_QUIT,
                ),
                ChatSettingItemIDs.GROUP_DISMISS to canPerformAction(
                    state.groupType,
                    state.selfRole,
                    GroupPermission.DISMISS_GROUP,
                ),
            )
        )
    }

    private fun showChatBackgroundPicker(viewModel: GroupChatSettingViewModel) {
        ChatBackgroundPickerDialog(
            context = context,
            selectedImageUri = viewModel.chatBackgroundImageUri.value,
            onBackgroundSelected = { imageUri ->
                if (imageUri.isNullOrBlank()) {
                    viewModel.clearChatBackground()
                } else {
                    viewModel.setChatBackground(imageUri)
                }
            }
        ).show()
    }

    private fun showGroupManagement(viewModel: GroupChatSettingViewModel) {
        GroupManagementView(
            context = context,
            groupID = viewModel.groupID,
            viewModel = viewModel
        ).show()
    }

    private fun showMemberList() {
        val vm = viewModel ?: return
        val selfRole = vm.selfRole.value
        GroupMemberListView(
            context = context,
            groupID = vm.groupID,
            currentUserRole = selfRole,
            title = context.getString(R.string.chat_setting_group_members),
            onMemberClick = { member -> onGroupMemberClick?.invoke(member) },
            onSetAsAdmin = { member ->
                vm.setMemberRole(member.userID, GroupMemberRole.ADMIN)
            },
            onRemoveAdmin = { member ->
                vm.setMemberRole(member.userID, GroupMemberRole.MEMBER)
            },
            onRemoveMember = { member ->
                vm.deleteMember(listOf(member))
            },
            onViewInfo = { member -> onGroupMemberClick?.invoke(member) }
        ).show()
    }

    private fun showRemoveMemberDialog() {
        val vm = viewModel ?: return
        val selfRole = vm.selfRole.value
        vm.loadAllGroupMembers {
            val candidates = GroupMemberActionPolicy.filterRemovableMembers(
                currentUserRole = selfRole,
                members = vm.memberList.value,
                roleSelector = GroupMember::role
            )
            GroupMemberPickerDialog(
                context = context,
                title = context.getString(R.string.chat_setting_delete_member),
                candidates = candidates,
                onConfirm = { selectedMembers ->
                    if (selectedMembers.isNotEmpty()) {
                        vm.deleteMember(selectedMembers)
                    }
                }
            ).show()
        }
    }

    private fun showGroupAvatarPicker() {
        val vm = viewModel ?: return
        AvatarPickerDialog(
            context = context,
            title = context.getString(R.string.chat_setting_select_avatar),
            imageUrls = getGroupAvatarUrls(),
            onImageSelected = { _, url ->
                vm.setGroupAvatar(url)
            }
        ).show()
    }

    private fun showAddMemberDialog() {
        val vm = viewModel ?: return
        vm.loadAllGroupMembers {
            val existingMemberIds = vm.memberList.value.map { it.userID }.toSet()
            val availableContacts = vm.friendList.value.filterNot { existingMemberIds.contains(it.userID) }
            ContactPickerDialog(
                context = context,
                title = context.getString(R.string.chat_setting_add_group_members),
                contacts = availableContacts,
                onConfirm = { selectedContacts ->
                    vm.addMember(selectedContacts.map(ContactInfo::userID))
                }
            ).show()
        }
    }

    private fun copyGroupID() {
        val vm = viewModel ?: return
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboardManager.setPrimaryClip(ClipData.newPlainText(vm.groupID, vm.groupID))
        Toast.makeText(context, context.getString(R.string.uikit_copied), Toast.LENGTH_SHORT).show()
    }

    private fun showJoinMethodActionSheet(viewModel: GroupChatSettingViewModel) {
        ActionSheet.show(
            context = context,
            options = listOf(
                ActionItem(
                    text = context.getString(R.string.chat_setting_join_method_forbid),
                    value = GroupJoinOption.FORBID
                ),
                ActionItem(
                    text = context.getString(R.string.chat_setting_method_auth),
                    value = GroupJoinOption.AUTH
                ),
                ActionItem(
                    text = context.getString(R.string.chat_setting_method_auto),
                    value = GroupJoinOption.ANY
                )
            ),
            onActionSelected = { item ->
                val selectedOption = item.value as? GroupJoinOption ?: return@show
                viewModel.setJoinGroupApproveType(selectedOption)
            }
        )
    }

    private fun showInviteMethodActionSheet(viewModel: GroupChatSettingViewModel) {
        ActionSheet.show(
            context = context,
            options = listOf(
                ActionItem(
                    text = context.getString(R.string.chat_setting_invite_method_forbid),
                    value = GroupInviteOption.FORBID
                ),
                ActionItem(
                    text = context.getString(R.string.chat_setting_method_auth),
                    value = GroupInviteOption.AUTH
                ),
                ActionItem(
                    text = context.getString(R.string.chat_setting_method_auto),
                    value = GroupInviteOption.ANY
                )
            ),
            onActionSelected = { item ->
                val selectedOption = item.value as? GroupInviteOption ?: return@show
                viewModel.setInviteGroupApproveType(selectedOption)
            }
        )
    }

    private fun canPerformAction(
        groupType: GroupType,
        selfRole: GroupMemberRole,
        permission: GroupPermission
    ): Boolean {
        return GroupPermissionManager.canPerformAction(groupType, selfRole, permission)
    }

    private fun applyThemeColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorInput)
        if (::scrollView.isInitialized) {
            scrollView.setBackgroundColor(colors.bgColorInput)
        }
        if (::contentLayout.isInitialized) {
            contentLayout.setBackgroundColor(colors.bgColorInput)
        }
        if (::headerSection.isInitialized) {
            headerSection.applyThemeColors(colors)
        }
        if (::itemRenderer.isInitialized) {
            itemRenderer.applyThemeColors(colors)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewModel == null) return
        bindViewModel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanupBinding()
    }
}
