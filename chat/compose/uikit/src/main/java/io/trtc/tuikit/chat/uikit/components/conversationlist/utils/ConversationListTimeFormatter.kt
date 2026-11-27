package io.trtc.tuikit.chat.uikit.components.conversationlist.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ConversationListTimeFormatter {

    @JvmStatic
    fun formatConversationListTime(timeStamp: Long?): String {
        if (timeStamp == null || timeStamp <= 0) return ""

        val millis = timeStamp * 1000L
        val date = Date(millis)
        if (date == Date(Long.MIN_VALUE)) return ""

        val locale = Locale.getDefault()
        val dateFmt = conversationDateFormatHolder.get()
            ?.takeIf { it.locale == locale }
            ?.formatter
            ?: SimpleDateFormat().also {
                conversationDateFormatHolder.set(DateFormatEntry(locale, it))
            }
        dateFmt.timeZone = TimeZone.getDefault()

        val calendar = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.SUNDAY
        }

        calendar.time = Date()
        val nowDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val nowMonth = calendar.get(Calendar.MONTH)
        val nowYear = calendar.get(Calendar.YEAR)
        val nowWeekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH)

        calendar.time = date
        val dateDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val dateMonth = calendar.get(Calendar.MONTH)
        val dateYear = calendar.get(Calendar.YEAR)
        val dateWeekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH)

        return when {
            nowYear != dateYear -> {
                dateFmt.apply { applyPattern(fullDatePattern(locale)) }.format(date)
            }
            nowMonth != dateMonth -> {
                dateFmt.apply { applyPattern(shortDatePattern(locale)) }.format(date)
            }
            nowWeekOfMonth != dateWeekOfMonth -> {
                dateFmt.apply { applyPattern(shortDatePattern(locale)) }.format(date)
            }
            nowDayOfMonth != dateDayOfMonth -> {
                dateFmt.apply { applyPattern("EEEE") }.format(date)
            }
            else -> {
                dateFmt.apply { applyPattern("HH:mm") }.format(date)
            }
        }
    }

    private fun isChineseLocale(locale: Locale): Boolean {
        return locale.language.equals(Locale.CHINESE.language, ignoreCase = true)
    }

    private fun shortDatePattern(locale: Locale): String {
        return if (isChineseLocale(locale)) "M'月'd'日'" else "M/d/yy"
    }

    private fun fullDatePattern(locale: Locale): String {
        return if (isChineseLocale(locale)) "yyyy'年'M'月'd'日'" else "M/d/yy"
    }

    private data class DateFormatEntry(
        val locale: Locale,
        val formatter: SimpleDateFormat
    )

    private val conversationDateFormatHolder = ThreadLocal<DateFormatEntry>()
}
