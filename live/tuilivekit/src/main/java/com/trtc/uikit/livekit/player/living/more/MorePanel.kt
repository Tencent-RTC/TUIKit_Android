package com.trtc.uikit.livekit.player.living.more

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat.getString
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.setDebounceClickListener
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveToast
import com.trtc.uikit.livekit.common.theme.ScreenUtil.dip2px

internal class MorePanel(context: Context, private val onFloating: () -> Unit) : BottomSheetView(context) {
    private var quality = R.string.livekit_player_more_auto
    private var generation = 0
    private var actionPending = false
    private var bottomInset = 0
    private val qualityLabel: TextView

    override val sheetHeight: Int get() = dip2px(135f) + bottomInset

    init {
        val root = LayoutInflater.from(context).inflate(R.layout.livekit_player_more_panel, contentView, true)
        qualityLabel = contentView.findViewById(R.id.livekit_player_more_quality_label)
        root.findViewById<LinearLayout>(R.id.livekit_player_more_quality_item).setDebounceClickListener {
            LiveToast.show(context, getString(context, R.string.livekit_stay_tuned))
        }
        root.findViewById<LinearLayout>(R.id.livekit_player_more_floating_item).setDebounceClickListener {
            val request = generation
            dismiss { if (request == generation) onFloating() }
        }
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (bottomInset != bottom) {
                bottomInset = bottom
                updateSheetHeight(animated = false)
            }
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        ViewCompat.requestApplyInsets(contentView)
    }

    override fun show() {
        generation++
        actionPending = false
        qualityLabel.setText(quality)
        (qualityLabel.parent as View).contentDescription = qualityLabel.text
        super.show()
    }

    fun dismissAll() {
        generation++
        dismiss()
    }
}
