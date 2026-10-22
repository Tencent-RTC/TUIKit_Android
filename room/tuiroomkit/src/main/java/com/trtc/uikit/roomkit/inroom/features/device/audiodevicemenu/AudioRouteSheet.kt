package com.trtc.uikit.roomkit.inroom.features.device.audiodevicemenu

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.base.util.RoomAudioOutput
import com.trtc.uikit.roomkit.base.util.dp

class AudioRouteSheet(
    context: Context,
    current: RoomAudioOutput,
) : FrameLayout(context) {

    var onSelect: ((RoomAudioOutput) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val popup = RoomPopup(context)

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_audio_route_sheet, this, true)
        val rowContainer = findViewById<LinearLayout>(R.id.route_row_container)
        for (output in entries()) {
            rowContainer.addView(inflateRow(output, output == current))
        }
        findViewById<Button>(R.id.route_cancel_button).setOnClickListener {
            popup.dismiss()
            onDismiss?.invoke()
        }
        popup.setContentView(this, RoomPopup.ContentHeight.AUTO)
        popup.onDismiss = {
            onDismiss?.invoke()
        }
    }

    fun show() {
        popup.show()
    }

    fun dismiss() {
        popup.dismiss()
    }

    private fun inflateRow(output: RoomAudioOutput, isCurrent: Boolean): View {
        val row = LayoutInflater.from(context).inflate(R.layout.roomkit_audio_route_row, this, false)
        row.setOnClickListener {
            popup.dismiss()
            onSelect?.invoke(output)
        }
        val accent = ContextCompat.getColor(context, R.color.roomkit_accent)
        val normal = ContextCompat.getColor(context, R.color.roomkit_fg_primary)
        val iconRes = when (output) {
            RoomAudioOutput.SPEAKER ->
                if (isCurrent) R.drawable.roomkit_icon_speaker_accent else R.drawable.roomkit_icon_speaker_primary

            RoomAudioOutput.EARPIECE ->
                if (isCurrent) R.drawable.roomkit_icon_earpiece_accent else R.drawable.roomkit_icon_earpiece_primary
        }
        val titleRes = when (output) {
            RoomAudioOutput.SPEAKER -> R.string.roomkit_main_audio_route_speaker
            RoomAudioOutput.EARPIECE -> R.string.roomkit_main_audio_route_earpiece
        }
        row.findViewById<ImageView>(R.id.route_row_icon).apply {
            setImageResource(iconRes)
        }
        row.findViewById<TextView>(R.id.route_row_title).apply {
            text = context.getString(titleRes)
            setTextColor(if (isCurrent) accent else normal)
        }
        row.findViewById<ImageView>(R.id.route_row_check).apply {
            visibility = if (isCurrent) View.VISIBLE else View.GONE
        }
        row.background = if (isCurrent) {
            GradientDrawable().apply {
                cornerRadius = context.dp(ROW_CORNER_RADIUS_DP).toFloat()
                setColor(ContextCompat.getColor(context, R.color.roomkit_accent_soft))
            }
        } else {
            ContextCompat.getDrawable(context, R.drawable.roomkit_bg_route_row)
        }
        return row
    }

    private fun entries(): List<RoomAudioOutput> =
        listOf(RoomAudioOutput.SPEAKER, RoomAudioOutput.EARPIECE)

    private companion object {
        const val ROW_CORNER_RADIUS_DP = 12
    }
}
