package com.trtc.uikit.roomkit.view.schedule

import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Time-zone display formatter. Uses rawOffset to compute the GMT offset, so
 * half-hour / 45-minute time zones are handled correctly.
 */
internal object TimeZoneFormatter {

    private const val MILLIS_PER_MINUTE = 60_000
    private const val MINUTES_PER_HOUR = 60

    /** Returns `(GMT±hh:mm){locale display name}`, e.g. `(GMT+08:00)China Standard Time`. */
    fun formatWithDisplayName(id: String): String {
        val timeZone = TimeZone.getTimeZone(id)
        val gmtOffset = formatGmtOffset(timeZone.rawOffset)
        val displayName = timeZone.getDisplayName(Locale.getDefault())
        return "($gmtOffset)$displayName"
    }

    /** Formats a raw offset (in milliseconds) as `GMT±hh:mm`. */
    fun formatGmtOffset(rawOffsetMillis: Int): String {
        val totalMinutes = rawOffsetMillis / MILLIS_PER_MINUTE
        val sign = if (totalMinutes >= 0) "+" else "-"
        val hours = abs(totalMinutes) / MINUTES_PER_HOUR
        val minutes = abs(totalMinutes) % MINUTES_PER_HOUR
        return String.format(Locale.US, "GMT%s%02d:%02d", sign, hours, minutes)
    }
}
