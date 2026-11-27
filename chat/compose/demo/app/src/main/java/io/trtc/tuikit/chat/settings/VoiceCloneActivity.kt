package io.trtc.tuikit.chat.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuicore.permission.PermissionCallback
import io.trtc.tuikit.chat.BaseActivity
import io.trtc.tuikit.chat.R
import io.trtc.tuikit.chat.uikit.components.ai.AiMediaProcessManager
import io.trtc.tuikit.chat.uikit.components.ai.tts.VoiceMessageConfig
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorder
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderListener
import io.trtc.tuikit.chat.uikit.components.audiorecorder.ResultCode
import io.trtc.tuikit.chat.uikit.components.common.ChatPermissionHelper
import io.trtc.tuikit.chat.uikit.components.common.SetActivitySystemBarAppearance
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin

private const val MIN_CLONE_MS = 3000
private const val MAX_CLONE_MS = 30000
private const val SUCCESS_DIALOG_AUTO_DISMISS_MS = 3000L

class VoiceCloneActivity : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, VoiceCloneActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SetActivitySystemBarAppearance()
            VoiceCloneScreen(onBackClick = { finish() }, onFinish = { finish() })
        }
    }
}

@Composable
private fun VoiceCloneScreen(onBackClick: () -> Unit, onFinish: () -> Unit) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current

    var recording by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var recordedPath by remember { mutableStateOf<String?>(null) }
    var completedDurationMs by remember { mutableStateOf(0) }
    var voiceNameInput by remember { mutableStateOf("") }
    var showTooShortDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val flowTimeMs by AudioRecorder.currentTimeMs.collectAsState()
    val powerLevel by AudioRecorder.currentPower.collectAsState()
    val displayDurationMs = if (recording) flowTimeMs else completedDurationMs

    val permissionDeniedTip = stringResource(R.string.compose_demo_voice_clone_permission_denied)
    val recordFailedTip = stringResource(R.string.compose_demo_voice_clone_record_failed)
    val emptyRecordTip = stringResource(R.string.compose_demo_voice_clone_empty_record)
    val cloneFailedTip = stringResource(R.string.compose_demo_voice_clone_failed)
    val defaultVoiceName = stringResource(R.string.compose_demo_voice_clone_default_name)

    DisposableEffect(Unit) {
        onDispose {
            if (recording) {
                AudioRecorder.cancelRecord()
            }
        }
    }

    if (showSuccessDialog) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(SUCCESS_DIALOG_AUTO_DISMISS_MS)
            showSuccessDialog = false
            onFinish()
        }
    }

    val recordListener = remember {
        object : AudioRecorderListener {
            override fun onCompleted(resultCode: ResultCode, path: String?, durationMs: Int) {
                recording = false
                stopping = false
                when (resultCode) {
                    ResultCode.ERROR_RECORD_PERMISSION_DENIED -> {
                        recordedPath = null
                        completedDurationMs = 0
                        Toast.error(context, permissionDeniedTip)
                    }

                    ResultCode.ERROR_CANCEL -> {
                        recordedPath = null
                        completedDurationMs = 0
                    }

                    ResultCode.ERROR_RECORDING,
                    ResultCode.ERROR_RECORD_INNER_FAIL,
                    ResultCode.ERROR_STORAGE_UNAVAILABLE -> {
                        recordedPath = null
                        completedDurationMs = 0
                        Toast.error(context, recordFailedTip)
                    }

                    else -> {
                        val success = resultCode == ResultCode.SUCCESS ||
                            resultCode == ResultCode.SUCCESS_EXCEED_MAX_DURATION
                        if (success && durationMs >= MIN_CLONE_MS && !path.isNullOrEmpty()) {
                            recordedPath = path
                            completedDurationMs = durationMs
                        } else {
                            recordedPath = null
                            completedDurationMs = 0
                            showTooShortDialog = true
                        }
                    }
                }
            }
        }
    }

    fun startRecording() {
        if (submitting || stopping || recording) return
        recordedPath = null
        completedDurationMs = 0
        recording = true
        AudioRecorder.startRecord(
            minDurationMs = MIN_CLONE_MS,
            maxDurationMs = MAX_CLONE_MS,
            listener = recordListener
        )
    }

    fun toggleRecord() {
        if (submitting || stopping) return
        if (recording) {
            stopping = true
            AudioRecorder.stopRecord()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecording()
        } else {
            ChatPermissionHelper.requestPermission(
                ChatPermissionHelper.PERMISSION_MICROPHONE,
                object : PermissionCallback() {
                    override fun onGranted() {
                        startRecording()
                    }

                    override fun onDenied() {
                        Toast.error(context, permissionDeniedTip)
                    }
                }
            )
        }
    }

    fun submit() {
        val path = recordedPath
        if (path.isNullOrEmpty()) {
            Toast.error(context, emptyRecordTip)
            return
        }
        if (submitting) return
        val voiceName = voiceNameInput.trim().ifEmpty { defaultVoiceName }
        submitting = true
        AiMediaProcessManager.voiceClone(
            filePath = path,
            voiceName = voiceName,
            onSuccess = { voiceId ->
                submitting = false
                VoiceMessageConfig.setSelectedVoice(context, voiceId, voiceName)
                showSuccessDialog = true
            },
            onFailure = { _, _ ->
                submitting = false
                Toast.error(context, cloneFailedTip)
            }
        )
    }

    val sampleText = "\u201C${stringResource(R.string.compose_demo_voice_clone_sample)}\u201D"
    val canSubmit = recordedPath != null && !submitting && !recording
    val totalSec = displayDurationMs / 1000
    val timerText = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
    val statusText = stringResource(
        when {
            recording -> R.string.compose_demo_voice_clone_stop
            recordedPath != null -> R.string.compose_demo_voice_clone_done
            else -> R.string.compose_demo_voice_clone_start
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorOperate)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
    ) {
        DemoPageHeader(
            title = stringResource(R.string.compose_demo_voice_clone),
            onBackClick = onBackClick
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.compose_demo_voice_clone_tip),
                fontSize = 13.sp,
                color = colors.textColorSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Text(
                text = stringResource(R.string.compose_demo_voice_clone_reading_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            )

            Text(
                text = sampleText,
                fontSize = 14.sp,
                color = colors.textColorPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgColorInput),
                contentAlignment = Alignment.Center
            ) {
                VoiceWaveform(
                    active = recording,
                    powerLevel = if (recording) powerLevel else 0,
                    barColor = colors.buttonColorPrimaryDefault,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = timerText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(80.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(if (recording) colors.textColorError else colors.buttonColorPrimaryDefault)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { toggleRecord() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        if (recording) R.drawable.demo_ic_voice_stop else R.drawable.demo_ic_voice_mic
                    ),
                    contentDescription = null,
                    tint = colors.textColorButton,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = statusText,
                fontSize = 13.sp,
                color = colors.textColorSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Text(
                text = stringResource(R.string.compose_demo_voice_clone_auth_tip),
                fontSize = 11.sp,
                color = colors.textColorTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.bgColorInput)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (voiceNameInput.isEmpty()) {
                    Text(
                        text = stringResource(R.string.compose_demo_voice_clone_name_hint),
                        fontSize = 15.sp,
                        color = colors.textColorTertiary
                    )
                }
                BasicTextField(
                    value = voiceNameInput,
                    onValueChange = { voiceNameInput = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = colors.textColorPrimary
                    ),
                    cursorBrush = SolidColor(colors.textColorLink),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (canSubmit) colors.buttonColorPrimaryDefault else colors.buttonColorPrimaryDisabled
                    )
                    .clickable(enabled = canSubmit) { submit() },
                contentAlignment = Alignment.Center
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colors.textColorButton,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.compose_demo_voice_clone_submit),
                        fontSize = 16.sp,
                        color = colors.textColorButton,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    VoiceCloneNoticeDialog(
        isVisible = showTooShortDialog,
        title = stringResource(R.string.compose_demo_voice_clone_too_short_title),
        message = stringResource(R.string.compose_demo_voice_clone_too_short_message),
        confirmText = stringResource(R.string.compose_demo_about_confirm),
        onDismiss = { showTooShortDialog = false }
    )

    VoiceCloneNoticeDialog(
        isVisible = showSuccessDialog,
        title = stringResource(R.string.compose_demo_voice_clone_success_title),
        message = stringResource(R.string.compose_demo_voice_clone_success_message),
        confirmText = stringResource(R.string.compose_demo_about_confirm),
        onDismiss = {
            showSuccessDialog = false
            onFinish()
        }
    )
}

@Composable
private fun VoiceCloneNoticeDialog(
    isVisible: Boolean,
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit
) {
    val colors = LocalTheme.current.colors
    if (!isVisible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = Modifier
                .width(327.dp)
                .heightIn(min = 134.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(color = colors.bgColorDialog),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W600,
                        color = colors.textColorPrimary,
                        lineHeight = 26.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.textColorPrimary,
                        lineHeight = 24.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .height(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = colors.strokeColorModule
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = confirmText,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W500,
                            color = colors.textColorLink,
                            lineHeight = 24.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceWaveform(
    active: Boolean,
    powerLevel: Int,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    var timeSeconds by remember { mutableStateOf(0.0) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var startNanos = -1L
        while (true) {
            withFrameNanos { frameNanos ->
                if (startNanos < 0) startNanos = frameNanos
                timeSeconds = (frameNanos - startNanos) / 1_000_000_000.0
            }
        }
    }

    Canvas(modifier = modifier) {
        val density = this.density
        val barWidth = 4f * density
        val barGap = 4f * density
        val barRadius = barWidth / 2f
        val minBarHeight = 4f * density
        val minWaveformWidth = 160f * density

        val targetWidth = maxOf(size.width * 0.72f, minWaveformWidth).coerceAtMost(size.width)
        val barCount = ceil((targetWidth + barGap) / (barWidth + barGap)).toInt().coerceAtLeast(1)
        val rng = java.util.Random(20260421L)
        val phases = FloatArray(barCount) { rng.nextFloat() * (Math.PI * 2).toFloat() }
        val freqs = FloatArray(barCount) { 2.4f + rng.nextFloat() * 3.2f }

        val totalWidth = barCount * barWidth + (barCount - 1) * barGap
        var startX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f
        val maxAmplitude = (size.height * 0.42f).coerceAtLeast(18f * density)
        val amplitude = maxAmplitude * (0.45f + (powerLevel.coerceIn(0, 100) / 100f) * 0.55f)

        for (i in 0 until barCount) {
            val wave = if (active) {
                abs(sin(timeSeconds * freqs[i].toDouble() + phases[i].toDouble())).toFloat()
            } else {
                0f
            }
            val barHeight = minBarHeight + amplitude * wave
            drawRoundRect(
                color = barColor,
                topLeft = Offset(startX, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barRadius, barRadius)
            )
            startX += barWidth + barGap
        }
    }
}
