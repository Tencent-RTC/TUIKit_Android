package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioOutputDevice
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioPlayer
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioPlayerListener
import io.trtc.tuikit.chat.uikit.components.messagelist.model.AudioPlayingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MessageAudioController(
    private val onPlaybackError: (String) -> Unit = {},
    private val playerFactory: () -> AudioPlayer = { AudioPlayer.create() }
) {
    private val _audioPlayingState = MutableStateFlow(AudioPlayingState())
    val audioPlayingState: StateFlow<AudioPlayingState> = _audioPlayingState.asStateFlow()

    private var audioPlayer: AudioPlayer? = null
    private var currentPlayingMessageId: String? = null

    fun ensureStarted() {
        if (audioPlayer != null) {
            return
        }
        audioPlayer = playerFactory()
            .setAudioOutputDevice(AudioOutputDevice.SPEAKER)
            .setListener(object : AudioPlayerListener {
                override fun onPlay() {
                    currentPlayingMessageId?.let { messageId ->
                        updateAudioPlayingState(messageId = messageId, isPlaying = true, playPosition = 0)
                    }
                }

                override fun onPause() {
                    currentPlayingMessageId?.let { messageId ->
                        updateAudioPlayingState(
                            messageId = messageId,
                            isPlaying = false,
                            playPosition = audioPlayingState.value.playPosition
                        )
                    }
                }

                override fun onResume() {
                    currentPlayingMessageId?.let { messageId ->
                        updateAudioPlayingState(
                            messageId = messageId,
                            isPlaying = true,
                            playPosition = audioPlayingState.value.playPosition
                        )
                    }
                }

                override fun onProgressUpdate(currentPosition: Int, duration: Int) {
                    val messageId = currentPlayingMessageId ?: return
                    val currentSecond = (currentPosition / 1000).coerceAtLeast(0)
                    if (audioPlayingState.value.playPosition == currentSecond &&
                        audioPlayingState.value.isPlaying
                    ) {
                        return
                    }
                    updateAudioPlayingState(
                        messageId = messageId,
                        isPlaying = true,
                        playPosition = currentSecond
                    )
                }

                override fun onCompletion() {
                    resetAudioPlayingState()
                }

                override fun onError(errorMessage: String) {
                    onPlaybackError(errorMessage)
                    resetAudioPlayingState()
                }
            })
    }

    fun toggle(messageId: String, filePath: String) {
        ensureStarted()
        val player = audioPlayer ?: return

        if (currentPlayingMessageId == messageId) {
            when {
                player.isPlaying() -> player.pause()
                player.isPaused() -> player.resume()
                else -> {
                    currentPlayingMessageId = messageId
                    updateAudioPlayingState(messageId = messageId, isPlaying = false, playPosition = 0)
                    player.play(filePath)
                }
            }
            return
        }

        if (currentPlayingMessageId != null) {
            player.stop()
        }
        currentPlayingMessageId = messageId
        updateAudioPlayingState(messageId = messageId, isPlaying = false, playPosition = 0)
        player.play(filePath)
    }

    fun release() {
        audioPlayer?.stop()
        audioPlayer = null
        resetAudioPlayingState()
    }

    private fun updateAudioPlayingState(messageId: String, isPlaying: Boolean, playPosition: Int) {
        _audioPlayingState.value = AudioPlayingState(
            playingMessageId = messageId,
            isPlaying = isPlaying,
            playPosition = playPosition
        )
    }

    private fun resetAudioPlayingState() {
        currentPlayingMessageId = null
        _audioPlayingState.value = AudioPlayingState()
    }
}
