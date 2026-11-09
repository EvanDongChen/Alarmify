package com.evanchen.alarmify.data

import android.content.Context
import android.content.SharedPreferences
import com.evanchen.alarmify.alarm.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId

/** Single source of truth for alarms: kept in SharedPreferences, mirrored to AlarmManager on every change. */
object AlarmRepository {
    const val SNOOZE_MINUTES = 10

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        // Device-protected storage so alarms still ring after a reboot, before the phone is unlocked.
        val storage = appContext.createDeviceProtectedStorageContext()
        storage.moveSharedPreferencesFrom(appContext, PREFS)
        prefs = storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _alarms.value = load()
    }

    fun get(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun newId(): Int = synchronized(this) {
        val next = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit().putInt(KEY_NEXT_ID, next + 1).apply()
        next
    }

    fun upsert(alarm: Alarm) = mutate { list ->
        if (list.any { it.id == alarm.id }) list.map { if (it.id == alarm.id) alarm else it } else list + alarm
    }

    fun delete(id: Int) {
        AlarmScheduler.cancel(appContext, id)
        mutate { list -> list.filterNot { it.id == id } }
    }

    fun setEnabled(id: Int, enabled: Boolean) = update(id) { it.copy(enabled = enabled, snoozedUntil = null) }

    /** A quick alarm that rings [minutes] from now. */
    fun addQuick(minutes: Int, label: String = ""): Alarm {
        val at = System.currentTimeMillis() + minutes * 60_000L
        return addQuickAt(at, label)
    }

    /** A quick alarm that rings at an exact instant. */
    fun addQuickAt(at: Long, label: String = ""): Alarm {
        val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        val alarm = Alarm(
            id = newId(), hour = t.hour, minute = t.minute,
            quick = true, fireAt = at, label = label,
        )
        upsert(alarm)
        return alarm
    }

    /** Called the moment an alarm goes off. */
    fun onFired(id: Int) = update(id) { a ->
        when {
            a.quick || !a.repeats -> a.copy(enabled = false, snoozedUntil = null)
            else -> a.copy(snoozedUntil = null) // repeating: the next occurrence gets scheduled straight away
        }
    }

    fun snooze(id: Int, minutes: Int = SNOOZE_MINUTES) =
        update(id) { it.copy(snoozedUntil = System.currentTimeMillis() + minutes * 60_000L) }

    fun dismiss(id: Int) {
        val a = get(id) ?: return
        if (a.quick) delete(id) else update(id) { it.copy(snoozedUntil = null) }
    }

    /** Re-registers every alarm with AlarmManager (after boot, time change, app update). */
    fun rescheduleAll() = AlarmScheduler.sync(appContext, _alarms.value)

    private fun update(id: Int, f: (Alarm) -> Alarm) = mutate { list -> list.map { if (it.id == id) f(it) else it } }

    private fun mutate(f: (List<Alarm>) -> List<Alarm>) {
        val updated = synchronized(this) {
            val now = System.currentTimeMillis()
            // Quick alarms whose moment has passed without ringing (e.g. phone was off) are cleaned up.
            val next = f(_alarms.value).filterNot {
                it.quick && it.nextTrigger(now) == null && (it.fireAt ?: 0L) < now - STALE_QUICK_MS
            }
            _alarms.value = next
            prefs.edit().putString(KEY_LIST, JSONArray(next.map { it.toJson() }).toString()).apply()
            next
        }
        AlarmScheduler.sync(appContext, updated)
    }

    private fun load(): List<Alarm> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private const val PREFS = "alarmify"
    private const val KEY_LIST = "alarms"
    private const val KEY_NEXT_ID = "next_id"
    private const val STALE_QUICK_MS = 6 * 60 * 60 * 1000L
}
