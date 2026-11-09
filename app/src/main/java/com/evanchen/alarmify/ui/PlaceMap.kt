package com.evanchen.alarmify.ui

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.evanchen.alarmify.data.LatLon
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.io.File
import kotlin.math.atan2
import kotlin.math.max

/**
 * A draggable map for choosing exactly where a place alarm should ring. The buoy stays in the middle of the map;
 * drag (and pinch) the map under it to move the spot, like dropping a pin in a transit app. The dashed ring is how
 * close you need to get, and the fish is you.
 *
 * [onCenterChange] reports the spot under the buoy whenever the map moves.
 */
@Composable
fun PlaceMap(
    start: LatLon,
    radiusM: Int,
    here: LatLon?,
    onCenterChange: (LatLon) -> Unit,
    modifier: Modifier = Modifier,
    recenter: Int = 0,
) {
    val context = LocalContext.current
    val onCenter by rememberUpdatedState(onCenterChange)
    val currentHere by rememberUpdatedState(here)
    val currentRadius by rememberUpdatedState(radiusM)
    var map by remember { mutableStateOf<MapView?>(null) }
    // Bumped whenever the map moves, so the overlay redraws against the new camera.
    var moves by remember { mutableIntStateOf(0) }
    val clock = rememberAmbientTime()

    fun fit(m: MapView, animated: Boolean) {
        val center = m.mapCenter.let { LatLon(it.latitude, it.longitude) }
        // With no ring yet (dropping a pin), open on a few blocks around you so nearby spots are one drag away.
        val pad = if (currentRadius == 0) 900.0 else max(currentRadius * 1.8, 300.0)
        val pts = buildList {
            add(GeoPoint(center.lat, center.lon))
            currentHere?.let { add(GeoPoint(it.lat, it.lon)) }
        }
        // Grow the box by the ring (degrees ≈ metres / 111 km), so the whole circle shows.
        val dLat = pad / 111_000.0
        val dLon = pad / (111_000.0 * kotlin.math.cos(Math.toRadians(center.lat)))
        val box = BoundingBox(
            pts.maxOf { it.latitude } + dLat, pts.maxOf { it.longitude } + dLon,
            pts.minOf { it.latitude } - dLat, pts.minOf { it.longitude } - dLon,
        )
        // Keep the buoy where it is (centred on the destination), just pick a zoom that fits.
        val zoom = MapView.getTileSystem().getBoundingBoxZoom(box, m.width - 80, m.height - 80).coerceIn(4.0, 18.5)
        if (animated) m.controller.animateTo(GeoPoint(center.lat, center.lon), zoom, 400L)
        else { m.controller.setZoom(zoom); m.controller.setCenter(GeoPoint(center.lat, center.lon)) }
    }

    Box(modifier.clip(RoundedCornerShape(24.dp)).border(1.dp, Night.Rule, RoundedCornerShape(24.dp))) {
        AndroidView(
            factory = { ctx ->
                createMap(ctx, start).also { m ->
                    m.addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            moves++
                            onCenter(m.mapCenter.let { LatLon(it.latitude, it.longitude) })
                            return false
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean { moves++; return false }
                    })
                    m.addOnFirstLayoutListener { _, _, _, _, _ -> fit(m, false); moves++ }
                    map = m
                }
            },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Map. Drag to move the alarm spot; pinch to zoom." },
            onRelease = { it.onDetach() },
        )

        // The ring, buoy and fish, drawn over the map in the ocean style.
        Canvas(Modifier.fillMaxSize()) {
            moves // read, so this redraws when the map moves
            val m = map ?: return@Canvas
            val time = clock.value
            val c = center
            val proj = m.projection
            val centre = m.mapCenter
            val rPx = proj.metersToPixels(currentRadius.toFloat(), centre.latitude, m.zoomLevelDouble)

            if (currentRadius > 0) {
                drawCircle(Night.Fg.copy(alpha = 0.05f), rPx, c)
                drawCircle(
                    Night.Accent.copy(alpha = 0.85f), rPx, c,
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }

            currentHere?.let { h ->
                val p = proj.toPixels(GeoPoint(h.lat, h.lon), null)
                val pos = Offset(p.x.toFloat(), p.y.toFloat())
                val towards = Math.toDegrees(atan2((c.y - pos.y).toDouble(), (c.x - pos.x).toDouble())).toFloat()
                drawCircle(Night.Bg.copy(alpha = 0.75f), 18.dp.toPx(), pos)
                drawCircle(Night.Fg.copy(alpha = 0.18f), 18.dp.toPx(), pos, style = Stroke(1.dp.toPx()))
                drawFish(pos, 26.dp.toPx(), towards, Night.Fg, sway = kotlin.math.sin(time * 6f) * 0.5f)
            }

            // The buoy, with a ripple, always dead centre.
            val pulse = (time % 2.4f) / 2.4f
            drawCircle(Night.Accent.copy(alpha = (1f - pulse) * 0.45f), 8.dp.toPx() + pulse * 22.dp.toPx(), c)
            drawCircle(Night.Bg, 10.dp.toPx(), c)
            drawCircle(Night.Accent, 7.5.dp.toPx(), c)
            drawCircle(Night.Bg.copy(alpha = 0.6f), 2.5.dp.toPx(), c + Offset(-2.5f, -2.5f) * density)
        }

        // Re-frame both you and the destination.
        Box(
            Modifier.align(Alignment.TopEnd).padding(10.dp).size(40.dp).clip(CircleShape)
                .background(Night.Bg.copy(alpha = 0.85f)).border(1.dp, Night.Rule, CircleShape)
                .pressable({ map?.let { fit(it, true) } })
                .semantics { contentDescription = "Show me and the destination" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.CenterFocusStrong, null, tint = Night.Fg, modifier = Modifier.size(20.dp)) }

        Text(
            "Esri · HERE · Garmin · © OpenStreetMap",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.sp, color = Night.FgSoft),
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                .background(Night.Bg.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }

    // Reset: glide back to where we started.
    androidx.compose.runtime.LaunchedEffect(recenter) {
        if (recenter > 0) map?.controller?.animateTo(GeoPoint(start.lat, start.lon), map?.zoomLevelDouble, 450L)
    }

    DisposableEffect(map) {
        map?.onResume()
        onDispose { map?.onPause() }
    }
}

/** Sets up an OpenStreetMap view with Esri's dark grey tiles, tinted to the app's deep-water teal. */
private fun createMap(context: Context, start: LatLon): MapView {
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        val cache = File(context.cacheDir, "osmdroid")
        osmdroidBasePath = cache
        osmdroidTileCache = File(cache, "tiles")
    }
    return MapView(context).apply {
        setTileSource(DARK_TILES)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        isHorizontalMapRepetitionEnabled = false
        isVerticalMapRepetitionEnabled = false
        minZoomLevel = 3.0
        maxZoomLevel = 19.0
        setBackgroundColor(android.graphics.Color.rgb(6, 20, 26))
        overlayManager.tilesOverlay.setLoadingBackgroundColor(android.graphics.Color.rgb(6, 20, 26))
        overlayManager.tilesOverlay.setLoadingLineColor(android.graphics.Color.rgb(14, 40, 48))
        overlayManager.tilesOverlay.setColorFilter(OCEAN_TINT)
        controller.setZoom(15.0)
        controller.setCenter(GeoPoint(start.lat, start.lon))
        // Inside a bottom sheet: keep drags on the map instead of letting the sheet take them.
        setOnTouchListener { v, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }
}

/** Esri's dark grey canvas: a quiet, label-light basemap (free with attribution, no key). Tiles are z/y/x. */
private val DARK_TILES = object : OnlineTileSourceBase(
    "EsriDarkGray", 0, 16, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/"),
    "Esri, HERE, Garmin, © OpenStreetMap contributors",
) {
    override fun getTileURLString(index: Long): String =
        baseUrl + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index)
}

/** Maps the tiles' greys onto the app's water: black → deep teal, light grey → seafoam. */
private val OCEAN_TINT = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            0.299f * 0.42f, 0.587f * 0.42f, 0.114f * 0.42f, 0f, 0f,
            0.299f * 0.58f, 0.587f * 0.58f, 0.114f * 0.58f, 0f, 8f,
            0.299f * 0.62f, 0.587f * 0.62f, 0.114f * 0.62f, 0f, 12f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)
