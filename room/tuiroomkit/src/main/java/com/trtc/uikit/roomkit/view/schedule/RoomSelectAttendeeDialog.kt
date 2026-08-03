package com.trtc.uikit.roomkit.view.schedule

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.trtc.uikit.roomkit.R

/** Full-screen dialog hosting [RoomSelectAttendeeView]; runs in immersive layout mode. */
class RoomSelectAttendeeDialog(
    context: Context,
    private val initialSelectedIds: List<String>,
    private val onConfirm: (List<String>) -> Unit
) : Dialog(context, R.style.RoomKitFullScreenDialog) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = RoomSelectAttendeeView(context).apply {
            setInitialSelectedIds(initialSelectedIds)
            onBackClick = { dismiss() }
            onConfirm = { ids ->
                onConfirm(ids)
                dismiss()
            }
        }
        setContentView(view)

        window?.apply {
            WindowCompat.setDecorFitsSystemWindows(this, false)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            statusBarColor = Color.WHITE
            WindowCompat.getInsetsController(this, decorView).isAppearanceLightStatusBars = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
}
