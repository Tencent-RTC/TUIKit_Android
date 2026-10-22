package com.trtc.uikit.roomkit.inroom.common.moremenu

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.popup.RoomPopup

interface MoreMenuCell {
    fun onMenuSelected()
}

class RoomMoreMenuSheet(context: Context) : FrameLayout(context) {

    var onDismiss: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onComingSoon: (() -> Unit)? = null
    var onScreenShare: (() -> Unit)? = null

    private val popup = RoomPopup(context)

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_more_menu, this, true)
        popup.setContentView(this, RoomPopup.ContentHeight.AUTO)
        popup.onDismiss = { onDismiss?.invoke() }

        bindCell(R.id.cell_share, R.drawable.roomkit_icon_screen_share_start, R.string.roomkit_more_screen_share) {
            dismiss()
            onScreenShare?.invoke()
        }
        bindCell(R.id.cell_captions, R.drawable.roomkit_icon_caption, R.string.roomkit_more_captions) {
            onComingSoon?.invoke()
        }
        bindCell(R.id.cell_record, R.drawable.roomkit_icon_record_primary, R.string.roomkit_cloud_recording_record_label)
        bindCell(R.id.cell_settings, R.drawable.roomkit_icon_settings, R.string.roomkit_settings_title) {
            onSettings?.invoke()
        }
    }

    fun show() {
        popup.show()
    }

    fun dismiss() {
        popup.dismiss()
    }
    
    fun setScreenSharing(isSharing: Boolean) {
        val cell = findViewById<View>(R.id.cell_share)
        cell.findViewById<AppCompatTextView>(R.id.cell_label)
            .setText(if (isSharing) R.string.roomkit_screen_share_stop else R.string.roomkit_more_screen_share)
        cell.findViewById<AppCompatImageView>(R.id.cell_icon).setImageResource(
            if (isSharing) R.drawable.roomkit_icon_screen_share_stop else R.drawable.roomkit_icon_screen_share_start
        )
    }

    private fun bindCell(cellId: Int, iconRes: Int, labelRes: Int, onClick: (() -> Unit)? = null) {
        val cell = findViewById<View>(cellId)
        cell.findViewById<AppCompatImageView>(R.id.cell_icon).setImageResource(iconRes)
        cell.findViewById<AppCompatTextView>(R.id.cell_label).setText(labelRes)
        cell.setOnClickListener {
            dismiss()
            val menuCell = cell as? MoreMenuCell
            if (menuCell != null) menuCell.onMenuSelected() else onClick?.invoke()
        }
    }
}
