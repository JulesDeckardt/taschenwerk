package de.taschenwerk.app.screens

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.taschenwerk.app.Nav
import de.taschenwerk.app.Overlay
import de.taschenwerk.app.Tab
import de.taschenwerk.app.alarm.AlarmScheduler
import de.taschenwerk.app.alarm.TimerStore
import de.taschenwerk.app.data.Alarm
import de.taschenwerk.app.data.Loc
import de.taschenwerk.app.data.Store
import de.taschenwerk.app.data.WeatherRepo
import de.taschenwerk.app.data.sunHours
import de.taschenwerk.app.ui.Card
import de.taschenwerk.app.ui.Ic
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.Toggle
import de.taschenwerk.app.ui.TwIcon
import de.taschenwerk.app.ui.num
import de.taschenwerk.app.util.fmt
import de.taschenwerk.app.util.two
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.roundToInt

// ------------------------------------------------------------------ Zweite Stadt

/** Zone der zweiten Stadt (Einstellungen → Startseite), Standard Berlin. */
fun secondZone(store: Store): String = store.getString("clock2Zone") ?: "Europe/Berlin"

/** Kurzer Städtename für eine Zeitzone („Mexiko-Stadt“ → „Mexiko“). */
fun shortCity(zone: String): String = cityForZone(zone).removeSuffix("-Stadt")

/** Uhrzeit eines Zeitpunkts in der zweiten Stadt. */
fun secondTime(store: Store, t: ZonedDateTime): ZonedDateTime =
    runCatching { t.withZoneSameInstant(ZoneId.of(secondZone(store))) }.getOrDefault(t)

/** z. B. „Berlin 14:30“ für den nächsten Klingeltermin eines Weckers. */
fun alarmSecondText(store: Store, a: Alarm): String {
    val t = secondTime(store, AlarmScheduler.nextTrigger(a))
    return "${shortCity(secondZone(store))} ${two(t.hour)}:${two(t.minute)}"
}

// ------------------------------------------------------------------ Bausteine

/** Kacheln zeigen einen kleinen Pfeil, wenn die untere Leiste keine Bereiche zeigt. */
val showOpenArrows: Boolean get() = NavCfg.mode != NavCfg.TABS

/** Kleiner Pfeil oben rechts in der Kachel-Ecke: „antippen öffnet den ganzen Bereich“. */
@Composable
fun BoxScope.OpenArrow() {
    if (!showOpenArrows) return
    TwIcon(
        Ic.openArrow, tint = LocalPal.current.muted, size = 14.dp,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 6.dp, end = 7.dp)
    )
}

/** Trennlinie über die ganze Kachelbreite (über den Innenabstand hinaus). */
@Composable
fun BleedDivider(side: Dp, vertical: Dp) {
    val p = LocalPal.current
    Box(
        Modifier
            .padding(vertical = vertical)
            .layout { m, c ->
                val extra = side.roundToPx() * 2
                val w = c.maxWidth + extra
                val pl = m.measure(Constraints.fixed(w, 1.dp.roundToPx().coerceAtLeast(1)))
                layout(c.maxWidth, pl.height) { pl.place(-side.roundToPx(), 0) }
            }
            .background(p.line)
    )
}

/**
 * Reihe, in der jeder Knopf so breit ist wie sein Inhalt und der übrige Platz gleichmäßig verteilt wird –
 * so hat jeder Knopf links und rechts gleich viel Luft. Reicht der Platz nicht, wird gleichmäßig gestaucht.
 */
@Composable
fun EqualPadRow(gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { ms, c ->
        val g = gap.roundToPx()
        val n = ms.size
        if (n == 0) return@Layout layout(c.maxWidth, 0) {}
        val natural = ms.map { it.maxIntrinsicWidth(c.maxHeight.coerceAtMost(10_000)) }
        val avail = c.maxWidth - g * (n - 1)
        val sum = natural.sum()
        val widths = if (sum <= avail) {
            val extra = (avail - sum) / n
            natural.mapIndexed { i, w -> w + extra + if (i == n - 1) (avail - sum) - extra * n else 0 }
        } else natural.map { (it.toLong() * avail / sum).toInt() }
        val ps = ms.mapIndexed { i, m -> m.measure(Constraints.fixedWidth(widths[i].coerceAtLeast(0))) }
        val h = ps.maxOf { it.height }
        layout(c.maxWidth, h) {
            var x = 0
            ps.forEach { p -> p.place(x, (h - p.height) / 2); x += p.width + g }
        }
    }
}

private fun clockText(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "$h:${two(m.toInt())}:${two(s.toInt())}" else "${two(m.toInt())}:${two(s.toInt())}"
}

/** Laufende Zeit für Timer und Stoppuhr (null = läuft nicht). Liest [tick], damit die Anzeige mitläuft. */
fun liveTime(id: String, tick: Long): String? {
    val nowMs = if (tick >= 0L) System.currentTimeMillis() else 0L
    return when {
        id == "timer" && TimerStore.running -> clockText(TimerStore.endAt - nowMs + 999)
        id == "timer" && TimerStore.paused -> clockText(TimerStore.pausedLeft + 999)
        id == "stopwatch" && (Stopwatch.running || Stopwatch.acc > 0L) -> clockText(Stopwatch.elapsed())
        else -> null
    }
}

/** Tick alle 250 ms, solange Timer oder Stoppuhr laufen. */
@Composable
fun rememberLiveTick(): Long {
    val ctx = LocalContext.current
    remember { TimerStore.load(ctx); Stopwatch.load(Store.get(ctx)); 0 }
    val live = TimerStore.running || TimerStore.paused || Stopwatch.running || Stopwatch.acc > 0L
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(live) {
        while (live) {
            tick = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    return tick
}

// ------------------------------------------------------------------ Kopf: Datum + Uhrzeiten (+ Wetter)

private object HomeAlt {
    var key = ""
    var value: Double? = null
}

/** Geländehöhe am Wetter-Ort (einmal pro Ort geladen und gemerkt). */
@Composable
fun rememberHomeAltitude(store: Store): Double? {
    val lat = WeatherRepo.homeLat
    val lon = WeatherRepo.homeLon
    val k = "%.3f,%.3f".format(Locale.US, lat, lon)
    var alt by remember(k) {
        mutableStateOf(
            if (HomeAlt.key == k) HomeAlt.value
            else if (store.getString("homeAltKey") == k) store.getDouble("homeAlt", Double.NaN).takeIf { !it.isNaN() }
            else null
        )
    }
    LaunchedEffect(k) {
        if (alt == null || HomeAlt.key != k) {
            WeatherRepo.elevation(lat, lon)?.let {
                alt = it
                HomeAlt.key = k
                HomeAlt.value = it
                store.putString("homeAltKey", k)
                store.putDouble("homeAlt", it)
            }
        }
    }
    return alt
}

@Composable
private fun DateRow(nav: Nav, store: Store) {
    val p = LocalPal.current
    val now by rememberNow(1000)
    val there = secondTime(store, ZonedDateTime.now())
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { nav.go(Tab.UHR, 3) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Kalenderblatt
        Column(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(p.fill.copy(alpha = if (p.dark) 0.22f else 0.42f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                now.format(DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)).take(3).uppercase(Locale.GERMAN),
                color = p.sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp
            )
            Text("${now.dayOfMonth}", color = p.ink, style = num(17.sp, FontWeight.Bold).copy(lineHeight = 18.sp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)),
                color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1
            )
            Text("KW ${now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", color = p.sub, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            ClockLine(shortCity(ZoneId.systemDefault().id), "${two(now.hour)}:${two(now.minute)}")
            ClockLine(shortCity(secondZone(store)), "${two(there.hour)}:${two(there.minute)}")
        }
        // ⋮ ohne Kreis, mit etwas Abstand
        Box(
            Modifier
                .padding(start = 4.dp)
                .size(width = 30.dp, height = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { nav.overlay = Overlay.Settings },
            contentAlignment = Alignment.Center
        ) { TwIcon(Ic.more, size = 20.dp, desc = "Einstellungen") }
    }
}

@Composable
private fun ClockLine(city: String, time: String) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(city, color = p.sub, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.width(7.dp))
        Text(time, color = p.ink, style = num(15.sp, FontWeight.SemiBold))
    }
}

@Composable
private fun Metric(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, value: String, unit: String, onClick: (() -> Unit)? = null) {
    val p = LocalPal.current
    var m = Modifier.height(22.dp)
    if (onClick != null) m = m
        .clip(RoundedCornerShape(6.dp))
        .clickable { onClick() }
    Row(m, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) { TwIcon(icon, size = 15.dp, tint = tint) }
        Spacer(Modifier.width(8.dp))
        Text(value, color = p.ink, style = num(14.sp, FontWeight.SemiBold), maxLines = 1, softWrap = false)
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(5.dp))
            Text(unit, color = p.sub, fontSize = 12.5.sp, maxLines = 1, softWrap = false)
        }
    }
}

/** Wetter: links Symbol + Temperatur, darunter klein der Ort; rechts Höhe, Sonne, Taupunkt. Beide Hälften gleich breit. */
@Composable
private fun WeatherBlock(nav: Nav, store: Store, askLocation: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val w = WeatherRepo.home
    val alt = rememberHomeAltitude(store)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { nav.go(Tab.WETTER) }
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(Modifier.height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                    TwIcon(
                        if (w != null) w.look.icon else Ic.cloudSun, size = 46.dp,
                        tint = if (w != null) weatherTint(w.look.tint, w.day) else p.fill
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (w != null) "${w.temp.roundToInt()}°" else "–",
                        color = p.ink, style = num(46.sp, FontWeight.SemiBold).copy(lineHeight = 46.sp),
                        maxLines = 1, softWrap = false
                    )
                }
                Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    TwIcon(Ic.pin, size = 13.dp, tint = p.sub)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            WeatherRepo.homeName.isNotBlank() -> WeatherRepo.homeName
                            WeatherRepo.loading -> "Wird geladen …"
                            else -> "–"
                        },
                        color = p.sub, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(p.line)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Metric(Ic.mountain, p.sub, if (alt != null) "${fmt(alt, 0)} m" else "–", "") { nav.go(Tab.HOEHE) }
                Metric(Ic.sun, p.accent, if (w != null) "${sunHours(w.sunH)} Std." else "–", "Sonne")
                Metric(Ic.droplet, p.fill, if (w != null && !w.dew.isNaN()) "${w.dew.roundToInt()}°" else "–", "Taupunkt")
            }
        }
        if (!Loc.hasPermission(ctx)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Standort erlauben", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { askLocation() }
            )
        } else if (w == null && !WeatherRepo.loading && WeatherRepo.error != null) {
            Spacer(Modifier.height(6.dp))
            Text(WeatherRepo.error ?: "", color = p.sub, fontSize = 12.sp)
        }
    }
}

/** Kopf-Kachel: Datum und Uhrzeiten, darunter (wenn eingeschaltet) das Wetter. */
@Composable
fun HeadTile(nav: Nav, store: Store, withWeather: Boolean, askLocation: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp)) {
        DateRow(nav, store)
        if (withWeather) {
            Box(Modifier.padding(end = 6.dp)) { Column { BleedDivider(16.dp, 12.dp) } }
            Box(Modifier.padding(end = 6.dp)) { WeatherBlock(nav, store, askLocation) }
        }
    }
}

/** Wetter allein (wenn die Datums-Kachel aus ist oder woanders steht). */
@Composable
fun WeatherTile(nav: Nav, store: Store, askLocation: () -> Unit) {
    Box {
        Card(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            WeatherBlock(nav, store, askLocation)
        }
        OpenArrow()
    }
}

// ------------------------------------------------------------------ Uhr-Kachel: Wecker + Schnellzugriffe

@Composable
private fun AlarmRow(nav: Nav, store: Store) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    // Startwecker (unter Uhr → Wecker wählbar), sonst automatisch der nächste
    val homeId = store.getLong("homeAlarm", -1L).toInt()
    val next = store.alarms.firstOrNull { it.id == homeId }
        ?: store.alarms.filter { it.enabled }.minByOrNull { AlarmScheduler.nextTrigger(it).toEpochSecond() }
        ?: store.alarms.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { nav.overlay = Overlay.EditAlarm(next?.id) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (next == null) {
            Text("Wecker", color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Text("Noch keiner – tippen zum Anlegen", color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "${two(next.hour)}:${two(next.minute)}",
                    color = if (next.enabled) p.ink else p.muted,
                    style = num(28.sp, FontWeight.SemiBold).copy(lineHeight = 31.sp)
                )
                val trig = AlarmScheduler.nextTrigger(next)
                val first = next.label.ifBlank { trig.format(DateTimeFormatter.ofPattern("EE", Locale.GERMAN)) }
                Text(
                    first + if (next.enabled) " · ${untilText(next)}" else "",
                    color = p.sub, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier
                    .padding(horizontal = 14.dp)
                    .size(width = 1.dp, height = 34.dp)
                    .background(p.line)
            )
            val there = secondTime(store, AlarmScheduler.nextTrigger(next))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(shortCity(secondZone(store)), color = p.sub, fontSize = 11.5.sp, maxLines = 1)
                Text(
                    "${two(there.hour)}:${two(there.minute)}",
                    color = if (next.enabled) p.ink else p.muted,
                    style = num(17.sp, FontWeight.SemiBold).copy(lineHeight = 19.sp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.padding(end = if (showOpenArrows) 8.dp else 0.dp)) {
                Toggle(next.enabled) { on ->
                    val na = next.copy(enabled = on)
                    store.upsertAlarm(na)
                    AlarmScheduler.schedule(ctx, na)
                }
            }
        }
    }
}

/** Uhr-Kachel: Wecker oben, darunter (falls eingeschaltet) die drei Schnellzugriffe. */
@Composable
fun ClockTile(nav: Nav, store: Store) {
    Box {
        Card(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
            AlarmRow(nav, store)
            if (HomeCfg.quickOn && HomeCfg.quick.isNotEmpty()) {
                BleedDivider(16.dp, 12.dp)
                QuickRow(nav, store)
            }
        }
        OpenArrow()
    }
}

@Composable
fun QuickRow(nav: Nav, store: Store) {
    val ctx = LocalContext.current
    val tick = rememberLiveTick()
    EqualPadRow(8.dp) {
        HomeCfg.quick.forEach { id ->
            QuickPill(quickDef(id), liveTime(id, tick), on = id == "torch" && Torch.on) { runQuick(id, nav, ctx, store) }
        }
    }
}

@Composable
private fun QuickPill(q: QuickDef, time: String?, on: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val strong = time != null || on
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (strong) p.fill else p.fill.copy(alpha = if (p.dark) 0.22f else 0.42f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (strong) p.onFill else p.ink
        if (q.icon != null) TwIcon(q.icon, size = 18.dp, tint = tint)
        else Text(q.badge ?: "", style = num(14.sp, FontWeight.Bold), color = tint)
        Spacer(Modifier.width(6.dp))
        if (time != null) Text(time, style = num(15.sp, FontWeight.Bold), color = tint, maxLines = 1, softWrap = false)
        else Text(q.label, color = tint, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

// ------------------------------------------------------------------ Mini-Knöpfe statt der unteren Leiste

@Composable
fun MiniDock(nav: Nav) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    val tick = rememberLiveTick()
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.nav)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(p.line)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NavCfg.mini.forEach { id ->
                val d = quickDef(id)
                val time = liveTime(id, tick)
                val strong = time != null || (id == "torch" && Torch.on)
                Column(
                    Modifier
                        .size(width = 76.dp, height = 50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (strong) p.fill else p.fill.copy(alpha = if (p.dark) 0.22f else 0.42f))
                        .clickable { runQuick(id, nav, ctx, store) }
                        .padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val tint = if (strong) p.onFill else p.ink
                    if (d.icon != null) TwIcon(d.icon, size = 18.dp, tint = tint)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        time ?: dockLabel(id), color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
