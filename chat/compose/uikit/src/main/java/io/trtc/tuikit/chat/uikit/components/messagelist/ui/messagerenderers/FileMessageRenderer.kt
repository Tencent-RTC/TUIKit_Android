package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalInteractionHandler
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageContentMaxWidth
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messageContentGestures
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.FileUtils
import io.trtc.tuikit.atomicxcore.api.message.FileMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import java.util.Locale

class FileMessageRenderer : MessageRenderer {
    @Composable
    override fun Render(message: MessageInfo) {
        val colors = LocalTheme.current.colors
        val messageInteraction = LocalInteractionHandler.current
        val payload = message.messagePayload as? FileMessagePayload
        val fileName = payload?.fileName.orEmpty()
        val filePath = payload?.filePath?.takeIf { it.isNotBlank() }
        val isDownloaded = !filePath.isNullOrEmpty()
        val downloadProgress = message.downloadMediaProgress.takeIf { it > 0 }
            ?: message.uploadMediaProgress
        val isDownloading = !isDownloaded && downloadProgress in 1..99
        val nameColor = if (message.isSentBySelf) {
            colors.textColorAntiPrimary
        } else {
            colors.textColorPrimary
        }
        val footerTextColor = if (message.isSentBySelf) {
            colors.textColorAntiSecondary
        } else {
            colors.textColorSecondary
        }

        val contentMaxWidth = LocalMessageContentMaxWidth.current
        val fileWidth = if (contentMaxWidth != Dp.Unspecified) {
            minOf(237.dp, contentMaxWidth)
        } else {
            237.dp
        }
        Row(
            modifier = Modifier
                .width(fileWidth)
                .padding(12.dp)
                .messageContentGestures(message, messageInteraction),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(40.dp),
                painter = painterResource(resolveFileTypeIcon(fileName)),
                contentDescription = null,
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                MiddleEllipsisFileName(
                    fileName = fileName,
                    color = nameColor
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = FileUtils.formatFileSize(payload?.fileSize?.toLong()),
                        fontSize = 12.sp,
                        color = footerTextColor
                    )
                    if (isDownloading) {
                        Text(
                            text = "$downloadProgress%",
                            fontSize = 12.sp,
                            color = footerTextColor
                        )
                    }
                    if (!isDownloaded && !isDownloading) {
                        Icon(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(20.dp),
                            painter = painterResource(R.drawable.message_list_file_download_btn),
                            contentDescription = null,
                            tint = Color.Unspecified
                        )
                    }
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

@Composable
private fun MiddleEllipsisFileName(
    fileName: String,
    color: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 14.sp, color = color)
    BoxWithConstraints {
        val maxWidthPx = constraints.maxWidth.toFloat()
        val displayText = remember(fileName, maxWidthPx) {
            ellipsizeMiddle(fileName, maxWidthPx) { text ->
                textMeasurer.measure(text = text, style = style, maxLines = 1).size.width.toFloat()
            }
        }
        Text(
            text = displayText,
            fontSize = 14.sp,
            maxLines = 1,
            softWrap = false,
            color = color
        )
    }
}

internal fun ellipsizeMiddle(
    text: String,
    maxWidthPx: Float,
    measureWidth: (String) -> Float
): String {
    if (text.isEmpty() || measureWidth(text) <= maxWidthPx) {
        return text
    }
    val ellipsis = "…"
    var low = 0
    var high = text.length
    var best = ellipsis
    while (low <= high) {
        val keep = (low + high) / 2
        if (keep <= 0) {
            high = keep - 1
            continue
        }
        val front = keep / 2
        val back = keep - front
        val candidate = text.take(front) + ellipsis + text.takeLast(back)
        if (measureWidth(candidate) <= maxWidthPx) {
            best = candidate
            low = keep + 1
        } else {
            high = keep - 1
        }
    }
    return best
}
