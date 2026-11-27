package io.trtc.tuikit.chat.uikit.components.audiorecorder.audiorecorderimpl

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuicore.permission.PermissionCallback
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorder
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderListener
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderReleaseAction
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderResult
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderViewConfig
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderViewResultListener
import io.trtc.tuikit.chat.uikit.components.audiorecorder.ResultCode
import io.trtc.tuikit.chat.uikit.components.common.ChatPermissionHelper
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.AudioRecorderGesturePolicy
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.AudioRecorderGestureTarget
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.AudioRecorderOverlay
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.AudioRecorderReleaseAction as OverlayReleaseAction
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.RecordUiState
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.ScreenRect
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.toScreenRect
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.AudioTranscriber
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

@Composable
internal fun AudioRecorderViewImpl(
    modifier: Modifier = Modifier,
    config: AudioRecorderViewConfig,
    resultListener: AudioRecorderViewResultListener?,
    onCompleted: (path: String?, duration: Int) -> Unit,
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val controller = remember { AudioRecorderViewController(context) }
    SideEffect {
        controller.bind(config, resultListener, onCompleted)
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    val resolvedPrimary: Color = config.primaryColor?.takeIf { it.isNotBlank() }?.let { parseColorOrNull(it) }
        ?: colors.buttonColorPrimaryDefault

    Box(
        modifier = modifier
            .onGloballyPositioned { controller.updateBubbleAnchor(it.toScreenRect()) }
            .audioRecorderHoldGesture(
                startOnLongPress = config.startOnLongPress,
                onRecordingStart = { controller.start() },
                onRecordingStop = { x, y -> controller.finish(x, y) },
                onRecordingCancel = { controller.cancel() },
                onDragPosition = { x, y -> controller.updateDrag(x, y) },
            ),
        contentAlignment = Alignment.Center
    ) {
        val painter = config.iconResId?.let { painterResource(it) }
            ?: painterResource(R.drawable.message_input_microphone_icon)
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painter,
            tint = resolvedPrimary,
            contentDescription = "microphone"
        )
    }

    AudioRecorderOverlay(
        visible = controller.uiState != RecordUiState.IDLE,
        uiState = controller.uiState,
        maxDurationMs = config.maxDurationMs,
        bubbleAnchor = controller.bubbleAnchor,
        onCancelTargetChanged = { controller.updateCancelTarget(it) },
        onTranscribeTargetChanged = { controller.updateTranscribeTarget(it) },
    )
}

private class AudioRecorderViewController(
    private val context: Context,
) {
    var uiState by mutableStateOf(RecordUiState.IDLE)
        private set
    var bubbleAnchor by mutableStateOf<ScreenRect?>(null)

    private var config = AudioRecorderViewConfig()
    private var resultListener: AudioRecorderViewResultListener? = null
    private var onCompleted: (String?, Int) -> Unit = { _, _ -> }

    private var isRecording = false
    private var pendingReleaseAction = AudioRecorderReleaseAction.SEND_AUDIO
    private var cancelTarget = AudioRecorderGestureTarget(0f, 0f, 0f, 0f)
    private var transcribeTarget = AudioRecorderGestureTarget(0f, 0f, 0f, 0f)
    private var released = false
    private var resultGeneration = 0
    private val audioTranscriber = AudioTranscriber()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun bind(
        config: AudioRecorderViewConfig,
        resultListener: AudioRecorderViewResultListener?,
        onCompleted: (String?, Int) -> Unit,
    ) {
        this.config = config
        this.resultListener = resultListener
        this.onCompleted = onCompleted
    }

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
        resultGeneration += 1
        val generation = resultGeneration
        AudioRecorder.startRecord(
            enableAIDeNoise = config.enableAIDeNoise,
            minDurationMs = config.minDurationMs,
            maxDurationMs = config.maxDurationMs,
            listener = object : AudioRecorderListener {
                override fun onCompleted(result: AudioRecorderResult) {
                    val action = pendingReleaseAction
                    isRecording = false
                    uiState = RecordUiState.IDLE
                    pendingReleaseAction = AudioRecorderReleaseAction.SEND_AUDIO
                    runOnMain { handleResult(result, action, generation) }
                }
            }
        )
        return true
    }

    fun updateDrag(screenX: Float, screenY: Float) {
        if (!isRecording) return
        val action = decideReleaseAction(screenX, screenY)
        pendingReleaseAction = action
        val nextState = uiStateFor(action)
        if (uiState != nextState) {
            uiState = nextState
        }
    }

    fun finish(screenX: Float, screenY: Float) {
        if (!isRecording) return
        pendingReleaseAction = decideReleaseAction(screenX, screenY)
        if (pendingReleaseAction == AudioRecorderReleaseAction.CANCEL) {
            cancel()
        } else {
            AudioRecorder.stopRecord()
        }
    }

    fun cancel() {
        if (!isRecording) return
        pendingReleaseAction = AudioRecorderReleaseAction.CANCEL
        AudioRecorder.cancelRecord()
    }

    fun release() {
        if (isRecording) {
            pendingReleaseAction = AudioRecorderReleaseAction.CANCEL
            AudioRecorder.cancelRecord()
        }
        resultGeneration += 1
        released = true
        uiState = RecordUiState.IDLE
        isRecording = false
    }

    private fun handleResult(
        result: AudioRecorderResult,
        action: AudioRecorderReleaseAction,
        generation: Int,
    ) {
        if (released || generation != resultGeneration) return
        showResultToast(result)
        if (result.isSuccess && action == AudioRecorderReleaseAction.TRANSCRIBE) {
            convertAndDispatch(result, action, generation)
            return
        }
        dispatchCompleted(result, action, generation)
    }

    private fun convertAndDispatch(
        result: AudioRecorderResult,
        action: AudioRecorderReleaseAction,
        sessionGeneration: Int,
    ) {
        if (sessionGeneration != resultGeneration) return
        resultGeneration += 1
        val convertGeneration = resultGeneration
        val audioPath = result.filePath
        if (audioPath == null || !isReadableAudioFile(audioPath)) {
            Toast.error(context, context.getString(R.string.message_input_convert_to_text_failed))
            dispatchCompleted(result, action, convertGeneration)
            return
        }
        audioTranscriber.convert(
            filePath = audioPath,
            onFailure = { _, _ ->
                runOnMain {
                    if (released || convertGeneration != resultGeneration) return@runOnMain
                    Toast.error(context, context.getString(R.string.message_input_convert_to_text_failed))
                }
            },
            onCompleted = { text ->
                runOnMain {
                    dispatchCompleted(
                        result.copy(transcribedText = text),
                        action,
                        convertGeneration,
                    )
                }
            }
        )
    }

    private fun dispatchCompleted(
        result: AudioRecorderResult,
        action: AudioRecorderReleaseAction,
        generation: Int,
    ) {
        if (released || generation != resultGeneration) return
        resultListener?.onCompleted(result, action)
        val legacyPath = if (result.isSuccess) result.filePath else null
        onCompleted(legacyPath, result.durationMs)
    }

    private fun showResultToast(result: AudioRecorderResult) {
        when (result.resultCode) {
            ResultCode.ERROR_LESS_THAN_MIN_DURATION -> {
                Toast.warning(context, context.getString(R.string.message_input_audio_too_short))
            }
            ResultCode.SUCCESS_EXCEED_MAX_DURATION -> {
                Toast.warning(context, context.getString(R.string.message_input_audio_time_limit_reached))
            }
            ResultCode.ERROR_RECORD_PERMISSION_DENIED -> {
                Toast.warning(
                    context,
                    context.getString(R.string.message_input_record_audio_permission_settings_tip)
                )
            }
            ResultCode.ERROR_RECORD_INNER_FAIL,
            ResultCode.ERROR_STORAGE_UNAVAILABLE,
            ResultCode.ERROR_RECORDING -> {
                Toast.error(context, context.getString(R.string.message_input_send_failed))
            }
            ResultCode.ERROR_CANCEL,
            ResultCode.SUCCESS -> Unit
        }
    }

    private fun decideReleaseAction(screenX: Float, screenY: Float): AudioRecorderReleaseAction {
        return when (
            AudioRecorderGesturePolicy.decideReleaseAction(
                fingerX = screenX,
                fingerY = screenY,
                cancelTarget = cancelTarget,
                transcribeTarget = transcribeTarget,
            )
        ) {
            OverlayReleaseAction.CANCEL -> AudioRecorderReleaseAction.CANCEL
            OverlayReleaseAction.TRANSCRIBE -> AudioRecorderReleaseAction.TRANSCRIBE
            OverlayReleaseAction.SEND_AUDIO -> AudioRecorderReleaseAction.SEND_AUDIO
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

private fun uiStateFor(action: AudioRecorderReleaseAction): RecordUiState {
    return when (action) {
        AudioRecorderReleaseAction.CANCEL -> RecordUiState.READY_TO_CANCEL
        AudioRecorderReleaseAction.TRANSCRIBE -> RecordUiState.READY_TO_TRANSCRIBE
        AudioRecorderReleaseAction.SEND_AUDIO -> RecordUiState.RECORDING
    }
}

private fun isReadableAudioFile(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val file = File(path)
    return file.exists() && file.isFile && file.canRead()
}

private fun parseColorOrNull(hex: String): Color? = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    null
}

private fun PointerEvent.isSystemCancel(): Boolean {
    val change = changes.firstOrNull() ?: return false
    return !change.pressed && !change.changedToUpIgnoreConsumed()
}

private fun Modifier.audioRecorderHoldGesture(
    startOnLongPress: Boolean,
    onRecordingStart: () -> Boolean,
    onRecordingStop: (Float, Float) -> Unit,
    onRecordingCancel: () -> Unit,
    onDragPosition: (Float, Float) -> Unit,
): Modifier = composed {
    val viewConfiguration = LocalViewConfiguration.current
    val hapticFeedback = LocalHapticFeedback.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun toScreen(local: Offset): Offset? {
        val coords = coordinates ?: return null
        val origin = coords.positionOnScreen()
        return Offset(origin.x + local.x, origin.y + local.y)
    }

    this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(startOnLongPress) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                val pointerId = down.id
                var lastScreen = toScreen(down.position)

                if (startOnLongPress) {
                    val endedBeforeTimeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                            lastScreen = toScreen(change.position) ?: lastScreen
                            if (change.isConsumed || !change.pressed) break
                        }
                    }
                    if (endedBeforeTimeout != null) {
                        return@awaitEachGesture
                    }
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }

                if (!onRecordingStart()) {
                    return@awaitEachGesture
                }
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                    lastScreen = toScreen(change.position) ?: lastScreen
                    lastScreen?.let { onDragPosition(it.x, it.y) }
                    val cancelled = event.isSystemCancel() ||
                        (!change.pressed && !change.changedToUpIgnoreConsumed())
                    if (cancelled) {
                        change.consume()
                        onRecordingCancel()
                        break
                    }
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
