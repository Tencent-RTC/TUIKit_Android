package io.trtc.tuikit.chat.uikit.components.ai.tts
import io.trtc.tuikit.chat.uikit.components.ai.AiMediaProcessManager
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioPlayer
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioPlayerListener

// Turns text into speech via [AiMediaProcessManager.convertTextToVoice] and plays
// the resulting audio URL through [AudioPlayer], which already accepts http(s)
// sources. Used by record-translation "read aloud" and "listen from here".
//
// A monotonically increasing session token guards against in-flight conversions
// resolving after a newer speak()/stop() has superseded them.
class TtsPlaybackHelper {

    var isPlaying: Boolean = false
        private set

    private var player: AudioPlayer? = null
    private var session: Int = 0
    private var pendingOnComplete: (() -> Unit)? = null

    fun speak(
        text: String,
        voiceId: String = "",
        language: String = "",
        onStart: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val currentSession = ++session
        pendingOnComplete = onComplete
        AiMediaProcessManager.convertTextToVoice(
            text = text,
            voiceId = voiceId,
            language = language,
            onSuccess = { audioUrl ->
                if (currentSession != session) return@convertTextToVoice
                startPlayback(currentSession, audioUrl, onStart, onComplete, onError)
            },
            onFailure = { _, desc ->
                if (currentSession != session) return@convertTextToVoice
                isPlaying = false
                pendingOnComplete = null
                onError?.invoke(desc)
            }
        )
    }

    fun stop() {
        // Invalidate any in-flight conversion or pending callback.
        session++
        val current = player
        player = null
        isPlaying = false
        val onComplete = pendingOnComplete
        pendingOnComplete = null
        current?.setListener(null)
        current?.stop()
        onComplete?.invoke()
    }

    private fun startPlayback(
        currentSession: Int,
        audioUrl: String,
        onStart: (() -> Unit)?,
        onComplete: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        releasePlayer()
        val newPlayer = AudioPlayer.create()
        player = newPlayer
        newPlayer.setListener(object : AudioPlayerListener {
            override fun onPlay() {
                if (currentSession != session) return
                isPlaying = true
                onStart?.invoke()
            }

            override fun onCompletion() {
                if (currentSession != session) return
                isPlaying = false
                player = null
                pendingOnComplete = null
                onComplete?.invoke()
            }

            override fun onError(errorMessage: String) {
                if (currentSession != session) return
                isPlaying = false
                player = null
                pendingOnComplete = null
                onError?.invoke(errorMessage)
            }
        })
        newPlayer.play(audioUrl)
    }

    private fun releasePlayer() {
        val current = player
        player = null
        current?.setListener(null)
        current?.stop()
    }
}
