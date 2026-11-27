package io.trtc.tuikit.chat.uikit.components.audioplayer.impl
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.trtc.tuikit.atomicxcore.impl.common.ContextProvider
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioOutputDevice
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioPlayer
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioPlayerListener
import java.io.File

internal class MediaAudioPlayer : AudioPlayer() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appContext: Context? = ContextProvider.getApplicationContext()
    private val audioManager: AudioManager? =
        appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var mediaPlayer: MediaPlayer? = null
    private var listener: AudioPlayerListener? = null
    private var currentOutputDevice = AudioOutputDevice.SPEAKER
    private var isPausedInternal = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            val player = mediaPlayer ?: return
            if (!player.isPlaying) {
                return
            }
            listener?.onProgressUpdate(getCurrentPosition(), getDuration())
            mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    override fun play(filePath: String) {
        stopInternal(clearCallback = false)
        val player = MediaPlayer()
        mediaPlayer = player
        isPausedInternal = false

        try {
            player.setAudioAttributes(createAudioAttributes(currentOutputDevice))
            bindPlayerListeners(player)
            setDataSource(player, filePath)
            applyAudioRoute(currentOutputDevice)
            player.prepareAsync()
        } catch (exception: Exception) {
            Log.e(TAG, "play failed, filePath=$filePath", exception)
            stopInternal(clearCallback = false)
            listener?.onError(exception.message ?: "play failed")
        }
    }

    override fun pause() {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) {
            return
        }
        try {
            player.pause()
            isPausedInternal = true
            stopProgressUpdates()
            listener?.onPause()
        } catch (exception: Exception) {
            Log.e(TAG, "pause failed", exception)
            listener?.onError(exception.message ?: "pause failed")
        }
    }

    override fun resume() {
        val player = mediaPlayer ?: return
        if (!isPausedInternal) {
            return
        }
        try {
            applyAudioRoute(currentOutputDevice)
            player.start()
            isPausedInternal = false
            listener?.onResume()
            startProgressUpdates()
        } catch (exception: Exception) {
            Log.e(TAG, "resume failed", exception)
            listener?.onError(exception.message ?: "resume failed")
        }
    }

    override fun stop() {
        stopInternal(clearCallback = false)
    }

    override fun setAudioOutputDevice(device: AudioOutputDevice): AudioPlayer {
        currentOutputDevice = device
        applyAudioRoute(device)
        mediaPlayer?.let {
            try {
                it.setAudioAttributes(createAudioAttributes(device))
            } catch (exception: Exception) {
                Log.w(TAG, "setAudioAttributes ignored after prepare", exception)
            }
        }
        listener?.onAudioOutputChanged(device)
        return this
    }

    override fun setListener(listener: AudioPlayerListener?): AudioPlayer {
        this.listener = listener
        return this
    }

    override fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (exception: Exception) {
            Log.w(TAG, "getCurrentPosition failed", exception)
            0
        }
    }

    override fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (exception: Exception) {
            Log.w(TAG, "getDuration failed", exception)
            0
        }
    }

    override fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (exception: Exception) {
            Log.w(TAG, "isPlaying failed", exception)
            false
        }
    }

    override fun isPaused(): Boolean {
        return isPausedInternal && mediaPlayer != null
    }

    private fun bindPlayerListeners(player: MediaPlayer) {
        player.setOnPreparedListener {
            try {
                applyAudioRoute(currentOutputDevice)
                it.start()
                isPausedInternal = false
                listener?.onPlay()
                startProgressUpdates()
            } catch (exception: Exception) {
                Log.e(TAG, "start after prepare failed", exception)
                stopInternal(clearCallback = false)
                listener?.onError(exception.message ?: "start failed")
            }
        }

        player.setOnCompletionListener {
            stopProgressUpdates()
            restoreAudioRoute()
            mediaPlayer = null
            isPausedInternal = false
            try {
                it.reset()
                it.release()
            } catch (exception: Exception) {
                Log.w(TAG, "release on completion failed", exception)
            }
            listener?.onCompletion()
        }

        player.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "mediaPlayer error what=$what extra=$extra")
            stopInternal(clearCallback = false)
            listener?.onError("play error: $what/$extra")
            true
        }
    }

    private fun setDataSource(player: MediaPlayer, filePath: String) {
        when {
            filePath.startsWith(FILE_URI_PREFIX) ||
                filePath.startsWith(CONTENT_URI_PREFIX) ||
                filePath.startsWith(HTTP_URI_PREFIX) ||
                filePath.startsWith(HTTPS_URI_PREFIX) -> {
                val context = appContext ?: error("application context is null")
                player.setDataSource(context, Uri.parse(filePath))
            }

            else -> {
                val file = File(filePath)
                require(file.exists() && file.canRead()) {
                    "audio file is not readable: $filePath"
                }
                player.setDataSource(file.absolutePath)
            }
        }
    }

    private fun createAudioAttributes(device: AudioOutputDevice): AudioAttributes {
        return when (device) {
            AudioOutputDevice.SPEAKER -> {
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            }

            AudioOutputDevice.EARPIECE -> {
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
            }

            else -> {
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            }
        }
    }

    private fun applyAudioRoute(device: AudioOutputDevice) {
        val manager = audioManager ?: return
        when (device) {
            AudioOutputDevice.SPEAKER -> {
                manager.mode = AudioManager.MODE_NORMAL
                manager.isSpeakerphoneOn = true
            }

            AudioOutputDevice.EARPIECE -> {
                manager.mode = AudioManager.MODE_IN_COMMUNICATION
                manager.isSpeakerphoneOn = false
            }

            else -> {
                manager.mode = AudioManager.MODE_NORMAL
                manager.isSpeakerphoneOn = true
            }
        }
    }

    private fun restoreAudioRoute() {
        val manager = audioManager ?: return
        manager.mode = AudioManager.MODE_NORMAL
        manager.isSpeakerphoneOn = false
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        mainHandler.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        mainHandler.removeCallbacks(progressRunnable)
    }

    private fun stopInternal(clearCallback: Boolean) {
        stopProgressUpdates()
        val player = mediaPlayer
        mediaPlayer = null
        isPausedInternal = false
        if (player != null) {
            try {
                player.setOnPreparedListener(null)
                player.setOnCompletionListener(null)
                player.setOnErrorListener(null)
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (exception: Exception) {
                Log.w(TAG, "stopInternal stop failed", exception)
            }
            try {
                player.reset()
                player.release()
            } catch (exception: Exception) {
                Log.w(TAG, "stopInternal release failed", exception)
            }
        }
        restoreAudioRoute()
        if (clearCallback) {
            listener = null
        }
    }

    companion object {
        private const val TAG = "MediaAudioPlayer"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
        private const val FILE_URI_PREFIX = "file://"
        private const val CONTENT_URI_PREFIX = "content://"
        private const val HTTP_URI_PREFIX = "http://"
        private const val HTTPS_URI_PREFIX = "https://"
    }
}
