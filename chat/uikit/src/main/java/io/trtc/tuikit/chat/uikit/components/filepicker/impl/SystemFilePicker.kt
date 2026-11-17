package io.trtc.tuikit.chat.uikit.components.filepicker.impl
import android.content.Intent
import io.trtc.tuikit.atomicxcore.impl.common.ContextProvider
import io.trtc.tuikit.chat.uikit.components.filepicker.AbstractFilePicker
import io.trtc.tuikit.chat.uikit.components.filepicker.FilePickerConfig
import io.trtc.tuikit.chat.uikit.components.filepicker.FilePickerListener

class SystemFilePicker : AbstractFilePicker {

    override fun pickFiles(config: FilePickerConfig, listener: FilePickerListener) {
        if (!FilePickerSessionStore.start(config, listener)) {
            listener.onCanceled()
            return
        }

        val context = ContextProvider.getApplicationContext() ?: run {
            FilePickerSessionStore.cancelActive()
            return
        }
        val intent = Intent(context, FilePickerBridgeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            FilePickerSessionStore.cancelActive()
        }
    }
}
