package io.trtc.tuikit.chat.uikit.components.contactlist.ui
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.WindowThemeUtil
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.AddContactFlowStep
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.AddContactNavigator
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.SelfWordingProvider
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactActionCard
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactCardRow
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactInfoCard
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactMultilineInputCard
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactSearchEmptyView
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactSearchResultView
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactSectionSpacer
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact.buildAddContactSectionTitle
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.setAfterTextChangedListener
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddContactAndGroupViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddContactAndGroupViewModelFactory
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.ContactRequestResultPolicy
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.chat.uikit.components.widgets.SearchBar
import io.trtc.tuikit.chat.uikit.components.widgets.SearchBarConfig
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class AddContactAndGroupDialog(
    context: Context,
    private val addType: AddType,
    private val contactStore: ContactStore = ContactStore.shared,
    private val groupStore: GroupStore = GroupStore.shared,
    private val initialContactInfo: ContactInfo? = null
) : Dialog(context, android.R.style.Theme_NoTitleBar) {

    private val viewModel: AddContactAndGroupViewModel by lazy {
        val owner = context.findViewModelStoreOwner()
            ?: error("AddContactAndGroupDialog requires a ViewModelStoreOwner host context.")
        val key = "${AddContactAndGroupViewModel::class.java.name}:${System.identityHashCode(this)}"
        ViewModelProvider(owner, AddContactAndGroupViewModelFactory(contactStore, groupStore))
            .get(key, AddContactAndGroupViewModel::class.java)
    }
    private var dialogScope: CoroutineScope? = null
    private var searchStateJob: Job? = null

    private lateinit var rootLayout: LinearLayout
    private lateinit var navBar: DialogNavBar
    private lateinit var divider: View
    private lateinit var contentContainer: FrameLayout

    private var currentStep = AddContactFlowStep.SEARCH
    private var selectedResult: ContactInfo? = null
    private var addFriendWordingDraft: String? = null
    private var addFriendRemarkDraft: String? = null
    private var groupJoinMessageDraft: String? = null

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
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            WindowThemeUtil.applyDialogSystemBarStyle(this, colors)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        val preset = initialContactInfo
        if (preset != null) {
            selectedResult = preset
            showContactDetailStep()
        } else {
            showSearchStep()
        }
    }

    override fun onStart() {
        super.onStart()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        dialogScope = scope

        scope.launch {
            viewModel.uiState.collectLatest { state ->
                state.requestResult?.let { result ->
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    viewModel.clearRequestResult()
                    if (ContactRequestResultPolicy.shouldDismissAfterRequest(result.isSuccess)) {
                        dismiss()
                    }
                }
            }
        }

        scope.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                refreshCurrentStep()
            }
        }

        if (currentStep == AddContactFlowStep.SEARCH && initialContactInfo == null) {
            showSearchStep()
        }
    }

    private fun refreshCurrentStep() {
        when (currentStep) {
            AddContactFlowStep.SEARCH -> if (initialContactInfo == null) showSearchStep()
            AddContactFlowStep.CONTACT_DETAIL -> showContactDetailStep()
            AddContactFlowStep.ADD_FRIEND_FORM -> showAddFriendFormStep()
            AddContactFlowStep.GROUP_JOIN_FORM -> showGroupJoinFormStep()
            else -> if (initialContactInfo == null) showSearchStep()
        }
    }

    override fun onStop() {
        super.onStop()
        searchStateJob?.cancel()
        searchStateJob = null
        dialogScope?.cancel()
        dialogScope = null
    }

    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        when (AddContactNavigator.backAction(currentStep, initialContactInfo != null)) {
            AddContactNavigator.BackAction.DISMISS -> {
                if (currentStep == AddContactFlowStep.SEARCH) {
                    viewModel.clearSearchResults()
                }
                dismiss()
            }
            AddContactNavigator.BackAction.SHOW_SEARCH -> showSearchStep()
            AddContactNavigator.BackAction.SHOW_CONTACT_DETAIL -> showContactDetailStep()
            else -> {
                if (currentStep == AddContactFlowStep.SEARCH) {
                    viewModel.clearSearchResults()
                }
                dismiss()
            }
        }
    }

    private fun updateHeader() {
        val titleRes = AddContactNavigator.titleRes(currentStep, addType)
        navBar.setTitle(context.getString(titleRes))
        applyWindowTheme()
    }

    private fun showSearchStep() {
        currentStep = AddContactFlowStep.SEARCH
        updateHeader()
        contentContainer.removeAllViews()

        val colors = getColors()
        val dm = context.resources.displayMetrics

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(colors.bgColorOperate)
        }

        val searchBar = SearchBar(context).configure(
            SearchBarConfig(
                showBack = false,
                showCancel = false,
                inputHeightDp = 36,
                hint = if (addType == AddType.CONTACT) {
                    context.getString(R.string.contact_list_user_id)
                } else {
                    context.getString(R.string.contact_list_group_id)
                },
                debounceMs = 0L,
                searchIconRes = R.drawable.contact_list_ic_search,
                clearIconRes = R.drawable.contact_list_ic_search_clear,
                paddingHorizontalDp = 16,
                paddingVerticalDp = 12,
                expandTouchTargets = false
            )
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            editText.imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        container.addView(searchBar)

        val myIdTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colors.textColorSecondary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    dp2px(16f, dm).toInt(),
                    dp2px(80f, dm).toInt(),
                    dp2px(16f, dm).toInt(),
                    0
                )
            }
            visibility = View.GONE
        }
        container.addView(myIdTextView)

        val resultContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(resultContainer)

        searchBar.onQueryChanged = { text ->
            viewModel.updateSearchKeyword(text)
            if (text.isEmpty()) {
                resultContainer.removeAllViews()
            }
        }
        searchBar.setQuery(viewModel.uiState.value.searchKeyword, notify = true)

        val performSearch = {
            searchBar.hideKeyboard()
            if (addType == AddType.CONTACT) {
                viewModel.searchContact()
            } else {
                viewModel.searchGroup()
            }
        }

        searchBar.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        contentContainer.addView(container)

        searchStateJob?.cancel()
        searchStateJob = dialogScope?.launch {
            viewModel.uiState.collectLatest { state ->
                if (currentStep != AddContactFlowStep.SEARCH) return@collectLatest

                if (addType == AddType.CONTACT && state.currentUserId.isNotEmpty() && state.searchKeyword.isEmpty()) {
                    myIdTextView.text = context.getString(
                        R.string.contact_list_label_value_format,
                        context.getString(R.string.contact_list_my_user_id),
                        state.currentUserId
                    )
                    myIdTextView.visibility = View.VISIBLE
                } else {
                    myIdTextView.visibility = View.GONE
                }

                resultContainer.removeAllViews()
                val info = if (addType == AddType.CONTACT) state.addFriendInfo else state.joinGroupInfo
                if (info != null) {
                    resultContainer.addView(
                        buildAddContactSearchResultView(
                            context = context,
                            colors = getColors(),
                            addType = addType,
                            result = info,
                            isJoinGroupAlready = state.isJoinGroupAlready
                        ) { result ->
                            if (addType == AddType.CONTACT && result.isFriend == true) return@buildAddContactSearchResultView
                            if (addType == AddType.GROUP && state.isJoinGroupAlready) return@buildAddContactSearchResultView
                            selectedResult = result
                            clearFormDrafts()
                            if (addType == AddType.CONTACT) {
                                showContactDetailStep()
                            } else {
                                showGroupJoinFormStep()
                            }
                        }
                    )
                } else if (!state.isSearching && state.searchKeyword.isNotEmpty()) {
                    resultContainer.addView(buildAddContactSearchEmptyView(context, getColors()))
                }
            }
        }
    }

    private fun showContactDetailStep() {
        currentStep = AddContactFlowStep.CONTACT_DETAIL
        updateHeader()
        contentContainer.removeAllViews()

        val result = selectedResult ?: return
        val colors = getColors()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(colors.bgColorDefault)
        }

        container.addView(buildAddContactInfoCard(context, colors, addType, result))

        container.addView(buildAddContactSectionSpacer(context, colors))

        container.addView(
            buildAddContactActionCard(
                context = context,
                colors = colors,
                text = context.getString(R.string.contact_list_add_contact),
                textColor = colors.textColorLink
            ) { showAddFriendFormStep() }
        )

        contentContainer.addView(container)
    }

    private fun showAddFriendFormStep() {
        currentStep = AddContactFlowStep.ADD_FRIEND_FORM
        updateHeader()
        contentContainer.removeAllViews()

        val result = selectedResult ?: return
        val colors = getColors()

        val scrollView = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(colors.bgColorDefault)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(colors.bgColorDefault)
        }

        container.addView(buildAddContactInfoCard(context, colors, addType, result))

        container.addView(
            buildAddContactSectionTitle(context, colors, context.getString(R.string.contact_list_fill_validation_message))
        )

        val defaultWording = SelfWordingProvider.defaultWording(context)

        val wordingInput = buildAddContactMultilineInputCard(
            context = context,
            colors = colors,
            defaultValue = addFriendWordingDraft ?: defaultWording,
            minHeightDp = 120f
        )
        wordingInput.second.setAfterTextChangedListener {
            addFriendWordingDraft = it
        }
        container.addView(wordingInput.first)

        container.addView(buildAddContactSectionSpacer(context, colors))

        val remarkInput = buildAddContactCardRow(
            context = context,
            colors = colors,
            label = context.getString(R.string.contact_list_remark),
            defaultValue = addFriendRemarkDraft ?: result.displayName,
            editable = true
        )
        remarkInput.second.setAfterTextChangedListener {
            addFriendRemarkDraft = it
        }
        container.addView(remarkInput.first)

        container.addView(buildAddContactSectionSpacer(context, colors))

        container.addView(
            buildAddContactActionCard(
                context = context,
                colors = colors,
                text = context.getString(R.string.contact_list_send),
                textColor = colors.textColorLink
            ) {
                viewModel.addFriend(
                    result = result,
                    addWording = wordingInput.second.text.toString(),
                    remark = remarkInput.second.text.toString(),
                    successMessage = context.getString(R.string.contact_list_add_friend_success),
                    failureMessageMapper = { _, desc -> desc }
                )
            }
        )

        scrollView.addView(container)
        contentContainer.addView(scrollView)
    }

    private fun showGroupJoinFormStep() {
        currentStep = AddContactFlowStep.GROUP_JOIN_FORM
        updateHeader()
        contentContainer.removeAllViews()

        val result = selectedResult ?: return
        val colors = getColors()

        val scrollView = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(colors.bgColorDefault)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.bgColorDefault)
        }

        container.addView(buildAddContactInfoCard(context, colors, addType, result))

        container.addView(
            buildAddContactSectionTitle(context, colors, context.getString(R.string.contact_list_fill_validation_message))
        )

        val defaultWording = SelfWordingProvider.defaultWording(context)

        val wordingInput = buildAddContactMultilineInputCard(
            context = context,
            colors = colors,
            defaultValue = groupJoinMessageDraft ?: defaultWording,
            minHeightDp = 120f
        )
        wordingInput.second.setAfterTextChangedListener {
            groupJoinMessageDraft = it
        }
        container.addView(wordingInput.first)

        container.addView(buildAddContactSectionSpacer(context, colors))

        container.addView(
            buildAddContactActionCard(
                context = context,
                colors = colors,
                text = context.getString(R.string.contact_list_send),
                textColor = colors.textColorLink
            ) {
                viewModel.joinGroup(
                    result = result,
                    message = wordingInput.second.text.toString(),
                    successMessage = context.getString(R.string.contact_list_join_group_request_sent),
                    failureMessageMapper = { _, desc -> desc }
                )
            }
        )

        scrollView.addView(container)
        contentContainer.addView(scrollView)
    }

    private fun clearFormDrafts() {
        addFriendWordingDraft = null
        addFriendRemarkDraft = null
        groupJoinMessageDraft = null
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
