package io.trtc.tuikit.chat.uikit.components.messageinput.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.trtc.tuikit.atomicx.albumpicker.AlbumMedia
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerConfig
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerListener
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerTheme
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerView

class MessageInputAlbumPickerActivity : AppCompatActivity() {

    private var forwardListener: AlbumPickerListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = pendingConfig ?: AlbumPickerConfig()
        val theme = pendingTheme ?: AlbumPickerTheme()
        forwardListener = pendingListener
        clearPending()

        val albumPickerView = AlbumPickerView(this)
        albumPickerView.initialize(config, theme, object : AlbumPickerListener {
            override fun onPickConfirm(pickedAlbumMedias: List<AlbumMedia>, textMessage: String?) {
                forwardListener?.onPickConfirm(pickedAlbumMedias, textMessage)
            }

            override fun onMediaProcessing(albumMedia: AlbumMedia, progress: Float, error: Boolean) {
                forwardListener?.onMediaProcessing(albumMedia, progress, error)
            }

            override fun onMediaProcessed() {
                forwardListener?.onMediaProcessed()
                finishSafely()
            }

            override fun onCancel() {
                forwardListener?.onCancel()
                finishSafely()
            }
        })

        setContentView(albumPickerView)
    }

    private fun finishSafely() {
        if (!isFinishing && !isDestroyed) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        forwardListener = null
    }

    companion object {
        private var pendingConfig: AlbumPickerConfig? = null
        private var pendingTheme: AlbumPickerTheme? = null
        private var pendingListener: AlbumPickerListener? = null

        fun start(
            context: Context,
            config: AlbumPickerConfig = AlbumPickerConfig(),
            theme: AlbumPickerTheme = AlbumPickerTheme(),
            listener: AlbumPickerListener
        ) {
            pendingConfig = config
            pendingTheme = theme
            pendingListener = listener
            val intent = Intent(context, MessageInputAlbumPickerActivity::class.java)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        private fun clearPending() {
            pendingConfig = null
            pendingTheme = null
            pendingListener = null
        }
    }
}
