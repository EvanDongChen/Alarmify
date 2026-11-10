package com.evanchen.alarmify.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One alarm. [days] holds java.time DayOfWeek values (1 = Monday … 7 = Sunday); empty means it rings once.
 * [fireAt] pins a one-time alarm to an exact instant (a quick alarm set "in 10 min" rings to the second);
 * once that has passed it behaves like any other one-time alarm at [hour]:[minute].
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val label: String = "",
    val vibrate: Boolean = true,
    val fireAt: Long? = null,
    val snoozedUntil: Long? = null,
) {
    val repeats: Boolean get() = days.isNotEmpty()

    /** A stand-in used while a place alarm rings (its id is the place's id, negated). */
    val isPlace: Boolean get() = id < 0

    /** The next moment this alarm should ring, strictly after [nowMs], or null if it won't. */
    fun nextTrigger(nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long? {
        snoozedUntil?.let { if (it > nowMs) return it }
        if (!enabled) return null
        fireAt?.let { if (it > nowMs && !repeats) return it }

        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
        val time = LocalTime.of(hour, minute)
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (repeats && date.dayOfWeek.value !in days) continue
            val candidate = ZonedDateTime.of(date, time, zone)
            if (candidate.isAfter(now)) return candidate.toInstant().toEpochMilli()
        }
        return null
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("days", JSONArray(days.sorted()))
        put("enabled", enabled)
        put("label", label)
        put("vibrate", vibrate)
        fireAt?.let { put("fireAt", it) }
        snoozedUntil?.let { put("snoozedUntil", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val daysArr = o.optJSONArray("days") ?: JSONArray()
            return Alarm(
                id = o.getInt("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                days = (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet(),
                enabled = o.optBoolean("enabled", true),
                label = o.optString("label", ""),
                vibrate = o.optBoolean("vibrate", true),
                fireAt = if (o.has("fireAt")) o.getLong("fireAt") else null,
                snoozedUntil = if (o.has("snoozedUntil")) o.getLong("snoozedUntil") else null,
            )
        }
    }
}
