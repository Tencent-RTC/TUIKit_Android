package io.trtc.tuikit.chat.uikit.components.filepicker

import android.net.Uri
import android.os.Parcelable
import io.trtc.tuikit.chat.uikit.components.filepicker.impl.SystemFilePicker
import kotlinx.parcelize.Parcelize

interface FilePickerListener {
    fun onPicked(result: List<Uri>)

    fun onCanceled()
}

@Parcelize
data class FilePickerConfig(
    val allowedMimeType: List<String> = emptyList(),
    val maxCount: Int = 1,
) : Parcelable {
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
