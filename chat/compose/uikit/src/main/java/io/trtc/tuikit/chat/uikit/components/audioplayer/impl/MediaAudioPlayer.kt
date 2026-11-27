package io.trtc.tuikit.chat.uikit.components.audioplayer.impl

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioOutputDevice
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioPlayer
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioPlayerListener
import io.trtc.tuikit.chat.uikit.components.common.ContextProvider
import java.io.File


class MediaAudioPlayer() : AudioPlayer() {
    private var mediaPlayer: MediaPlayer? = null
    private var currentFilePath: String? = null
    private var listener: AudioPlayerListener? = null
    private var isPaused = false
    private var currentOutputDevice = AudioOutputDevice.SPEAKER
    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioManager: AudioManager

    init {
        val context = ContextProvider.appContext
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val progressUpdater = object : Runnable {
        override fun run() {
            if (isPlaying()) {
                try {
                    val currentPosition = getCurrentPosition()
                    val duration = getDuration()
                    listener?.onProgressUpdate(currentPosition, duration)
                } catch (e: Exception) {

                }

                mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
            }
        }
    }

    override fun play(filePath: String) {

        if (isPlaying() || isPaused) {
            stop()
        }

        try {

            val isUriSource = isUriSource(filePath)
            if (!isUriSource) {
                val file = File(filePath)
                if (!file.exists() || !file.canRead()) {
                    listener?.onError("file not found : $filePath")
                    return
                }
            }


            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )


                setOnCompletionListener {
                    stopProgressUpdates()
                    listener?.onCompletion()

                    release()
                    mediaPlayer = null
                    isPaused = false
                }


                setOnErrorListener { _, what, extra ->
                    stopProgressUpdates()
                    val errorMessage = "play error, code: $what, extra: $extra"
                    Log.e(TAG, errorMessage)
                    listener?.onError(errorMessage)

                    release()
                    mediaPlayer = null
                    isPaused = false
                    true
                }


                setOnPreparedListener {

                    applyAudioOutputDevice()


                    start()
                    isPaused = false


                    listener?.onPlay()


                    startProgressUpdates()
                }

                this@MediaAudioPlayer.setDataSource(this, filePath)
                if (isUriSource) {
                    prepareAsync()
                } else {
                    prepare()
                }
            }

            currentFilePath = filePath

        } catch (e: Exception) {
            Log.e(TAG, "play error", e)
            listener?.onError("play error: ${e.message}")
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    override fun pause() {
        if (isPlaying()) {
            mediaPlayer?.pause()
            isPaused = true
            stopProgressUpdates()
            listener?.onPause()
        }
    }

    override fun resume() {
        if (isPaused && mediaPlayer != null) {
            mediaPlayer?.start()
            isPaused = false
            startProgressUpdates()
            listener?.onResume()
        }
    }

    override fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying() || isPaused) {
                    stop()
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "stop error", e)
        } finally {
            mediaPlayer = null
            isPaused = false
            stopProgressUpdates()
            listener?.onCompletion()
        }
    }

    override fun setAudioOutputDevice(device: AudioOutputDevice): AudioPlayer {

        if (currentOutputDevice == device) {
            return this
        }

        currentOutputDevice = device
        applyAudioOutputDevice()


        listener?.onAudioOutputChanged(device)

        return this
    }


    private fun applyAudioOutputDevice() {
        val player = mediaPlayer ?: return

        when (currentOutputDevice) {
            AudioOutputDevice.SPEAKER -> {
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = true
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
            }

            AudioOutputDevice.EARPIECE -> {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = false
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .build()
                )
            }
        }
    }

    override fun setListener(listener: AudioPlayerListener?): AudioPlayer {
        this.listener = listener
        return this
    }

    override fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "get current position failed", e)
            0
        }
    }

    override fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "get duration failed", e)
            0
        }
    }

    override fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            Log.e(TAG, "check play state failed", e)
            false
        }
    }

    override fun isPaused(): Boolean {
        return isPaused && mediaPlayer != null
    }


    private fun isUriSource(filePath: String): Boolean {
        return filePath.startsWith(FILE_URI_PREFIX) ||
            filePath.startsWith(CONTENT_URI_PREFIX) ||
            filePath.startsWith(HTTP_URI_PREFIX) ||
            filePath.startsWith(HTTPS_URI_PREFIX)
    }

    private fun setDataSource(player: MediaPlayer, filePath: String) {
        when {
            isUriSource(filePath) -> {
                player.setDataSource(ContextProvider.appContext, Uri.parse(filePath))
            }

            else -> {
                val file = File(filePath)
                player.setDataSource(file.absolutePath)
            }
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        mainHandler.post(progressUpdater)
    }


    private fun stopProgressUpdates() {
        mainHandler.removeCallbacks(progressUpdater)
    }

    companion object {
        private const val TAG = "MediaAudioPlayer"
        private const val PROGRESS_UPDATE_INTERVAL = 100L
        private const val FILE_URI_PREFIX = "file://"
        private const val CONTENT_URI_PREFIX = "content://"
        private const val HTTP_URI_PREFIX = "http://"
        private const val HTTPS_URI_PREFIX = "https://"
    }
} 