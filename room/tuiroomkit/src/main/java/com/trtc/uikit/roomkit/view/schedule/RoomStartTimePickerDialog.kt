package com.trtc.uikit.roomkit.view.schedule

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.view.schedule.wheelpicker.WheelPicker
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Start-time picker (BottomSheet with three WheelPicker columns).
 *
 * Date column spans 365 days starting today; hours 0..23; minutes step by 5. The picked time must
 * be later than "now".
 */
class RoomStartTimePickerDialog(
    private val context: Context,
    private val initialTimeMillis: Long,
    private val onConfirm: (Long) -> Unit
) {

    companion object {
        private const val DATE_RANGE_DAYS = 365
        private const val MINUTE_STEP = 5
        private const val MINUTE_MAX = 55
        private const val HOUR_MAX = 23
        private const val TIME_FORMAT = "%02d"
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }

    private val dialog = BottomSheetDialog(context, R.style.RoomkitBottomSheetDialog)

    private val hours: List<String> = (0..HOUR_MAX).map { String.format(Locale.getDefault(), TIME_FORMAT, it) }
    private val minutes: List<String> = (0..MINUTE_MAX step MINUTE_STEP)
        .map { String.format(Locale.getDefault(), TIME_FORMAT, it) }

    private val today: Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    private val dates: List<Calendar> = (0 until DATE_RANGE_DAYS).map { offset ->
        (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
    }

    fun show() {
        val view = View.inflate(context, R.layout.roomkit_view_start_time_picker, null)
        val dragIndicator: View = view.findViewById(R.id.drag_indicator_start_time)
        val wpDate: WheelPicker = view.findViewById(R.id.wp_start_time_date)
        val wpHour: WheelPicker = view.findViewById(R.id.wp_start_time_hour)
        val wpMinute: WheelPicker = view.findViewById(R.id.wp_start_time_minute)
        val btnClose: ImageView = view.findViewById(R.id.btn_start_time_close)
        val btnConfirm: ImageView = view.findViewById(R.id.btn_start_time_confirm)

        dragIndicator.setOnClickListener { dialog.dismiss() }

        wpDate.setData(dates.map { formatDate(it) })
        wpHour.setData(hours)
        wpMinute.setData(minutes)

        // Snap the initial minute down to the 5-minute grid.
        val initial = Calendar.getInstance().apply { timeInMillis = initialTimeMillis }
        val initDateIndex = dateIndexOf(initial).coerceAtLeast(0)
        val initHour = initial.get(Calendar.HOUR_OF_DAY)
        val initMinute = (initial.get(Calendar.MINUTE) / MINUTE_STEP) * MINUTE_STEP

        wpDate.selectedItemPosition = initDateIndex
        wpHour.selectedItemPosition = initHour
        wpMinute.selectedItemPosition =
            minutes.indexOf(String.format(Locale.getDefault(), TIME_FORMAT, initMinute)).coerceAtLeast(0)

        btnClose.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val picked = buildTime(
                dates[wpDate.currentItemPosition],
                wpHour.currentItemPosition,
                minutes[wpMinute.currentItemPosition].toInt()
            )
            if (picked.timeInMillis <= System.currentTimeMillis()) {
                AtomicToast.show(
                    context,
                    context.getString(R.string.roomkit_start_time_earlier_than_current_time),
                    AtomicToast.Style.WARNING
                )
                return@setOnClickListener
            }
            onConfirm(picked.timeInMillis)
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

    private fun buildTime(date: Calendar, hour: Int, minute: Int): Calendar =
        (date.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun dateIndexOf(cal: Calendar): Int {
        val target = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMs = target.timeInMillis - today.timeInMillis
        val day = (diffMs / MILLIS_PER_DAY).toInt()
        return day.coerceIn(0, DATE_RANGE_DAYS - 1)
    }

    private fun formatDate(cal: Calendar): String {
        if (cal.timeInMillis == today.timeInMillis) {
            return context.getString(R.string.roomkit_today)
        }
        val weekdayRes = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> R.string.roomkit_monday_text
            Calendar.TUESDAY -> R.string.roomkit_tuesday_text
            Calendar.WEDNESDAY -> R.string.roomkit_wednesday_text
            Calendar.THURSDAY -> R.string.roomkit_thursday_text
            Calendar.FRIDAY -> R.string.roomkit_friday_text
            Calendar.SATURDAY -> R.string.roomkit_saturday_text
            else -> R.string.roomkit_sunday_text
        }
        val formatter = SimpleDateFormat(context.getString(R.string.roomkit_date_pattern), Locale.getDefault())
        return "${formatter.format(cal.time)} ${context.getString(weekdayRes)}"
    }
}
