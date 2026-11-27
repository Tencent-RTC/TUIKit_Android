package io.trtc.tuikit.chat.uikit.components.filepicker.impl
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import io.trtc.tuikit.chat.uikit.components.filepicker.FilePickerConfig

class FilePickerBridgeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "FilePickerActivity"
    }

    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Translucent_NoTitleBar)
        super.onCreate(savedInstanceState)

        val session = FilePickerSessionStore.current()
        if (session == null) {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    FilePickerSessionStore.cancelActive()
                    finish()
                }
            }
        )

        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val selectedFiles = collectSelectedFiles(result.data, session.config.maxCount)
                FilePickerSessionStore.completePicked(selectedFiles)
            } else {
                FilePickerSessionStore.cancelActive()
            }

            finish()
        }

        try {
            filePickerLauncher.launch(createOpenDocumentIntent(session.config))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start file picker", e)
            FilePickerSessionStore.cancelActive()
            finish()
        }
    }

    private fun createOpenDocumentIntent(config: FilePickerConfig): Intent {
        val resolvedMimeTypes = FilePickerMimeTypePolicy.resolve(config.allowedMimeType)
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = resolvedMimeTypes.intentType
            if (resolvedMimeTypes.hasExtraMimeTypes) {
                putExtra(Intent.EXTRA_MIME_TYPES, resolvedMimeTypes.extraMimeTypes)
            }
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, config.maxCount > 1)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun collectSelectedFiles(data: Intent?, maxCount: Int): List<Uri> {
        val selectedFiles = mutableListOf<Uri>()
        val clipData: ClipData? = data?.clipData
        if (clipData != null) {
            val itemCount = clipData.itemCount.coerceAtMost(maxCount)
            for (i in 0 until itemCount) {
                val uri = clipData.getItemAt(i).uri
                if (canOpenFile(uri)) {
                    selectedFiles.add(uri)
                }
            }
        } else {
            val uri = data?.data
            if (uri != null && canOpenFile(uri)) {
                selectedFiles.add(uri)
            }
        }
        return selectedFiles
    }

    private fun canOpenFile(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error validating file URI", e)
            false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
