package com.tencent.uikit.app.call.entry

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.common.CallAccessTapRow
import com.tencent.uikit.app.call.common.CallAccessToast
import com.tencent.uikit.app.call.common.CallAccessToggle

@SuppressLint("ClickableViewAccessibility")
internal class CallEntryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    internal var onFriendsTap: (() -> Unit)? = null
    internal var onMoreSettingsTap: (() -> Unit)? = null
    internal var onStartCallTap: (() -> Unit)? = null

    private lateinit var state: CallEntryState

    private val scrollView: ScrollView
    private val inputBox: View
    private val idInput: EditText
    private val addButton: TextView
    private val tokenFlow: TokenFlowView
    private val remainLabel: TextView
    private val mediaSegment: CallAccessSegment
    private val resolutionSegment: CallAccessSegment
    private val resolutionRow: View
    private val resolutionDivider: View
    private val captionToggle: CallAccessToggle
    private val callButton: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.callaccess_entry_view, this, true)
        scrollView = findViewById(R.id.entry_scroll)
        inputBox = findViewById(R.id.input_box)
        idInput = findViewById(R.id.id_input)
        addButton = findViewById(R.id.add_button)
        tokenFlow = findViewById(R.id.token_flow)
        remainLabel = findViewById(R.id.remain_label)
        mediaSegment = findViewById(R.id.media_segment)
        resolutionSegment = findViewById(R.id.resolution_segment)
        resolutionRow = findViewById(R.id.resolution_row)
        resolutionDivider = findViewById(R.id.resolution_divider)
        captionToggle = findViewById(R.id.caption_toggle)
        captionToggle.setStyle(CallAccessToggle.Style.CAPTION)
        callButton = findViewById(R.id.call_button)
        findViewById<ImageView>(R.id.settings_chevron).setColorFilter(ContextCompat.getColor(context, R.color.callaccess_arrow))
    }

    fun bind(state: CallEntryState) {
        this.state = state
        setupBindings()
        reloadFromState()
    }

    fun reloadFromState() {
        renderTokens()
        captionToggle.isOn = state.captionOn
        mediaSegment.select(if (state.media == CallEntryState.Media.VIDEO) 0 else 1)
        resolutionSegment.select(CallEntryState.Resolution.entries.indexOf(state.resolution).coerceAtLeast(0))
        val isAudio = state.media == CallEntryState.Media.AUDIO
        resolutionRow.isVisible = !isAudio
        resolutionDivider.isVisible = !isAudio
    }

    fun commitPendingInput(isForCall: Boolean): Boolean {
        val parsed = CallEntryState.parseIDs(idInput.text?.toString().orEmpty())
        if (parsed.isEmpty()) {
            return true
        }
        val next = state.userIDs.toMutableList()
        for (id in parsed) {
            if (next.none { it.equals(id, ignoreCase = true) }) {
                next.add(id)
            }
        }
        if (next.size > CallEntryState.MAX_CALLEE_IDS) {
            val messageRes = if (isForCall) R.string.callaccess_call_too_many_no_dial else R.string.callaccess_call_too_many
            CallAccessToast.show(findHostingActivity(), context.getString(messageRes))
            return false
        }
        state.userIDs = next
        idInput.text?.clear()
        renderTokens()
        return true
    }

    fun setCallButtonEnabled(enabled: Boolean) {
        callButton.isEnabled = enabled
    }

    private fun setupBindings() {
        mediaSegment.configure(
            listOf(
                context.getString(R.string.callaccess_entry_video_call),
                context.getString(R.string.callaccess_entry_audio_call)
            ),
            13f,
            178
        )
        mediaSegment.onSelect = { index ->
            state.media = if (index == 0) CallEntryState.Media.VIDEO else CallEntryState.Media.AUDIO
            reloadFromState()
        }
        resolutionSegment.configure(
            CallEntryState.Resolution.entries.map { context.getString(it.displayNameRes) },
            12f,
            232
        )
        resolutionSegment.onSelect = { index ->
            state.resolution = CallEntryState.Resolution.entries[index]
        }
        callButton.setOnClickListener { onStartCallTap?.invoke() }
        findViewById<TextView>(R.id.pick_button).setOnClickListener { onFriendsTap?.invoke() }
        captionToggle.onChange = { isOn ->
            state.captionOn = isOn
            TUICallKit.createInstance(context).enableAITranscriber(isOn)
        }
        findViewById<CallAccessTapRow>(R.id.settings_row).onTap = { onMoreSettingsTap?.invoke() }

        idInput.doOnTextChanged { text, _, _, _ ->
            val value = text?.toString().orEmpty()
            if (value.length > MAX_INPUT_LENGTH) {
                idInput.setText(value.take(MAX_INPUT_LENGTH))
                idInput.setSelection(MAX_INPUT_LENGTH)
                return@doOnTextChanged
            }
            if (CallEntryState.containsSeparator(value)) {
                commitPendingInput(isForCall = false)
            }
        }
        idInput.setOnEditorActionListener { _, _, _ ->
            commitPendingInput(isForCall = false)
            true
        }
        idInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN && idInput.text.isNullOrEmpty()) {
                val last = state.userIDs.lastOrNull()
                if (last != null) {
                    removeToken(last)
                    return@setOnKeyListener true
                }
            }
            false
        }
        idInput.setOnFocusChangeListener { _, hasFocus ->
            inputBox.setBackgroundResource(
                if (hasFocus) R.drawable.callaccess_bg_input_focused else R.drawable.callaccess_bg_input
            )
            if (!hasFocus) {
                commitPendingInput(isForCall = false)
            }
        }
        addButton.setOnClickListener {
            commitPendingInput(isForCall = false)
            idInput.requestFocus()
        }
        inputBox.setOnClickListener { idInput.requestFocus() }
        scrollView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }
    }

    private fun renderTokens() {
        tokenFlow.setTokens(state.userIDs) { id -> removeToken(id) }
        val remaining = CallEntryState.MAX_CALLEE_IDS - state.userIDs.size
        val prefix = context.getString(R.string.callaccess_entry_remain_prefix)
        val count = remaining.toString()
        val suffix = context.getString(R.string.callaccess_entry_remain_suffix)
        val full = prefix + count + suffix
        val spannable = SpannableString(full)
        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.callaccess_text3)),
            0, prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.callaccess_brand)),
            prefix.length, prefix.length + count.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.callaccess_text3)),
            prefix.length + count.length, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        remainLabel.text = spannable
        idInput.hint = context.getString(
            if (remaining <= 0) R.string.callaccess_entry_user_id_placeholder_full else R.string.callaccess_entry_user_id_placeholder
        )
    }

    private fun removeToken(id: String) {
        state.userIDs = state.userIDs.filter { it != id }
        renderTokens()
    }

    private fun hideKeyboard() {
        idInput.clearFocus()
        ViewCompat.getWindowInsetsController(this)?.hide(WindowInsetsCompat.Type.ime())
    }

    private fun findHostingActivity(): Activity {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) {
                return current
            }
            current = current.baseContext
        }
        throw IllegalStateException("CallEntryView must be hosted in an Activity")
    }

    private companion object {
        const val MAX_INPUT_LENGTH = 32
    }
}
