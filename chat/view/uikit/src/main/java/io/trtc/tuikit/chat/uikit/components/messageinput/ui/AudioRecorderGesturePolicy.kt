package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import kotlin.math.ceil

internal data class AudioRecorderGestureTarget(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun contains(x: Float, y: Float): Boolean {
        return x >= left && x <= left + width && y >= top && y <= top + height
    }
}

enum class AudioRecorderReleaseAction {
    SEND_AUDIO,
    CANCEL,
    TRANSCRIBE,
}

internal object AudioRecorderGesturePolicy {
    private const val COUNTDOWN_WINDOW_MS = 10_000

    fun decideReleaseAction(
        fingerX: Float,
        fingerY: Float,
        cancelTarget: AudioRecorderGestureTarget,
        transcribeTarget: AudioRecorderGestureTarget,
    ): AudioRecorderReleaseAction {
        return when {
            cancelTarget.contains(fingerX, fingerY) -> AudioRecorderReleaseAction.CANCEL
            transcribeTarget.contains(fingerX, fingerY) -> AudioRecorderReleaseAction.TRANSCRIBE
            else -> AudioRecorderReleaseAction.SEND_AUDIO
        }
    }

    fun remainingSecondsBeforeAutoStop(durationMs: Int, maxDurationMs: Int): Int? {
        if (maxDurationMs <= 0) {
            return null
        }
        val remainingMs = (maxDurationMs - durationMs).coerceAtLeast(0)
        if (remainingMs > COUNTDOWN_WINDOW_MS) {
            return null
        }
        return ceil(remainingMs / 1000f).toInt().coerceAtLeast(1)
    }
}
