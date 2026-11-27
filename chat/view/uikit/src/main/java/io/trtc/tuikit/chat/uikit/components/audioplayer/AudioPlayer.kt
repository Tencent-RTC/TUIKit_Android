package io.trtc.tuikit.chat.uikit.components.audioplayer
import io.trtc.tuikit.chat.uikit.components.audioplayer.impl.MediaAudioPlayer

abstract class AudioPlayer {
    companion object {
        @JvmStatic
        fun create(): AudioPlayer {
            return MediaAudioPlayer()
        }
    }

    abstract fun play(filePath: String)

    abstract fun pause()

    abstract fun resume()

    abstract fun stop()

    abstract fun setAudioOutputDevice(device: AudioOutputDevice): AudioPlayer

    abstract fun setListener(listener: AudioPlayerListener?): AudioPlayer

    abstract fun getCurrentPosition(): Int

    abstract fun getDuration(): Int

    abstract fun isPlaying(): Boolean

    abstract fun isPaused(): Boolean
}

enum class AudioOutputDevice {
    SPEAKER,
    EARPIECE,
}

interface AudioPlayerListener {
    fun onPlay() {}

    fun onPause() {}

    fun onResume() {}

    fun onProgressUpdate(currentPosition: Int, duration: Int) {}

    fun onCompletion() {}

    fun onError(errorMessage: String) {}

    fun onAudioOutputChanged(device: AudioOutputDevice) {}
}
