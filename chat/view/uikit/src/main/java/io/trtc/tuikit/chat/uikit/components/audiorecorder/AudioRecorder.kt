package io.trtc.tuikit.chat.uikit.components.audiorecorder
import io.trtc.tuikit.chat.uikit.components.audiorecorder.audiorecorderimpl.AudioRecorderImpl
import kotlinx.coroutines.flow.StateFlow

enum class AudioRecorderResultCode(val code: Int) {
    SUCCESS_EXCEED_MAX_DURATION(1),
    SUCCESS(0),
    ERROR_CANCEL(-1),
    ERROR_RECORDING(-2),
    ERROR_STORAGE_UNAVAILABLE(-3),
    ERROR_LESS_THAN_MIN_DURATION(-4),
    ERROR_RECORD_INNER_FAIL(-5),
    ERROR_RECORD_PERMISSION_DENIED(-6),
}

data class AudioRecorderResult(
    val resultCode: AudioRecorderResultCode,
    val filePath: String? = null,
    val durationMs: Int = 0,
) {
    val isSuccess: Boolean
        get() = resultCode == AudioRecorderResultCode.SUCCESS ||
            resultCode == AudioRecorderResultCode.SUCCESS_EXCEED_MAX_DURATION
}

interface AudioRecorderListener {
    fun onStart() {}

    fun onProgress(durationMs: Int, powerLevel: Int) {}

    fun onCompleted(result: AudioRecorderResult) {}
}

object AudioRecorder {
    private val instance = AudioRecorderImpl()

    val currentPower: StateFlow<Int> = instance.currentPowerFlow
    val currentTimeMs: StateFlow<Int> = instance.recordTimeMsFlow

    fun startRecord(
        filePath: String? = null,
        enableAIDeNoise: Boolean = false,
        minDurationMs: Int = 1000,
        maxDurationMs: Int = 60000,
        listener: AudioRecorderListener
    ) {
        instance.startRecord(filePath, enableAIDeNoise, minDurationMs, maxDurationMs, listener)
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
