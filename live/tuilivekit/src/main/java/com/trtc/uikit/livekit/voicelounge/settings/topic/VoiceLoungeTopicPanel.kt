package com.trtc.uikit.livekit.voicelounge.settings.topic

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.applyFont
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.graphemeCount
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.onClick
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.singleLineEnd
import com.trtc.uikit.livekit.voicelounge.tint
import com.trtc.uikit.livekit.voicelounge.vlString
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha
import com.trtc.uikit.livekit.voicelounge.withAlpha

internal class VoiceLoungeTopicPanel(context: Context) : BottomSheetView(context) {
    var onConfirm: ((String) -> Unit)? = null

    private val inputField = AppCompatEditText(context)
    private val counterLabel = TextView(context)
    private val listStack = LinearLayout(context)
    private val scrollView = ScrollView(context)
    private val footerLabel = voiceLoungeText(
        context, LiveTheme.TextRole.METADATA, LiveTheme.TextTone.TERTIARY, Gravity.CENTER,
    )
    private val confirmButton = TextView(context)

    private val topics = VoiceLoungeTopic.presets
    private val rows = mutableListOf<TopicRow>()
    private var selectedText = ""
    private var skin = VoiceLoungeSkin.defaultSkin
    private var isSyncingText = false

    override val sheetHeight: Int get() = context.dp(560)

    init {
        setupViews()
        setupStyles()
        setupBindings()
    }

    fun show(current: String, skin: VoiceLoungeSkin) {
        this.skin = skin
        selectedText = current
        setInputText(current)
        syncSelection()
        syncLimit()
        show()
    }

    private fun setupViews() {
        val inset = context.dp(20)
        val inputBox = FrameLayout(context)
        inputBox.addView(inputField, frameParams(MATCH, MATCH))
        inputBox.addView(
            counterLabel,
            frameParams(context.dp(50), WRAP, Gravity.END or Gravity.CENTER_VERTICAL, end = context.dp(14)),
        )
        contentView.addView(
            inputBox, frameParams(MATCH, context.dp(50), start = inset, end = inset, top = context.dp(66))
        )

        topics.forEach { topic ->
            val row = TopicRow(context, topic)
            row.onClick { selectTopic(topic.text) }
            listStack.addView(row, linearParams(MATCH, context.dp(56), bottom = context.dp(10)))
            rows.add(row)
        }
        scrollView.addView(listStack, FrameLayout.LayoutParams(MATCH, WRAP))
        val bottomBlock = context.dp(34 + 48 + 12) + context.dp(36)
        contentView.addView(
            scrollView,
            frameParams(MATCH, MATCH, start = inset, end = inset, top = context.dp(66 + 50 + 16), bottom = bottomBlock),
        )
        contentView.addView(
            footerLabel,
            frameParams(MATCH, WRAP, Gravity.BOTTOM, start = inset, end = inset, bottom = context.dp(34 + 48 + 12)),
        )
        contentView.addView(
            confirmButton,
            frameParams(MATCH, context.dp(48), Gravity.BOTTOM, start = inset, end = inset, bottom = context.dp(34)),
        )
    }

    private fun setupStyles() {
        sheetTitle = context.getString(R.string.livekit_voiceroom_topic_title)
        inputField.hint = context.getString(R.string.livekit_voiceroom_topic_placeholder)
        inputField.setHintTextColor(whiteAlpha(0.4f))
        inputField.setTextColor(Color.WHITE)
        inputField.applyFont(16f)
        inputField.setPadding(context.dp(14), 0, context.dp(64), 0)
        inputField.gravity = Gravity.CENTER_VERTICAL
        inputField.isSingleLine = true
        inputField.inputType = InputType.TYPE_CLASS_TEXT
        inputField.imeOptions = EditorInfo.IME_ACTION_DONE
        counterLabel.applyFont(14f)
        counterLabel.gravity = Gravity.END
        counterLabel.fontFeatureSettings = "tnum"
        scrollView.isVerticalScrollBarEnabled = false
        scrollView.overScrollMode = View.OVER_SCROLL_NEVER
        listStack.orientation = LinearLayout.VERTICAL
        footerLabel.text = context.getString(R.string.livekit_voiceroom_topic_footer)
        footerLabel.maxLines = 2
        confirmButton.text = context.getString(R.string.livekit_voiceroom_topic_confirm)
        confirmButton.setTextColor(Color.WHITE)
        confirmButton.applyFont(17f, semibold = true)
        confirmButton.gravity = Gravity.CENTER
        confirmButton.background = roundedDrawable(skin.mid, context.dp(24).toFloat())
        syncLimit()
    }

    private fun setupBindings() {
        inputField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (isSyncingText) return
                selectedText = s?.toString().orEmpty()
                syncSelection()
                syncLimit()
            }
        })
        inputField.setOnEditorActionListener { view, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            hideKeyboard(view)
            true
        }
        confirmButton.onClick { tapConfirm() }
    }

    override fun sheetWillDismiss() {
        hideKeyboard(inputField)
    }

    private fun hideKeyboard(view: View) {
        view.clearFocus()
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setInputText(text: String) {
        isSyncingText = true
        inputField.setText(text)
        inputField.setSelection(inputField.text?.length ?: 0)
        isSyncingText = false
    }

    private fun selectTopic(text: String) {
        selectedText = text
        setInputText(text)
        syncSelection()
        syncLimit()
    }

    private fun syncSelection() {
        rows.forEach { it.setSelected(it.topicText == selectedText, skin) }
    }

    private val currentTopic: String get() = inputField.text?.toString().orEmpty().trim()

    private val isOverLimit: Boolean get() = currentTopic.graphemeCount > MAX_LENGTH

    private fun syncLimit() {
        val count = currentTopic.graphemeCount
        val over = isOverLimit
        counterLabel.text = if (count > 0) "$count/$MAX_LENGTH" else ""
        counterLabel.setTextColor(if (over) VoiceLoungeColor.rose else whiteAlpha(0.4f))
        inputField.background = roundedDrawable(
            whiteAlpha(0.07f), context.dp(12).toFloat(), context.dp(1),
            if (count > 0) skin.mid else Color.TRANSPARENT,
        )
        footerLabel.text = if (over) {
            context.getString(R.string.livekit_voiceroom_topic_over_limit_hint, MAX_LENGTH)
        } else {
            context.getString(R.string.livekit_voiceroom_topic_footer)
        }
        footerLabel.setTextColor(if (over) VoiceLoungeColor.rose else LiveTheme.Color.text(LiveTheme.TextTone.TERTIARY))
        confirmButton.isEnabled = !over
        confirmButton.text = context.getString(
            if (over) R.string.livekit_voiceroom_topic_over_limit else R.string.livekit_voiceroom_topic_confirm,
        )
        confirmButton.setTextColor(if (over) VoiceLoungeColor.rose.withAlpha(0.6f) else Color.WHITE)
        confirmButton.background = roundedDrawable(
            if (over) VoiceLoungeColor.rose.withAlpha(0.15f) else skin.mid, context.dp(24).toFloat(),
        )
    }

    private fun tapConfirm() {
        if (isOverLimit) return
        onConfirm?.invoke(currentTopic)
        dismiss()
    }

    companion object {
        const val MAX_LENGTH = 40
    }
}

private class TopicRow(context: Context, topic: VoiceLoungeTopic) : FrameLayout(context) {
    val topicText: String = topic.text

    private val tagLabel = TextView(context)
    private val textLabel = TextView(context)
    private val checkIcon = ImageView(context)

    init {
        background = roundedDrawable(whiteAlpha(0.05f), context.dp(12).toFloat())

        tagLabel.text = topic.tag
        tagLabel.applyFont(13f, semibold = true)
        tagLabel.setTextColor(Color.WHITE)
        tagLabel.gravity = Gravity.CENTER
        tagLabel.minWidth = context.dp(44)
        tagLabel.setPadding(context.dp(8), 0, context.dp(8), 0)
        tagLabel.background = roundedDrawable(VoiceLoungeColor.overlayMarked, context.dp(9).toFloat())
        addView(
            tagLabel,
            frameParams(WRAP, context.dp(22), Gravity.START or Gravity.CENTER_VERTICAL, start = context.dp(12)),
        )

        textLabel.text = topic.text
        textLabel.applyFont(15f)
        textLabel.setTextColor(whiteAlpha(0.9f))
        textLabel.singleLineEnd()

        checkIcon.setImageResource(R.drawable.livekit_summary_done)
        checkIcon.visibility = View.GONE
        addView(
            checkIcon,
            frameParams(
                context.dp(18), context.dp(18), Gravity.END or Gravity.CENTER_VERTICAL, end = context.dp(14),
            ),
        )

        tagLabel.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left == oldRight - oldLeft) return@addOnLayoutChangeListener
            val params = textLabel.layoutParams as LayoutParams
            params.marginStart = context.dp(12) + (right - left) + context.dp(10)
            textLabel.layoutParams = params
        }
        addView(
            textLabel,
            frameParams(
                MATCH, WRAP, Gravity.START or Gravity.CENTER_VERTICAL, start = context.dp(12 + 44 + 10),
                end = context.dp(14 + 18 + 10),
            ),
        )
    }

    fun setSelected(selected: Boolean, skin: VoiceLoungeSkin) {
        checkIcon.visibility = if (selected) View.VISIBLE else View.GONE
        checkIcon.tint(skin.light)
        background = roundedDrawable(
            whiteAlpha(0.05f), context.dp(12).toFloat(), context.dp(1),
            if (selected) skin.mid else Color.TRANSPARENT,
        )
    }
}

internal data class VoiceLoungeTopic(val tag: String, val text: String) {
    companion object {
        val presets: List<VoiceLoungeTopic>
            get() = listOf(
                R.string.livekit_voiceroom_topic_preset_tea_tag to R.string.livekit_voiceroom_topic_preset_tea_text,
                R.string.livekit_voiceroom_topic_preset_icebreak_tag to R.string.livekit_voiceroom_topic_preset_icebreak_text,
                R.string.livekit_voiceroom_topic_preset_awkward_tag to R.string.livekit_voiceroom_topic_preset_awkward_text,
                R.string.livekit_voiceroom_topic_preset_whatif_tag to R.string.livekit_voiceroom_topic_preset_whatif_text,
                R.string.livekit_voiceroom_topic_preset_latenight_tag to R.string.livekit_voiceroom_topic_preset_latenight_text,
                R.string.livekit_voiceroom_topic_preset_happy_tag to R.string.livekit_voiceroom_topic_preset_happy_text,
            ).map { (tag, text) -> VoiceLoungeTopic(vlString(tag), vlString(text)) }
    }
}
