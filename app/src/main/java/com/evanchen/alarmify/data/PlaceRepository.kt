package com.evanchen.alarmify.data

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.evanchen.alarmify.place.PlaceTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Active place alarms, recently picked places, and your last known position. */
object PlaceRepository {
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences

    private val _active = MutableStateFlow<List<PlaceAlarm>>(emptyList())
    val active: StateFlow<List<PlaceAlarm>> = _active.asStateFlow()

    private val _recents = MutableStateFlow<List<Place>>(emptyList())
    val recents: StateFlow<List<Place>> = _recents.asStateFlow()

    /** Starred places, in the order they were starred. */
    private val _favourites = MutableStateFlow<List<Place>>(emptyList())
    val favourites: StateFlow<List<Place>> = _favourites.asStateFlow()

    /** Where you are, as last reported by the tracker or a one-off fix. */
    private val _here = MutableStateFlow<LatLon?>(null)
    val here: StateFlow<LatLon?> = _here.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _active.value = readList(KEY_ACTIVE) { PlaceAlarm.fromJson(it) }
        _recents.value = readList(KEY_RECENTS) { Place.fromJson(it) }
        _favourites.value = readList(KEY_FAVOURITES) { Place.fromJson(it) }
    }

    fun setHere(here: LatLon) { _here.value = here }

    /** Starts watching for [place]. Call while the app is on screen (location tracking must start in the foreground). */
    fun start(place: Place, radiusM: Int, vibrate: Boolean): PlaceAlarm {
        val id = synchronized(this) {
            val next = prefs.getInt(KEY_NEXT_ID, 1)
            prefs.edit().putInt(KEY_NEXT_ID, next + 1).apply()
            next
        }
        val alarm = PlaceAlarm(id, place, radiusM, vibrate)
        save(_active.value + alarm)
        remember(place)
        ensureTracking()
        return alarm
    }

    fun cancel(id: Int) {
        save(_active.value.filterNot { it.id == id })
        if (_active.value.isEmpty()) PlaceTrackingService.stop(appContext)
    }

    /** Puts a cancelled alarm back (undo). */
    fun restore(alarm: PlaceAlarm) {
        save(_active.value.filterNot { it.id == alarm.id } + alarm)
        ensureTracking()
    }

    /** Called by the tracker when you've arrived: the alarm is done. */
    fun arrived(id: Int) = save(_active.value.filterNot { it.id == id })

    /** (Re)starts the tracker if something is being watched and we're allowed to. */
    fun ensureTracking() {
        if (_active.value.isEmpty() || !hasLocationPermission(appContext)) return
        PlaceTrackingService.start(appContext)
    }

    fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The favourite at (about) the same spot as [place], if it's starred: within 25 m counts as the same place. */
    fun favouriteFor(place: Place): Place? =
        _favourites.value.firstOrNull { it.distanceTo(place.lat, place.lon) < 25f }

    fun toggleFavourite(place: Place) {
        val existing = favouriteFor(place)
        saveFavourites(if (existing != null) _favourites.value - existing else _favourites.value + place)
    }

    fun renameFavourite(place: Place, name: String) {
        val clean = name.trim().take(40).ifBlank { return }
        saveFavourites(_favourites.value.map { if (it == place) it.copy(name = clean) else it })
    }

    fun removeFavourite(place: Place) = saveFavourites(_favourites.value - place)

    /** Puts a removed favourite back where it was (undo). */
    fun restoreFavourite(place: Place, index: Int) {
        val list = _favourites.value.toMutableList()
        if (list.none { it.distanceTo(place.lat, place.lon) < 25f }) list.add(index.coerceIn(0, list.size), place)
        saveFavourites(list)
    }

    private fun saveFavourites(list: List<Place>) {
        _favourites.value = list
        prefs.edit().putString(KEY_FAVOURITES, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    private fun remember(place: Place) {
        val list = (listOf(place) + _recents.value.filterNot { it.name == place.name && it.address == place.address }).take(6)
        _recents.value = list
        prefs.edit().putString(KEY_RECENTS, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    private fun save(list: List<PlaceAlarm>) {
        _active.value = list
        prefs.edit().putString(KEY_ACTIVE, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    private fun <T> readList(key: String, parse: (org.json.JSONObject) -> T): List<T> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private const val PREFS = "alarmify_places"
    private const val KEY_ACTIVE = "active"
    private const val KEY_RECENTS = "recents"
    private const val KEY_FAVOURITES = "favourites"
    private const val KEY_NEXT_ID = "next_id"
}
