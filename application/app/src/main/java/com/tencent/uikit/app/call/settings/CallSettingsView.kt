package com.tencent.uikit.app.call.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.bumptech.glide.Glide
import com.tencent.uikit.app.App
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.common.CallAccessLogger
import com.tencent.uikit.app.call.common.CallAccessRowFactory
import com.tencent.uikit.app.call.common.CallAccessSheet
import com.tencent.uikit.app.call.common.CallAccessTapRow
import com.tencent.uikit.app.call.common.CallAccessToast
import com.tencent.uikit.app.call.common.CallAccessToggle
import com.tencent.uikit.app.call.common.dp
import com.tencent.uikit.app.call.entry.CallEntryState
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import io.trtc.tuikit.atomicxcore.api.login.UserProfile

internal class CallSettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val state = (findHostingActivity().application as App).callEntryState
    private val facade by lazy { TUICallKit.createInstance(context) }

    private val scrollView: ScrollView
    private val avatarImage: ImageView
    private val nicknameInput: EditText
    private val nicknameHint: TextView
    private val ringtoneValue: TextView
    private val roomIDInput: EditText
    private val roomIDHint: TextView
    private val timeoutInput: EditText
    private val timeoutHint: TextView
    private val extendedInfoInput: EditText
    private val extendedInfoHint: TextView
    private val advancedCard: View
    private val advancedChevron: ImageView

    private var currentAvatarURL: String = ""
    private var currentNickname: String = ""
    private var activeEditingHint: TextView? = null
    private var activeEditingInput: EditText? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.callaccess_settings_view, this, true)
        scrollView = findViewById(R.id.settings_scroll)
        avatarImage = findViewById(R.id.avatar_image)
        nicknameInput = findViewById(R.id.nickname_input)
        nicknameHint = findViewById(R.id.nickname_hint)
        ringtoneValue = findViewById(R.id.ringtone_value)
        roomIDInput = findViewById(R.id.room_id_input)
        roomIDHint = findViewById(R.id.room_id_hint)
        timeoutInput = findViewById(R.id.timeout_input)
        timeoutHint = findViewById(R.id.timeout_hint)
        extendedInfoInput = findViewById(R.id.extended_info_input)
        extendedInfoHint = findViewById(R.id.extended_info_hint)
        advancedCard = findViewById(R.id.advanced_card)
        advancedChevron = findViewById(R.id.advanced_chevron)

        setupProfileCard()
        setupIncomingCard()
        setupCallUICard()
        setupAdvancedCard()
        reloadFromState()

        scrollView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                dismissActiveEditor()
            }
            false
        }
        setupImeHandling()
    }

    private fun setupImeHandling() {
        val initialPaddingBottom = scrollView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            scrollView.setPadding(
                scrollView.paddingLeft,
                scrollView.paddingTop,
                scrollView.paddingRight,
                initialPaddingBottom + kotlin.math.max(ime, nav)
            )
            if (ime > 0 && activeEditingInput != null) {
                scrollView.post { scrollEditorIntoView() }
            }
            insets
        }
    }

    private fun scrollEditorIntoView() {
        val target = activeEditingHint?.takeIf { it.isVisible } ?: activeEditingInput ?: return
        val rect = Rect()
        target.getDrawingRect(rect)
        scrollView.offsetDescendantRectToMyCoords(target, rect)
        val margin = 8.dp(context)
        val viewportTop = scrollView.scrollY
        val viewportBottom = scrollView.scrollY + scrollView.height - scrollView.paddingBottom
        val dy = when {
            rect.bottom + margin > viewportBottom -> rect.bottom + margin - viewportBottom
            rect.top - margin < viewportTop -> rect.top - margin - viewportTop
            else -> 0
        }
        if (dy != 0) {
            scrollView.smoothScrollBy(0, dy)
        }
    }

    fun updateProfile(profile: UserProfile?) {
        currentNickname = profile?.nickname?.takeIf { it.isNotEmpty() } ?: profile?.userID.orEmpty()
        nicknameInput.setText(currentNickname)
        currentAvatarURL = profile?.avatarURL.orEmpty()
        if (currentAvatarURL.isEmpty()) {
            avatarImage.setImageDrawable(null)
        } else {
            Glide.with(avatarImage).load(currentAvatarURL).into(avatarImage)
        }
    }

    private fun reloadFromState() {
        roomIDInput.setText(state.roomID.ifEmpty { getContext().getString(R.string.callaccess_settings_room_auto_assign) })
        timeoutInput.setText(state.timeoutSeconds.toString())
        extendedInfoInput.setText(
            state.extendedInfo.ifEmpty { getContext().getString(R.string.callaccess_settings_extended_info_empty) }
        )
    }

    private fun setupProfileCard() {
        findViewById<CallAccessTapRow>(R.id.avatar_row).onTap = {
            dismissActiveEditor()
            openAvatarPicker()
        }
        findViewById<CallAccessTapRow>(R.id.nickname_row).onTap = {
            beginEditing(nicknameInput, nicknameHint)
        }
        nicknameInput.doOnTextChanged { text, _, _, _ ->
            val value = text?.toString().orEmpty()
            if (value.length > MAX_NICKNAME_LENGTH) {
                nicknameInput.setText(value.take(MAX_NICKNAME_LENGTH))
                nicknameInput.setSelection(MAX_NICKNAME_LENGTH)
                return@doOnTextChanged
            }
            nicknameHint.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isValidNickname(value)) R.color.callaccess_text3 else R.color.callaccess_red
                )
            )
        }
        nicknameInput.setOnEditorActionListener { _, _, _ -> commitNickname(); true }
        nicknameInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commitNickname() }
    }

    private fun setupIncomingCard() {
        ringtoneValue.text = state.ringtoneLabel(context)
        findViewById<CallAccessTapRow>(R.id.ringtone_row).onTap = {
            dismissActiveEditor()
            openRingtonePicker()
        }
        val muteToggle = findViewById<CallAccessToggle>(R.id.mute_toggle)
        muteToggle.isOn = state.muteModeEnabled
        muteToggle.onChange = { isOn ->
            dismissActiveEditor()
            state.muteModeEnabled = isOn
            facade.enableMuteMode(isOn)
        }
        val bannerToggle = findViewById<CallAccessToggle>(R.id.banner_toggle)
        bannerToggle.isOn = state.bannerOn
        bannerToggle.onChange = { isOn ->
            dismissActiveEditor()
            state.bannerOn = isOn
            facade.enableIncomingBanner(isOn)
        }
    }

    private fun setupCallUICard() {
        val floatToggle = findViewById<CallAccessToggle>(R.id.float_toggle)
        floatToggle.isOn = state.showFloatButton
        floatToggle.onChange = { isOn ->
            dismissActiveEditor()
            state.showFloatButton = isOn
            facade.enableFloatWindow(isOn)
        }
        val blurToggle = findViewById<CallAccessToggle>(R.id.blur_toggle)
        blurToggle.isOn = state.blurBackgroundOn
        blurToggle.onChange = { isOn ->
            dismissActiveEditor()
            state.blurBackgroundOn = isOn
            facade.enableVirtualBackground(isOn)
        }
        val proToggle = findViewById<CallAccessToggle>(R.id.pro_toggle)
        proToggle.isOn = state.proBeautyOn
        proToggle.onChange = { isOn ->
            dismissActiveEditor()
            state.proBeautyOn = isOn
        }
    }

    private fun setupAdvancedCard() {
        findViewById<CallAccessTapRow>(R.id.advanced_toggle).onTap = {
            dismissActiveEditor()
            toggleAdvanced()
        }
        findViewById<CallAccessTapRow>(R.id.room_id_row).onTap = { beginEditing(roomIDInput, roomIDHint) }
        findViewById<CallAccessTapRow>(R.id.timeout_row).onTap = { beginEditing(timeoutInput, timeoutHint) }
        findViewById<CallAccessTapRow>(R.id.extended_info_row).onTap = { beginEditing(extendedInfoInput, extendedInfoHint) }

        roomIDInput.doOnTextChanged { text, _, _, _ -> capLength(roomIDInput, text?.toString(), MAX_ROOM_ID_LENGTH) }
        timeoutInput.doOnTextChanged { text, _, _, _ -> capLength(timeoutInput, text?.toString(), MAX_TIMEOUT_LENGTH) }
        extendedInfoInput.doOnTextChanged { text, _, _, _ -> capLength(extendedInfoInput, text?.toString(), MAX_EXTENDED_INFO_LENGTH) }

        roomIDInput.setOnEditorActionListener { _, _, _ -> commitRoomID(); true }
        roomIDInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commitRoomID() }
        timeoutInput.setOnEditorActionListener { _, _, _ -> commitTimeout(); true }
        timeoutInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commitTimeout() }
        extendedInfoInput.setOnEditorActionListener { _, _, _ -> commitExtendedInfo(); true }
        extendedInfoInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commitExtendedInfo() }
    }

    private fun toggleAdvanced() {
        advancedCard.isVisible = !advancedCard.isVisible
        advancedChevron.animate().cancel()
        advancedChevron.animate().rotation(if (advancedCard.isVisible) 90f else 0f).setDuration(180).start()
    }

    private fun beginEditing(input: EditText, hint: TextView) {
        if (input.isEnabled) {
            input.requestFocus()
            showKeyboard(input)
            return
        }
        if (input.text.toString() == context.getString(R.string.callaccess_settings_room_auto_assign) ||
            input.text.toString() == context.getString(R.string.callaccess_settings_extended_info_empty)
        ) {
            input.setText("")
        }
        input.isEnabled = true
        input.setTextColor(ContextCompat.getColor(context, R.color.callaccess_text))
        hint.isVisible = true
        hint.setTextColor(ContextCompat.getColor(context, R.color.callaccess_text3))
        input.requestFocus()
        input.selectAll()
        activeEditingHint = hint
        activeEditingInput = input
        post { scrollEditorIntoView() }
        showKeyboard(input)
        scrollView.postDelayed({
            if (activeEditingInput === input) {
                scrollEditorIntoView()
            }
        }, 350)
    }

    private fun showKeyboard(input: EditText) {
        input.post {
            if (activeEditingInput === input && input.hasFocus()) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun endEditing(input: EditText, hint: TextView) {
        input.isEnabled = false
        input.setTextColor(ContextCompat.getColor(context, R.color.callaccess_text2))
        hint.isVisible = false
        hint.setTextColor(ContextCompat.getColor(context, R.color.callaccess_text3))
        if (activeEditingHint === hint) {
            activeEditingHint = null
            activeEditingInput = null
        }
        ViewCompat.getWindowInsetsController(input)?.hide(WindowInsetsCompat.Type.ime())
    }

    private fun commitNickname() {
        if (!nicknameInput.isEnabled) {
            return
        }
        val value = nicknameInput.text.toString().trim()
        if (value.isNotEmpty() && isValidNickname(value) && value != currentNickname) {
            currentNickname = value
            facade.setSelfInfo(value, currentAvatarURL, null)
        } else {
            nicknameInput.setText(currentNickname)
        }
        endEditing(nicknameInput, nicknameHint)
    }

    private fun commitRoomID() {
        if (!roomIDInput.isEnabled) {
            return
        }
        val value = roomIDInput.text.toString().trim()
        if (value.isEmpty() || isValidRoomID(value)) {
            state.roomID = value
        }
        roomIDInput.setText(state.roomID.ifEmpty { context.getString(R.string.callaccess_settings_room_auto_assign) })
        endEditing(roomIDInput, roomIDHint)
    }

    private fun commitTimeout() {
        if (!timeoutInput.isEnabled) {
            return
        }
        val seconds = timeoutInput.text.toString().trim().toIntOrNull()
        if (seconds != null && seconds in 1..MAX_TIMEOUT_SECONDS) {
            state.timeoutSeconds = seconds
        }
        timeoutInput.setText(state.timeoutSeconds.toString())
        endEditing(timeoutInput, timeoutHint)
    }

    private fun commitExtendedInfo() {
        if (!extendedInfoInput.isEnabled) {
            return
        }
        val value = extendedInfoInput.text.toString()
        if (value.length <= MAX_EXTENDED_INFO_LENGTH) {
            state.extendedInfo = value
        }
        extendedInfoInput.setText(
            state.extendedInfo.ifEmpty { context.getString(R.string.callaccess_settings_extended_info_empty) }
        )
        endEditing(extendedInfoInput, extendedInfoHint)
    }

    private fun capLength(input: EditText, value: String?, limit: Int) {
        val text = value.orEmpty()
        if (text.length > limit) {
            input.setText(text.take(limit))
            input.setSelection(limit)
        }
    }

    private fun isValidNickname(value: String): Boolean {
        return value.matches(Regex("^[一-龥A-Za-z0-9]{2,24}$"))
    }

    private fun isValidRoomID(value: String): Boolean {
        return value.matches(Regex("^(?:\\d{1,32}|[A-Za-z]{1,32})$"))
    }

    private fun openAvatarPicker() {
        val activity = findHostingActivity()
        val sheet = CallAccessSheet(activity)
        sheet.setTitle(activity.getString(R.string.callaccess_avatar_picker_title))

        // 统一抽屉口径：点选只换高亮，右上动作键落地；所选==当前=取消，不同=确定
        val cells = mutableMapOf<String, FrameLayout>()
        var picked = currentAvatarURL
        fun syncAction() {
            val unchanged = picked == currentAvatarURL
            sheet.setAction(
                activity.getString(if (unchanged) R.string.callaccess_sheet_cancel else R.string.callaccess_sheet_confirm),
                isCancel = unchanged
            )
        }
        sheet.onAction = {
            if (picked == currentAvatarURL) {
                sheet.dismiss()
            } else {
                applyAvatar(activity, picked)
                sheet.dismiss()
            }
        }

        val grid = LinearLayout(activity)
        grid.orientation = LinearLayout.VERTICAL
        val rowSpacing = 16.dp(activity)
        var row: LinearLayout? = null
        PRESET_AVATAR_URLS.forEachIndexed { index, url ->
            if (index % AVATAR_COLUMNS == 0) {
                row = LinearLayout(activity)
                row?.orientation = LinearLayout.HORIZONTAL
                grid.addView(row)
            }
            val cell = makeAvatarCell(activity, url)
            updateAvatarCellState(cell, url == picked)
            cells[url] = cell
            cell.setOnClickListener {
                if (picked == url) {
                    return@setOnClickListener
                }
                cells[picked]?.let { updateAvatarCellState(it, false) }
                picked = url
                updateAvatarCellState(cell, true)
                syncAction()
            }
            val params = LinearLayout.LayoutParams(0, 60.dp(activity), 1f)
            params.topMargin = if (index >= AVATAR_COLUMNS) rowSpacing else 0
            row?.addView(cell, params)
        }
        syncAction()
        sheet.setContent(grid)
        sheet.present(activity.findViewById(android.R.id.content))
    }

    private fun makeAvatarCell(activity: Activity, url: String): FrameLayout {
        val cell = LayoutInflater.from(activity).inflate(R.layout.callaccess_avatar_cell, null) as FrameLayout
        val avatar = cell.findViewById<ImageView>(R.id.avatar_cell_image)
        Glide.with(avatar).load(url).into(avatar)
        return cell
    }

    private fun updateAvatarCellState(cell: FrameLayout, selected: Boolean) {
        val avatar = cell.findViewById<ImageView>(R.id.avatar_cell_image)
        val badge = cell.findViewById<TextView>(R.id.avatar_cell_badge)
        badge.isVisible = selected
        avatar.foreground = if (selected) {
            ContextCompat.getDrawable(cell.context, R.drawable.callaccess_bg_avatar_circle_selected)
        } else {
            null
        }
    }

    private fun applyAvatar(activity: Activity, url: String) {
        currentAvatarURL = url
        Glide.with(avatarImage).load(url).into(avatarImage)
        facade.setSelfInfo(currentNickname, url, null)
        CallAccessToast.show(activity, activity.getString(R.string.callaccess_settings_avatar_set_toast))
    }

    private fun openRingtonePicker() {
        val activity = findHostingActivity()
        val sheet = CallAccessSheet(activity)
        sheet.setTitle(activity.getString(R.string.callaccess_ringtone_picker_title))

        val defaultLabel = activity.getString(R.string.callaccess_settings_ringtone_default)
        val options = listOf("" to defaultLabel, CallEntryState.RINGTONE_BUILTIN to CallEntryState.RINGTONE_BUILTIN)
        var picked = if (state.isDefaultRingtone()) "" else CallEntryState.RINGTONE_BUILTIN
        val rows = mutableMapOf<String, View>()

        fun updateRowState(row: View, selected: Boolean) {
            val title = row.findViewById<TextView>(R.id.option_title)
            title.setTextColor(ContextCompat.getColor(activity, if (selected) R.color.callaccess_brand else R.color.callaccess_text))
            title.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            row.findViewById<View>(R.id.option_check).isVisible = selected
        }

        fun syncAction() {
            val unchanged = picked == state.ringtone.normalizedRingtone()
            sheet.setAction(
                activity.getString(if (unchanged) R.string.callaccess_sheet_cancel else R.string.callaccess_sheet_confirm),
                isCancel = unchanged
            )
        }
        sheet.onAction = {
            if (picked == state.ringtone.normalizedRingtone()) {
                sheet.dismiss()
            } else {
                applyRingtone(activity, picked)
                sheet.dismiss()
            }
        }

        val list = LinearLayout(activity)
        list.orientation = LinearLayout.VERTICAL
        options.forEachIndexed { index, (id, label) ->
            val row = CallAccessRowFactory.optionRow(
                activity,
                label,
                isSelected = id == picked,
                showsSeparator = index < options.size - 1
            ) {
                if (picked == id) {
                    return@optionRow
                }
                rows[picked]?.let { updateRowState(it, false) }
                picked = id
                rows[id]?.let { updateRowState(it, true) }
                syncAction()
            }
            rows[id] = row
            list.addView(row)
        }
        syncAction()
        sheet.setContent(list)
        sheet.present(activity.findViewById(android.R.id.content))
    }

    private fun String.normalizedRingtone(): String {
        return if (isEmpty()) "" else CallEntryState.RINGTONE_BUILTIN
    }

    private fun applyRingtone(activity: Activity, id: String) {
        if (id.isEmpty()) {
            state.ringtone = ""
            facade.setCallingBell("")
        } else {
            val path = ensureBuiltinRingtoneFile(activity)
            if (path == null) {
                CallAccessToast.show(activity, activity.getString(R.string.callaccess_call_failed, "ringtone"))
                return
            }
            state.ringtone = id
            facade.setCallingBell(path)
        }
        val label = state.ringtoneLabel(activity)
        ringtoneValue.text = label
        CallAccessToast.show(activity, activity.getString(R.string.callaccess_settings_ringtone_set_toast, label))
    }

    private fun ensureBuiltinRingtoneFile(activity: Activity): String? {
        return try {
            val dir = java.io.File(activity.filesDir, "ringtones")
            val target = java.io.File(dir, CallEntryState.RINGTONE_BUILTIN)
            val assetSize = activity.assets.open(CallEntryState.RINGTONE_BUILTIN).use { it.available() }
            if (!target.exists() || target.length() != assetSize.toLong()) {
                dir.mkdirs()
                activity.assets.open(CallEntryState.RINGTONE_BUILTIN).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            target.absolutePath
        } catch (e: Exception) {
            CallAccessLogger.e("CallSettingsView", "copy ringtone failed: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun dismissActiveEditor() {
        when (activeEditingHint) {
            nicknameHint -> commitNickname()
            roomIDHint -> commitRoomID()
            timeoutHint -> commitTimeout()
            extendedInfoHint -> commitExtendedInfo()
            else -> ViewCompat.getWindowInsetsController(this)?.hide(WindowInsetsCompat.Type.ime())
        }
    }

    private fun findHostingActivity(): Activity {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) {
                return current
            }
            current = current.baseContext
        }
        throw IllegalStateException("CallSettingsView must be hosted in an Activity")
    }

    companion object {
        private const val MAX_NICKNAME_LENGTH = 24
        private const val MAX_ROOM_ID_LENGTH = 32
        private const val MAX_TIMEOUT_LENGTH = 3
        private const val MAX_TIMEOUT_SECONDS = 180
        private const val MAX_EXTENDED_INFO_LENGTH = 64
        private const val AVATAR_COLUMNS = 4
        private val PRESET_AVATAR_URLS: List<String> = (1..24).map {
            "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar$it.png"
        }
    }
}
