package com.evanchen.alarmify.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** "6:30" + "AM", or "18:30" + "" on a 24-hour phone. */
fun formatClock(context: Context, hour: Int, minute: Int): Pair<String, String> =
    formatClock(DateFormat.is24HourFormat(context), hour, minute)

fun formatClock(is24: Boolean, hour: Int, minute: Int): Pair<String, String> =
    if (is24) "%d:%02d".format(hour, minute) to ""
    else "%d:%02d".format(if (hour % 12 == 0) 12 else hour % 12, minute) to (if (hour < 12) "AM" else "PM")

fun formatClockAt(is24: Boolean, epochMs: Long): String {
    val t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    val (time, suffix) = formatClock(is24, t.hour, t.minute)
    return "$time $suffix".trim()
}

/** "7h 23m", "23 min", "under a minute". */
fun formatDuration(ms: Long): String {
    val totalMin = ((ms + 59_999) / 60_000).coerceAtLeast(0)
    if (ms < 60_000) return "under a minute"
    val d = totalMin / (60 * 24)
    val m = totalMin % 60
    // Under two days, hours read better than "1d 1h".
    val h = if (d >= 2) (totalMin / 60) % 24 else totalMin / 60
    return when {
        d >= 2 -> if (h > 0) "${d}d ${h}h" else "${d}d"
        h > 0 -> if (m > 0) "${h}h ${m}m" else "${h}h"
        else -> "$m min"
    }
}

/** "12:04" countdown, or "1:02:04" past an hour. */
fun formatCountdown(ms: Long): String {
    val s = (ms.coerceAtLeast(0) + 999) / 1000
    val h = s / 3600
    val m = (s / 60) % 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun minutesLabel(min: Int): String = when {
    min < 60 -> "${min}m"
    min % 60 == 0 -> "${min / 60}h"
    else -> "${min / 60}h ${min % 60}"
}

val WEEKDAYS = setOf(1, 2, 3, 4, 5)
val WEEKENDS = setOf(6, 7)
val EVERY_DAY = (1..7).toSet()

fun dayShort(day: Int): String = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())
fun dayLetter(day: Int): String = DayOfWeek.of(day).getDisplayName(TextStyle.NARROW, Locale.getDefault())

fun repeatSummary(days: Set<Int>): String = when (days) {
    emptySet<Int>() -> "Once"
    EVERY_DAY -> "Every day"
    WEEKDAYS -> "Weekdays"
    WEEKENDS -> "Weekends"
    else -> days.sorted().joinToString(", ") { dayShort(it) }
}

/** "Today", "Tomorrow", or "Wed". */
fun relativeDay(epochMs: Long): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
}

fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

/** The current time, ticking on the second (or [periodMs]). */
@Composable
fun rememberNow(periodMs: Long = 1_000): State<Long> = produceState(System.currentTimeMillis(), periodMs) {
    while (true) {
        value = System.currentTimeMillis()
        delay(periodMs - value % periodMs)
    }
}

/** "13 hours 6 minutes", "9 minutes", "under a minute". */
fun formatDurationLong(ms: Long): String {
    if (ms < 60_000) return "under a minute"
    val totalMin = (ms + 59_999) / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    fun unit(n: Long, one: String) = "$n $one" + if (n == 1L) "" else "s"
    return when {
        h == 0L -> unit(m, "minute")
        m == 0L -> unit(h, "hour")
        h >= 24 -> unit(h, "hour")
        else -> "${unit(h, "hour")} ${unit(m, "minute")}"
    }
}

/** "today", "tomorrow", or "on Wednesday". */
fun dayPhrase(epochMs: Long): String = when (val d = relativeDay(epochMs)) {
    "Today", "Tomorrow" -> d.lowercase()
    else -> "on $d"
}

/** "25 min", "1 hr", "1 hr 30 min". */
fun durationWords(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} hr"
    else -> "${min / 60} hr ${min % 60} min"
}
