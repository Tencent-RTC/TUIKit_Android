package io.trtc.tuikit.chat.uikit.components.messagelist.listen

import android.os.Handler
import android.os.Looper
import io.trtc.tuikit.chat.uikit.components.ai.tts.TtsPlaybackHelper
import io.trtc.tuikit.chat.uikit.components.ai.tts.VoiceMessageConfig
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioPlayer
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioPlayerListener
import io.trtc.tuikit.chat.uikit.components.common.appContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ListenPlaybackState(
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val currentText: String = ""
)

internal class ListenFromHereController(
    private val tts: TtsPlaybackHelper = TtsPlaybackHelper(),
    private val audioPlayer: AudioPlayer = AudioPlayer.create()
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var queue: List<ListenItem> = emptyList()
    private var index = -1
    private var active = false
    private var voiceId = ""
    private var session = 0

    private val _state = MutableStateFlow(ListenPlaybackState())
    val state: StateFlow<ListenPlaybackState> = _state.asStateFlow()

    fun start(plan: List<ListenItem>) {
        stop()
        if (plan.isEmpty()) return
        voiceId = VoiceMessageConfig.getSelectedVoiceId(appContext)
        queue = plan
        index = 0
        active = true
        val currentSession = ++session
        _state.value = ListenPlaybackState(
            isActive = true,
            isLoading = true,
            currentText = plan[0].speechText
        )
        playCurrent(showLoading = true, currentSession = currentSession)
    }

    fun stop() {
        val wasActive = active
        active = false
        session++
        index = -1
        queue = emptyList()
        audioPlayer.setListener(null)
        audioPlayer.stop()
        tts.stop()
        if (wasActive || _state.value.isActive) {
            _state.value = ListenPlaybackState()
        }
    }

    private fun playCurrent(showLoading: Boolean, currentSession: Int) {
        if (!active || currentSession != session || index < 0 || index >= queue.size) {
            return
        }
        val item = queue[index]
        _state.value = _state.value.copy(
            currentText = item.speechText,
            isLoading = showLoading
        )
        val audioPath = item.audioPath

        if (item.speechText.isEmpty()) {
            if (!audioPath.isNullOrEmpty()) {
                playAudio(audioPath, currentSession)
            } else {
                advance(currentSession)
            }
            return
        }

        tts.speak(
            text = item.speechText,
            voiceId = voiceId,
            onStart = { runOnMain { clearLoading(currentSession) } },
            onComplete = {
                runOnMain {
                    if (!active || currentSession != session) return@runOnMain
                    if (!audioPath.isNullOrEmpty()) {
                        playAudio(audioPath, currentSession)
                    } else {
                        advance(currentSession)
                    }
                }
            },
            onError = { runOnMain { if (active && currentSession == session) advance(currentSession) } }
        )
    }

    private fun playAudio(audioPath: String, currentSession: Int) {
        audioPlayer.setListener(object : AudioPlayerListener {
            override fun onPlay() {
                runOnMain { clearLoading(currentSession) }
            }

            override fun onCompletion() {
                runOnMain { if (active && currentSession == session) advance(currentSession) }
            }

            override fun onError(errorMessage: String) {
                runOnMain { if (active && currentSession == session) advance(currentSession) }
            }
        })
        audioPlayer.play(audioPath)
    }

    private fun advance(currentSession: Int) {
        if (!active || currentSession != session) return
        index++
        if (index >= queue.size) {
            stop()
        } else {
            playCurrent(showLoading = false, currentSession = currentSession)
        }
    }

    private fun clearLoading(currentSession: Int) {
        if (active && currentSession == session && _state.value.isLoading) {
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
