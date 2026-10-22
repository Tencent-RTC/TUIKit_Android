package com.trtc.uikit.roomkit.inroom.common.devicefaultsheet

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.base.util.RoomDevice

class RoomDeviceFaultSheet(
    context: Context,
    kind: RoomDevice,
) : FrameLayout(context) {

    var onPrimary: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val popup = RoomPopup(context)

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_device_fault_sheet, this, true)
        val name = context.getString(kind.localizedNameRes)
        findViewById<ImageView>(R.id.fault_icon_image).apply {
            setImageResource(
                when (kind) {
                    RoomDevice.MICROPHONE -> R.drawable.roomkit_icon_microphone_fault
                    RoomDevice.CAMERA -> R.drawable.roomkit_icon_camera_fault
                }
            )
        }
        findViewById<TextView>(R.id.fault_title_text).text =
            context.getString(R.string.roomkit_main_device_fault_system_title, name)
        findViewById<TextView>(R.id.fault_desc_text).text =
            context.getString(R.string.roomkit_main_device_fault_system_body, name)
        findViewById<Button>(R.id.fault_primary_button).apply {
            text = context.getString(R.string.roomkit_main_device_open_settings)
            setOnClickListener {
                popup.dismiss()
                onPrimary?.invoke()
            }
        }
        findViewById<Button>(R.id.fault_secondary_button).apply {
            text = context.getString(R.string.roomkit_main_device_not_now)
            setOnClickListener {
                popup.dismiss()
                onDismiss?.invoke()
            }
        }
        popup.setContentView(this, RoomPopup.ContentHeight.AUTO)
        popup.onDismiss = { onDismiss?.invoke() }
    }

    fun show() {
        popup.show()
    }

    fun dismiss() {
        popup.dismiss()
    }
}
