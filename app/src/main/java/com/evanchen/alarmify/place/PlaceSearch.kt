package com.evanchen.alarmify.place

import android.content.Context
import android.location.Geocoder
import com.evanchen.alarmify.data.LatLon
import com.evanchen.alarmify.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Search-as-you-type for places: Photon (OpenStreetMap data, no key needed, built for autocomplete), nudged towards
 * where you are; Android's own geocoder if Photon can't be reached.
 */
object PlaceSearch {
    class Offline : Exception()

    suspend fun search(context: Context, query: String, near: LatLon?): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val photon = runCatching { photon(q, near) }
        photon.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        val local = runCatching { geocoder(context, q) }.getOrDefault(emptyList())
        if (local.isEmpty() && photon.isFailure) throw Offline()
        local
    }

    private fun photon(q: String, near: LatLon?): List<Place> {
        val url = buildString {
            append("https://photon.komoot.io/api/?limit=8&lang=en&q=")
            append(URLEncoder.encode(q, "UTF-8"))
            if (near != null) append("&lat=${near.lat}&lon=${near.lon}")
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
            setRequestProperty("User-Agent", "Alarmify/1.0 (Android alarm app)")
        }
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val features = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("features")
            return (0 until features.length()).mapNotNull { i ->
                val f = features.getJSONObject(i)
                val p = f.getJSONObject("properties")
                val coords = f.getJSONObject("geometry").getJSONArray("coordinates")
                val street = listOfNotNull(p.optStringOrNull("housenumber"), p.optStringOrNull("street")).joinToString(" ")
                val name = p.optStringOrNull("name") ?: street.ifBlank { null } ?: return@mapNotNull null
                val address = listOf(
                    street.takeIf { it != name },
                    p.optStringOrNull("district"),
                    p.optStringOrNull("city"),
                    p.optStringOrNull("state"),
                ).filterNotNull().filter { it.isNotBlank() && it != name }.distinct().take(3).joinToString(", ")
                Place(name, address, coords.getDouble(1), coords.getDouble(0))
            }.distinctBy { "${it.name}|${it.address}" }
        } finally {
            conn.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun geocoder(context: Context, q: String): List<Place> {
        if (!Geocoder.isPresent()) return emptyList()
        return Geocoder(context, Locale.getDefault()).getFromLocationName(q, 6).orEmpty().map { a ->
            val line = a.getAddressLine(0).orEmpty()
            val name = a.featureName?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) } ?: line.substringBefore(",")
            Place(name, line.removePrefix(name).trimStart(',', ' '), a.latitude, a.longitude)
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? = optString(key, "").takeIf { it.isNotBlank() }
}
