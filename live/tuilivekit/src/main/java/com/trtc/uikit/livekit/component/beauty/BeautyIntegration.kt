package com.trtc.uikit.livekit.component.beauty

import android.content.Context
import android.os.Bundle
import com.tencent.qcloud.tuicore.interfaces.TUIServiceCallback
import com.trtc.uikit.livekit.component.beauty.tebeauty.BeautyPanelDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import com.trtc.uikit.livekit.component.beauty.tebeauty.TEBeautyManager
import io.trtc.tuikit.atomicxcore.api.device.BaseBeautyStore
import java.lang.ref.WeakReference

object BeautyIntegration {
    private var dialogWeakRef = WeakReference<BeautyPanelDialog?>(null)

    @JvmStatic
    fun showBeautyDialog(context: Context) {
        if (TEBeautyManager.isSupportTEBeauty()) {
            TEBeautyManager.checkBeautyResource(context, object : TUIServiceCallback() {
                override fun onServiceCallback(code: Int, message: String?, bundle: Bundle?) {
                    if (code == 0) {
                        val dialog = BeautyPanelDialog(context)
                        dialogWeakRef = WeakReference(dialog)
                        dialog.setOnDismissListener {
                            TEBeautyManager.exportParam()
                        }
                        dialog.show()
                    } else {
                        AtomicToast.show(context, "check beauty resource failed:$code,message:$message", AtomicToast.Style.ERROR)
                    }
                }
            })
        } else {
            val dialog = BeautyPanelDialog(context)
            dialogWeakRef = WeakReference(dialog)
            dialog.setOnDismissListener {
                TEBeautyManager.exportParam()
            }
            dialog.show()
        }
    }

    @JvmStatic
    fun resetBeauty() {
        BaseBeautyStore.shared().reset()
        TEBeautyManager.clear()
    }

    @JvmStatic
    fun dismissBeautyDialog() {
        dialogWeakRef.get()?.dismiss()
    }
}
