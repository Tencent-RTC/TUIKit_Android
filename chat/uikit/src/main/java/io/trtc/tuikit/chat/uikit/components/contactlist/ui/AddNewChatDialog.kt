package io.trtc.tuikit.chat.uikit.components.contactlist.ui
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.AppBarLayout
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.common.WindowThemeUtil
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat.ContactSelectionStateMerger
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat.CreateGroupSubmissionPolicy
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat.GroupAvatarSelectorView
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat.GroupTypeSelectionStepView
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat.SelectedContactsBottomBar
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat.SelectedMembersPreviewView
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.matchesSearchQuery
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddNewChatViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddNewChatViewModelFactory
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.ChatType
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.GroupFlowStep
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.GroupTypeOption
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData
import io.trtc.tuikit.chat.uikit.components.userpicker.ui.UserPickerView
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class AddNewChatDialog(
    context: Context,
    private val chatType: ChatType = ChatType.GROUP,
    private val contactStore: ContactStore = ContactStore.shared,
    private val groupStore: GroupStore = GroupStore.shared,
    private val onCreateChat: ((String) -> Unit)? = null
) : Dialog(context, android.R.style.Theme_NoTitleBar) {

    private val viewModel: AddNewChatViewModel by lazy {
        val owner = context.findViewModelStoreOwner()
            ?: error("AddNewChatDialog requires a ViewModelStoreOwner host context.")
        val key = "${AddNewChatViewModel::class.java.name}:${System.identityHashCode(this)}"
        ViewModelProvider(owner, AddNewChatViewModelFactory(contactStore, groupStore))
            .get(key, AddNewChatViewModel::class.java)
    }
    private var dialogScope: CoroutineScope? = null

    private lateinit var rootLayout: LinearLayout
    private lateinit var navBar: DialogNavBar
    private lateinit var divider: View
    private lateinit var contentContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val colors = getColors()
        val dm = context.resources.displayMetrics

        rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorOperate)
            fitsSystemWindows = true
        }

        navBar = DialogNavBar.create(
            context,
            DialogNavBar.Config(
                mode = DialogNavBar.Mode.BackTitle,
                colors = colors,
                onLeadingClick = { handleBack() },
                leadingContentDescription = context.getString(R.string.uikit_back)
            )
        )
        rootLayout.addView(navBar)

        divider = View(context).apply {
            setBackgroundColor(colors.strokeColorSecondary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(0.5f, dm).toInt().coerceAtLeast(1)
            )
        }
        rootLayout.addView(divider)

        contentContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        rootLayout.addView(contentContainer)

        setContentView(
            rootLayout,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            WindowThemeUtil.applyDialogSystemBarStyle(this, colors)
        }

        viewModel.setChatType(chatType)
    }

    override fun onStart() {
        super.onStart()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        dialogScope = scope

        scope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state.groupFlowStep) {
                    GroupFlowStep.CONTACT_SELECTION -> showContactSelectionStep()
                    GroupFlowStep.GROUP_SETTINGS -> showGroupSettingsStep()
                    GroupFlowStep.GROUP_TYPE_SELECTION -> showGroupTypeSelectionStep()
                    else -> showContactSelectionStep()
                }

                state.createdConversationId?.let { convId ->
                    onCreateChat?.invoke(convId)
                    dismiss()
                    viewModel.consumeCreatedConversationId()
                }

                state.error?.let { error ->
                    if (error.isNotEmpty()) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                    viewModel.clearError()
                }
            }
        }

        scope.launch {
            viewModel.contactDataSource.collectLatest { dataSource ->
                updateUserPickerIfVisible(dataSource)
            }
        }

        scope.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                refreshCurrentStepForTheme()
            }
        }

        showContactSelectionStep()
    }

    override fun onStop() {
        super.onStop()
        dialogScope?.cancel()
        dialogScope = null
    }

    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        val state = viewModel.uiState.value
        when (state.groupFlowStep) {
            GroupFlowStep.CONTACT_SELECTION -> dismiss()
            GroupFlowStep.GROUP_SETTINGS -> viewModel.clearGroupSettingsScreen()
            GroupFlowStep.GROUP_TYPE_SELECTION -> viewModel.clearGroupTypeSelectionScreen()
            else -> dismiss()
        }
    }

    private fun refreshCurrentStepForTheme() {
        currentDisplayedStep = null
        val state = viewModel.uiState.value
        when (state.groupFlowStep) {
            GroupFlowStep.CONTACT_SELECTION -> showContactSelectionStep()
            GroupFlowStep.GROUP_SETTINGS -> showGroupSettingsStep()
            GroupFlowStep.GROUP_TYPE_SELECTION -> showGroupTypeSelectionStep()
            else -> showContactSelectionStep()
        }
    }

    private var currentDisplayedStep: GroupFlowStep? = null
    private var userPickerView: UserPickerView? = null
    private var allContactDataSource: List<UserPickerData<ContactInfo>> = emptyList()
    private var currentSearchQuery = ""

    private var selectionBottomBar: SelectedContactsBottomBar? = null

    private fun showContactSelectionStep() {
        if (currentDisplayedStep == GroupFlowStep.CONTACT_SELECTION) {
            applyWindowTheme()
            applyFilteredContactDataSource()
            updateSelectionBottomBar(viewModel.uiState.value.selectedContacts)
            return
        }
        currentDisplayedStep = GroupFlowStep.CONTACT_SELECTION
        contentContainer.removeAllViews()

        navBar.setTitle(
            if (chatType == ChatType.GROUP) {
                context.getString(R.string.contact_list_create_group)
            } else {
                context.getString(R.string.contact_list_create_c2c)
            }
        )

        applyWindowTheme()

        val rootContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val coordinatorLayout = CoordinatorLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val appBarLayout = AppBarLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            fitsSystemWindows = false
            stateListAnimator = null
            setBackgroundColor(getColors().bgColorOperate)
        }
        coordinatorLayout.addView(
            appBarLayout,
            CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val searchBarView = ContactListSearchBarView(context).apply {
            setQuery(currentSearchQuery)
            onQueryChange = { query ->
                currentSearchQuery = query
                applyFilteredContactDataSource()
            }
        }
        val searchBarParams = AppBarLayout.LayoutParams(
            AppBarLayout.LayoutParams.MATCH_PARENT,
            AppBarLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
                AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        }
        appBarLayout.addView(searchBarView, searchBarParams)

        val pickerContainer = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        val pickerContainerParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT
        ).apply {
            behavior = AppBarLayout.ScrollingViewBehavior()
        }
        coordinatorLayout.addView(pickerContainer, pickerContainerParams)

        val picker = UserPickerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setMaxCount(if (chatType == ChatType.SINGLE) 1 else 100)
            setShowCheckbox(chatType != ChatType.SINGLE)
        }
        userPickerView = picker
        pickerContainer.addView(picker)

        rootContainer.addView(coordinatorLayout)

        if (chatType == ChatType.GROUP) {
            val bottomBar = SelectedContactsBottomBar(
                context = context,
                selectedContacts = viewModel.uiState.value.selectedContacts,
                onConfirmClick = {
                    val count = viewModel.uiState.value.selectedContacts.size
                    if (count > 0) {
                        viewModel.startChat()
                    }
                }
            )
            selectionBottomBar = bottomBar
            rootContainer.addView(bottomBar)
        }

        picker.setOnSelectedChangedListener<ContactInfo> { selectedItems ->
            if (chatType == ChatType.SINGLE) {
                if (selectedItems.isNotEmpty()) {
                    onCreateChat?.invoke(ConversationIDUtil.fromUser(selectedItems.first().key))
                    dismiss()
                }
            } else {
                updateSelectedContactsFromVisibleItems(selectedItems)
            }
        }

        contentContainer.addView(rootContainer)
        applyFilteredContactDataSource()
    }

    private fun updateSelectionBottomBar(selectedContacts: List<ContactInfo>) {
        selectionBottomBar?.update(selectedContacts)
    }

    private fun updateUserPickerIfVisible(dataSource: List<UserPickerData<ContactInfo>>) {
        allContactDataSource = dataSource
        applyFilteredContactDataSource()
    }

    private fun applyFilteredContactDataSource() {
        val picker = userPickerView ?: return
        picker.setDefaultSelectedItems(viewModel.uiState.value.selectedContacts.map { it.userID })
        picker.setDataSource(getFilteredContactDataSource())
    }

    private fun getFilteredContactDataSource(): List<UserPickerData<ContactInfo>> {
        val keyword = currentSearchQuery.trim()
        if (keyword.isEmpty()) {
            return allContactDataSource
        }
        return allContactDataSource.filter { item ->
            item.extraData.matchesSearchQuery(keyword)
        }
    }

    private fun updateSelectedContactsFromVisibleItems(
        visibleSelectedItems: List<UserPickerData<ContactInfo>>
    ) {
        val mergedSelectedContacts = ContactSelectionStateMerger.merge(
            currentSelected = viewModel.uiState.value.selectedContacts,
            visibleItems = getFilteredContactDataSource(),
            visibleSelectedItems = visibleSelectedItems,
            selectedKeySelector = { it.userID },
            visibleKeySelector = { it.key },
            visibleToSelectedMapper = { it.extraData }
        )

        viewModel.setSelectedContacts(
            mergedSelectedContacts.map { contact ->
                UserPickerData(
                    key = contact.userID,
                    label = contact.displayName,
                    avatarUrl = contact.avatarURL,
                    extraData = contact
                )
            }
        )
    }

    private var groupTypeValueView: TextView? = null
    private var groupTypeDescView: TextView? = null
    private var groupAvatarSelectorView: GroupAvatarSelectorView? = null
    private var createButtonView: TextView? = null
    private var selectedMembersPreviewView: SelectedMembersPreviewView? = null

    private fun showGroupSettingsStep() {
        val alreadyDisplayed = currentDisplayedStep == GroupFlowStep.GROUP_SETTINGS
        currentDisplayedStep = GroupFlowStep.GROUP_SETTINGS
        if (alreadyDisplayed) {
            refreshGroupSettingsDynamicViews()
            return
        }

        groupTypeValueView = null
        groupTypeDescView = null
        groupAvatarSelectorView = null
        createButtonView = null
        selectedMembersPreviewView = null

        contentContainer.removeAllViews()

        val colors = getColors()
        val dm = context.resources.displayMetrics
        val state = viewModel.uiState.value
        val selectedType = viewModel.currentSelectedGroupType.value
        val displayedGroupName = state.groupName.ifBlank {
            viewModel.generateGroupName(state.selectedContacts)
        }

        navBar.setTitle(context.getString(R.string.contact_list_create_group))
        applyWindowTheme()

        val rootContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        container.addView(
            createEditableSettingRow(
                title = context.getString(R.string.contact_list_group_name),
                initialValue = displayedGroupName,
                onValueChange = { viewModel.updateGroupName(it) }
            )
        )
        container.addView(createSettingsDivider())
        container.addView(
            createEditableSettingRow(
                title = context.getString(R.string.contact_list_group_id),
                initialValue = state.groupID.orEmpty(),
                hint = context.getString(R.string.contact_list_group_id_option),
                onValueChange = { viewModel.updateGroupID(it.ifBlank { null }) }
            )
        )
        container.addView(createSettingsDivider())
        container.addView(createGroupTypeRow(selectedType))

        container.addView(
            TextView(context).apply {
                text = context.getString(selectedType.descriptionResID)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(colors.textColorSecondary)
                setLineSpacing(0f, 1.25f)
                setPadding(
                    dp2px(16f, dm).toInt(),
                    dp2px(8f, dm).toInt(),
                    dp2px(16f, dm).toInt(),
                    0
                )
                groupTypeDescView = this
            }
        )

        val avatarSelector = GroupAvatarSelectorView(
            context = context,
            displayedGroupName = displayedGroupName,
            selectedAvatarUrl = state.groupAvatarUrl,
            avatarUrls = AddNewChatViewModel.getGroupAvatarUrls(),
            onAvatarSelected = { viewModel.updateGroupAvatarUrl(it) }
        )
        groupAvatarSelectorView = avatarSelector
        container.addView(avatarSelector)

        container.addView(SelectedMembersPreviewView(context, state.selectedContacts).also { selectedMembersPreviewView = it })

        scrollView.addView(container)
        rootContainer.addView(scrollView)
        rootContainer.addView(createCreateButton(state.isCreating))
        contentContainer.addView(rootContainer)
    }

    private fun refreshGroupSettingsDynamicViews() {
        val state = viewModel.uiState.value
        val selectedType = viewModel.currentSelectedGroupType.value
        groupTypeValueView?.text = context.getString(selectedType.displayNameResID)
        groupTypeDescView?.text = context.getString(selectedType.descriptionResID)
        groupAvatarSelectorView?.updateSelection(state.groupAvatarUrl)
        createButtonView?.let { applyCreateButtonState(it, state.isCreating) }
        selectedMembersPreviewView?.update(state.selectedContacts)
    }

    private fun createGroupTypeRow(selectedType: GroupTypeOption): View {
        val colors = getColors()
        val dm = context.resources.displayMetrics
        val rowHeight = dp2px(48f, dm).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                rowHeight
            )
            setBackgroundColor(colors.bgColorTopBar)
            setPadding(dp2px(16f, dm).toInt(), 0, dp2px(16f, dm).toInt(), 0)
            setOnClickListener { viewModel.showGroupTypeSelectionScreen() }

            addView(TextView(context).apply {
                text = context.getString(R.string.contact_list_group_type_text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(colors.textColorPrimary)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            addView(TextView(context).apply {
                text = context.getString(selectedType.displayNameResID)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(colors.textColorPrimary)
                ellipsize = TextUtils.TruncateAt.END
                maxLines = 1
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                groupTypeValueView = this
            })

            addView(TextView(context).apply {
                text = if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) "‹" else "›"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(colors.textColorSecondary)
                setPaddingRelative(dp2px(8f, dm).toInt(), 0, 0, 0)
            })
        }
    }

    private fun createEditableSettingRow(
        title: String,
        initialValue: String,
        hint: String = "",
        onValueChange: (String) -> Unit
    ): View {
        val colors = getColors()
        val dm = context.resources.displayMetrics
        val rowHeight = dp2px(48f, dm).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                rowHeight
            )
            setBackgroundColor(colors.bgColorTopBar)
            setPadding(dp2px(16f, dm).toInt(), 0, dp2px(16f, dm).toInt(), 0)

            addView(TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(colors.textColorPrimary)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            addView(EditText(context).apply {
                setText(initialValue)
                if (hint.isNotEmpty()) this.hint = hint
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(colors.textColorPrimary)
                setHintTextColor(colors.textColorSecondary)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                background = null
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_DONE
                setPaddingRelative(dp2px(8f, dm).toInt(), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        onValueChange(s?.toString().orEmpty())
                    }
                })
            })
        }
    }

    private fun showGroupTypeSelectionStep() {
        if (currentDisplayedStep == GroupFlowStep.GROUP_TYPE_SELECTION) return
        currentDisplayedStep = GroupFlowStep.GROUP_TYPE_SELECTION
        contentContainer.removeAllViews()

        val currentType = viewModel.currentSelectedGroupType.value
        val groupTypes = AddNewChatViewModel.getGroupTypeOptionList()

        navBar.setTitle(context.getString(R.string.contact_list_group_type_select_text))
        applyWindowTheme()

        contentContainer.addView(
            GroupTypeSelectionStepView(
                context = context,
                currentType = currentType,
                groupTypes = groupTypes,
                onTypeSelected = { viewModel.updateSelectedGroupType(it) }
            )
        )
    }

    private fun createCreateButton(isCreating: Boolean): View {
        val colors = getColors()
        val dm = context.resources.displayMetrics
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(
                dp2px(16f, dm).toInt(),
                dp2px(12f, dm).toInt(),
                dp2px(16f, dm).toInt(),
                dp2px(20f, dm).toInt()
            )
            setBackgroundColor(colors.bgColorOperate)
            val button = TextView(context).apply {
                text = context.getString(R.string.contact_list_create)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(colors.textColorButton)
                gravity = Gravity.CENTER
                val btnWidth = dp2px(76f, dm).toInt()
                val btnHeight = dp2px(30f, dm).toInt()
                layoutParams = FrameLayout.LayoutParams(
                    btnWidth,
                    btnHeight,
                    Gravity.END or Gravity.CENTER_VERTICAL
                )
                setOnClickListener {
                    if (!viewModel.uiState.value.isCreating) {
                        submitCreateGroup()
                    }
                }
            }
            applyCreateButtonState(button, isCreating)
            addView(button)
            createButtonView = button
        }
    }

    private fun applyCreateButtonState(button: TextView, isCreating: Boolean) {
        val colors = getColors()
        val dm = context.resources.displayMetrics
        button.background = GradientDrawable().apply {
            setColor(if (isCreating) colors.textColorDisable else colors.textColorLink)
            cornerRadius = dp2px(6f, dm)
        }
        button.isEnabled = !isCreating
    }

    private fun submitCreateGroup() {
        val currentState = viewModel.uiState.value
        val groupId = currentState.groupID
        val groupType = viewModel.currentSelectedGroupType.value.type
        when (CreateGroupSubmissionPolicy.evaluate(currentState.isCreating, groupId, groupType)) {
            CreateGroupSubmissionPolicy.Decision.BLOCKED_CREATING -> return
            CreateGroupSubmissionPolicy.Decision.BLOCKED_RESERVED_GROUP_ID -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.contact_list_group_id_edit_format_tips),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            CreateGroupSubmissionPolicy.Decision.BLOCKED_COMMUNITY_GROUP_ID -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.contact_list_community_id_edit_format_tips),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            CreateGroupSubmissionPolicy.Decision.ALLOW -> Unit
            else -> return
        }
        viewModel.createGroupChatWithSettings(
            groupName = currentState.groupName,
            groupID = groupId,
            groupAvatarUrl = currentState.groupAvatarUrl,
            onSuccess = {},
            onFailure = { _, desc ->
                Toast.makeText(context, desc, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun createSettingsDivider(): View {
        val dm = context.resources.displayMetrics
        val colors = getColors()
        return View(context).apply {
            setBackgroundColor(colors.strokeColorPrimary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(0.5f, dm).toInt().coerceAtLeast(1)
            )
        }
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }

    private fun applyWindowTheme() {
        val colors = getColors()
        if (::rootLayout.isInitialized) {
            rootLayout.setBackgroundColor(colors.bgColorOperate)
        }
        if (::navBar.isInitialized) {
            navBar.applyColors(colors)
        }
        if (::divider.isInitialized) {
            divider.setBackgroundColor(colors.strokeColorSecondary)
        }
        window?.let { WindowThemeUtil.applyDialogSystemBarStyle(it, colors) }
    }
}
