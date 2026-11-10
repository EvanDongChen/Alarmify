package com.evanchen.alarmify.data

import android.location.Location
import org.json.JSONObject

/** A place you can search for and pick. */
data class Place(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
) {
    fun distanceTo(lat: Double, lon: Double): Float {
        val out = FloatArray(1)
        Location.distanceBetween(lat, lon, this.lat, this.lon, out)
        return out[0]
    }

    fun toJson(): JSONObject = JSONObject().put("name", name).put("address", address).put("lat", lat).put("lon", lon)

    companion object {
        fun fromJson(o: JSONObject) = Place(o.getString("name"), o.optString("address"), o.getDouble("lat"), o.getDouble("lon"))
    }
}

/** Rings once when you come within [radiusM] metres of [place]; then it's finished. */
data class PlaceAlarm(
    val id: Int,
    val place: Place,
    val radiusM: Int = 500,
    val vibrate: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("place", place.toJson()).put("radius", radiusM).put("vibrate", vibrate)

    companion object {
        fun fromJson(o: JSONObject) = PlaceAlarm(
            id = o.getInt("id"),
            place = Place.fromJson(o.getJSONObject("place")),
            radiusM = o.optInt("radius", 500),
            vibrate = o.optBoolean("vibrate", true),
        )
    }
}

/** A simple latitude/longitude pair (your position). */
data class LatLon(val lat: Double, val lon: Double)
