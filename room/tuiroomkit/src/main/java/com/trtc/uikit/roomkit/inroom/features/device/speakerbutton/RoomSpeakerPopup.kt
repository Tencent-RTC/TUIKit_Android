package com.trtc.uikit.roomkit.inroom.features.device.speakerbutton

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import io.trtc.tuikit.atomicxcore.api.device.AudioRoute
import com.trtc.uikit.roomkit.base.util.dp

class RoomSpeakerPopup(
    context: Context,
    current: AudioRoute,
) : FrameLayout(context) {

    var onSelect: ((AudioRoute) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val popup = RoomPopup(context)

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_speaker_popup, this, true)
        val rowContainer = findViewById<LinearLayout>(R.id.speaker_row_container)
        for (output in entries()) {
            rowContainer.addView(inflateRow(output, output == current))
        }
        findViewById<Button>(R.id.speaker_cancel_button).setOnClickListener {
            popup.dismiss()
            onDismiss?.invoke()
        }
        popup.panelBackgroundColor = Color.TRANSPARENT
        popup.panelBorderColor = Color.TRANSPARENT
        popup.showsHandleBar = false
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

    private fun inflateRow(output: AudioRoute, isCurrent: Boolean): View {
        val row = LayoutInflater.from(context).inflate(R.layout.roomkit_speaker_popup_row, this, false)
        row.setOnClickListener {
            popup.dismiss()
            onSelect?.invoke(output)
        }
        val accent = ContextCompat.getColor(context, R.color.roomkit_accent)
        val normal = ContextCompat.getColor(context, R.color.roomkit_fg_primary)
        val iconRes = when (output) {
            AudioRoute.SPEAKERPHONE ->
                if (isCurrent) R.drawable.roomkit_icon_speaker_accent else R.drawable.roomkit_icon_speaker_primary

            AudioRoute.EARPIECE ->
                if (isCurrent) R.drawable.roomkit_icon_earpiece_accent else R.drawable.roomkit_icon_earpiece_primary
        }
        val titleRes = when (output) {
            AudioRoute.SPEAKERPHONE -> R.string.roomkit_main_audio_route_speaker
            AudioRoute.EARPIECE -> R.string.roomkit_main_audio_route_earpiece
        }
        row.findViewById<ImageView>(R.id.speaker_row_icon).apply {
            setImageResource(iconRes)
        }
        row.findViewById<TextView>(R.id.speaker_row_title).apply {
            text = context.getString(titleRes)
            setTextColor(if (isCurrent) accent else normal)
        }
        row.findViewById<ImageView>(R.id.speaker_row_check).apply {
            visibility = if (isCurrent) View.VISIBLE else View.GONE
        }
        row.background = if (isCurrent) {
            GradientDrawable().apply {
                cornerRadius = context.dp(ROW_CORNER_RADIUS_DP).toFloat()
                setColor(ContextCompat.getColor(context, R.color.roomkit_accent_soft))
            }
        } else {
            ContextCompat.getDrawable(context, R.drawable.roomkit_bg_speaker_popup_row)
        }
        return row
    }

    private fun entries(): List<AudioRoute> =
        listOf(AudioRoute.SPEAKERPHONE, AudioRoute.EARPIECE)

    private companion object {
        const val ROW_CORNER_RADIUS_DP = 12
    }
}
