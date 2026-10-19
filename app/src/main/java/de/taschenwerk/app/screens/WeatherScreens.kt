package de.taschenwerk.app.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.composed
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.taschenwerk.app.data.GeoHit
import de.taschenwerk.app.data.Loc
import de.taschenwerk.app.data.Place
import de.taschenwerk.app.data.Store
import de.taschenwerk.app.data.WeatherRepo
import de.taschenwerk.app.data.weatherIcon
import de.taschenwerk.app.data.dayLook
import de.taschenwerk.app.data.sunHours
import de.taschenwerk.app.data.weatherText
import de.taschenwerk.app.ui.Card
import de.taschenwerk.app.ui.CircleButton
import de.taschenwerk.app.ui.Divider
import de.taschenwerk.app.ui.Ic
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.PillButton
import de.taschenwerk.app.ui.SectionTitle
import de.taschenwerk.app.ui.SmallButton
import de.taschenwerk.app.ui.TwIcon
import de.taschenwerk.app.ui.num
import de.taschenwerk.app.util.fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun deg(v: Double) = if (v.isNaN()) "–" else "${v.roundToInt()}°"

@Composable
fun WeatherScreen() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    val ask = rememberLocationAsk { scope.launch { WeatherRepo.refreshAll(ctx, store, force = true) } }
    LaunchedEffect(Unit) { WeatherRepo.refreshAll(ctx, store) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Place?>(null) }
    val w = WeatherRepo.home

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TwIcon(Ic.pin, size = 18.dp)
            Text(
                WeatherRepo.homeName, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .weight(1f), maxLines = 1
            )
            CircleButton(
                Ic.refresh, { scope.launch { WeatherRepo.refreshAll(ctx, store, force = true) } },
                size = 44.dp, bg = p.bg, iconSize = 20.dp, desc = "Aktualisieren"
            )
        }
        if (!Loc.hasPermission(ctx)) {
            Card {
                Text("Für das Wetter an deinem Standort bitte den Standort erlauben.", color = p.ink, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                SmallButton("Standort erlauben") { ask() }
            }
        }
        if (w == null) {
            Card {
                Text(
                    if (WeatherRepo.loading) "Wetter wird geladen …" else WeatherRepo.error ?: "Noch keine Daten",
                    color = p.sub, fontSize = 15.sp
                )
            }
        } else {
            // Hauptkarte: weiß, Farbe nur über Symbol und Akzente
            Card(padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            buildAnnotatedString {
                                append("${w.temp.roundToInt()}")
                                withStyle(SpanStyle(color = p.accent)) { append("°") }
                            },
                            style = num(76.sp, FontWeight.SemiBold), color = p.ink
                        )
                        Text("${w.look.text} · H ${deg(w.high)} · T ${deg(w.low)}", color = p.sub, fontSize = 15.sp)
                    }
                    TwIcon(w.look.icon, size = 72.dp, tint = weatherTint(w.look.tint, w.day))
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(p.track)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TwIcon(Ic.sun, size = 16.dp, tint = p.accent)
                    Text(
                        "${sunHours(w.sunH)} Sonnenstunden heute", color = p.ink, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Taupunkt", deg(w.dew), Ic.droplet, Modifier.weight(1f))
                StatTile("Wind", if (w.wind.isNaN()) "–" else "${w.wind.roundToInt()} km/h", Ic.wind, Modifier.weight(1f))
                StatTile("Gefühlt", deg(w.feels), Ic.thermo, Modifier.weight(1f))
            }
            Card(padding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    w.hours.forEach { h ->
                        Forecast(
                            h.label, h.look.icon, weatherTint(h.look.tint, h.day), h.temp, null,
                            Modifier.weight(1f), strongLabel = !h.past, sub = h.time, faded = h.past
                        )
                    }
                }
            }
            Text("Nächste 5 Tage", color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                w.days.take(5).forEachIndexed { i, d ->
                    val tinted = i % 2 == 1
                    val (dIcon, dTint) = dayLook(d)
                    Forecast(
                        d.label, dIcon, weatherTint(dTint, true), d.max, d.min,
                        Modifier
                            .weight(1f)
                            .tile(tinted, 22.dp),
                        strongLabel = true, padV = 14.dp, sunH = d.sunH, rainProb = d.precipProb
                    )
                }
            }
        }

        SectionTitle("Weitere Orte", "Hinzufügen") { adding = true }
        store.places.forEach { pl ->
            val ow = WeatherRepo.others[pl.name]
            Card(
                modifier = Modifier.pointerInput(pl) { detectTapGestures(onLongPress = { removing = pl }) },
                padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pl.name, color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (ow != null) "${ow.look.text} · Taupunkt ${deg(ow.dew)}" else "–",
                            color = p.sub, fontSize = 13.sp
                        )
                    }
                    if (ow != null) TwIcon(ow.look.icon, size = 26.dp, tint = weatherTint(ow.look.tint, ow.day))
                    Text(
                        if (ow != null) deg(ow.temp) else "–", style = num(30.sp, FontWeight.SemiBold), color = p.ink,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(70.dp)
                            .padding(start = 8.dp)
                    )
                }
            }
        }
        Text("Lange drücken zum Entfernen · Daten: Open-Meteo", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(10.dp))
    }

    if (adding) AddPlaceDialog(store) { adding = false }
    val rm = removing
    if (rm != null) {
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = p.card,
            title = { Text("${rm.name} entfernen?", color = p.ink) },
            confirmButton = {
                TextButton({
                    store.places.remove(rm)
                    store.savePlaces()
                    removing = null
                }) { Text("Entfernen", color = p.ink) }
            },
            dismissButton = { TextButton({ removing = null }) { Text("Abbrechen", color = p.sub) } }
        )
    }
}

/**
 * Farbe der Wettersymbole, einheitlich aus der Schemafarbe abgeleitet:
 * Sonne/klar in voller Schemafarbe, Wolken/Nebel weicher, Regen/Schnee/Gewitter tiefer.
 */
@Composable
fun weatherTint(code: Int, day: Boolean): Color {
    val p = LocalPal.current
    return when (code) {
        0, 1 -> if (day) p.fill else lerp(p.fill, p.sub, 0.35f)
        2, 3, 45, 48 -> lerp(p.fill, p.sub, 0.5f)
        else -> lerp(p.fill, p.ink, if (p.dark) 0.15f else 0.38f)
    }
}

private fun Modifier.tile(tinted: Boolean, radius: androidx.compose.ui.unit.Dp): Modifier = composed {
    val p = LocalPal.current
    val shape = RoundedCornerShape(radius)
    var m = this.clip(shape).background(if (tinted) p.fill.copy(alpha = if (p.dark) 0.14f else 0.2f) else p.card)
    if (!tinted && p.cardRing != Color.Transparent) m = m.border(1.dp, p.cardRing, shape)
    m
}

@Composable
private fun StatTile(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    val p = LocalPal.current
    Column(
        modifier
            .tile(false, 18.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TwIcon(icon, tint = p.fill, size = 15.dp)
            Text(label, color = p.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp), maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = num(21.sp, FontWeight.SemiBold), color = p.ink, maxLines = 1)
    }
}

/** Gradzahl, deren Ziffern exakt mittig stehen (das °-Zeichen wird links unsichtbar ausgeglichen). */
private fun centeredDeg(v: Double) = buildAnnotatedString {
    if (v.isNaN()) {
        append("–")
    } else {
        withStyle(SpanStyle(color = Color.Transparent)) { append("°") }
        append("${v.roundToInt()}°")
    }
}

@Composable
private fun Forecast(
    label: String, icon: ImageVector, tint: Color, temp: Double, low: Double?, modifier: Modifier,
    strongLabel: Boolean = false, padV: androidx.compose.ui.unit.Dp = 4.dp,
    sub: String? = null, faded: Boolean = false, sunH: Double? = null, rainProb: Double? = null
) {
    val p = LocalPal.current
    Column(
        modifier
            .padding(vertical = padV)
            .alpha(if (faded) 0.4f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label, color = if (strongLabel) p.ink else p.sub, fontSize = 13.sp,
                fontWeight = if (strongLabel) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            if (sub != null) Text(
                sub, color = p.sub, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TwIcon(icon, size = 24.dp, tint = tint) }
        Text(
            centeredDeg(temp), style = num(16.sp, FontWeight.SemiBold), color = p.ink,
            maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        if (low != null) Text(
            centeredDeg(low), style = num(13.sp, FontWeight.Normal), color = p.sub,
            maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        if (sunH != null) {
            Row(
                Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                TwIcon(Ic.sun, size = 12.dp, tint = p.accent)
                Text(
                    "${sunHours(sunH)} h", style = num(12.sp, FontWeight.SemiBold), color = p.accent,
                    maxLines = 1, modifier = Modifier.padding(start = 3.dp)
                )
            }
        }
        if (rainProb != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                TwIcon(Ic.droplet, size = 11.dp, tint = p.sub)
                Text(
                    if (rainProb.isNaN()) "–" else "${rainProb.roundToInt()} %",
                    style = num(12.sp, FontWeight.Medium), color = p.sub,
                    maxLines = 1, modifier = Modifier.padding(start = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun AddPlaceDialog(store: Store, onClose: () -> Unit) {
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<GeoHit>>(emptyList()) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        hits = runCatching { WeatherRepo.search(q.trim()) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = p.card,
        title = { Text("Ort hinzufügen", color = p.ink) },
        confirmButton = { TextButton(onClose) { Text("Schließen", color = p.ink) } },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.track)
                        .padding(12.dp)
                ) {
                    BasicTextField(
                        value = q, onValueChange = { q = it }, singleLine = true,
                        textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
                        cursorBrush = SolidColor(p.ink), modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner -> if (q.isEmpty()) Text("Stadt suchen …", color = p.muted); inner() }
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(hits) { h ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val pl = Place(h.name, h.lat, h.lon)
                                    if (store.places.none { it.name == pl.name }) {
                                        store.places.add(pl)
                                        store.savePlaces()
                                        WeatherRepo.refreshPlace(pl)
                                    }
                                    onClose()
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(h.name, color = p.ink, fontSize = 16.sp)
                            if (h.detail.isNotEmpty()) Text(h.detail, color = p.sub, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    )
}

// ------------------------------------------------------------------ Höhe

@Composable
fun AltitudeScreen() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    val scope = rememberCoroutineScope()

    var pressure by remember { mutableStateOf<Float?>(null) }
    var gps by remember { mutableStateOf<Location?>(null) }
    var gpsBusy by remember { mutableStateOf(false) }
    var terrain by remember { mutableStateOf<Double?>(null) }
    var base by remember { mutableStateOf(store.getDouble("altBase", Double.NaN)) }
    var baseTime by remember { mutableStateOf(store.getString("altBaseTime") ?: "") }
    var refreshKey by remember { mutableStateOf(0) }

    val hasBaro = remember {
        (ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = e.values[0]
                val old = pressure
                pressure = if (old == null) v else old * 0.85f + v * 0.15f
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }

    val ask = rememberLocationAsk { refreshKey++ }
    LaunchedEffect(refreshKey) {
        if (!Loc.hasPermission(ctx)) return@LaunchedEffect
        WeatherRepo.refreshAll(ctx, store, force = refreshKey > 0)
        terrain = WeatherRepo.elevation(WeatherRepo.homeLat, WeatherRepo.homeLon)
        gpsBusy = true
        gps = Loc.gps(ctx) ?: gps
        gpsBusy = false
        // Geländehöhe genau für die GPS-Position nachladen
        gps?.let { g -> WeatherRepo.elevation(g.latitude, g.longitude)?.let { terrain = it } }
    }

    val msl = WeatherRepo.home?.pressureMsl?.takeIf { !it.isNaN() }
    val baroAlt = pressure?.let { SensorManager.getAltitude((msl ?: 1013.25).toFloat(), it).toDouble() }
    val gpsAlt = gps?.takeIf { it.hasAltitude() }?.altitude
    val main = terrain ?: baroAlt ?: gpsAlt
    val current = baroAlt ?: gpsAlt
    val diff = if (!base.isNaN() && current != null) current - base else null

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TwIcon(Ic.pin, size = 18.dp)
            Text(
                WeatherRepo.homeName, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .weight(1f), maxLines = 1
            )
            CircleButton(Ic.refresh, { refreshKey++ }, size = 44.dp, bg = p.bg, iconSize = 20.dp, desc = "Neu messen")
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Höhe über dem Meeresspiegel", color = p.sub, fontSize = 14.sp)
            Text(
                buildAnnotatedString {
                    append(if (main != null) fmt(main, 0) else "–")
                    withStyle(SpanStyle(color = p.accent, fontSize = 44.sp)) { append(" m") }
                },
                style = num(78.sp), color = p.ink
            )
            val acc = gps?.takeIf { it.hasVerticalAccuracy() }?.verticalAccuracyMeters
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(p.card)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    when {
                        !Loc.hasPermission(ctx) -> "Standort nicht erlaubt"
                        gpsBusy -> "GPS misst …"
                        acc != null -> "GPS-Genauigkeit ± ${acc.roundToInt()} m"
                        else -> "Beste verfügbare Schätzung"
                    },
                    color = p.sub, fontSize = 13.sp
                )
            }
        }
        if (!Loc.hasPermission(ctx)) {
            SmallButton("Standort erlauben", Modifier.fillMaxWidth()) { ask() }
        }
        Card(padding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
            SourceRow(Ic.crosshair, "GPS", if (gpsBusy && gps == null) "misst …" else "Satelliten", gpsAlt?.let { fmt(it, 0) + " m" } ?: "–")
            Divider()
            SourceRow(Ic.map, "Karte", "Geländehöhe am Standort", terrain?.let { fmt(it, 0) + " m" } ?: "–")
            Divider()
            SourceRow(
                Ic.gauge, "Barometer",
                if (!hasBaro) "kein Sensor im Handy" else pressure?.let { "${fmt(it.toDouble(), 0)} hPa Luftdruck" } ?: "–",
                baroAlt?.let { fmt(it, 0) + " m" } ?: "–"
            )
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Höhenunterschied", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (base.isNaN()) "Nullpunkt tippen zum Messen" else "seit Nullpunkt $baseTime", color = p.sub, fontSize = 13.sp)
                }
                if (diff != null) Text(
                    (if (diff >= 0) "+" else "") + fmt(diff, 0) + " m",
                    style = num(30.sp, FontWeight.SemiBold), color = p.accent
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SmallButton("Nullpunkt", Modifier.width(160.dp)) {
                    if (current != null) {
                        base = current
                        baseTime = java.time.LocalTime.now().withSecond(0).withNano(0).toString()
                        store.putDouble("altBase", current)
                        store.putString("altBaseTime", baseTime)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SourceRow(icon: ImageVector, title: String, sub: String, value: String) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(p.track),
            contentAlignment = Alignment.Center
        ) { TwIcon(icon, size = 20.dp) }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = p.sub, fontSize = 13.sp, maxLines = 1)
        }
        Text(value, style = num(20.sp), color = p.ink)
    }
}
