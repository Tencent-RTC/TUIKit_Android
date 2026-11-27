package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import android.content.Context
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.messageinput.asr.AsrServiceError

/**
 * 语音识别资源包用完 / 未开通（[AsrServiceError.isServiceUnavailable]）时的提示框。
 */
internal object AsrServiceUnavailableDialog {

    /** 进程级：聊天页反复重建，放在页面上就成了每进一次页面弹一次。 */
    private var shown = false

    /**
     * 同一进程只弹一次，之后的失败由调用方按普通失败提示。每次转文字照常请求，
     * 资源包续上后不用重启就能恢复。主线程调用。
     *
     * @return 这次是否弹了框
     */
    fun showOnce(context: Context): Boolean {
        if (shown) {
            return false
        }
        shown = true
        AtomicAlertDialog(context).apply {
            init {
                title = context.getString(R.string.message_input_asr_unavailable_title)
                content = context.getString(R.string.message_input_asr_unavailable_content)
                confirmButton(context.getString(R.string.message_input_asr_unavailable_confirm))
            }
            show()
        }
        return true
    }
}
