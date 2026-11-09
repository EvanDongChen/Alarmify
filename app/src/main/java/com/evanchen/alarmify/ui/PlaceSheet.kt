package com.evanchen.alarmify.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evanchen.alarmify.data.LatLon
import com.evanchen.alarmify.data.Place
import com.evanchen.alarmify.data.PlaceAlarm
import com.evanchen.alarmify.data.PlaceRepository
import com.evanchen.alarmify.place.PlaceSearch
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RADII = listOf(100, 250, 500, 1000, 2000)

private sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Results(val places: List<Place>) : SearchState
    data object Offline : SearchState
}

/**
 * The place-alarm popup: search for somewhere (autocomplete as you type, nearest first), then choose how close
 * you need to get and start watching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSheet(onStarted: (PlaceAlarm) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val here by PlaceRepository.here.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf<Place?>(null) }
    var permissionAsked by remember { mutableStateOf(false) }
    var startWhenAllowed by remember { mutableStateOf<(() -> Unit)?>(null) }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            fetchHere(context)
            startWhenAllowed?.invoke()
        }
        startWhenAllowed = null
    }

    // Ask once, in context, so results can be sorted by distance and the sonar can show where you are.
    LaunchedEffect(Unit) {
        if (PlaceRepository.hasLocationPermission(context)) fetchHere(context)
        else if (!permissionAsked) {
            permissionAsked = true
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    fun close(then: () -> Unit = {}) {
        scope.launch { sheet.hide() }.invokeOnCompletion { then(); onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Night.Raised,
        shape = WaveTopShape(),
        dragHandle = {
            Box(Modifier.padding(top = 18.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Night.Rule))
        },
    ) {
        AnimatedContent(
            targetState = picked,
            transitionSpec = {
                val forward = targetState != null
                (fadeIn() + slideInHorizontally { if (forward) it / 4 else -it / 4 }) togetherWith
                    (fadeOut() + slideOutHorizontally { if (forward) -it / 4 else it / 4 })
            },
            label = "placeStep",
        ) { place ->
            if (place == null) {
                SearchStep(here = here, onPick = { picked = it })
            } else {
                SetupStep(
                    place = place,
                    here = here,
                    onBack = { picked = null },
                    onStart = { spot, radius, vibrate ->
                        val go = {
                            val alarm = PlaceRepository.start(spot, radius, vibrate)
                            close { onStarted(alarm) }
                        }
                        if (PlaceRepository.hasLocationPermission(context)) go()
                        else {
                            startWhenAllowed = go
                            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchHere(context: Context) {
    if (!PlaceRepository.hasLocationPermission(context)) return
    val client = LocationServices.getFusedLocationProviderClient(context)
    var fresh = false
    // A cached position shows something straight away; a fresh, precise one-off fix then replaces it.
    client.lastLocation.addOnSuccessListener { l ->
        if (l != null && !fresh) PlaceRepository.setHere(LatLon(l.latitude, l.longitude))
    }
    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { l ->
        if (l != null) { fresh = true; PlaceRepository.setHere(LatLon(l.latitude, l.longitude)) }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Step 1: search

@Composable
private fun SearchStep(here: LatLon?, onPick: (Place) -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    val recents by PlaceRepository.recents.collectAsStateWithLifecycle()

    // Autocomplete: wait for a pause in typing, then search (restarts on every keystroke).
    LaunchedEffect(query, here?.let { "%.2f,%.2f".format(it.lat, it.lon) }) {
        val q = query.trim()
        if (q.length < 2) { state = SearchState.Idle; return@LaunchedEffect }
        delay(280)
        state = SearchState.Searching
        state = try {
            val found = PlaceSearch.search(context, q, here)
            SearchState.Results(here?.let { h -> found.sortedBy { it.distanceTo(h.lat, h.lon) } }?.let { sorted ->
                // Keep the search engine's best match first, then nearest.
                (found.take(1) + sorted).distinct()
            } ?: found)
        } catch (e: PlaceSearch.Offline) {
            SearchState.Offline
        }
    }
    // Picking on the map: the spot under the pin, and a name for it once the map settles.
    var pin by remember { mutableStateOf<LatLon?>(null) }
    var pinPlace by remember { mutableStateOf<Place?>(null) }
    var naming by remember { mutableStateOf(false) }
    LaunchedEffect(pin) {
        val p = pin ?: return@LaunchedEffect
        pinPlace = null
        delay(600) // wait for the drag to settle
        naming = true
        pinPlace = PlaceSearch.reverse(context, p)?.copy(lat = p.lat, lon = p.lon)
        naming = false
    }

    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding().imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, start = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Place alarm", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp))
                Text(
                    "Rings once when you get close.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Night.FgSoft),
                )
            }
            FishGlyph(Modifier.size(width = 40.dp, height = 22.dp), color = Night.FgSoft)
        }

        // The search field.
        Row(
            Modifier
                .padding(top = 18.dp, bottom = 8.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Night.Bg)
                .border(1.dp, if (query.isNotEmpty()) Night.Accent.copy(alpha = 0.6f) else Night.Rule, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Night.FgSoft)
            Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        "Where are you going?",
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = Night.FgFaint),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, color = Night.Fg),
                    cursorBrush = SolidColor(Night.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                        .semantics { contentDescription = "Search for a place" },
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Night.Raised).pressable({ query = "" })
                        .semantics { contentDescription = "Clear search" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, null, tint = Night.FgSoft, modifier = Modifier.size(18.dp)) }
            }
        }

        // Nothing typed: recents as quick pills, then a map around you to drop a pin on.
        if (query.trim().length < 2) {
            if (recents.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                ) {
                    items(recents) { p ->
                        Row(
                            Modifier.clip(CircleShape).border(1.dp, Night.Rule, CircleShape).pressable({ onPick(p) })
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.History, null, tint = Night.FgSoft, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(p.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
            PinPicker(
                here = here,
                pin = pin,
                pinPlace = pinPlace,
                naming = naming,
                onMove = { pin = it },
                onSet = { spot ->
                    keyboard?.hide()
                    onPick(
                        pinPlace?.let { it.copy(name = "Near ${it.name}") }
                            ?: Place("Dropped pin", "%.5f, %.5f".format(spot.lat, spot.lon), spot.lat, spot.lon),
                    )
                },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
            )
            return@Column
        }

        // Typing: results as you go.
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            when (val s = state) {
                SearchState.Searching, SearchState.Idle -> item { Hint("Searching…") }
                SearchState.Offline -> item { Hint("Can't search right now. Check your connection and try again.") }
                is SearchState.Results ->
                    if (s.places.isEmpty()) item { Hint("No places found. Try a street, stop or landmark name.") }
                    else items(s.places) { p -> PlaceRow(p, query.trim(), here, recent = false) { keyboard?.hide(); onPick(p) } }
            }
        }
        Text(
            "Search by Photon · © OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.Normal),
            modifier = Modifier.padding(vertical = 8.dp).align(Alignment.CenterHorizontally),
        )
    }
}

/** A map centred on you with a pin in the middle: drag the map to put the pin anywhere, then set the alarm there. */
@Composable
private fun PinPicker(
    here: LatLon?,
    pin: LatLon?,
    pinPlace: Place?,
    naming: Boolean,
    onMove: (LatLon) -> Unit,
    onSet: (LatLon) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    Column(modifier) {
        if (here == null) {
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(Night.Bg)
                    .border(1.dp, Night.Rule, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    FishGlyph(Modifier.size(width = 56.dp, height = 30.dp))
                    Text(
                        if (PlaceRepository.hasLocationPermission(context)) "Finding you…"
                        else "Allow location to pick a spot on the map, or search above.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Night.FgSoft),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            return@Column
        }
        PlaceMap(
            start = here,
            radiusM = 0,
            here = here,
            onCenterChange = onMove,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        val spot = pin ?: here
        val away = here.let { h -> Place("", "", spot.lat, spot.lon).distanceTo(h.lat, h.lon) }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp)) {
            Text(
                when {
                    pin == null -> "Drag the map to put the pin where you want to wake up."
                    pinPlace != null -> "Near ${pinPlace.name}"
                    naming -> "Finding what's here…"
                    else -> "Dropped pin"
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 19.sp, color = Night.Fg),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (pin == null) "Or search above for a station, stop or address."
                else listOfNotNull(pinPlace?.address?.takeIf { it.isNotBlank() }, "${formatDistance(away)} from you").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(color = Night.FgSoft),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val ready = pin != null && away > 30f
        Box(
            Modifier.padding(top = 12.dp, bottom = 4.dp).fillMaxWidth().height(56.dp).clip(CircleShape)
                .background(if (ready) Night.Accent else Night.Rule)
                .pressable({ if (ready) onSet(spot) }, enabled = ready)
                .semantics { contentDescription = if (ready) "Set alarm at the pin" else "Move the map to choose a spot first" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (ready) "Set alarm here" else "Move the map to choose a spot",
                style = MaterialTheme.typography.labelLarge.copy(color = if (ready) Night.Bg else Night.FgFaint, fontSize = 17.sp),
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(color = Night.FgSoft),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 20.dp),
    )
}

/** One search result: a buoy, the name (the part you typed in bold), the address, and how far away it is. */
@Composable
private fun PlaceRow(place: Place, query: String, here: LatLon?, recent: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().pressable(onClick).padding(horizontal = 4.dp, vertical = 14.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Night.Bg).border(1.dp, Night.Rule, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (recent) Icon(Icons.Rounded.History, null, tint = Night.FgSoft, modifier = Modifier.size(18.dp))
                else Canvas(Modifier.size(18.dp)) {
                    drawCircle(Night.Accent, size.minDimension / 2f - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                    drawCircle(Night.Accent, size.minDimension / 5f)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    highlight(place.name, query),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, color = Night.Fg),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (place.address.isNotBlank()) {
                    Text(
                        place.address,
                        style = MaterialTheme.typography.bodySmall.copy(color = Night.FgSoft),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            here?.let {
                Text(
                    formatDistance(place.distanceTo(it.lat, it.lon)),
                    style = MaterialTheme.typography.labelMedium.copy(color = Night.FgSoft),
                )
            }
        }
        WaveRule()
    }
}

private fun highlight(text: String, query: String) = buildAnnotatedString {
    val i = if (query.isBlank()) -1 else text.indexOf(query, ignoreCase = true)
    if (i < 0) { append(text); return@buildAnnotatedString }
    append(text.substring(0, i))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Night.Fg)) { append(text.substring(i, i + query.length)) }
    append(text.substring(i + query.length))
}

// ---------------------------------------------------------------------------------------------------------------
// Step 2: how close, then start

@Composable
private fun SetupStep(place: Place, here: LatLon?, onBack: () -> Unit, onStart: (spot: Place, radiusM: Int, vibrate: Boolean) -> Unit) {
    val context = LocalContext.current
    var radius by remember { mutableIntStateOf(500) }
    var vibrate by remember { mutableStateOf(true) }
    var pops by remember { mutableIntStateOf(0) }
    // Where the alarm will ring: the place you picked, nudged by dragging the map.
    var spot by remember(place) { mutableStateOf(LatLon(place.lat, place.lon)) }
    var resets by remember { mutableIntStateOf(0) }
    val pinned = place.copy(lat = spot.lat, lon = spot.lon)
    val moved = place.distanceTo(spot.lat, spot.lon)
    val dist = here?.let { pinned.distanceTo(it.lat, it.lon) }
    val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val anyLocation = PlaceRepository.hasLocationPermission(context)

    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.94f).navigationBarsPadding()
            .padding(horizontal = 20.dp).padding(bottom = 16.dp),
    ) {
        Row(
            Modifier.padding(top = 4.dp).clip(CircleShape).pressable(onBack).padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Night.FgSoft, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Change place", style = MaterialTheme.typography.labelLarge.copy(color = Night.FgSoft))
        }

        Text(
            place.name, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        )
        if (place.address.isNotBlank()) {
            Text(
                place.address, style = MaterialTheme.typography.bodyMedium.copy(color = Night.FgSoft),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp),
            )
        }

        PlaceMap(
            start = LatLon(place.lat, place.lon),
            radiusM = radius,
            here = here,
            onCenterChange = { spot = it },
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().weight(1f),
            recenter = resets,
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (moved < 15f) "Drag the map to fine-tune the spot." else "Spot moved ${formatDistance(moved)} from ${place.name}.",
                style = MaterialTheme.typography.bodySmall.copy(color = Night.FgFaint),
                modifier = Modifier.weight(1f),
            )
            if (moved >= 15f) {
                Text(
                    "Reset",
                    style = MaterialTheme.typography.labelLarge.copy(color = Night.Accent),
                    modifier = Modifier.clip(CircleShape).pressable({ resets++ }).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            when {
                dist == null && !anyLocation -> "Allow location so Alarmify can tell when you're close."
                dist == null -> "Finding you…"
                dist <= radius -> "You're already within ${formatDistance(radius.toFloat())}."
                else -> "You're ${formatDistance(dist)} away."
            },
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 19.sp, color = Night.FgSoft),
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
        )

        SectionLabel("Ring when I'm within", Modifier.padding(top = 14.dp, bottom = 10.dp, start = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RADII.forEach { m -> Chip(formatDistance(m.toFloat()), radius == m, Modifier.weight(1f)) { radius = m } }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Vibrate", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            PaperSwitch(vibrate, { vibrate = it }, "Vibrate")
        }

        if (anyLocation && !precise) {
            Notice(
                "Precise location is off",
                "With only approximate location it may ring late.",
                "Settings",
            ) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
            }
        }

        val label = "Start · rings within ${formatDistance(radius.toFloat())}"
        Box(
            Modifier
                .padding(top = 14.dp)
                .bubbleBurst(pops)
                .fillMaxWidth()
                .height(60.dp)
                .clip(CircleShape)
                .background(Night.Accent)
                .pressable({ pops++; onStart(pinned, radius, vibrate) })
                .semantics { contentDescription = "Start place alarm for ${place.name}, ringing within ${formatDistance(radius.toFloat())}" },
            contentAlignment = Alignment.Center,
        ) { Text(label, style = MaterialTheme.typography.labelLarge.copy(color = Night.Bg, fontSize = 17.sp)) }
    }
}

/** The card in the alarm list while a place alarm is watching. */
@Composable
fun PlaceAlarmCard(alarm: PlaceAlarm, here: LatLon?, onStop: () -> Unit) {
    val context = LocalContext.current
    val dist = here?.let { alarm.place.distanceTo(it.lat, it.lon) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sonar(alarm.place, alarm.radiusM, here, Modifier.size(76.dp), compact = true)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text("WAKING YOU NEAR", style = MaterialTheme.typography.labelSmall.copy(color = Night.Accent))
            Text(
                alarm.place.name,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    !PlaceRepository.hasLocationPermission(context) -> "Location is off, so it can't watch"
                    dist == null -> "Finding you… · within ${formatDistance(alarm.radiusM.toFloat())}"
                    else -> "${formatDistance(dist)} away · rings within ${formatDistance(alarm.radiusM.toFloat())}"
                },
                style = MaterialTheme.typography.bodyMedium.copy(color = Night.FgSoft),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "Stop",
            style = MaterialTheme.typography.labelLarge.copy(color = Night.Fg),
            modifier = Modifier.clip(CircleShape).border(1.dp, Night.Rule, CircleShape).pressable(onStop)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
