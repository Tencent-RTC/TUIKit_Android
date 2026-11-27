package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuicore.permission.PermissionCallback
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorder
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderListener
import io.trtc.tuikit.chat.uikit.components.audiorecorder.ResultCode
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.ChatPermissionHelper
import kotlinx.coroutines.withTimeoutOrNull

internal class PressToTalkController(
    private val context: Context,
    private val minDurationMs: Int,
    private val maxDurationMs: Int,
    private val onSendAudio: (path: String, durationSecond: Int) -> Unit,
    private val onTranscribe: (path: String, durationSecond: Int) -> Unit,
) {
    var isRecording by mutableStateOf(false)
        private set
    var uiState by mutableStateOf(RecordUiState.IDLE)
        private set
    var bubbleAnchor by mutableStateOf<ScreenRect?>(null)

    private var cancelTarget = AudioRecorderGestureTarget(0f, 0f, 0f, 0f)
    private var transcribeTarget = AudioRecorderGestureTarget(0f, 0f, 0f, 0f)
    private var pendingReleaseAction = AudioRecorderReleaseAction.SEND_AUDIO
    private val mainHandler = Handler(Looper.getMainLooper())

    fun updateBubbleAnchor(rect: ScreenRect?) {
        bubbleAnchor = rect
    }

    fun updateCancelTarget(target: AudioRecorderGestureTarget) {
        cancelTarget = target
    }

    fun updateTranscribeTarget(target: AudioRecorderGestureTarget) {
        transcribeTarget = target
    }

    fun start(): Boolean {
        if (isRecording) return true
        if (!hasRecordPermission()) {
            requestRecordPermission()
            return false
        }
        pendingReleaseAction = AudioRecorderReleaseAction.SEND_AUDIO
        isRecording = true
        uiState = RecordUiState.RECORDING
        AudioRecorder.startRecord(
            enableAIDeNoise = false,
            minDurationMs = minDurationMs,
            maxDurationMs = maxDurationMs,
            listener = object : AudioRecorderListener {
                override fun onCompleted(resultCode: ResultCode, path: String?, durationMs: Int) {
                    val action = pendingReleaseAction
                    isRecording = false
                    uiState = RecordUiState.IDLE
                    pendingReleaseAction = AudioRecorderReleaseAction.SEND_AUDIO
                    runOnMain { handleResult(resultCode, path, durationMs, action) }
                }
            }
        )
        return true
    }

    fun updateDrag(screenX: Float, screenY: Float) {
        if (!isRecording) return
        val action = AudioRecorderGesturePolicy.decideReleaseAction(
            fingerX = screenX,
            fingerY = screenY,
            cancelTarget = cancelTarget,
            transcribeTarget = transcribeTarget,
        )
        pendingReleaseAction = action
        uiState = when (action) {
            AudioRecorderReleaseAction.CANCEL -> RecordUiState.READY_TO_CANCEL
            AudioRecorderReleaseAction.TRANSCRIBE -> RecordUiState.READY_TO_TRANSCRIBE
            AudioRecorderReleaseAction.SEND_AUDIO -> RecordUiState.RECORDING
        }
    }

    fun finish(screenX: Float, screenY: Float) {
        if (!isRecording) return
        pendingReleaseAction = AudioRecorderGesturePolicy.decideReleaseAction(
            fingerX = screenX,
            fingerY = screenY,
            cancelTarget = cancelTarget,
            transcribeTarget = transcribeTarget,
        )
        if (pendingReleaseAction == AudioRecorderReleaseAction.CANCEL) {
            cancel()
        } else {
            AudioRecorder.stopRecord()
        }
    }

    fun cancel() {
        if (!isRecording) return
        pendingReleaseAction = AudioRecorderReleaseAction.CANCEL
        isRecording = false
        uiState = RecordUiState.IDLE
        AudioRecorder.cancelRecord()
    }

    private fun handleResult(
        resultCode: ResultCode,
        path: String?,
        durationMs: Int,
        action: AudioRecorderReleaseAction
    ) {
        when (resultCode) {
            ResultCode.SUCCESS, ResultCode.SUCCESS_EXCEED_MAX_DURATION -> {
                if (resultCode == ResultCode.SUCCESS_EXCEED_MAX_DURATION) {
                    Toast.warning(context, context.getString(R.string.message_input_audio_time_limit_reached))
                }
                val audioPath = path ?: return
                val durationSecond = (durationMs / 1000).coerceAtLeast(1)
                when (action) {
                    AudioRecorderReleaseAction.SEND_AUDIO -> onSendAudio(audioPath, durationSecond)
                    AudioRecorderReleaseAction.TRANSCRIBE -> onTranscribe(audioPath, durationSecond)
                    AudioRecorderReleaseAction.CANCEL -> Unit
                }
            }

            ResultCode.ERROR_LESS_THAN_MIN_DURATION -> {
                Toast.warning(context, context.getString(R.string.message_input_audio_too_short))
            }

            ResultCode.ERROR_RECORD_PERMISSION_DENIED -> {
                Toast.warning(
                    context,
                    context.getString(R.string.message_input_record_audio_permission_settings_tip)
                )
            }

            ResultCode.ERROR_CANCEL -> Unit

            else -> {
                Toast.error(context, context.getString(R.string.message_input_send_failed))
            }
        }
    }

    private fun requestRecordPermission() {
        ChatPermissionHelper.requestPermission(
            ChatPermissionHelper.PERMISSION_MICROPHONE,
            object : PermissionCallback() {
                override fun onGranted() {
                    Toast.success(context, context.getString(R.string.message_input_press_to_talk))
                }

                override fun onDenied() {
                    Toast.warning(
                        context,
                        context.getString(R.string.message_input_record_audio_permission_settings_tip)
                    )
                }
            }
        )
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}

@Composable
internal fun PressToTalkBar(
    controller: PressToTalkController,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .onGloballyPositioned { controller.updateBubbleAnchor(it.toScreenRect()) }
            .longPressRecordGesture(
                enabled = { true },
                onRecordingStart = { controller.start() },
                onRecordingStop = { x, y -> controller.finish(x, y) },
                onDragPosition = { x, y -> controller.updateDrag(x, y) },
                onTapWithoutLongPress = onTap
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.message_input_press_to_talk),
            color = colors.textColorPrimary,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun Modifier.longPressRecordGesture(
    enabled: () -> Boolean,
    onRecordingStart: () -> Boolean,
    onRecordingStop: (Float, Float) -> Unit,
    onDragPosition: (Float, Float) -> Unit,
    onTapWithoutLongPress: (() -> Unit)? = null
): Modifier = composed {
    val viewConfiguration = LocalViewConfiguration.current
    val hapticFeedback = LocalHapticFeedback.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // The cancel / transcribe hit targets are reported in screen coordinates by the recorder
    // overlay, so the finger position must use the same origin. Window coordinates drift from
    // screen coordinates whenever the app window is inset (status bar, split screen), which
    // would offset the whole hit area.
    fun toScreen(local: Offset): Offset? {
        val coords = coordinates ?: return null
        val origin = coords.positionOnScreen()
        return Offset(origin.x + local.x, origin.y + local.y)
    }

    this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                if (!enabled()) return@awaitEachGesture
                val pointerId = down.id
                var lastScreen = toScreen(down.position)

                val endedBeforeTimeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                        lastScreen = toScreen(change.position) ?: lastScreen
                        if (change.isConsumed || !change.pressed) break
                    }
                }
                if (endedBeforeTimeout != null) {
                    onTapWithoutLongPress?.invoke()
                    return@awaitEachGesture
                }

                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                if (!onRecordingStart()) {
                    return@awaitEachGesture
                }
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                    lastScreen = toScreen(change.position) ?: lastScreen
                    lastScreen?.let { onDragPosition(it.x, it.y) }
                    if (!change.pressed || change.changedToUpIgnoreConsumed()) {
                        change.consume()
                        lastScreen?.let { onRecordingStop(it.x, it.y) }
                        break
                    }
                    change.consume()
                }
            }
        }
}
