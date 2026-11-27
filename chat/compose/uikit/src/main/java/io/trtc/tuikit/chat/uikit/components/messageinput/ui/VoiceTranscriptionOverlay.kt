package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.ai.tts.VoiceMessageConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.keyboard.KeyboardInsetsUtil
import io.trtc.tuikit.chat.uikit.components.messageinput.keyboard.LegacyKeyboardHeightProbe
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.DateTimeUtils
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlin.math.abs
import kotlin.math.sin

internal enum class TranscriptionState {
    LOADING,
    EMPTY,
    TEXT,
}

internal data class VoiceTranscriptionDraft(
    val audioPath: String,
    val audioDurationSecond: Int,
    val text: String? = null,
    // Distinguishes "transcription still running" from "transcription came back with
    // nothing". A failed or empty result also carries a null text, so without this flag
    // the overlay cannot leave the loading state.
    val hasResult: Boolean = false,
)

private data class RecordTranslateLanguage(val code: String, val nativeName: String)

// Insets for the transcription overlay, all sourced from one manual listener.
private data class VoiceTranscriptionInsets(
    // Keyboard height above the navigation bar, i.e. measured inside the display frame.
    val keyboardHeight: Int = 0,
    val statusBarTop: Int = 0,
    val navigationBarBottom: Int = 0,
)

private const val DESIGN_WIDTH_DP = 390f
private const val BUBBLE_TRIANGLE_HEIGHT_DP = 14

private val TRANSLATE_LANGUAGE_OPTIONS = listOf(
    RecordTranslateLanguage("zh", "简体中文"),
    RecordTranslateLanguage("zh-TR", "繁體中文"),
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

@Composable
internal fun VoiceTranscriptionOverlay(
    draft: VoiceTranscriptionDraft?,
    onCancel: () -> Unit,
    onSendAudio: (String, Int) -> Unit,
    onSendText: (String) -> Unit,
    onTranslate: (String, String, (String) -> Unit, () -> Unit) -> Unit,
    onStartSpeak: (String, () -> Unit, () -> Unit, () -> Unit) -> Unit,
    onStopSpeak: () -> Unit,
) {
    if (draft == null) return
    val context = LocalContext.current
    val colors = LocalTheme.current.colors
    val state = when {
        !draft.hasResult -> TranscriptionState.LOADING
        draft.text.isNullOrBlank() -> TranscriptionState.EMPTY
        else -> TranscriptionState.TEXT
    }
    // The immutable first transcription text. All translations use this as the source so
    // switching languages never translates a prior translation.
    val originalText = if (state == TranscriptionState.TEXT) draft.text.orEmpty() else ""
    // Keying the editable text on the result seeds the field in the same composition the
    // result lands in, so the bubble never shows an empty frame between loading and text.
    var editFieldValue by remember(draft.audioPath, originalText) {
        mutableStateOf(TextFieldValue(originalText, TextRange(originalText.length)))
    }
    var translatedText by remember(draft.audioPath, originalText) { mutableStateOf<String?>(null) }
    var isTranslating by remember(draft.audioPath, originalText) { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isDismissed by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    LaunchedEffect(draft.audioPath, originalText) {
        if (originalText.isNotEmpty() && isSpeaking) {
            isSpeaking = false
            onStopSpeak()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isDismissed = true
            if (isSpeaking) {
                onStopSpeak()
            }
        }
    }

    // Always translates from the immutable originalText, never the current translation, so
    // switching languages does not compound translation loss.
    fun translateWith(languageCode: String) {
        if (originalText.isEmpty() || isTranslating) return
        isTranslating = true
        onTranslate(
            originalText,
            languageCode,
            { translated ->
                if (isDismissed) return@onTranslate
                isTranslating = false
                if (translated.isBlank()) {
                    Toast.error(context, context.getString(R.string.voice_message_translate_failed))
                    return@onTranslate
                }
                VoiceMessageConfig.setRecordTranslateTargetLanguage(context, languageCode)
                translatedText = translated
                editFieldValue = TextFieldValue(translated, TextRange(translated.length))
            },
            {
                if (isDismissed) return@onTranslate
                isTranslating = false
                Toast.error(context, context.getString(R.string.voice_message_translate_failed))
            }
        )
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val density = LocalDensity.current
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                val current = window.attributes.softInputMode
                val adjusted = (current and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                if (current != adjusted) {
                    window.setSoftInputMode(adjusted)
                }
            }
        }
        val insets = rememberVoiceTranscriptionInsets()
        val operate = colors.bgColorOperate
        val designScale = LocalConfiguration.current.screenWidthDp / DESIGN_WIDTH_DP
        fun scaledDp(valueDp: Int): Dp = (valueDp * designScale).dp
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to operate.copy(alpha = 0f),
                            0.42f to operate.copy(alpha = 0.7f),
                            0.67f to operate,
                            1f to operate
                        )
                    )
                )
        ) {
            val showChipRow = state == TranscriptionState.TEXT
            val bubbleHeight = if (showChipRow && editFieldValue.text.length > 24) 104.dp else 68.dp
            val fallbackHeight = LocalContext.current.resources.displayMetrics.heightPixels
            val windowHeightPx = constraints.maxHeight.takeIf { it > 0 } ?: fallbackHeight
            // The View overlay runs in a PopupWindow whose content is capped at the visible
            // display frame, so its 549dp bubble top and 41dp waveform bottom are measured
            // between the system bars. This dialog spans the whole window, so the bars have to
            // be taken out here too, otherwise the bubble sits a status bar too high while the
            // action row drops a navigation bar too low and the gap between them grows by both.
            // The keyboard height already excludes the navigation bar, so it shares this space.
            val rootHeightPx = (windowHeightPx - insets.statusBarTop - insets.navigationBarBottom)
                .coerceAtLeast(1)
            val layout = VoiceTranscriptionOverlayLayoutPolicy.calculate(
                rootHeightPx = rootHeightPx,
                bubbleHeightPx = with(density) { bubbleHeight.roundToPx() },
                keyboardHeightPx = insets.keyboardHeight,
                showChipRow = showChipRow,
                density = density.density,
            ).offsetBy(insets.statusBarTop)
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val bubbleWidth = 330.dp
            val bubbleMarginStart = scaledDp(16)
            val cancelButtonStart = scaledDp(48)
            val sendAudioButtonStart = scaledDp(142)
            val sendTextButtonStart = scaledDp(262)
            // Aligns the bubble's pointer with the horizontal center of the send button.
            // Positions are start-based so RTL mirrors automatically; the arrow offset is in
            // bubble-local coordinates.
            val arrowCenterX = if (isRtl) {
                bubbleMarginStart + bubbleWidth - (sendTextButtonStart + 40.dp)
            } else {
                sendTextButtonStart + 40.dp - bubbleMarginStart
            }
            val bubbleColor = if (state == TranscriptionState.EMPTY) {
                colors.buttonColorHangupDefault
            } else {
                colors.buttonColorPrimaryDefault
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = bubbleMarginStart)
                    .offset { IntOffset(0, layout.bubbleTop) }
            ) {
                Box(
                    modifier = Modifier
                        .width(bubbleWidth)
                        .height(bubbleHeight - BUBBLE_TRIANGLE_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bubbleColor),
                    contentAlignment = Alignment.CenterStart
                ) {
                    when (state) {
                        TranscriptionState.LOADING -> LoadingDots(
                            color = colors.textColorButton,
                            modifier = Modifier.padding(start = 20.dp)
                        )
                        TranscriptionState.EMPTY -> Text(
                            text = stringResource(R.string.message_input_voice_transcription_empty),
                            color = colors.textColorButton,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        TranscriptionState.TEXT -> BasicTextField(
                            value = editFieldValue,
                            onValueChange = { editFieldValue = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            textStyle = TextStyle(color = colors.textColorButton, fontSize = 16.sp),
                            cursorBrush = SolidColor(colors.textColorButton),
                            maxLines = 4
                        )
                    }
                }
                Canvas(
                    modifier = Modifier
                        .width(bubbleWidth)
                        .height(BUBBLE_TRIANGLE_HEIGHT_DP.dp)
                ) {
                    val halfArrowWidth = 12.dp.toPx()
                    val cornerRadius = 8.dp.toPx()
                    val centerX = arrowCenterX.toPx()
                        .coerceIn(cornerRadius + halfArrowWidth, size.width - cornerRadius - halfArrowWidth)
                    val path = Path().apply {
                        moveTo(centerX - halfArrowWidth, 0f)
                        lineTo(centerX, size.height)
                        lineTo(centerX + halfArrowWidth, 0f)
                        close()
                    }
                    drawPath(path, color = bubbleColor)
                }
            }

            if (showChipRow) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = bubbleMarginStart)
                        .offset { IntOffset(0, layout.chipRowTop) },
                    horizontalArrangement = Arrangement.Start
                ) {
                        if (translatedText == null) {
                            OverlayChip(
                                label = stringResource(R.string.voice_message_translate),
                                enabled = !isTranslating,
                                onClick = {
                                    val lang = VoiceMessageConfig.getRecordTranslateTargetLanguage(context)
                                    if (lang.isEmpty()) {
                                        showLanguagePicker = true
                                    } else {
                                        translateWith(lang)
                                    }
                                }
                            )
                            OverlayChip(
                                label = stringResource(R.string.voice_message_switch_language),
                                enabled = !isTranslating,
                                onClick = { showLanguagePicker = true }
                            )
                        } else {
                            OverlayChip(
                                label = stringResource(R.string.voice_message_undo_translate),
                                enabled = true,
                                onClick = {
                                    if (isSpeaking) {
                                        isSpeaking = false
                                        onStopSpeak()
                                    }
                                    translatedText = null
                                    editFieldValue = TextFieldValue(originalText, TextRange(originalText.length))
                                }
                            )
                            OverlayChip(
                                label = stringResource(R.string.voice_message_switch_language),
                                enabled = !isTranslating,
                                onClick = { showLanguagePicker = true }
                            )
                            OverlayChip(
                                label = stringResource(
                                    if (isSpeaking) R.string.voice_message_stop else R.string.voice_message_read_aloud
                                ),
                                enabled = true,
                                onClick = {
                                    if (isSpeaking) {
                                        isSpeaking = false
                                        onStopSpeak()
                                        return@OverlayChip
                                    }
                                    val currentText = editFieldValue.text
                                    if (currentText.isBlank()) return@OverlayChip
                                    isSpeaking = true
                                    onStartSpeak(
                                        currentText,
                                        {},
                                        {
                                            if (!isDismissed) {
                                                isSpeaking = false
                                            }
                                        },
                                        {
                                            if (isDismissed) return@onStartSpeak
                                            isSpeaking = false
                                            Toast.error(
                                                context,
                                                context.getString(R.string.voice_message_speak_failed)
                                            )
                                        }
                                    )
                                }
                            )
                        }
                }
            }

            OverlayCircleButton(
                iconRes = R.drawable.message_input_voice_close_icon,
                label = stringResource(R.string.message_input_cancel),
                background = colors.buttonColorSecondaryDefault,
                iconTint = colors.textColorPrimary,
                size = 48.dp,
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = cancelButtonStart)
                    .offset { IntOffset(0, layout.cancelButtonTop) }
            )
            OverlayTextCircleButton(
                text = stringResource(R.string.message_input_send),
                enabled = state == TranscriptionState.TEXT,
                enabledColor = colors.buttonColorPrimaryDefault,
                disabledColor = colors.buttonColorPrimaryDisabled,
                textColor = colors.textColorButton,
                onClick = {
                    val currentText = editFieldValue.text
                    if (currentText.isNotBlank()) {
                        onCancel()
                        onSendText(currentText)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = sendTextButtonStart)
                    .offset { IntOffset(0, layout.sendTextButtonTop) }
            )
            OverlayCircleButton(
                iconRes = R.drawable.message_input_voice_transcribe_icon,
                label = stringResource(R.string.message_input_send_original_voice),
                background = colors.buttonColorSecondaryDefault,
                iconTint = colors.textColorPrimary,
                size = 48.dp,
                onClick = {
                    val audioPath = draft.audioPath
                    val audioDurationSecond = draft.audioDurationSecond.coerceAtLeast(1)
                    onCancel()
                    onSendAudio(audioPath, audioDurationSecond)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = sendAudioButtonStart)
                    .offset { IntOffset(0, layout.sendAudioButtonTop) }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = scaledDp(72) - 48.dp)
                    .offset { IntOffset(0, layout.labelTop) }
                    .width(96.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = stringResource(R.string.message_input_cancel),
                    color = colors.textColorTertiary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.wrapContentWidth(unbounded = true)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = scaledDp(166) - 48.dp)
                    .offset { IntOffset(0, layout.labelTop) }
                    .width(96.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = stringResource(R.string.message_input_send_original_voice),
                    color = colors.textColorTertiary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.wrapContentWidth(unbounded = true)
                )
            }

            OverlayWaveformBar(
                durationSecond = draft.audioDurationSecond,
                barColor = colors.bgColorInput,
                contentColor = colors.textColorPrimary,
                modifier = Modifier
                    .offset { IntOffset(0, layout.waveformTop) }
                    .fillMaxWidth()
                    .height(VoiceTranscriptionOverlayLayoutPolicy.WAVEFORM_HEIGHT_DP.dp)
                    .padding(horizontal = bubbleMarginStart)
            )
        }
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            currentLanguage = VoiceMessageConfig.getRecordTranslateTargetLanguage(context),
            onDismiss = { showLanguagePicker = false },
            onPicked = { picked ->
                showLanguagePicker = false
                translateWith(picked)
            }
        )
    }
}

@Composable
private fun rememberVoiceTranscriptionInsets(): VoiceTranscriptionInsets {
    val density = LocalDensity.current
    val view = LocalView.current
    val dialogWindow = remember(view) {
        var current = view.parent
        while (current != null && current !is DialogWindowProvider) {
            current = current.parent
        }
        (current as? DialogWindowProvider)?.window
    }
    // The manual listener installed below replaces the one Compose's WindowInsetsHolder puts on
    // this view, which freezes WindowInsets.* at the value the holder was seeded with - zero
    // while the dialog view is still detached. Every inset must therefore come from this same
    // manual source; the Compose values only serve as a fallback until the first dispatch.
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val statusTop = WindowInsets.statusBars.getTop(density)

    var primary by remember { mutableStateOf<VoiceTranscriptionInsets?>(null) }

    DisposableEffect(view, dialogWindow) {
        val applyHeight: (Int) -> Unit = { height ->
            primary = (primary ?: VoiceTranscriptionInsets())
                .copy(keyboardHeight = height.coerceAtLeast(0))
        }
        val applyInsets: (WindowInsetsCompat) -> Unit = { insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            primary = VoiceTranscriptionInsets(
                keyboardHeight = KeyboardInsetsUtil.toPanelSpacerHeight(ime, nav),
                statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                navigationBarBottom = nav,
            )
        }
        // Seed the bars synchronously: the layout needs them on the very first frame, and the
        // listener below only fires on the next insets dispatch.
        ViewCompat.getRootWindowInsets(view)?.let(applyInsets)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            applyInsets(insets)
            ViewCompat.onApplyWindowInsets(v, insets)
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            view,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: List<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    applyInsets(insets)
                    return insets
                }
            }
        )
        ViewCompat.requestApplyInsets(view)

        val probeContext = dialogWindow?.context
        val probe = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && probeContext != null) {
            val minKeyboardHeight = with(density) { 80.dp.roundToPx() }
            LegacyKeyboardHeightProbe(
                context = probeContext,
                minKeyboardHeight = minKeyboardHeight,
                navigationBarBottomProvider = {
                    val host = probeContext.findActivityOrNull()
                    if (host != null) {
                        KeyboardInsetsUtil.getNavigationBarBottom(host, view)
                    } else {
                        ViewCompat.getRootWindowInsets(view)
                            ?.getInsets(WindowInsetsCompat.Type.navigationBars())
                            ?.bottom
                            ?: 0
                    }
                },
                listener = object : LegacyKeyboardHeightProbe.Listener {
                    override fun onProbeHeightChanged(height: Int, visible: Boolean) {
                        applyHeight(if (visible) height else 0)
                    }
                }
            ).also { it.start(view) }
        } else {
            null
        }

        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
            ViewCompat.setWindowInsetsAnimationCallback(view, null)
            probe?.stop()
        }
    }

    return primary ?: VoiceTranscriptionInsets(
        keyboardHeight = KeyboardInsetsUtil.toPanelSpacerHeight(imeBottom, navBottom),
        statusBarTop = statusTop,
        navigationBarBottom = navBottom,
    )
}

private fun Context.findActivityOrNull(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return current as? Activity
}

@Composable
private fun OverlayWaveformBar(
    durationSecond: Int,
    barColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var startNanos = 0L
        while (true) {
            val nanos = withInfiniteAnimationFrameNanos { it }
            if (startNanos == 0L) {
                startNanos = nanos
            }
            t = ((nanos - startNanos) / 1_000_000_000.0).toFloat()
        }
    }
    val durationText = DateTimeUtils.formatDurationSeconds(durationSecond.toLong())
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(barColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val phase = t.toDouble()
            val dotRadius = 1.5.dp.toPx()
            val gap = 8.dp.toPx()
            val count = 24
            val totalWidth = (count - 1) * gap
            val startX = ((size.width - totalWidth) / 2f) - 18.dp.toPx()
            val centerY = size.height / 2f
            for (i in 0 until count) {
                val alpha = (120 + 80 * abs(sin(phase * 3.0 + i * 0.35))).toInt().coerceIn(0, 255)
                drawCircle(
                    color = contentColor.copy(alpha = alpha / 255f),
                    radius = dotRadius,
                    center = Offset(startX + i * gap, centerY)
                )
            }
        }
        Text(
            text = durationText,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun OverlayChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalTheme.current.colors
    Text(
        text = label,
        color = if (enabled) colors.textColorPrimary else colors.textColorDisable,
        fontSize = 12.sp,
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.buttonColorSecondaryDefault)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun OverlayCircleButton(
    iconRes: Int,
    label: String,
    background: Color,
    iconTint: Color,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun OverlayTextCircleButton(
    text: String,
    enabled: Boolean,
    enabledColor: Color,
    disabledColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(if (enabled) enabledColor else disabledColor)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LoadingDots(color: Color, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "dots")
    val progress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "dotsProgress"
    )
    Canvas(modifier = modifier.width(26.dp).height(20.dp)) {
        val radius = 3.dp.toPx()
        val gap = 10.dp.toPx()
        val centerY = size.height / 2f
        for (index in 0 until 3) {
            val phase = (progress + index * 0.18f) % 1f
            val alpha = (90 + 165 * abs(sin(phase * Math.PI))).toInt().coerceIn(90, 255)
            drawCircle(
                color = color.copy(alpha = alpha / 255f),
                radius = radius,
                center = Offset(radius + index * gap, centerY)
            )
        }
    }
}

@Composable
private fun LanguagePickerDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit
) {
    val colors = LocalTheme.current.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgColorMask)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(colors.bgColorOperate)
                    .clickable(enabled = false, onClick = {})
            ) {
                Text(
                    text = stringResource(R.string.voice_message_select_language),
                    color = colors.textColorSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    TRANSLATE_LANGUAGE_OPTIONS.forEach { option ->
                        val selected = option.code == currentLanguage
                        Text(
                            text = option.nativeName,
                            color = if (selected) colors.textColorLink else colors.textColorPrimary,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onPicked(option.code) }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }
}
