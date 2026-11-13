package com.tencent.qcloud.tuikit.tuicallkit.call.aitranscriber

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.call.CallKitConfig
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitToast
import com.tencent.qcloud.tuikit.tuicallkit.common.LightSheetHeader
import com.tencent.qcloud.tuikit.tuicallkit.common.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class TranscriptionSettingsSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var onPickLanguage: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private val enableSwitch: SwitchCompat
    private val originalSwitch: SwitchCompat
    private val sizeSegment: LightSegmentedControl
    private val targetValueLabel: TextView
    private val setDefaultRow: LinearLayout
    private val setDefaultTick: TextView
    private var subscribeJob: Job? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)

        val header = LightSheetHeader(context)
        header.setTitle(context.getString(R.string.tuicallkit_cc_title))
        header.onClose = { onClose?.invoke() }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        // sheet-body 顶部 4dp 节奏（对齐 iOS/html）
        addView(View(context), LayoutParams(1, 4.dp(context)))

        val config = CallKitConfig.shared
        enableSwitch = makeSwitch()
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (config.enableAITranscriber != isChecked) {
                config.updateAITranscriber(isChecked)
            }
        }
        originalSwitch = makeSwitch()
        originalSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (config.showOriginalTranscript != isChecked) {
                config.updateShowOriginalTranscript(isChecked)
            }
        }

        val enableRow = makeSwitchRow(
            context.getString(R.string.tuicallkit_cc_title),
            context.getString(R.string.tuicallkit_cc_enable_sub),
            enableSwitch
        )
        val originalRow = makeSwitchRow(
            context.getString(R.string.tuicallkit_cc_show_original),
            context.getString(R.string.tuicallkit_cc_show_original_sub),
            originalSwitch
        )
        addView(enableRow, rowParams(62))
        addView(originalRow, rowParams(62))

        sizeSegment = LightSegmentedControl(context)
        sizeSegment.items = listOf(
            context.getString(R.string.tuicallkit_cc_size_sm),
            context.getString(R.string.tuicallkit_cc_size_md),
            context.getString(R.string.tuicallkit_cc_size_lg)
        )
        sizeSegment.onSelect = { index -> handleSizeChange(index) }
        val sizeTitle = TextView(context).apply {
            setText(R.string.tuicallkit_cc_font_size)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text))
        }
        val sizeRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sizeTitle, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(sizeSegment, LinearLayout.LayoutParams(170.dp(context), 32.dp(context)))
        }
        addView(sizeRow, rowParams(60))

        targetValueLabel = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text2))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val chevron = ImageView(context).apply {
            // 对齐 iOS：5×9 chevron 图标，而非文本字符
            setImageResource(R.drawable.tuicallkit_icon_chevron)
            setColorFilter(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text2))
        }
        val valueGroup = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(targetValueLabel)
            val chevronParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            chevronParams.marginStart = 5.dp(context)
            addView(chevron, chevronParams)
        }
        val targetRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val title = TextView(context).apply {
                setText(R.string.tuicallkit_cc_target_language)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text))
            }
            addView(title, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(valueGroup)
            setOnClickListener { onPickLanguage?.invoke() }
        }
        addView(targetRow, rowParams(60))

        setDefaultTick = TextView(context).apply {
            text = "\u2713"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val tickSize = 16.dp(context)
        val tickFrame = android.widget.FrameLayout(context).apply {
            addView(setDefaultTick, android.widget.FrameLayout.LayoutParams(tickSize, tickSize))
        }
        val setDefaultText = TextView(context).apply {
            setText(R.string.tuicallkit_cc_set_default)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text3))
        }
        setDefaultRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            background = null
            addView(tickFrame, LinearLayout.LayoutParams(tickSize, tickSize).apply {
                marginEnd = 8.dp(context)
            })
            addView(setDefaultText, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener {
                CallKitConfig.shared.updateCcPresetEnabled(!CallKitConfig.shared.ccPresetEnabled)
            }
        }
        addView(setDefaultRow, rowParams(44))

        reload()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            CallKitConfig.shared.transcriptionChanged.collect { reload() }
        }
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    private fun makeSwitch(): SwitchCompat {
        return SwitchCompat(context).apply {
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(
                    ContextCompat.getColor(context, R.color.tuicallkit_brand),
                    ContextCompat.getColor(context, R.color.tuicallkit_switch_off)
                )
            )
        }
    }

    private fun makeSwitchRow(title: String, subtitle: String, toggle: SwitchCompat): View {
        val titleLabel = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text))
        }
        val subtitleLabel = TextView(context).apply {
            text = subtitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text4))
        }
        val textColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(titleLabel)
            val subParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            subParams.topMargin = 3.dp(context)
            addView(subtitleLabel, subParams)
        }
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(textColumn, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = 12.dp(context)
            })
        }
    }

    private fun rowParams(heightDp: Int): LayoutParams {
        val params = LayoutParams(LayoutParams.MATCH_PARENT, heightDp.dp(context))
        params.marginStart = 16.dp(context)
        params.marginEnd = 16.dp(context)
        return params
    }

    private fun reload() {
        val config = CallKitConfig.shared
        enableSwitch.isChecked = config.enableAITranscriber
        originalSwitch.isChecked = config.showOriginalTranscript
        sizeSegment.selectedIndex = when (config.transcriptFontSize) {
            CallKitConfig.TranscriptFontSize.SM -> 0
            CallKitConfig.TranscriptFontSize.MD -> 1
            CallKitConfig.TranscriptFontSize.LG -> 2
        }
        targetValueLabel.text = languageLabel(context, config.transcriptionTargetLanguage)
        updateSetDefaultState(config.ccPresetEnabled)
    }

    private fun updateSetDefaultState(enabled: Boolean) {
        setDefaultRow.isSelected = enabled
        val brand = ContextCompat.getColor(context, R.color.tuicallkit_brand)
        val border = ContextCompat.getColor(context, R.color.tuicallkit_sheet_text4)
        setDefaultTick.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (enabled) {
                setColor(brand)
            } else {
                setColor(Color.TRANSPARENT)
                setStroke(1.5f.dp(context), border)
            }
        }
        setDefaultTick.setTextColor(if (enabled) Color.WHITE else Color.TRANSPARENT)
    }

    private fun handleSizeChange(index: Int) {
        val sizes = CallKitConfig.TranscriptFontSize.entries
        if (index !in sizes.indices) {
            return
        }
        CallKitConfig.shared.updateTranscriptFontSize(sizes[index])
        val label = sizeSegment.items.getOrNull(index).orEmpty()
        CallKitToast.show(context.getString(R.string.tuicallkit_cc_size_changed, label))
    }

    companion object {
        fun languageLabel(context: Context, code: String): String {
            val resId = when (code) {
                "auto" -> R.string.tuicallkit_cc_lang_auto
                "zh" -> R.string.tuicallkit_cc_lang_zh
                "en" -> R.string.tuicallkit_cc_lang_en
                "ar" -> R.string.tuicallkit_cc_lang_ar
                "ja" -> R.string.tuicallkit_cc_lang_ja
                else -> return code
            }
            return context.getString(resId)
        }
    }
}

internal class LightSegmentedControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var items: List<String> = emptyList()
        set(value) {
            field = value
            rebuild()
        }

    var onSelect: ((Int) -> Unit)? = null

    var selectedIndex = 0
        set(value) {
            field = value
            refreshSelection()
        }

    private val buttons = mutableListOf<TextView>()

    init {
        orientation = HORIZONTAL
        background = GradientDrawable().apply {
            cornerRadius = 9f.dp(context).toFloat()
            setColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_grey))
        }
        val padding = 2.dp(context)
        setPadding(padding, padding, padding, padding)
    }

    private fun rebuild() {
        removeAllViews()
        buttons.clear()
        items.forEachIndexed { index, title ->
            val button = TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setOnClickListener {
                    if (selectedIndex != index) {
                        selectedIndex = index
                    }
                    onSelect?.invoke(index)
                }
            }
            val params = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            params.marginEnd = if (index == items.size - 1) 0 else 2.dp(context)
            addView(button, params)
            buttons.add(button)
        }
        refreshSelection()
    }

    private fun refreshSelection() {
        buttons.forEachIndexed { index, button ->
            val selected = index == selectedIndex
            button.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (selected) R.color.tuicallkit_brand else R.color.tuicallkit_sheet_text2
                )
            )
            button.typeface = if (selected) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
            button.background = if (selected) {
                GradientDrawable().apply {
                    cornerRadius = 7f.dp(context).toFloat()
                    setColor(Color.WHITE)
                }
            } else {
                null
            }
            button.elevation = if (selected) 1.5f.dp(context).toFloat() else 0f
        }
    }
}

internal class LanguagePickerSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var onFinish: (() -> Unit)? = null

    private val current: String = CallKitConfig.shared.transcriptionTargetLanguage
    private var picked: String = current
    private val rowViews = mutableMapOf<String, Pair<TextView, TextView>>()
    private val header: LightSheetHeader

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)
        header = LightSheetHeader(context)
        header.setTitle(context.getString(R.string.tuicallkit_cc_target_language))
        header.onAction = { handleActionTap() }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val list = LinearLayout(context).apply { orientation = VERTICAL }
        languageCodes.forEachIndexed { index, code ->
            if (index > 0) {
                val divider = View(context).apply {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_line))
                }
                list.addView(divider, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1.dp(context)))
            }
            list.addView(makeRow(code), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 52.dp(context)))
        }
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        refreshSelection()
    }

    private val languageCodes: List<String>
        get() = listOf("zh", "en", "ar", "ja")

    private fun makeRow(code: String): View {
        val label = TextView(context).apply {
            text = TranscriptionSettingsSheet.languageLabel(context, code)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text))
        }
        val check = TextView(context).apply {
            text = "✓"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_brand))
            isVisible = false
        }
        rowViews[code] = label to check
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val paddingH = 16.dp(context)
            setPadding(paddingH, 0, paddingH, 0)
            addView(label, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(check)
            setOnClickListener { pick(code) }
        }
    }

    private fun pick(code: String) {
        if (picked == code) {
            return
        }
        picked = code
        refreshSelection()
    }

    private fun refreshSelection() {
        for ((code, views) in rowViews) {
            val selected = code == picked
            views.first.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (selected) R.color.tuicallkit_brand else R.color.tuicallkit_sheet_text
                )
            )
            views.first.typeface = if (selected) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
            views.second.isVisible = selected
        }
        val isCancel = picked == current
        header.setActionTitle(
            context.getString(if (isCancel) R.string.tuicallkit_common_cancel else R.string.tuicallkit_common_confirm),
            isCancel = isCancel
        )
    }

    private fun handleActionTap() {
        if (picked != current) {
            val config = CallKitConfig.shared
            config.updateTranscriptionLanguages(config.transcriptionSourceLanguage, picked)
            val label = TranscriptionSettingsSheet.languageLabel(context, picked)
            CallKitToast.show(context.getString(R.string.tuicallkit_cc_target_changed, label))
        }
        onFinish?.invoke()
    }
}
