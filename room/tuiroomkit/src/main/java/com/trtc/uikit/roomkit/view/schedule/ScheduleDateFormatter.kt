package com.trtc.uikit.roomkit.view.schedule

import android.content.Context
import com.trtc.uikit.roomkit.R
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Locale-aware date/time formatter.
 *
 * Year/month/day separators come from string resources (e.g. CJK-style unit chars vs "/") to avoid
 * hard-coding date patterns in code.
 */
internal object ScheduleDateFormatter {

    private const val DATE_TIME_FORMAT = "%d%s%02d%s%02d%s %02d:%02d"
    private const val DATE_ONLY_FORMAT = "%d%s%02d%s%02d%s"

    fun formatDateTime(context: Context, timestampMillis: Long): String {
        val calendar = Calendar.getInstance().apply { time = Date(timestampMillis) }
        val yearUnit = context.getString(R.string.roomkit_year_text)
        val monthUnit = context.getString(R.string.roomkit_month_text)
        val dayUnit = context.getString(R.string.roomkit_day_text)
        return String.format(
            Locale.getDefault(),
            DATE_TIME_FORMAT,
            calendar.get(Calendar.YEAR), yearUnit,
            calendar.get(Calendar.MONTH) + 1, monthUnit,
            calendar.get(Calendar.DAY_OF_MONTH), dayUnit,
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
    }

    fun formatDate(context: Context, timestampMillis: Long): String {
        val calendar = Calendar.getInstance().apply { time = Date(timestampMillis) }
        val yearUnit = context.getString(R.string.roomkit_year_text)
        val monthUnit = context.getString(R.string.roomkit_month_text)
        val dayUnit = context.getString(R.string.roomkit_day_text)
        return String.format(
            Locale.getDefault(),
            DATE_ONLY_FORMAT,
            calendar.get(Calendar.YEAR), yearUnit,
            calendar.get(Calendar.MONTH) + 1, monthUnit,
            calendar.get(Calendar.DAY_OF_MONTH), dayUnit
        )
    }

    /**
     * Formats a conference time range. When start and end fall on the same day the end value shows
     * only hh:mm; when they span days a "(Next Day)" marker is added.
     */
    fun formatDateTimeRange(context: Context, startMillis: Long, endMillis: Long): String {
        val calendar = Calendar.getInstance().apply { time = Date(startMillis) }
        val startYear = calendar.get(Calendar.YEAR)
        val startMonth = calendar.get(Calendar.MONTH) + 1
        val startDay = calendar.get(Calendar.DAY_OF_MONTH)

        calendar.time = Date(endMillis)
        val endYear = calendar.get(Calendar.YEAR)
        val endMonth = calendar.get(Calendar.MONTH) + 1
        val endDay = calendar.get(Calendar.DAY_OF_MONTH)
        val endHour = calendar.get(Calendar.HOUR_OF_DAY)
        val endMinute = calendar.get(Calendar.MINUTE)

        val startTimeText = formatDateTime(context, startMillis)
        val patternRes = if (startYear == endYear && startMonth == endMonth && startDay == endDay) {
            R.string.roomkit_format_conference_time
        } else {
            R.string.roomkit_format_conference_next_day_time
        }
        return String.format(
            Locale.getDefault(),
            context.getString(patternRes),
            startTimeText,
            String.format(Locale.getDefault(), "%02d", endHour),
            String.format(Locale.getDefault(), "%02d", endMinute)
        )
    }

    /**
     * Formats a duration in minutes as "Hh Mm" (e.g. "1h30m"), skipping the hour
     * segment when [totalMinutes] < 60 and the minute segment when the value is a whole number of
     * hours. Negative inputs are clamped to zero.
     */
    fun formatDuration(context: Context, totalMinutes: Int): String {
        val safeMinutes = totalMinutes.coerceAtLeast(0)
        val hours = safeMinutes / MINUTES_PER_HOUR
        val minutes = safeMinutes % MINUTES_PER_HOUR
        val hourText = context.getString(R.string.roomkit_hour_text, hours.toString())
        val minuteText = context.getString(R.string.roomkit_minute_text, minutes.toString())
        return when {
            hours == 0 -> minuteText
            minutes == 0 -> hourText
            else -> hourText + minuteText
        }
    }

    private const val MINUTES_PER_HOUR = 60
}
