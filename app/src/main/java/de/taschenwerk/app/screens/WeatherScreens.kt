package de.taschenwerk.app.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
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
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        buildAnnotatedString {
                            append("${w.temp.roundToInt()}")
                            withStyle(SpanStyle(color = p.accent)) { append("°") }
                        },
                        style = num(80.sp), color = p.ink
                    )
                    Text("${weatherText(w.code)} · H ${deg(w.high)} · T ${deg(w.low)}", color = p.sub, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TwIcon(Ic.sun, size = 17.dp)
                        Text(
                            "${fmt(w.sunH, 1)} Sonnenstunden heute", color = p.ink, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                TwIcon(weatherIcon(w.code, w.day), size = 76.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Taupunkt", deg(w.dew), Ic.droplet, Modifier.weight(1f))
                StatTile("Wind", if (w.wind.isNaN()) "–" else "${w.wind.roundToInt()} km/h", Ic.wind, Modifier.weight(1f))
                StatTile("Gefühlt", deg(w.feels), Ic.thermo, Modifier.weight(1f))
            }
            Card(padding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                Row {
                    Forecast("Jetzt", weatherIcon(w.code, w.day), deg(w.temp), null, true, Modifier.weight(1f))
                    w.hours.take(4).forEach { h ->
                        Forecast(h.label, weatherIcon(h.code, h.day), deg(h.temp), null, false, Modifier.weight(1f))
                    }
                }
            }
            Card(padding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                Text("Nächste 5 Tage", color = p.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                Row {
                    w.days.take(5).forEach { d ->
                        Forecast(d.label, weatherIcon(d.code), deg(d.max), deg(d.min), true, Modifier.weight(1f))
                    }
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
                            if (ow != null) "${weatherText(ow.code)} · Taupunkt ${deg(ow.dew)}" else "–",
                            color = p.sub, fontSize = 13.sp
                        )
                    }
                    if (ow != null) TwIcon(weatherIcon(ow.code, ow.day), size = 26.dp)
                    Text(
                        if (ow != null) deg(ow.temp) else "–", style = num(30.sp), color = p.ink,
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

@Composable
private fun StatTile(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    val p = LocalPal.current
    Card(modifier = modifier, radius = 18.dp, padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TwIcon(icon, tint = p.sub, size = 14.dp)
            Text(label, color = p.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp), maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = num(21.sp), color = p.ink, maxLines = 1)
    }
}

@Composable
private fun Forecast(label: String, icon: ImageVector, temp: String, low: String?, bold: Boolean, modifier: Modifier) {
    val p = LocalPal.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = if (bold) p.ink else p.sub, fontSize = 13.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        TwIcon(icon, size = 24.dp)
        Text(
            buildAnnotatedString {
                append(temp)
                if (low != null) withStyle(SpanStyle(color = p.sub, fontWeight = FontWeight.Normal)) { append(" $low") }
            },
            style = num(15.sp), color = p.ink
        )
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
                                        scope.launch {
                                            runCatching { WeatherRepo.others[pl.name] = WeatherRepo.fetch(pl.lat, pl.lon, pl.name) }
                                        }
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
                    Text(if (base.isNaN()) "Nullpunkt setzen zum Messen" else "seit Nullpunkt $baseTime", color = p.sub, fontSize = 13.sp)
                }
                Text(
                    diff?.let { (if (it >= 0) "+" else "") + fmt(it, 0) + " m" } ?: "–",
                    style = num(30.sp), color = p.accent
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallButton("Nullpunkt setzen", Modifier.weight(1f)) {
                    if (current != null) {
                        base = current
                        baseTime = java.time.LocalTime.now().withSecond(0).withNano(0).toString()
                        store.putDouble("altBase", current)
                        store.putString("altBaseTime", baseTime)
                    }
                }
                SmallButton("Kalibrieren", Modifier.weight(1f)) { refreshKey++ }
            }
        }
        Text(
            "Barometer = Luftdruck, mit aktuellem Luftdruck auf Meereshöhe kalibriert. Karte = Geländehöhe am Standort.",
            color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)
        )
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
