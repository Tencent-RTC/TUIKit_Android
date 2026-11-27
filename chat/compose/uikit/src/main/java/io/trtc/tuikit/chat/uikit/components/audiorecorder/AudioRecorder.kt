package io.trtc.tuikit.chat.uikit.components.audiorecorder

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.trtc.tuikit.chat.uikit.components.audiorecorder.audiorecorderimpl.AudioRecorderImpl
import io.trtc.tuikit.chat.uikit.components.audiorecorder.audiorecorderimpl.AudioRecorderViewImpl
import kotlinx.coroutines.flow.StateFlow

enum class ResultCode(val code: Int) {
    SUCCESS_EXCEED_MAX_DURATION(1),
    SUCCESS(0),
    ERROR_CANCEL(-1),
    ERROR_RECORDING(-2),
    ERROR_STORAGE_UNAVAILABLE(-3),
    ERROR_LESS_THAN_MIN_DURATION(-4),
    ERROR_RECORD_INNER_FAIL(-5),
    ERROR_RECORD_PERMISSION_DENIED(-6),
}

typealias AudioRecorderResultCode = ResultCode

data class AudioRecorderResult @JvmOverloads constructor(
    val resultCode: ResultCode,
    val filePath: String? = null,
    val durationMs: Int = 0,
    val transcribedText: String? = null,
) {
    val isSuccess: Boolean
        get() = resultCode == ResultCode.SUCCESS ||
            resultCode == ResultCode.SUCCESS_EXCEED_MAX_DURATION
}

enum class AudioRecorderReleaseAction {
    SEND_AUDIO,
    CANCEL,
    TRANSCRIBE,
}

fun interface AudioRecorderViewResultListener {
    fun onCompleted(result: AudioRecorderResult, releaseAction: AudioRecorderReleaseAction)
}

interface AudioRecorderListener {
    fun onStart() {}

    fun onProgress(durationMs: Int, powerLevel: Int) {}

    fun onCompleted(resultCode: ResultCode, path: String?, durationMs: Int) {}

    fun onCompleted(result: AudioRecorderResult) {
        onCompleted(result.resultCode, result.filePath, result.durationMs)
    }
}

object AudioRecorder {
    private val instance = AudioRecorderImpl()

    val currentPower: StateFlow<Int> = instance.currentPowerFlow
    val currentTimeMs: StateFlow<Int> = instance.recordTimeMsFlow

    // To use AI noise reduction, the app must depend on LiteAVSDK_Professional v12.7+ and have the feature enabled.
    // Dependency: Add to app module's build.gradle dependencies: implementation("com.tencent.liteav:LiteAVSDK_Professional:12.7.0.xxxxx")
    // For enabling permissions, see documentation: https://cloud.tencent.com/document/product/269/113290
    fun startRecord(
        filepath: String? = null,
        enableAIDeNoise: Boolean = false,
        minDurationMs: Int = 1000,
        maxDurationMs: Int = 60000,
        listener: AudioRecorderListener
    ) {
        instance.startRecord(filepath, enableAIDeNoise, minDurationMs, maxDurationMs, listener)
    }

    fun stopRecord() {
        instance.stopRecord()
    }

    fun cancelRecord() {
        instance.cancelRecord()
    }

    fun isRecording(): Boolean {
        return instance.isRecording()
    }
}

data class AudioRecorderViewConfig @JvmOverloads constructor(
    val enableAIDeNoise: Boolean = true,
    val minDurationMs: Int = 1000,
    val maxDurationMs: Int = 60000,
    val primaryColor: String? = null,
    val backgroundColor: String? = null,
    val iconResId: Int? = null,
    val cancelTouchSlopDp: Int = 60,
    val startOnLongPress: Boolean = true,
)

@Composable
fun AudioRecorderView(
    modifier: Modifier = Modifier,
    config: AudioRecorderViewConfig = AudioRecorderViewConfig(),
    resultListener: AudioRecorderViewResultListener? = null,
    onCompleted: (path: String?, durationMs: Int) -> Unit = { _, _ -> },
) {
    AudioRecorderViewImpl(
        modifier = modifier,
        config = config,
        resultListener = resultListener,
        onCompleted = onCompleted,
    )
}
