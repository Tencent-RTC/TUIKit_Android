package com.trtc.uikit.roomkit.view.schedule

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.view.schedule.wheelpicker.WheelPicker
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast

/**
 * Room-duration picker (BottomSheet with two WheelPicker columns).
 *
 * Hours 0..23; minutes step by 5; minimum duration 15 minutes. The callback returns the total
 * duration in minutes.
 */
class RoomDurationPickerDialog(
    private val context: Context,
    private val initialDurationMinutes: Int,
    private val onConfirm: (Int) -> Unit
) {

    companion object {
        private const val MINUTE_STEP = 5
        private const val MIN_DURATION_MINUTES = 15
        private const val HOUR_MAX = 23
        private const val MINUTE_MAX = 55
        private const val MINUTES_PER_HOUR = 60
    }

    private val dialog = BottomSheetDialog(context, R.style.RoomkitBottomSheetDialog)

    private val hours: List<String> = (0..HOUR_MAX).map { it.toString() }
    private val minutes: List<String> = (0..MINUTE_MAX step MINUTE_STEP).map { it.toString() }

    fun show() {
        val view = View.inflate(context, R.layout.roomkit_view_duration_picker, null)
        val dragIndicator: View = view.findViewById(R.id.drag_indicator_duration)
        val wpHour: WheelPicker = view.findViewById(R.id.wp_duration_hour)
        val wpMinute: WheelPicker = view.findViewById(R.id.wp_duration_minute)
        val btnClose: ImageView = view.findViewById(R.id.btn_duration_close)
        val btnConfirm: ImageView = view.findViewById(R.id.btn_duration_confirm)

        dragIndicator.setOnClickListener { dialog.dismiss() }

        wpHour.setData(hours.map { context.getString(R.string.roomkit_hour_text, it) })
        wpMinute.setData(minutes.map { context.getString(R.string.roomkit_minute_text, it) })

        val initHour = initialDurationMinutes / MINUTES_PER_HOUR
        val initMinute = (initialDurationMinutes % MINUTES_PER_HOUR / MINUTE_STEP) * MINUTE_STEP
        wpHour.selectedItemPosition = initHour.coerceIn(0, hours.size - 1)
        wpMinute.selectedItemPosition = minutes.indexOf(initMinute.toString()).coerceAtLeast(0)

        btnClose.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val hour = wpHour.currentItemPosition
            val minute = minutes[wpMinute.currentItemPosition].toInt()
            val total = hour * MINUTES_PER_HOUR + minute
            if (total < MIN_DURATION_MINUTES) {
                AtomicToast.show(
                    context,
                    context.getString(R.string.roomkit_minimum_duration_is_fifteen_minutes),
                    AtomicToast.Style.WARNING
                )
                return@setOnClickListener
            }
            onConfirm(total)
            dialog.dismiss()
        }

        dialog.setContentView(view)
        applyRoundedCornerBackground()
        dialog.show()
    }

    /** Makes the BottomSheetDialog's default white background transparent so the top corners round correctly. */
    private fun applyRoundedCornerBackground() {
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.setBackgroundResource(android.R.color.transparent)
    }

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    val isShowing: Boolean get() = dialog.isShowing
}
