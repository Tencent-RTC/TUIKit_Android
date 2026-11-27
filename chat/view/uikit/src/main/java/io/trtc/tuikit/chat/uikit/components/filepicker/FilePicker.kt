package io.trtc.tuikit.chat.uikit.components.filepicker
import android.net.Uri
import io.trtc.tuikit.chat.uikit.components.filepicker.impl.SystemFilePicker

interface FilePickerListener {
    fun onPicked(result: List<Uri>)

    fun onCanceled()
}

data class FilePickerConfig(
    val allowedMimeType: List<String> = emptyList(),
    val maxCount: Int = 1,
) {
    init {
        require(maxCount >= 1) { "maxCount must be at least 1" }
    }
}

interface AbstractFilePicker {
    fun pickFiles(config: FilePickerConfig, listener: FilePickerListener)
}

object FilePicker {
    private val instance = SystemFilePicker()
    fun pickFiles(config: FilePickerConfig = FilePickerConfig(), listener: FilePickerListener) {
        instance.pickFiles(config, listener)
    }
}
