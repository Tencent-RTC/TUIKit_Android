package com.trtc.uikit.livekit.voicelounge.prepare

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveTextInputView
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.blackAlpha
import com.trtc.uikit.livekit.voicelounge.clipRounded
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.loadUrl
import com.trtc.uikit.livekit.voicelounge.rgb
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeConfig
import com.trtc.uikit.livekit.voicelounge.tint
import com.trtc.uikit.livekit.voicelounge.whiteAlpha

internal class VoiceLoungeInfoEditorView(context: Context) : LinearLayout(context) {
    var onRoomNameChanged: ((String) -> Unit)? = null
    var onSelectTopic: (() -> Unit)? = null
    var onChangeCover: (() -> Unit)? = null

    private val coverButton = FrameLayout(context)
    private val coverImageView = ImageView(context)
    private val coverHintLabel = TextView(context)

    private val copyColumn = LinearLayout(context).apply { orientation = VERTICAL }
    private val nameRow = FrameLayout(context)
    private val roomNameInput = LiveTextInputView(context)
    private val editButton = FrameLayout(context)
    private val editIconView = ImageView(context)
    private val underlineView = View(context)
    private val topicButton = FrameLayout(context)
    private val topicIconView = ImageView(context)
    private val topicLabel = TextView(context)
    private val topicArrowView = ImageView(context)

    private var representedCoverURL = ""
    private var isBindingRoomName = false

    val roomName: String get() = roomNameInput.text?.toString().orEmpty()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.TOP
        clipChildren = false
        clipToPadding = false
        setupViews()
        setupStyles()
        setupBindings()
        bindTopic("")
    }

    fun bind(input: VoiceLoungeConfig, hostName: String) {
        if (!roomNameInput.hasFocus()) {
            val name = input.roomName.ifEmpty { VoiceLoungeConfig.defaultRoomName(hostName) }
            isBindingRoomName = true
            roomNameInput.setText(name)
            isBindingRoomName = false
        }
        loadCover(input.coverURL)
        bindTopic(input.topic)
    }

    fun setRoomName(value: String) {
        endRoomNameEditing()
        isBindingRoomName = true
        roomNameInput.setText(value)
        isBindingRoomName = false
    }

    fun endRoomNameEditing() {
        if (!roomNameInput.hasFocus()) return
        roomNameInput.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(roomNameInput.windowToken, 0)
    }

    private fun setupViews() {
        coverButton.clipChildren = true
        coverButton.clipRounded(dp(COVER_CORNER).toFloat())
        coverButton.addView(coverImageView, frameParams(MATCH, MATCH))
        coverButton.addView(coverHintLabel, frameParams(MATCH, dp(20), Gravity.BOTTOM))
        addView(coverButton, linearParams(dp(COVER_SIZE), dp(COVER_SIZE)))

        nameRow.addView(editButton, frameParams(dp(EDIT_WIDTH), WRAP, Gravity.END or Gravity.TOP))
        editButton.addView(editIconView, frameParams(dp(EDIT_ICON_SIZE), dp(EDIT_ICON_SIZE), Gravity.CENTER))
        nameRow.addView(
            roomNameInput, frameParams(MATCH, WRAP, Gravity.TOP, end = dp(EDIT_WIDTH)),
        )
        copyColumn.addView(nameRow, linearParams(MATCH, WRAP))
        copyColumn.addView(underlineView, linearParams(MATCH, dp(1), top = dp(10)))

        topicButton.addView(
            topicIconView,
            frameParams(
                dp(TOPIC_ICON_SIZE), dp(TOPIC_ICON_SIZE), Gravity.CENTER_VERTICAL or Gravity.START, start = dp(10)
            ),
        )
        topicButton.addView(
            topicLabel,
            frameParams(
                WRAP, WRAP, Gravity.CENTER_VERTICAL or Gravity.START,
                start = dp(10 + TOPIC_ICON_SIZE + 6), end = dp(6 + TOPIC_ARROW_SIZE + 10),
            ),
        )
        topicButton.addView(
            topicArrowView,
            frameParams(
                dp(TOPIC_ARROW_SIZE), dp(TOPIC_ARROW_SIZE),
                Gravity.CENTER_VERTICAL or Gravity.END, end = dp(10),
            ),
        )
        copyColumn.addView(topicButton, linearParams(WRAP, dp(TOPIC_HEIGHT), top = dp(8)))

        addView(copyColumn, linearParams(MATCH, WRAP, start = dp(14), top = dp(2)))
    }

    private fun setupStyles() {
        coverButton.background = roundedDrawable(
            COVER_BACKGROUND, dp(COVER_CORNER).toFloat(), dp(1), whiteAlpha(0.16f),
        )
        coverImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        coverHintLabel.text = context.getString(R.string.livekit_voiceroom_prepare_change_cover)
        coverHintLabel.setTextColor(Color.WHITE)
        coverHintLabel.gravity = Gravity.CENTER
        coverHintLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 7f)
        coverHintLabel.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        coverHintLabel.includeFontPadding = false
        coverHintLabel.setPadding(dp(2), 0, dp(2), 0)
        coverHintLabel.setBackgroundColor(blackAlpha(0.55f))
        coverHintLabel.setSingleLine(true)
        coverHintLabel.ellipsize = TextUtils.TruncateAt.END

        roomNameInput.background = null
        roomNameInput.setPadding(0, 0, 0, 0)
        roomNameInput.setTextColor(Color.WHITE)
        roomNameInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        roomNameInput.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        roomNameInput.includeFontPadding = false
        roomNameInput.gravity = Gravity.START or Gravity.TOP
        roomNameInput.maxLines = 2
        roomNameInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        roomNameInput.imeOptions = EditorInfo.IME_ACTION_DONE
        roomNameInput.setHorizontallyScrolling(false)
        roomNameInput.setInputLimit(
            ROOM_NAME_BYTE_LIMIT, R.string.livekit_voiceroom_prepare_room_name_byte_limit,
            LiveTextInputView.LimitUnit.UTF8_BYTES,
        )

        editIconView.setImageResource(R.drawable.livekit_voiceprepare_edit)
        editIconView.scaleType = ImageView.ScaleType.FIT_CENTER
        editIconView.tint(whiteAlpha(0.7f))
        editButton.contentDescription = context.getString(R.string.livekit_voiceroom_prepare_room_name_edit)

        underlineView.setBackgroundColor(whiteAlpha(0.22f))

        topicButton.background = roundedDrawable(
            whiteAlpha(0.06f), dp(14).toFloat(), dp(1), whiteAlpha(0.18f),
        )
        topicIconView.setImageResource(R.drawable.livekit_voiceprepare_topic)
        topicIconView.scaleType = ImageView.ScaleType.FIT_CENTER
        topicIconView.tint(whiteAlpha(0.78f))
        topicArrowView.setImageResource(R.drawable.livekit_voiceprepare_arrow)
        topicArrowView.scaleType = ImageView.ScaleType.FIT_CENTER
        topicArrowView.tint(whiteAlpha(0.78f))

        topicLabel.setTextColor(whiteAlpha(0.78f))
        topicLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
        topicLabel.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        topicLabel.includeFontPadding = false
        topicLabel.maxLines = 1
        topicLabel.ellipsize = TextUtils.TruncateAt.END
    }

    private fun setupBindings() {
        coverButton.setOnClickListener { onChangeCover?.invoke() }
        editButton.setOnClickListener { roomNameInput.requestFocus() }
        topicButton.setOnClickListener { onSelectTopic?.invoke() }
        roomNameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                endRoomNameEditing()
                true
            } else {
                false
            }
        }
        roomNameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!isBindingRoomName) onRoomNameChanged?.invoke(roomName)
            }
        })
    }

    private fun bindTopic(topic: String) {
        val trimmed = topic.trim()
        topicLabel.text = if (trimmed.isEmpty()) {
            context.getString(R.string.livekit_voiceroom_prepare_topic_placeholder)
        } else {
            context.getString(R.string.livekit_voiceroom_prepare_topic_picked, trimmed)
        }
        topicButton.contentDescription = topicLabel.text
    }

    private fun loadCover(coverURL: String) {
        if (coverURL == representedCoverURL) return
        representedCoverURL = coverURL
        if (coverURL.isNotEmpty()) coverImageView.loadUrl(coverURL)
    }

    private companion object {
        const val ROOM_NAME_BYTE_LIMIT = 100
        const val COVER_SIZE = 68
        const val COVER_CORNER = 12
        const val EDIT_WIDTH = 15 + 8
        const val EDIT_ICON_SIZE = 15
        const val TOPIC_HEIGHT = 28
        const val TOPIC_ICON_SIZE = 17
        const val TOPIC_ARROW_SIZE = 13

        val COVER_BACKGROUND = rgb(0.165f, 0.184f, 0.271f)
    }
}
