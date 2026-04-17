package com.trtc.uikit.roomkit.view.main.screenshare

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.operator.DeviceOperator
import com.trtc.uikit.roomkit.base.ui.RoomAlertDialog
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus

/**
 * Screen share overlay view: a placeholder UI displayed over the video view during screen sharing,
 * responsible for showing the sharing status and handling the stop-sharing interaction.
 */
class ScreenShareOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("ScreenShareOverlayView")

    private var floatingWindow: View? = null
    private val deviceOperator by lazy { DeviceOperator(context) }

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_screen_share_overlay_view, this, true)
        visibility = GONE
        findViewById<View>(R.id.btn_stop_screen_capture).setOnClickListener {
            logger.info("Stop screen capture button clicked")
            showStopScreenShareConfirmDialog()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        logger.info("onDetachedFromWindow")
        hideFloatingWindow()
    }

    fun updateScreenStatus(screenStatus: DeviceStatus) {
        logger.info("updateScreenStatus: $screenStatus")
        if (screenStatus == DeviceStatus.ON) {
            visibility = VISIBLE
            showFloatingWindow()
        } else {
            visibility = GONE
            hideFloatingWindow()
        }
    }

    private fun showFloatingWindow() {
        if (floatingWindow != null) return
        try {
            floatingWindow = LayoutInflater.from(context)
                .inflate(R.layout.roomkit_screen_capture_floating_window, null, false)

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (windowManager == null) {
                logger.error("showFloatingWindow failed: WindowManager is null")
                floatingWindow = null
                return
            }

            val type = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                Build.VERSION.SDK_INT > Build.VERSION_CODES.N -> WindowManager.LayoutParams.TYPE_PHONE
                else -> WindowManager.LayoutParams.TYPE_TOAST
            }

            val layoutParams = WindowManager.LayoutParams(type).apply {
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }

            windowManager.addView(floatingWindow, layoutParams)
            logger.info("showFloatingWindow success")
        } catch (e: Exception) {
            logger.error("showFloatingWindow failed: ${e.message}")
            floatingWindow = null
        }
    }

    fun hideFloatingWindow() {
        if (floatingWindow == null) return
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            windowManager?.removeViewImmediate(floatingWindow)
            logger.info("hideFloatingWindow success")
        } catch (e: Exception) {
            logger.error("hideFloatingWindow failed: ${e.message}")
        } finally {
            floatingWindow = null
        }
    }

    private fun showStopScreenShareConfirmDialog() {
        logger.info("showStopScreenShareConfirmDialog")
        RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_stop_screen_share)
            .setNegativeButton(R.string.roomkit_cancel)
            .setPositiveButton(R.string.roomkit_btn_stop) {
                deviceOperator.stopScreenShare()
            }
            .show()
    }
}