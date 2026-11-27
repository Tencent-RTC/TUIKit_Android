package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel
import java.io.File

internal object MessageInputSendGuards {
    fun isReadableFilePath(path: String): Boolean {
        if (path.isBlank()) return false
        val file = File(path)
        return file.exists() && file.isFile && file.canRead()
    }
}
