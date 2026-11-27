package io.trtc.tuikit.chat.uikit.components.messagelist.utils

import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object DateTimeUtils {

    /**
     * Calculate the interval seconds between two timestamps
     * @param timeStamp1 First timestamp in seconds
     * @param timeStamp2 Second timestamp in seconds
     * @return Absolute difference in seconds
     */
    fun getIntervalSeconds(timeStamp1: Long?, timeStamp2: Long?): Long {
        if (timeStamp1 == null || timeStamp2 == null) return 0L
        if (timeStamp1 == 0L || timeStamp2 == 0L) return 0L
        return abs(timeStamp2 - timeStamp1)
    }

    /**
     * Convert seconds to smart time format with auto-scaling
     * @param totalSeconds Total seconds to convert
     * @return Formatted time string (MM:SS or HH:MM:SS)
     */
    fun formatSmartTime(totalSeconds: Int?): String {
        if (totalSeconds == null) return "00:00"
        return formatDurationSeconds(totalSeconds.toLong())
    }

    // ---------- Locale-aware date patterns ----------

    @JvmStatic
    fun isChineseLocale(locale: Locale): Boolean {
        return locale.language.equals(Locale.CHINESE.language, ignoreCase = true)
    }

    @JvmStatic
    fun shortDatePattern(locale: Locale): String {
        return if (isChineseLocale(locale)) "M'月'd'日'" else "M/d/yy"
    }

    @JvmStatic
    fun fullDatePattern(locale: Locale): String {
        return if (isChineseLocale(locale)) "yyyy'年'M'月'd'日'" else "M/d/yy"
    }

    // ---------- Absolute time text ----------

    @JvmStatic
    @JvmOverloads
    fun formatMessageListTime(
        timestampMs: Long?,
        now: Date = Date(),
        locale: Locale = Locale.getDefault(),
        yesterdayLabel: String = "Yesterday"
    ): String? {
        val date = timestampMs?.let { Date(it) } ?: return null
        if (date.time == 0L) return null

        val timeString = formatDate(date, "HH:mm", Locale.US)
        val nowCalendar = calendarFor(now)
        val dateCalendar = calendarFor(date)
        return when {
            isSameDay(nowCalendar, dateCalendar) -> timeString
            isYesterday(nowCalendar, dateCalendar) -> "$yesterdayLabel $timeString"
            isSameYear(nowCalendar, dateCalendar) && isSameWeek(nowCalendar, dateCalendar) -> {
                "${formatDate(date, "EEEE", locale)} $timeString"
            }
            isSameYear(nowCalendar, dateCalendar) -> {
                "${formatDate(date, shortDatePattern(locale), Locale.US)} $timeString"
            }
            else -> {
                "${formatDate(date, fullDatePattern(locale), Locale.US)} $timeString"
            }
        }
    }

    @JvmStatic
    fun formatFullDateTime(timeMillis: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timeMillis))
    }

    // ---------- Duration text ----------

    @JvmStatic
    fun formatDurationSeconds(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "00:00"
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    @JvmStatic
    fun formatDurationMillis(timeMs: Long): String {
        return formatDurationSeconds(timeMs / 1000)
    }

    @JvmStatic
    fun formatCallDuration(totalSeconds: Int): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", safeSeconds / 60, safeSeconds % 60)
    }

    // ---------- Internals ----------

    private fun formatDate(date: Date, pattern: String, locale: Locale): String {
        return SimpleDateFormat(pattern, locale).apply {
            numberFormat = DecimalFormat("0", DecimalFormatSymbols(Locale.US)).apply {
                isGroupingUsed = false
            }
        }.format(date)
    }

    private fun calendarFor(date: Date): Calendar {
        return Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            time = date
        }
    }

    private fun isSameDay(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(nowCalendar: Calendar, dateCalendar: Calendar): Boolean {
        val yesterday = startOfDay(nowCalendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, dateCalendar)
    }

    private fun isSameYear(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
    }

    private fun isSameWeek(first: Calendar, second: Calendar): Boolean {
        return isSameDay(startOfWeek(first), startOfWeek(second))
    }

    private fun startOfWeek(calendar: Calendar): Calendar {
        return startOfDay(calendar).apply {
            val mondayBasedIndex = (get(Calendar.DAY_OF_WEEK) + 5) % 7
            add(Calendar.DAY_OF_YEAR, -mondayBasedIndex)
        }
    }

    private fun startOfDay(calendar: Calendar): Calendar {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}