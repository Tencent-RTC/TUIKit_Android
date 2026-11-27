package com.tencent.uikit.app.call.entry

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.common.CallAccessRowFactory
import com.tencent.uikit.app.call.common.CallAccessSheet

internal class CallEntryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    internal var onMoreSettingsTap: (() -> Unit)? = null
    internal var onStartCallTap: (() -> Unit)? = null

    private lateinit var state: CallEntryState
    private lateinit var controller: EntryContactController
    private val selection: CallEntrySelection get() = controller.selection
    private lateinit var contactAdapter: EntryContactAdapter
    private var callAllowed = true
    private var resolutionSheet: CallAccessSheet? = null

    private val inputBox: LinearLayout
    private val idInput: EditText
    private val searchClear: TextView
    private val searchButton: TextView
    private val selectedSection: LinearLayout
    private val clearSelected: TextView
    private val tokenFlow: TokenFlowView
    private val remainLabel: TextView
    private val listTitle: TextView
    private val listCount: TextView
    private val contactList: RecyclerView
    private val mediaChip: TextView
    private val captionChip: TextView
    private val resolutionChip: TextView
    private val callButton: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.callaccess_entry_view, this, true)
        inputBox = findViewById(R.id.input_box)
        idInput = findViewById(R.id.id_input)
        searchClear = findViewById(R.id.search_clear)
        searchButton = findViewById(R.id.search_button)
        selectedSection = findViewById(R.id.selected_section)
        clearSelected = findViewById(R.id.clear_selected)
        tokenFlow = findViewById(R.id.token_flow)
        remainLabel = findViewById(R.id.remain_label)
        listTitle = findViewById(R.id.list_title)
        listCount = findViewById(R.id.list_count)
        contactList = findViewById(R.id.contact_list)
        mediaChip = findViewById(R.id.media_chip)
        captionChip = findViewById(R.id.caption_chip)
        resolutionChip = findViewById(R.id.resolution_chip)
        callButton = findViewById(R.id.call_button)
    }

    fun bind(state: CallEntryState, controller: EntryContactController) {
        this.state = state
        this.controller = controller
        contactAdapter = EntryContactAdapter(controller) { controller.reload() }
        contactList.layoutManager = LinearLayoutManager(context)
        contactList.itemAnimator = null
        contactList.adapter = contactAdapter
        controller.onUpdate = { reloadFromState() }
        controller.onQueryConsumed = { idInput.text?.clear() }
        setupBindings()
        reloadFromState()
    }

    fun reloadFromState() {
        if (!::state.isInitialized) return
        val selected = selection.userIDs.map(controller::contactFor)
        selectedSection.isVisible = selected.isNotEmpty()
        tokenFlow.setContacts(selected) { controller.remove(it) }
        remainLabel.text = remainText(CallEntryState.MAX_CALLEE_IDS - selection.userIDs.size)
        renderListHeader()
        contactAdapter.submitList(buildItems())

        val full = controller.isFull
        searchButton.isEnabled = !full
        idInput.hint = context.getString(
            if (full) R.string.callaccess_entry_user_id_placeholder_full else R.string.callaccess_entry_search_hint,
        )
        updateSearchBar()
        renderParamChips()
        syncCallButton()
    }

    fun setCallButtonEnabled(enabled: Boolean) {
        callAllowed = enabled
        if (::state.isInitialized) syncCallButton()
    }

    fun hideKeyboard() {
        idInput.clearFocus()
        ViewCompat.getWindowInsetsController(this)?.hide(WindowInsetsCompat.Type.ime())
    }

    fun dismissSheets(): Boolean {
        val sheet = resolutionSheet?.takeIf { it.isShowing } ?: return false
        sheet.dismiss()
        return true
    }

    override fun onDetachedFromWindow() {
        resolutionSheet?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        resolutionSheet = null
        super.onDetachedFromWindow()
    }

    private fun renderListHeader() {
        val isResult = controller.resultMode != EntryResultMode.DEFAULT
        val count = controller.visibleContacts.size
        listTitle.text = context.getString(
            if (isResult) {
                R.string.callaccess_entry_search_results
            } else {
                R.string.callaccess_entry_friends
            },
        )
        listCount.isVisible = isResult && count > 0
        if (listCount.isVisible) {
            listCount.text = context.getString(R.string.callaccess_entry_match_count, count)
        }
    }

    private fun buildItems(): List<EntryListItem> {
        val items = mutableListOf<EntryListItem>()
        when (controller.resultMode) {
            EntryResultMode.DEFAULT -> {
                val friends = controller.visibleContacts
                if (friends.isEmpty()) {
                    items += when (controller.loadState) {
                        EntryLoadState.LOADING -> EntryListItem.Empty(
                            context.getString(R.string.callaccess_entry_loading), null, null,
                        )
                        EntryLoadState.FAILED -> EntryListItem.Empty(
                            context.getString(R.string.callaccess_entry_load_failed),
                            null,
                            context.getString(R.string.callaccess_entry_retry),
                        )
                        EntryLoadState.READY -> EntryListItem.Empty(
                            context.getString(R.string.callaccess_entry_empty_friends),
                            context.getString(R.string.callaccess_entry_empty_friends_sub),
                            null,
                        )
                    }
                } else {
                    items += friendRows(friends)
                }
            }
            EntryResultMode.FRIEND_RESULTS -> {
                val hits = controller.visibleContacts
                if (hits.isEmpty()) {
                    items += EntryListItem.Empty(
                        context.getString(R.string.callaccess_entry_empty_results),
                        context.getString(R.string.callaccess_entry_empty_results_sub),
                        null,
                    )
                } else {
                    items += friendRows(hits)
                }
            }
            EntryResultMode.REMOTE_ID -> {
                val remote = controller.visibleContacts
                remote.forEach { items += EntryListItem.Remote(it, controller.isSelected(it.userID)) }
            }
        }
        return items
    }

    private fun friendRows(contacts: List<EntryContact>): List<EntryListItem> {
        return contacts.mapIndexed { index, contact ->
            EntryListItem.Friend(
                contact = contact,
                selected = controller.isSelected(contact.userID),
                first = index == 0,
                last = index == contacts.size - 1,
            )
        }
    }

    private fun remainText(remain: Int): CharSequence {
        val number = remain.coerceAtLeast(0).toString()
        val text = context.getString(R.string.callaccess_entry_remain_format, number)
        val start = text.indexOf(number)
        if (start < 0) return text
        return SpannableString(text).apply {
            val end = start + number.length
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, R.color.callaccess_brand)),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun renderParamChips() {
        val isVideo = selection.media == CallEntryState.Media.VIDEO
        mediaChip.text = context.getString(
            R.string.callaccess_entry_chip_format,
            context.getString(if (isVideo) R.string.callaccess_entry_video_call else R.string.callaccess_entry_audio_call),
        )
        captionChip.text = context.getString(
            R.string.callaccess_entry_caption_format,
            context.getString(if (state.captionOn) R.string.callaccess_entry_on else R.string.callaccess_entry_off),
        )
        captionChip.setBackgroundResource(
            if (state.captionOn) R.drawable.callaccess_bg_chip_selected else R.drawable.callaccess_bg_chip,
        )
        captionChip.setTextColor(
            ContextCompat.getColor(context, if (state.captionOn) R.color.callaccess_brand else R.color.callaccess_text),
        )
        resolutionChip.isVisible = isVideo
        resolutionChip.text = context.getString(
            R.string.callaccess_entry_chip_format,
            context.getString(selection.resolution.displayNameRes),
        )
    }

    private fun setupBindings() {
        idInput.doOnTextChanged { _, _, _, _ -> updateSearchBar() }
        idInput.setOnFocusChangeListener { _, _ -> updateSearchBar() }
        idInput.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnter) submitSearch()
            true
        }
        ViewCompat.setOnReceiveContentListener(idInput, arrayOf("text/*")) { _, payload ->
            val split = payload.partition { it.text != null }
            val pasted = split.first?.clip?.let { clip ->
                (0 until clip.itemCount).joinToString(" ") { clip.getItemAt(it).coerceToText(context).toString() }
            }.orEmpty()
            if (payload.source == ContentInfoCompat.SOURCE_CLIPBOARD && CallEntryState.containsSeparator(pasted)) {
                controller.addIDs(pasted)
                split.second
            } else {
                payload
            }
        }
        searchButton.setOnClickListener { submitSearch() }
        searchClear.setOnClickListener {
            idInput.text?.clear()
            controller.clearSearch()
            idInput.requestFocus()
        }
        clearSelected.setOnClickListener { controller.clearSelection() }

        mediaChip.setOnClickListener {
            selection.media = if (selection.media == CallEntryState.Media.VIDEO) {
                CallEntryState.Media.AUDIO
            } else {
                CallEntryState.Media.VIDEO
            }
            renderParamChips()
        }
        captionChip.setOnClickListener {
            state.captionOn = !state.captionOn
            TUICallKit.createInstance(context).enableAITranscriber(state.captionOn)
            renderParamChips()
        }
        resolutionChip.setOnClickListener { openResolutionPicker() }
        findViewById<ImageView>(R.id.settings_chip).setOnClickListener { onMoreSettingsTap?.invoke() }
        callButton.setOnClickListener {
            if (selection.userIDs.isNotEmpty()) onStartCallTap?.invoke()
        }
    }

    private fun openResolutionPicker() {
        val activity = context as? Activity ?: return
        hideKeyboard()
        val sheet = CallAccessSheet(activity)
        sheet.setTitle(context.getString(R.string.callaccess_entry_resolution))
        val list = LinearLayout(activity).apply { orientation = VERTICAL }
        val options = CallEntryState.Resolution.entries
        options.forEachIndexed { index, option ->
            val row = CallAccessRowFactory.optionRow(
                activity,
                context.getString(option.displayNameRes),
                isSelected = option == selection.resolution,
                showsSeparator = index < options.size - 1,
            ) {
                selection.resolution = option
                renderParamChips()
                sheet.dismiss()
            }
            list.addView(row)
        }
        sheet.setContent(list)
        sheet.onDismiss = { if (resolutionSheet === sheet) resolutionSheet = null }
        resolutionSheet = sheet
        sheet.present(activity.findViewById(android.R.id.content))
    }

    private fun submitSearch() {
        controller.submitSearch(idInput.text?.toString().orEmpty())
    }

    private fun updateSearchBar() {
        val typed = !idInput.text.isNullOrEmpty()
        searchClear.isVisible = typed
        val active = idInput.isFocused || typed
        inputBox.setBackgroundResource(if (active) R.drawable.callaccess_bg_search_active else R.drawable.callaccess_bg_search)
    }

    private fun syncCallButton() {
        callButton.isEnabled = callAllowed && selection.userIDs.isNotEmpty()
    }
}
