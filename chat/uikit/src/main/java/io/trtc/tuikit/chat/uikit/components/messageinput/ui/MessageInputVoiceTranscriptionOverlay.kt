package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.ai.tts.VoiceMessageConfig
import io.trtc.tuikit.chat.uikit.components.common.ChatDateTimeUtils
import io.trtc.tuikit.chat.uikit.components.messageinput.keyboard.KeyboardInsetsUtil
import io.trtc.tuikit.chat.uikit.components.messageinput.keyboard.LegacyKeyboardHeightProbe
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import kotlin.math.abs
import kotlin.math.sin

internal class MessageInputVoiceTranscriptionOverlay(
    private val anchorView: View,
    private val audioPath: String,
    private val audioDurationSecond: Int,
    private val text: String?,
    private val onCancel: () -> Unit,
    private val onSendAudio: (String, Int) -> Unit,
    private val onSendText: (String) -> Unit,
    private val onDismissed: () -> Unit,
    private val onTranslate: (String, String, (String) -> Unit, () -> Unit) -> Unit,
    private val onStartSpeak: (String, () -> Unit, () -> Unit, () -> Unit) -> Unit,
    private val onStopSpeak: () -> Unit,
) {
    private val context = anchorView.context
    private val density = context.resources.displayMetrics.density
    private val colors
        get() = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

    private val rootView = FrameLayout(context)
    private val backgroundView = OverlayGradientBackgroundView(context)
    private val bubbleView = TranscriptionBubbleView(context)
    private val editText = EditText(context)
    private val loadingDotsView = LoadingDotsView(context)
    private val cancelButton = FrameLayout(context)
    private val sendAudioButton = FrameLayout(context)
    private val cancelIcon = ImageView(context)
    private val sendAudioIcon = ImageView(context)
    private val sendTextButton = TextView(context)
    private val cancelLabel = TextView(context)
    private val sendAudioLabel = TextView(context)
    private val waveformView = OverlayWaveformBarView(context)
    private val chipRow = LinearLayout(context)
    private var chipRowLayoutParams: FrameLayout.LayoutParams? = null
    private var bubbleLayoutParams: FrameLayout.LayoutParams? = null
    private var editTextLayoutParams: FrameLayout.LayoutParams? = null
    private var cancelButtonLayoutParams: FrameLayout.LayoutParams? = null
    private var sendAudioButtonLayoutParams: FrameLayout.LayoutParams? = null
    private var sendTextButtonLayoutParams: FrameLayout.LayoutParams? = null
    private var cancelLabelLayoutParams: FrameLayout.LayoutParams? = null
    private var sendAudioLabelLayoutParams: FrameLayout.LayoutParams? = null
    private var waveformLayoutParams: FrameLayout.LayoutParams? = null
    private var bubbleHeightPx = 0
    private var keyboardHeightPx = 0
    private var legacyKeyboardProbe: LegacyKeyboardHeightProbe? = null
    private val rootLayoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateOverlayLayout()
    }
    private val popupWindow = PopupWindow(
        rootView,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        true,
    ).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isClippingEnabled = true
        isTouchable = true
        isFocusable = true
        isOutsideTouchable = true
        inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
        setOnDismissListener {
            isDismissed = true
            languagePopupWindow?.let { popup ->
                if (popup.isShowing) popup.dismiss()
            }
            languagePopupWindow = null
            cleanupKeyboardTracking()
            isSpeaking = false
            onStopSpeak()
            onDismissed()
        }
    }

    private var state = if (text == null) {
        TranscriptionState.LOADING
    } else if (text.isBlank()) {
        TranscriptionState.EMPTY
    } else {
        TranscriptionState.TEXT
    }

    // The immutable first transcription text. All translations use this as the
    // source so switching languages never translates a prior translation.
    private var originalText: String = if (state == TranscriptionState.TEXT) text.orEmpty() else ""
    private var translatedText: String? = null
    private var isTranslating = false
    private var isSpeaking = false
    private var isDismissed = false
    private var languagePopupWindow: PopupWindow? = null

    init {
        buildLayout()
        bindActions()
        applyTheme()
    }

    fun show() {
        if (popupWindow.isShowing || !anchorView.isAttachedToWindow) {
            return
        }
        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, 0, 0)
        rootView.addOnLayoutChangeListener(rootLayoutChangeListener)
        installKeyboardTracking()
        rootView.post {
            ViewCompat.requestApplyInsets(rootView)
            updateOverlayLayout()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    fun updateResult(resultText: String?) {
        state = if (resultText.isNullOrBlank()) {
            TranscriptionState.EMPTY
        } else {
            TranscriptionState.TEXT
        }
        if (state == TranscriptionState.TEXT) {
            if (originalText.isEmpty()) {
                originalText = resultText.orEmpty()
            }
            translatedText = null
            isTranslating = false
            if (isSpeaking) {
                isSpeaking = false
                onStopSpeak()
            }
        }
        updateBubbleHeightForState(resultText)
        bindBubbleText(resultText)
        renderChipRow()
        applyTheme()
        updateOverlayLayout()
    }

    private fun buildLayout() {
        val hostDirection = ViewCompat.getLayoutDirection(anchorView)
        rootView.layoutDirection = hostDirection
        bubbleView.layoutDirection = hostDirection
        chipRow.layoutDirection = hostDirection
        rootView.addView(
            backgroundView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val bubbleHeight = if (state == TranscriptionState.TEXT && (text?.length ?: 0) > 24) 104 else 68
        bubbleHeightPx = (bubbleHeight * density).toInt()
        val initialLayout = calculateOverlayLayout(
            rootHeight = context.resources.displayMetrics.heightPixels,
            bubbleHeight = bubbleHeightPx,
            keyboardHeight = 0,
            showChipRow = state == TranscriptionState.TEXT,
        )
        val bubbleLp = FrameLayout.LayoutParams(
            (330 * density).toInt(),
            bubbleHeightPx,
            Gravity.TOP or Gravity.START,
        )
        bubbleLp.marginStart = scaleX(16)
        bubbleLp.topMargin = initialLayout.bubbleTop
        bubbleLayoutParams = bubbleLp
        rootView.addView(bubbleView, bubbleLp)

        editText.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        editText.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        editText.textDirection = View.TEXT_DIRECTION_LOCALE
        editText.includeFontPadding = false
        editText.minHeight = 0
        editText.setMinLines(1)
        editText.maxLines = 4
        editText.isVerticalScrollBarEnabled = true
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        editText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        editText.background = null
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        editText.setPaddingRelative((16 * density).toInt(), 0, (16 * density).toInt(), 0)
        val editLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            bubbleContentHeightPx(),
            Gravity.TOP or Gravity.START,
        )
        editTextLayoutParams = editLp
        bubbleView.addView(editText, editLp)
        val dotsLp = FrameLayout.LayoutParams(
            (48 * density).toInt(),
            (20 * density).toInt(),
            Gravity.START or Gravity.TOP,
        )
        dotsLp.marginStart = (20 * density).toInt()
        dotsLp.topMargin = ((bubbleContentHeightPx() - dotsLp.height) / 2).coerceAtLeast(0)
        bubbleView.addView(loadingDotsView, dotsLp)
        bindBubbleText(text)

        chipRow.orientation = LinearLayout.HORIZONTAL
        val chipRowLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START,
        )
        chipRowLp.marginStart = scaleX(16)
        chipRowLp.topMargin = initialLayout.chipRowTop
        chipRowLayoutParams = chipRowLp
        rootView.addView(chipRow, chipRowLp)
        renderChipRow()

        cancelButtonLayoutParams = addCircleIcon(
            cancelButton,
            cancelIcon,
            R.drawable.message_input_voice_close_icon,
            48,
            48,
            initialLayout.cancelButtonTop,
        )
        sendAudioButtonLayoutParams = addCircleIcon(
            sendAudioButton,
            sendAudioIcon,
            R.drawable.message_input_voice_transcribe_icon,
            48,
            142,
            initialLayout.sendAudioButtonTop,
        )
        sendTextButtonLayoutParams = addCircleText(
            sendTextButton,
            context.getString(R.string.message_input_send),
            80,
            262,
            initialLayout.sendTextButtonTop,
        )
        alignBubbleArrowToSendButton()
        cancelLabelLayoutParams = addLabel(
            cancelLabel,
            context.getString(R.string.message_input_cancel),
            72,
            initialLayout.labelTop,
        )
        sendAudioLabelLayoutParams = addLabel(
            sendAudioLabel,
            context.getString(R.string.message_input_send_original_voice),
            166,
            initialLayout.labelTop,
        )

        val waveformLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (40 * density).toInt(),
            Gravity.TOP,
        )
        waveformLp.marginStart = scaleX(16)
        waveformLp.marginEnd = scaleX(16)
        waveformLp.topMargin = initialLayout.waveformTop
        waveformLayoutParams = waveformLp
        waveformView.setDurationSecond(audioDurationSecond)
        rootView.addView(waveformView, waveformLp)
    }

    private fun addCircleIcon(
        view: FrameLayout,
        icon: ImageView,
        iconRes: Int,
        sizeDp: Int,
        startDp: Int,
        topPx: Int,
    ): FrameLayout.LayoutParams {
        icon.setImageResource(iconRes)
        icon.scaleType = ImageView.ScaleType.CENTER
        icon.setColorFilter(colors.textColorPrimary)
        view.addView(
            icon,
            FrameLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt(), Gravity.CENTER)
        )
        val lp = FrameLayout.LayoutParams(
            (sizeDp * density).toInt(),
            (sizeDp * density).toInt(),
            Gravity.TOP or Gravity.START,
        )
        lp.marginStart = scaleX(startDp)
        lp.topMargin = topPx
        rootView.addView(view, lp)
        return lp
    }

    private fun addCircleText(
        view: TextView,
        label: String,
        sizeDp: Int,
        startDp: Int,
        topPx: Int,
    ): FrameLayout.LayoutParams {
        view.gravity = Gravity.CENTER
        view.text = label
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (sizeDp >= 80) 16f else 24f)
        val lp = FrameLayout.LayoutParams(
            (sizeDp * density).toInt(),
            (sizeDp * density).toInt(),
            Gravity.TOP or Gravity.START,
        )
        lp.marginStart = scaleX(startDp)
        lp.topMargin = topPx
        rootView.addView(view, lp)
        return lp
    }

    private fun addLabel(view: TextView, label: String, centerXDp: Int, topPx: Int): FrameLayout.LayoutParams {
        view.gravity = Gravity.CENTER
        view.text = label
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        val labelWidth = (96 * density).toInt()
        val lp = FrameLayout.LayoutParams(labelWidth, (20 * density).toInt(), Gravity.TOP or Gravity.START)
        lp.marginStart = scaleX(centerXDp) - labelWidth / 2
        lp.topMargin = topPx
        rootView.addView(view, lp)
        return lp
    }

    private fun bindActions() {
        cancelButton.setOnClickListener {
            dismiss()
            onCancel()
        }
        sendAudioButton.setOnClickListener {
            dismiss()
            onSendAudio(audioPath, audioDurationSecond.coerceAtLeast(1))
        }
        sendTextButton.isEnabled = state == TranscriptionState.TEXT
        sendTextButton.setOnClickListener {
            val currentText = editText.text?.toString().orEmpty()
            if (currentText.isBlank()) {
                return@setOnClickListener
            }
            dismiss()
            onSendText(currentText)
        }
    }

    private fun applyTheme() {
        val c = colors
        backgroundView.setGradientSpec(AudioRecorderOverlayBackgroundPolicy.createGradientSpec(c.bgColorOperate))
        bubbleView.setBubbleColor(if (state == TranscriptionState.EMPTY) c.buttonColorHangupDefault else c.buttonColorPrimaryDefault)
        loadingDotsView.setDotColor(c.textColorButton)
        editText.setTextColor(c.textColorButton)
        setCircleBackground(cancelButton, c.buttonColorSecondaryDefault)
        setCircleBackground(sendAudioButton, c.buttonColorSecondaryDefault)
        setCircleBackground(sendTextButton, if (state == TranscriptionState.TEXT) c.buttonColorPrimaryDefault else c.buttonColorPrimaryDisabled)
        cancelIcon.setColorFilter(c.textColorPrimary)
        sendAudioIcon.setColorFilter(c.textColorPrimary)
        sendTextButton.setTextColor(c.textColorButton)
        cancelLabel.setTextColor(c.textColorTertiary)
        sendAudioLabel.setTextColor(c.textColorTertiary)
        waveformView.setBarColor(c.bgColorInput)
        waveformView.setContentColor(c.textColorPrimary)
    }

    private fun bindBubbleText(resultText: String?) {
        if (state == TranscriptionState.LOADING) {
            editText.visibility = View.GONE
            loadingDotsView.visibility = View.VISIBLE
            editText.setText("")
        } else {
            val displayText = when (state) {
                TranscriptionState.EMPTY -> context.getString(R.string.message_input_voice_transcription_empty)
                TranscriptionState.TEXT -> resultText.orEmpty()
                TranscriptionState.LOADING -> ""
                else -> ""
            }
            loadingDotsView.visibility = View.GONE
            editText.visibility = View.VISIBLE
            editText.setText(displayText)
            editText.setSelection(editText.text?.length ?: 0)
            editText.isEnabled = state == TranscriptionState.TEXT
        }
        sendTextButton.isEnabled = state == TranscriptionState.TEXT
    }

    private fun renderChipRow() {
        chipRow.removeAllViews()
        if (state != TranscriptionState.TEXT) {
            chipRow.visibility = View.GONE
            return
        }
        chipRow.visibility = View.VISIBLE
        when {
            translatedText == null -> {
                chipRow.addView(
                    buildChip(context.getString(R.string.voice_message_translate), enabled = !isTranslating) {
                        onTranslateChipClicked()
                    }
                )
            }
            else -> {
                chipRow.addView(
                    buildChip(context.getString(R.string.voice_message_undo_translate), enabled = true) {
                        onUndoTranslateClicked()
                    }
                )
                chipRow.addView(
                    buildChip(context.getString(R.string.voice_message_switch_language), enabled = !isTranslating) {
                        onSwitchLanguageClicked()
                    }
                )
                val speakLabelRes = if (isSpeaking) {
                    R.string.voice_message_stop
                } else {
                    R.string.voice_message_read_aloud
                }
                chipRow.addView(
                    buildChip(context.getString(speakLabelRes), enabled = true) {
                        onReadAloudClicked()
                    }
                )
            }
        }
    }

    private fun buildChip(label: String, enabled: Boolean, onClick: (() -> Unit)?): TextView {
        val c = colors
        val chip = TextView(context)
        chip.text = label
        chip.gravity = Gravity.CENTER
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        chip.setTextColor(if (enabled) c.textColorPrimary else c.textColorDisable)
        chip.setPaddingRelative(dp(12), dp(6), dp(12), dp(6))
        chip.background = GradientDrawable().apply {
            cornerRadius = 12f * density
            setColor(c.buttonColorSecondaryDefault)
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        lp.marginEnd = dp(8)
        chip.layoutParams = lp
        chip.isEnabled = enabled
        if (enabled && onClick != null) {
            chip.setOnClickListener { onClick() }
        }
        return chip
    }

    private fun onTranslateChipClicked() {
        if (isTranslating) return
        val lang = VoiceMessageConfig.getRecordTranslateTargetLanguage(context)
        if (lang.isEmpty()) {
            showLanguageSelector { picked ->
                VoiceMessageConfig.setRecordTranslateTargetLanguage(context, picked)
                translateWith(picked)
            }
        } else {
            translateWith(lang)
        }
    }

    private fun onSwitchLanguageClicked() {
        if (isTranslating) return
        showLanguageSelector { picked ->
            VoiceMessageConfig.setRecordTranslateTargetLanguage(context, picked)
            translateWith(picked)
        }
    }

    private fun onUndoTranslateClicked() {
        if (isSpeaking) {
            isSpeaking = false
            onStopSpeak()
        }
        translatedText = null
        setBubbleText(originalText)
        renderChipRow()
    }

    private fun onReadAloudClicked() {
        if (isSpeaking) {
            isSpeaking = false
            onStopSpeak()
            renderChipRow()
            return
        }
        val current = editText.text?.toString().orEmpty()
        if (current.isBlank()) return
        isSpeaking = true
        renderChipRow()
        onStartSpeak(
            current,
            {},
            {
                if (isDismissed) return@onStartSpeak
                if (isSpeaking) {
                    isSpeaking = false
                    renderChipRow()
                }
            },
            {
                if (isDismissed) return@onStartSpeak
                isSpeaking = false
                renderChipRow()
                AtomicToast.show(
                    context,
                    context.getString(R.string.voice_message_speak_failed),
                    style = AtomicToast.Style.ERROR,
                )
            },
        )
    }

    // Always translates from the immutable [originalText], never the current
    // translation, so switching languages does not compound translation loss.
    private fun translateWith(languageCode: String) {
        if (originalText.isEmpty() || isTranslating) return
        isTranslating = true
        renderChipRow()
        onTranslate(
            originalText,
            languageCode,
            { translated ->
                if (isDismissed) return@onTranslate
                isTranslating = false
                if (translated.isBlank()) {
                    renderChipRow()
                    AtomicToast.show(
                        context,
                        context.getString(R.string.voice_message_translate_failed),
                        style = AtomicToast.Style.ERROR,
                    )
                    return@onTranslate
                }
                translatedText = translated
                setBubbleText(translated)
                renderChipRow()
            },
            {
                if (isDismissed) return@onTranslate
                isTranslating = false
                renderChipRow()
                AtomicToast.show(
                    context,
                    context.getString(R.string.voice_message_translate_failed),
                    style = AtomicToast.Style.ERROR,
                )
            },
        )
    }

    private fun setBubbleText(value: String) {
        updateBubbleHeightForState(value)
        editText.setText(value)
        editText.setSelection(editText.text?.length ?: 0)
        sendTextButton.isEnabled = state == TranscriptionState.TEXT && value.isNotBlank()
    }

    private fun showLanguageSelector(onPicked: (String) -> Unit) {
        val c = colors
        val currentLanguage = VoiceMessageConfig.getRecordTranslateTargetLanguage(context)
        val contentView = FrameLayout(context)
        val popupWindow = PopupWindow(
            contentView,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            true,
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isClippingEnabled = true
            isTouchable = true
            isOutsideTouchable = true
            setOnDismissListener {
                if (languagePopupWindow === this) languagePopupWindow = null
            }
        }
        languagePopupWindow?.let { if (it.isShowing) it.dismiss() }
        languagePopupWindow = popupWindow

        val hostDirection = rootView.layoutDirection
        contentView.layoutDirection = hostDirection

        val maskView = View(context).apply {
            setBackgroundColor(c.bgColorMask)
            setOnClickListener { popupWindow.dismiss() }
        }
        contentView.addView(
            maskView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val panelBackgroundColor = c.bgColorOperate
        val panel = LinearLayout(context).apply {
            layoutDirection = hostDirection
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    12f * density, 12f * density,
                    12f * density, 12f * density,
                    0f, 0f,
                    0f, 0f,
                )
                setColor(panelBackgroundColor)
            }
        }
        val maxPanelHeight = (context.resources.displayMetrics.heightPixels * 0.6f).toInt()
        contentView.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                maxPanelHeight,
                Gravity.BOTTOM,
            ),
        )

        val titleView = TextView(context).apply {
            text = context.getString(R.string.voice_message_select_language)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(c.textColorSecondary)
            setPaddingRelative(0, dp(16), 0, dp(16))
        }
        panel.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        TRANSLATE_LANGUAGE_OPTIONS.forEach { option ->
            val isSelected = option.code == currentLanguage
            val itemView = TextView(context).apply {
                text = option.nativeName
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(if (isSelected) c.textColorLink else c.textColorPrimary)
                setPaddingRelative(dp(16), dp(14), dp(16), dp(14))
                setOnClickListener {
                    popupWindow.dismiss()
                    onPicked(option.code)
                }
            }
            listContainer.addView(
                itemView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val scrollView = ScrollView(context).apply {
            addView(
                listContainer,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        if (isDismissed || !anchorView.isAttachedToWindow) {
            languagePopupWindow = null
            return
        }
        try {
            popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, 0, 0)
        } catch (exception: Exception) {
            languagePopupWindow = null
        }
    }

    private fun installKeyboardTracking() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            updateKeyboardHeight(KeyboardInsetsUtil.toPanelSpacerHeight(imeBottom, insets))
            insets
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            rootView,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: List<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    updateKeyboardHeight(KeyboardInsetsUtil.toPanelSpacerHeight(imeBottom, insets))
                    return insets
                }
            }
        )
        startLegacyKeyboardProbeIfNeeded()
    }

    private fun startLegacyKeyboardProbeIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || legacyKeyboardProbe != null) {
            return
        }
        val activity = findActivity(context) ?: return
        val minKeyboardHeight = (80 * density).toInt()
        legacyKeyboardProbe = LegacyKeyboardHeightProbe(
            activity = activity,
            minKeyboardHeight = minKeyboardHeight,
            navigationBarBottomProvider = {
                KeyboardInsetsUtil.getNavigationBarBottom(activity, rootView)
            },
            listener = object : LegacyKeyboardHeightProbe.Listener {
                override fun onProbeHeightChanged(height: Int, visible: Boolean) {
                    updateKeyboardHeight(if (visible) height else 0)
                }
            }
        ).also { probe ->
            probe.start(rootView)
        }
    }

    private fun cleanupKeyboardTracking() {
        rootView.removeOnLayoutChangeListener(rootLayoutChangeListener)
        ViewCompat.setOnApplyWindowInsetsListener(rootView, null)
        ViewCompat.setWindowInsetsAnimationCallback(rootView, null)
        legacyKeyboardProbe?.stop()
        legacyKeyboardProbe = null
        keyboardHeightPx = 0
    }

    private fun updateKeyboardHeight(height: Int) {
        val nextHeight = height.coerceAtLeast(0)
        if (keyboardHeightPx == nextHeight) {
            return
        }
        keyboardHeightPx = nextHeight
        updateOverlayLayout()
    }

    private fun updateOverlayLayout() {
        if (bubbleHeightPx <= 0) return
        val rootHeight = rootView.height.takeIf { it > 0 }
            ?: context.resources.displayMetrics.heightPixels
        val layout = calculateOverlayLayout(
            rootHeight = rootHeight,
            bubbleHeight = bubbleHeightPx,
            keyboardHeight = keyboardHeightPx,
            showChipRow = state == TranscriptionState.TEXT,
        )
        updateTopMargin(bubbleView, bubbleLayoutParams, layout.bubbleTop)
        updateTopMargin(chipRow, chipRowLayoutParams, layout.chipRowTop)
        updateTopMargin(cancelButton, cancelButtonLayoutParams, layout.cancelButtonTop)
        updateTopMargin(sendAudioButton, sendAudioButtonLayoutParams, layout.sendAudioButtonTop)
        updateTopMargin(sendTextButton, sendTextButtonLayoutParams, layout.sendTextButtonTop)
        updateTopMargin(cancelLabel, cancelLabelLayoutParams, layout.labelTop)
        updateTopMargin(sendAudioLabel, sendAudioLabelLayoutParams, layout.labelTop)
        updateTopMargin(waveformView, waveformLayoutParams, layout.waveformTop)
    }

    private fun updateTopMargin(view: View, lp: FrameLayout.LayoutParams?, topMargin: Int) {
        if (lp == null || lp.topMargin == topMargin) {
            return
        }
        lp.topMargin = topMargin
        view.layoutParams = lp
    }

    private fun updateBubbleHeightForState(resultText: String?) {
        val bubbleHeight = if (state == TranscriptionState.TEXT && (resultText?.length ?: 0) > 24) 104 else 68
        val nextHeightPx = (bubbleHeight * density).toInt()
        if (nextHeightPx == bubbleHeightPx) {
            return
        }
        bubbleHeightPx = nextHeightPx
        bubbleLayoutParams?.let { lp ->
            lp.height = nextHeightPx
            bubbleView.layoutParams = lp
        }
        editTextLayoutParams?.let { lp ->
            lp.height = bubbleContentHeightPx()
            editText.layoutParams = lp
        }
        updateLoadingDotsPosition()
        updateOverlayLayout()
    }

    private fun calculateOverlayLayout(
        rootHeight: Int,
        bubbleHeight: Int,
        keyboardHeight: Int,
        showChipRow: Boolean,
    ): OverlayLayout {
        val safeRootHeight = rootHeight.coerceAtLeast(1)
        val iconButtonSize = dp(48)
        val sendTextButtonSize = dp(80)
        val labelHeight = dp(20)
        val waveformHeight = dp(40)
        val waveformBottom = dp(41)
        val labelToWaveformGap = dp(26)
        val iconToLabelGap = dp(4)
        val sendTextToWaveformGap = dp(24)
        val bubbleToActionGap = dp(12)
        val bubbleToKeyboardGap = dp(24)
        val bubbleToChipGap = if (showChipRow) dp(8) else 0
        // The chip row sits between the bubble and the action row, so the
        // bubble must leave room for both the chip row and its gap.
        val chipBlock = if (showChipRow) dp(CHIP_ROW_HEIGHT_DP) + bubbleToChipGap else 0

        val waveformTop = (safeRootHeight - waveformBottom - waveformHeight).coerceAtLeast(0)
        val labelTop = (waveformTop - labelToWaveformGap - labelHeight).coerceAtLeast(0)
        val iconButtonTop = (labelTop - iconToLabelGap - iconButtonSize).coerceAtLeast(0)
        val sendTextButtonTop = (waveformTop - sendTextToWaveformGap - sendTextButtonSize).coerceAtLeast(0)
        val firstActionTop = minOf(iconButtonTop, sendTextButtonTop)

        val designBubbleTop = dp(549)
        val maxBubbleTopBeforeActions =
            (firstActionTop - bubbleToActionGap - chipBlock - bubbleHeight).coerceAtLeast(0)
        val maxBubbleTopBeforeKeyboard = if (keyboardHeight > 0) {
            (safeRootHeight - keyboardHeight - bubbleToKeyboardGap - chipBlock - bubbleHeight).coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }

        val bubbleTop = minOf(designBubbleTop, maxBubbleTopBeforeActions, maxBubbleTopBeforeKeyboard)
        return OverlayLayout(
            bubbleTop = bubbleTop,
            chipRowTop = bubbleTop + bubbleHeight + bubbleToChipGap,
            cancelButtonTop = iconButtonTop,
            sendAudioButtonTop = iconButtonTop,
            sendTextButtonTop = sendTextButtonTop,
            labelTop = labelTop,
            waveformTop = waveformTop,
        )
    }

    // Aligns the bubble's pointer with the horizontal center of the send button.
    // The bubble uses a fixed density-based width while the send button is
    // positioned proportionally via scaleX, so the pointer must be derived from
    // the button's actual center to stay aligned across screen widths.
    // Positions use marginStart + Gravity.START so RTL mirrors automatically;
    // arrow offset is converted into bubble-local coordinates.
    private fun alignBubbleArrowToSendButton() {
        val sendButtonStart = sendTextButtonLayoutParams?.marginStart ?: scaleX(262)
        val sendButtonWidth = sendTextButtonLayoutParams?.width ?: dp(80)
        val bubbleStart = bubbleLayoutParams?.marginStart ?: scaleX(16)
        val bubbleWidth = bubbleLayoutParams?.width ?: (330 * density).toInt()
        val arrowCenterX = if (isRtl()) {
            bubbleStart + bubbleWidth - sendButtonStart - sendButtonWidth / 2f
        } else {
            sendButtonStart + sendButtonWidth / 2f - bubbleStart
        }
        bubbleView.setArrowCenterX(arrowCenterX)
    }

    private fun isRtl(): Boolean {
        return rootView.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    private fun updateLoadingDotsPosition() {
        val lp = loadingDotsView.layoutParams as? FrameLayout.LayoutParams ?: return
        val nextTop = ((bubbleContentHeightPx() - lp.height) / 2).coerceAtLeast(0)
        if (lp.topMargin != nextTop) {
            lp.topMargin = nextTop
            loadingDotsView.layoutParams = lp
        }
    }

    private fun bubbleContentHeightPx(): Int {
        return (bubbleHeightPx - dp(BUBBLE_TRIANGLE_HEIGHT_DP)).coerceAtLeast(0)
    }

    private fun dp(value: Int): Int {
        return (value * density).toInt()
    }

    private fun scaleX(valueDp: Int): Int {
        val screenWidth = context.resources.displayMetrics.widthPixels
        return (valueDp * screenWidth / DESIGN_WIDTH_DP).toInt()
    }

    private fun setCircleBackground(view: View, color: Int) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun findActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return currentContext as? Activity
    }

    private enum class TranscriptionState {
        LOADING,
        EMPTY,
        TEXT,
    }

    private companion object {
        private const val DESIGN_WIDTH_DP = 390f
        private const val BUBBLE_TRIANGLE_HEIGHT_DP = 14
        private const val CHIP_ROW_HEIGHT_DP = 32
        private val TRANSLATE_LANGUAGE_OPTIONS = listOf(
            RecordTranslateLanguage("zh", "简体中文"),
            RecordTranslateLanguage("zh-TW", "繁體中文"),
            RecordTranslateLanguage("en", "English"),
            RecordTranslateLanguage("ja", "日本語"),
            RecordTranslateLanguage("ko", "한국어"),
            RecordTranslateLanguage("fr", "Français"),
            RecordTranslateLanguage("es", "Español"),
            RecordTranslateLanguage("it", "Italiano"),
            RecordTranslateLanguage("de", "Deutsch"),
            RecordTranslateLanguage("tr", "Türkçe"),
            RecordTranslateLanguage("ru", "Русский"),
            RecordTranslateLanguage("pt", "Português"),
            RecordTranslateLanguage("vi", "Tiếng Việt"),
            RecordTranslateLanguage("id", "Bahasa Indonesia"),
            RecordTranslateLanguage("th", "ภาษาไทย"),
            RecordTranslateLanguage("ms", "Bahasa Melayu"),
            RecordTranslateLanguage("hi", "हिन्दी"),
        )
    }

    private data class RecordTranslateLanguage(val code: String, val nativeName: String)

    private data class OverlayLayout(
        val bubbleTop: Int,
        val chipRowTop: Int,
        val cancelButtonTop: Int,
        val sendAudioButtonTop: Int,
        val sendTextButtonTop: Int,
        val labelTop: Int,
        val waveformTop: Int,
    )
}

private class TranscriptionBubbleView(context: Context) : FrameLayout(context) {
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private var bubbleColor = Color.TRANSPARENT
    private var arrowCenterX: Float? = null

    init {
        setWillNotDraw(false)
    }

    fun setBubbleColor(color: Int) {
        bubbleColor = color
        invalidate()
    }

    fun setArrowCenterX(x: Float) {
        arrowCenterX = x
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val triangleHeight = 14f * density
        val bodyBottom = height - triangleHeight
        val radius = 8f * density
        val halfArrowWidth = 12f * density
        path.reset()
        path.addRoundRect(RectF(0f, 0f, width.toFloat(), bodyBottom), radius, radius, Path.Direction.CW)
        val centerX = (arrowCenterX ?: if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            64f * density
        } else {
            width - 64f * density
        }).coerceIn(radius + halfArrowWidth, width - radius - halfArrowWidth)
        path.moveTo(centerX - halfArrowWidth, bodyBottom)
        path.lineTo(centerX, height.toFloat())
        path.lineTo(centerX + halfArrowWidth, bodyBottom)
        path.close()
        paint.color = bubbleColor
        canvas.drawPath(path, paint)
        super.onDraw(canvas)
    }
}

private class LoadingDotsView(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private var animator: ValueAnimator? = null
    private var progress = 0f

    fun setDotColor(color: Int) {
        paint.color = color
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                progress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = 3f * density
        val gap = 10f * density
        val startX = radius
        val centerY = height / 2f
        for (index in 0 until 3) {
            val phase = (progress + index * 0.18f) % 1f
            val alpha = (90 + 165 * abs(sin(phase * Math.PI))).toInt().coerceIn(90, 255)
            paint.alpha = alpha
            canvas.drawCircle(startX + index * gap, centerY, radius, paint)
        }
        paint.alpha = 255
    }
}

private class OverlayWaveformBarView(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private var animator: ValueAnimator? = null
    private val startTimeNanos = System.nanoTime()
    private var durationText = "00:00"
    private val durationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 160
        textAlign = Paint.Align.RIGHT
        textSize = 12f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun setBarColor(color: Int) {
        barPaint.color = color
        invalidate()
    }

    fun setContentColor(color: Int) {
        dotPaint.color = color
        durationPaint.color = color
        invalidate()
    }

    fun setDurationSecond(durationSecond: Int) {
        durationText = ChatDateTimeUtils.formatDurationSeconds(durationSecond.toLong())
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = 12f * density
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, barPaint)
        val dotRadius = 1.5f * density
        val gap = 8f * density
        val count = 24
        val totalWidth = (count - 1) * gap
        val startX = ((width - totalWidth) / 2f) - 18f * density
        val centerY = height / 2f
        val t = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0
        for (i in 0 until count) {
            val alpha = (120 + 80 * abs(sin(t * 3.0 + i * 0.35))).toInt().coerceIn(0, 255)
            dotPaint.alpha = alpha
            canvas.drawCircle(startX + i * gap, centerY, dotRadius, dotPaint)
        }
        dotPaint.alpha = 255
        val textY = centerY - (durationPaint.descent() + durationPaint.ascent()) / 2f
        val padding = 16f * density
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            durationPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(durationText, padding, textY, durationPaint)
        } else {
            durationPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(durationText, width - padding, textY, durationPaint)
        }
    }
}
