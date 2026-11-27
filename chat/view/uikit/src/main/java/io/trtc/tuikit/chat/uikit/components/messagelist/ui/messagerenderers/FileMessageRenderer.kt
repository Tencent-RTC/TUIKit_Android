package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.FileUtil
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.FileUtils
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.FileMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import java.util.Locale

class FileMessageRenderer : MessageRenderer {

    override fun createView(context: Context, parent: ViewGroup): View {
        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            gravity = Gravity.CENTER_VERTICAL
            val pad = (12 * density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                (237 * density).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val icon = ImageView(context).apply {
            tag = "file_icon"
        }
        container.addView(
            icon,
            LinearLayout.LayoutParams(
                (40 * density).toInt(),
                (40 * density).toInt()
            )
        )

        val spacer = View(context)
        container.addView(
            spacer,
            LinearLayout.LayoutParams(
                (8 * density).toInt(),
                0
            )
        )

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(
            textColumn,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val nameView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            typeface = Typeface.DEFAULT
            tag = "file_name"
        }
        textColumn.addView(
            nameView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val footerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        textColumn.addView(
            footerRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * density).toInt()
            }
        )

        val sizeView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            tag = "file_size"
        }
        footerRow.addView(
            sizeView,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val statusView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.END
            visibility = View.GONE
            tag = "file_status"
        }
        footerRow.addView(
            statusView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val downloadIcon = ImageView(context).apply {
            setImageResource(R.drawable.message_list_file_download_btn)
            visibility = View.GONE
            tag = "file_download"
        }
        footerRow.addView(
            downloadIcon,
            LinearLayout.LayoutParams(
                (20 * density).toInt(),
                (20 * density).toInt()
            ).apply {
                marginStart = (6 * density).toInt()
            }
        )

        return container
    }

    override fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens
    ) {
        val iconView = view.findViewWithTag<ImageView>("file_icon")
        val nameView = view.findViewWithTag<TextView>("file_name")
        val sizeView = view.findViewWithTag<TextView>("file_size")
        val statusView = view.findViewWithTag<TextView>("file_status")
        val downloadView = view.findViewWithTag<ImageView>("file_download")
        val payload = message.messagePayload as? FileMessagePayload

        val fileName = payload?.fileName.orEmpty()
        iconView.setImageResource(resolveFileTypeIcon(fileName))

        nameView.text = fileName
        nameView.setTextColor(
            if (message.isSentBySelf) colors.textColorAntiPrimary else colors.textColorPrimary
        )

        val footerTextColor = if (message.isSentBySelf) {
            colors.textColorAntiSecondary
        } else {
            colors.textColorSecondary
        }
        val fileSize = payload?.fileSize?.toLong() ?: 0L
        sizeView.text = FileUtil.formatFileSize(fileSize)
        sizeView.setTextColor(footerTextColor)

        val filePath = payload?.filePath?.takeIf { it.isNotBlank() }
        val isDownloaded = !filePath.isNullOrEmpty()
        val downloadProgress = message.downloadMediaProgress.takeIf { it > 0 } ?: message.uploadMediaProgress
        val isDownloading = !isDownloaded && downloadProgress in 1..99

        val statusText = when {
            isDownloading -> "$downloadProgress%"
            else -> ""
        }
        if (statusText.isEmpty()) {
            statusView.visibility = View.GONE
        } else {
            statusView.visibility = View.VISIBLE
            statusView.text = statusText
            statusView.setTextColor(footerTextColor)
        }

        downloadView.visibility = if (!isDownloaded && !isDownloading) View.VISIBLE else View.GONE

        if (message.status == MessageStatus.VIOLATION) {
            view.setOnClickListener(null)
            view.isClickable = false
        } else {
            view.isClickable = true
            view.setOnClickListener {
                if (filePath.isNullOrEmpty()) {
                    viewModel.downloadFile(message)
                } else {
                    FileUtils.openFile(view.context, filePath, payload?.fileName)
                }
            }
        }
    }

    private fun resolveFileTypeIcon(fileName: String): Int {
        val dotIndex = fileName.lastIndexOf('.')
        if (dotIndex < 0 || dotIndex == fileName.length - 1) {
            return R.drawable.message_list_file_type_unknown
        }
        val extension = fileName.substring(dotIndex + 1).lowercase(Locale.getDefault())
        return when (extension) {
            "pdf" -> R.drawable.message_list_file_type_pdf
            "ppt", "pptx", "key", "keynote" -> R.drawable.message_list_file_type_ppt
            "doc", "docx" -> R.drawable.message_list_file_type_word
            "xls", "xlsx", "csv", "numbers" -> R.drawable.message_list_file_type_excel
            "txt", "log", "md", "rtf" -> R.drawable.message_list_file_type_txt
            "zip", "rar", "7z", "tar", "gz", "bz2" -> R.drawable.message_list_file_type_zip
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif" ->
                R.drawable.message_list_file_type_img
            else -> R.drawable.message_list_file_type_unknown
        }
    }
}
