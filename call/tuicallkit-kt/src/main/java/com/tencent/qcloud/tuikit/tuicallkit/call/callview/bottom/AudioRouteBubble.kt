package com.tencent.qcloud.tuikit.tuicallkit.call.callview.bottom

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitBubble
import com.tencent.qcloud.tuikit.tuicallkit.common.dp
import io.trtc.tuikit.atomicxcore.api.device.DeviceInfo

internal class AudioRouteBubble @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : CallKitBubble(context, attrs, defStyleAttr) {

    var onSelect: ((DeviceInfo) -> Unit)? = null

    fun reload(devices: List<DeviceInfo>, current: DeviceInfo?) {
        stackView.removeAllViews()
        for (device in devices) {
            stackView.addView(makeRow(device, isCurrent = device.deviceId == current?.deviceId))
        }
    }

    private fun makeRow(device: DeviceInfo, isCurrent: Boolean): View {
        val context = context
        val tintColor = ContextCompat.getColor(
            context,
            if (isCurrent) R.color.tuicallkit_brand_text else android.R.color.white
        )

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val paddingV = 12.dp(context)
            val paddingH = 18.dp(context)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            minimumHeight = 52.dp(context)
            setBackgroundResource(R.drawable.tuicallkit_bg_row_ripple)
            isClickable = true
            setOnClickListener { onSelect?.invoke(device) }
        }

        val icon = ImageView(context).apply {
            setImageResource(iconFor(device))
            setColorFilter(tintColor)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        row.addView(icon, LinearLayout.LayoutParams(20.dp(context), 20.dp(context)))

        val label = TextView(context).apply {
            text = labelFor(device)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(tintColor)
        }
        val labelParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        labelParams.marginStart = 12.dp(context)
        labelParams.marginEnd = 12.dp(context)
        row.addView(label, labelParams)

        val check = TextView(context).apply {
            text = "✓"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_brand_text))
            visibility = if (isCurrent) View.VISIBLE else View.GONE
        }
        row.addView(check)
        return row
    }

    private fun labelFor(device: DeviceInfo): String {
        // 对齐 iOS：按 deviceName 判断；外部设备直接采用引擎 state 给的名称
        return when (device.deviceName) {
            "speakerphone" -> context.getString(R.string.tuicallkit_route_speaker)
            "earpiece" -> context.getString(R.string.tuicallkit_route_earpiece)
            else -> device.deviceName
        }
    }

    private fun iconFor(device: DeviceInfo): Int {
        return when (device.deviceId) {
            "speaker_1" -> R.drawable.tuicallkit_icon_speaker
            "speaker_0" -> R.drawable.tuicallkit_icon_earpiece
            else -> if (isBluetoothDevice(device)) R.drawable.tuicallkit_icon_bluetooth else R.drawable.tuicallkit_icon_earpiece
        }
    }

    private fun isBluetoothDevice(device: DeviceInfo): Boolean {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bluetoothTypes = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER
        )
        return outputs.any { it.type in bluetoothTypes && it.productName?.toString() == device.deviceName }
            || device.deviceName.contains("bluetooth", ignoreCase = true)
    }
}
