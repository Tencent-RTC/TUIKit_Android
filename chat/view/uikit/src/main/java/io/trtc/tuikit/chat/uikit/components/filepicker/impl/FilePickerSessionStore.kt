package io.trtc.tuikit.chat.uikit.components.filepicker.impl
import android.net.Uri
import io.trtc.tuikit.chat.uikit.components.filepicker.FilePickerConfig
import io.trtc.tuikit.chat.uikit.components.filepicker.FilePickerListener

internal object FilePickerSessionStore {

    private var activeSession: FilePickerSession? = null

    @Synchronized
    fun start(config: FilePickerConfig, listener: FilePickerListener): Boolean {
        if (activeSession != null) {
            return false
        }
        activeSession = FilePickerSession(config, listener)
        return true
    }

    @Synchronized
    fun current(): FilePickerSession? = activeSession

    @Synchronized
    fun hasActiveSession(): Boolean = activeSession != null

    @Synchronized
    fun clear(): FilePickerSession? {
        val session = activeSession
        activeSession = null
        return session
    }

    fun completePicked(result: List<Uri>) {
        clear()?.listener?.onPicked(result)
    }

    fun cancelActive() {
        clear()?.listener?.onCanceled()
    }
}

internal data class FilePickerSession(
    val config: FilePickerConfig,
    val listener: FilePickerListener,
)
