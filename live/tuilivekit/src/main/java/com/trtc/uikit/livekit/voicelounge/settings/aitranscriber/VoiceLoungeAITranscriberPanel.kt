package com.trtc.uikit.livekit.voicelounge.settings.aitranscriber

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveSwitch
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.setPressAlpha
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeTranscriberScale
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.vlString
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha
import io.trtc.tuikit.atomicxcore.api.ai.TranslationLanguage

internal class VoiceLoungeAITranscriberPanel(context: Context) : BottomSheetView(context) {
    data class State(
        val isEnabled: Boolean,
        val showOriginal: Boolean,
        val scale: VoiceLoungeTranscriberScale,
        val language: TranslationLanguage,
        val saveAsDefault: Boolean,
    )

    var onStateChanged: ((State) -> Unit)? = null

    private val enableRow = SwitchRow(context)
    private val originalRow = SwitchRow(context)
    private val scaleRow = SegmentRow(context)
    private val languageRow = ValueRow(context)
    private val defaultCheck = DefaultCheckRow(context)
    private val cardContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val languagePanel by lazy {
        VoiceLoungeTranscriberLanguagePanel(context).apply {
            onSelect = { language ->
                this@VoiceLoungeAITranscriberPanel.state =
                    this@VoiceLoungeAITranscriberPanel.state.copy(language = language)
                languageRow.setValue(VoiceLoungeTranscriberPanelText.languageDisplayName(language))
                emit()
            }
        }
    }

    private var state: State = State(
        isEnabled = false, showOriginal = false, scale = VoiceLoungeTranscriberScale.STANDARD,
        language = TranslationLanguage.ENGLISH, saveAsDefault = false,
    )
    private var skin: VoiceLoungeSkin = VoiceLoungeSkin.defaultSkin

    override val sheetHeight: Int
        get() {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(
                (context.resources.displayMetrics.widthPixels - context.dp(32)).coerceAtLeast(0),
                View.MeasureSpec.EXACTLY,
            )
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            cardContainer.measure(widthSpec, heightSpec)
            return context.dp(66) + cardContainer.measuredHeight + context.dp(44)
        }

    init {
        sheetTitle = context.getString(R.string.livekit_voiceroom_caption_title)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(VoiceLoungeColor.card, context.dp(16).toFloat())
        }
        card.addView(enableRow, linearParams(MATCH, WRAP))
        card.addView(originalRow, linearParams(MATCH, WRAP))
        card.addView(scaleRow, linearParams(MATCH, WRAP))
        card.addView(languageRow, linearParams(MATCH, WRAP))

        cardContainer.addView(card, linearParams(MATCH, WRAP))
        cardContainer.addView(
            defaultCheck, linearParams(MATCH, WRAP, top = context.dp(10)).apply { gravity = Gravity.CENTER_HORIZONTAL },
        )

        contentView.addView(
            cardContainer,
            frameParams(
                MATCH, WRAP, Gravity.TOP,
                start = context.dp(16), end = context.dp(16), top = context.dp(66),
            ),
        )

        enableRow.bind(
            title = vlString(R.string.livekit_voiceroom_caption_title),
            subtitle = vlString(R.string.livekit_voiceroom_caption_subtitle),
        )
        originalRow.bind(
            title = vlString(R.string.livekit_voiceroom_caption_show_source),
            subtitle = vlString(R.string.livekit_voiceroom_caption_show_source_subtitle),
        )
        scaleRow.bind(
            title = vlString(R.string.livekit_voiceroom_caption_font_scale),
            segments = listOf(
                vlString(R.string.livekit_voiceroom_caption_font_small),
                vlString(R.string.livekit_voiceroom_caption_font_standard),
                vlString(R.string.livekit_voiceroom_caption_font_large),
            ),
        )
        languageRow.bind(title = vlString(R.string.livekit_voiceroom_caption_target_language))

        enableRow.onCheckedChange = { isOn ->
            state = state.copy(isEnabled = isOn)
            updateInteractability()
            emit()
        }
        originalRow.onCheckedChange = { isOn ->
            state = state.copy(showOriginal = isOn)
            emit()
        }
        scaleRow.onSelect = { index ->
            val newScale = VoiceLoungeTranscriberScale.values().getOrNull(index)
            if (newScale != null) {
                state = state.copy(scale = newScale)
                emit()
            }
        }
        languageRow.setOnClickListener {
            languagePanel.show(state.language, skin)
        }
        defaultCheck.onToggle = { checked ->
            state = state.copy(saveAsDefault = checked)
            emit()
        }
    }

    fun show(initial: State, skin: VoiceLoungeSkin) {
        this.state = initial
        this.skin = skin
        enableRow.setChecked(initial.isEnabled, animate = false)
        enableRow.onTrackColor = skin.mid
        originalRow.setChecked(initial.showOriginal, animate = false)
        originalRow.onTrackColor = skin.mid
        scaleRow.applySkin(skin)
        scaleRow.setSelectedIndex(initial.scale.ordinal)
        languageRow.setValue(VoiceLoungeTranscriberPanelText.languageDisplayName(initial.language))
        defaultCheck.applySkin(skin)
        defaultCheck.setChecked(initial.saveAsDefault)
        updateInteractability()
        show()
    }

    override fun dismiss() {
        if (languagePanel.isShowing) languagePanel.dismiss()
        super.dismiss()
    }

    private fun emit() {
        onStateChanged?.invoke(state)
    }

    private fun updateInteractability() {
        val enabled = state.isEnabled
        originalRow.setContentAlpha(if (enabled) 1f else 0.4f)
        scaleRow.setContentAlpha(if (enabled) 1f else 0.4f)
        languageRow.setContentAlpha(if (enabled) 1f else 0.4f)
    }

    private class SwitchRow(context: Context) : FrameLayout(context) {
        private val titleLabel =
            voiceLoungeText(context, LiveTheme.TextRole.ITEM_TITLE, LiveTheme.TextTone.PRIMARY, emphasized = true)
        private val subtitleLabel = voiceLoungeText(context, LiveTheme.TextRole.CAPTION, LiveTheme.TextTone.TERTIARY)
        private val toggle = LiveSwitch(context)

        var onCheckedChange: ((Boolean) -> Unit)?
            get() = toggle.onCheckedChange
            set(value) {
                toggle.onCheckedChange = value
            }

        var onTrackColor: Int
            get() = toggle.onTrackColor
            set(value) {
                toggle.onTrackColor = value
            }

        init {
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            column.addView(titleLabel, linearParams(WRAP, WRAP))
            column.addView(subtitleLabel, linearParams(WRAP, WRAP, top = context.dp(4)))
            addView(
                column,
                frameParams(
                    MATCH, WRAP, Gravity.CENTER_VERTICAL,
                    start = context.dp(16), end = context.dp(16 + 51 + 10), top = context.dp(12),
                    bottom = context.dp(12),
                ),
            )
            addView(
                toggle,
                frameParams(
                    context.dp(51), context.dp(31), Gravity.END or Gravity.CENTER_VERTICAL, end = context.dp(16),
                ),
            )
        }

        fun bind(title: String, subtitle: String) {
            titleLabel.text = title
            subtitleLabel.text = subtitle
        }

        fun setChecked(checked: Boolean, animate: Boolean) = toggle.setChecked(checked, animate)

        fun setContentAlpha(alpha: Float) {
            titleLabel.alpha = alpha
            subtitleLabel.alpha = alpha
        }
    }

    private class SegmentRow(context: Context) : FrameLayout(context) {
        var onSelect: ((Int) -> Unit)? = null

        private val titleLabel =
            voiceLoungeText(context, LiveTheme.TextRole.ITEM_TITLE, LiveTheme.TextTone.PRIMARY, emphasized = true)
        private val track = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        private val segments = mutableListOf<TextView>()
        private var selectedIndex = -1
        private var skinColor = VoiceLoungeSkin.defaultSkin.mid

        init {
            val trackBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(8f).toFloat()
                setColor(whiteAlpha(0.08f))
            }
            track.background = trackBackground
            track.setPadding(context.dp(2), context.dp(2), context.dp(2), context.dp(2))

            addView(
                titleLabel,
                frameParams(
                    WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL,
                    start = context.dp(16), top = context.dp(14), bottom = context.dp(14),
                ),
            )
            addView(
                track,
                frameParams(
                    WRAP, context.dp(32), Gravity.END or Gravity.CENTER_VERTICAL,
                    end = context.dp(16), top = context.dp(10), bottom = context.dp(10),
                ),
            )
        }

        fun bind(title: String, segments: List<String>) {
            titleLabel.text = title
            this.segments.forEach { track.removeView(it) }
            this.segments.clear()
            segments.forEachIndexed { index, label ->
                val chip = TextView(context).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    includeFontPadding = false
                    setPadding(context.dp(16), 0, context.dp(16), 0)
                }
                chip.setOnClickListener {
                    if (selectedIndex == index) return@setOnClickListener
                    setSelectedIndex(index)
                    onSelect?.invoke(index)
                }
                track.addView(chip, LinearLayout.LayoutParams(WRAP, MATCH).apply {
                    marginEnd = if (index != segments.size - 1) context.dp(2) else 0
                })
                this.segments += chip
            }
        }

        fun setSelectedIndex(index: Int) {
            if (selectedIndex == index) return
            selectedIndex = index
            segments.forEachIndexed { i, chip ->
                val isSelected = i == index
                chip.background = if (isSelected) {
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = context.dp(6f).toFloat()
                        setColor(skinColor)
                    }
                } else {
                    null
                }
                chip.setTextColor(if (isSelected) Color.WHITE else whiteAlpha(0.78f))
            }
        }

        fun applySkin(skin: VoiceLoungeSkin) {
            skinColor = skin.mid
            if (selectedIndex >= 0) {
                val i = selectedIndex
                selectedIndex = -1
                setSelectedIndex(i)
            }
        }

        fun setContentAlpha(alpha: Float) {
            titleLabel.alpha = alpha
            track.alpha = alpha
        }
    }

    private class ValueRow(context: Context) : FrameLayout(context) {
        private val titleLabel =
            voiceLoungeText(context, LiveTheme.TextRole.ITEM_TITLE, LiveTheme.TextTone.PRIMARY, emphasized = true)
        private val valueLabel = voiceLoungeText(context, LiveTheme.TextRole.ITEM_TITLE, LiveTheme.TextTone.SECONDARY)
        private val chevron = TextView(context).apply {
            text = "›"
            setTextColor(whiteAlpha(0.6f))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            includeFontPadding = false
        }

        init {
            addView(
                titleLabel,
                frameParams(
                    WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL,
                    start = context.dp(16), top = context.dp(16), bottom = context.dp(16),
                ),
            )
            addView(chevron, frameParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL, end = context.dp(16)))
            addView(
                valueLabel,
                frameParams(
                    WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL,
                    end = context.dp(16 + 14), top = context.dp(16), bottom = context.dp(16),
                ),
            )
            setPressAlpha()
            isClickable = true
            isFocusable = true
        }

        fun bind(title: String) {
            titleLabel.text = title
        }

        fun setValue(text: String) {
            valueLabel.text = text
        }

        fun setContentAlpha(alpha: Float) {
            titleLabel.alpha = alpha
            valueLabel.alpha = alpha
            chevron.alpha = alpha
        }
    }

    private class DefaultCheckRow(context: Context) : LinearLayout(context) {
        var onToggle: ((Boolean) -> Unit)? = null

        private val box = CheckBoxView(context)
        private val label = voiceLoungeText(context, LiveTheme.TextRole.CAPTION, LiveTheme.TextTone.SECONDARY).apply {
            text = vlString(R.string.livekit_voiceroom_caption_default_hint)
        }
        private var skinColor = VoiceLoungeSkin.defaultSkin.mid
        private var checked = false

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPressAlpha()
            isClickable = true
            isFocusable = true
            addView(box, linearParams(context.dp(16), context.dp(16)))
            addView(label, linearParams(WRAP, WRAP, start = context.dp(8)))
            setOnClickListener {
                setChecked(!checked)
                onToggle?.invoke(checked)
            }
            applySkin(VoiceLoungeSkin.defaultSkin)
        }

        fun applySkin(skin: VoiceLoungeSkin) {
            skinColor = skin.mid
            render()
        }

        fun setChecked(value: Boolean) {
            if (checked == value) return
            checked = value
            render()
        }

        private fun render() {
            box.checked = checked
            box.fillColor = if (checked) skinColor else Color.TRANSPARENT
            box.borderColor = if (checked) skinColor else whiteAlpha(0.35f)
            box.invalidate()
        }

        private class CheckBoxView(context: Context) : View(context) {
            var checked = false
            var fillColor = Color.TRANSPARENT
            var borderColor = Color.WHITE

            private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            private val check = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.WHITE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val path = Path()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val d = resources.displayMetrics.density
                val radius = width / 2f
                fill.color = fillColor
                canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, fill)
                border.color = borderColor
                border.strokeWidth = 1f * d
                val inset = 0.5f * d
                canvas.drawRoundRect(
                    inset, inset, width - inset, height - inset, radius, radius, border,
                )
                if (!checked) return
                check.strokeWidth = 1.5f * d
                path.reset()
                path.moveTo(4 * d, 8 * d)
                path.lineTo(7 * d, 11 * d)
                path.lineTo(12 * d, 5 * d)
                canvas.drawPath(path, check)
            }
        }
    }
}

internal object VoiceLoungeTranscriberPanelText {
    private val supportedLanguages = listOf(
        TranslationLanguage.ENGLISH to R.string.livekit_voiceroom_caption_lang_english,
        TranslationLanguage.CHINESE to R.string.livekit_voiceroom_caption_lang_chinese,
        TranslationLanguage.JAPANESE to R.string.livekit_voiceroom_caption_lang_japanese,
        TranslationLanguage.KOREAN to R.string.livekit_voiceroom_caption_lang_korean,
        TranslationLanguage.SPANISH to R.string.livekit_voiceroom_caption_lang_spanish,
        TranslationLanguage.FRENCH to R.string.livekit_voiceroom_caption_lang_french,
        TranslationLanguage.GERMAN to R.string.livekit_voiceroom_caption_lang_german,
        TranslationLanguage.RUSSIAN to R.string.livekit_voiceroom_caption_lang_russian,
    )

    fun languageDisplayName(language: TranslationLanguage): String = vlString(
        supportedLanguages.firstOrNull { it.first == language }?.second
            ?: R.string.livekit_voiceroom_caption_lang_english,
    )

    fun supported(): List<TranslationLanguage> = supportedLanguages.map { it.first }
}

internal class VoiceLoungeTranscriberLanguagePanel(context: Context) : BottomSheetView(context) {
    var onSelect: ((TranslationLanguage) -> Unit)? = null

    private val listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var selected: TranslationLanguage = TranslationLanguage.ENGLISH
    private var skin: VoiceLoungeSkin = VoiceLoungeSkin.defaultSkin

    override val sheetHeight: Int
        get() = context.dp(66 + VoiceLoungeTranscriberPanelText.supported().size * 52 + 44)

    init {
        sheetTitle = context.getString(R.string.livekit_voiceroom_caption_target_language)
        val scroll = android.widget.ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        scroll.addView(listContainer, android.view.ViewGroup.LayoutParams(MATCH, WRAP))
        contentView.addView(
            scroll,
            frameParams(
                MATCH, WRAP, Gravity.TOP,
                start = context.dp(16), end = context.dp(16), top = context.dp(66),
            ),
        )
        VoiceLoungeTranscriberPanelText.supported().forEach { language ->
            listContainer.addView(makeRow(language), linearParams(MATCH, context.dp(52)))
        }
    }

    fun show(selected: TranslationLanguage, skin: VoiceLoungeSkin) {
        this.selected = selected
        this.skin = skin
        refreshSelection()
        show()
    }

    private fun makeRow(language: TranslationLanguage): View {
        val row = FrameLayout(context)
        val title = voiceLoungeText(context, LiveTheme.TextRole.ITEM_TITLE, LiveTheme.TextTone.PRIMARY).apply {
            text = VoiceLoungeTranscriberPanelText.languageDisplayName(language)
        }
        val tick = TextView(context).apply {
            text = "✓"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            includeFontPadding = false
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            visibility = View.INVISIBLE
            tag = language
        }
        row.addView(title, frameParams(WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL))
        row.addView(tick, frameParams(context.dp(14), context.dp(14), Gravity.END or Gravity.CENTER_VERTICAL))
        row.setPressAlpha()
        row.setOnClickListener {
            if (selected == language) {
                dismiss()
                return@setOnClickListener
            }
            selected = language
            refreshSelection()
            onSelect?.invoke(language)
            dismiss()
        }
        row.tag = language
        return row
    }

    private fun refreshSelection() {
        for (i in 0 until listContainer.childCount) {
            val row = listContainer.getChildAt(i) as? FrameLayout ?: continue
            val language = row.tag as? TranslationLanguage ?: continue
            val tick = row.getChildAt(1) as? TextView ?: continue
            tick.visibility = if (language == selected) View.VISIBLE else View.INVISIBLE
            tick.setTextColor(skin.mid)
        }
    }
}
