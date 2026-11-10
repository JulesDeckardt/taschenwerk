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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawWithContent
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

/** Datum frei oben (ohne Kachel), rechts daneben ⋮ für die Einstellungen. */
@Composable
fun DateHeader(nav: Nav) {
    val p = LocalPal.current
    val now by rememberNow(1000)
    val wd = now.format(DateTimeFormatter.ofPattern("EE", Locale.GERMAN)).removeSuffix(".")
    val mon = now.format(DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)).removeSuffix(".")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f)) {
            Text(
                "$wd, ${now.dayOfMonth}. $mon", color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.alignByBaseline()
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "KW ${now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", color = p.sub, fontSize = 13.sp,
                maxLines = 1, modifier = Modifier.alignByBaseline()
            )
        }
        Box(
            Modifier
                .offset(x = 8.dp)
                .size(width = 36.dp, height = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { nav.overlay = Overlay.Settings },
            contentAlignment = Alignment.Center
        ) { TwIcon(Ic.more, size = 20.dp, desc = "Einstellungen") }
    }
}

/**
 * Text in einer Zeile, der bei Platzmangel automatisch kleiner wird statt abgeschnitten zu werden
 * (z. B. bei großer Systemschrift). Wird erst gezeichnet, wenn er passt.
 */
@Composable
fun FitText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    min: Float = 8f
) {
    val base = style.fontSize
    var cur by remember(text, base) { mutableStateOf(base) }
    var ready by remember(text, base) { mutableStateOf(false) }
    // Am Minimum angekommen: lieber mit „…“ kürzen als abschneiden
    val atMin = cur.value <= min
    Text(
        text, color = color, style = style.copy(fontSize = cur), maxLines = 1, softWrap = atMin,
        overflow = if (atMin) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { r ->
            if (r.didOverflowWidth && cur.value > min) cur = (cur.value * 0.92f).coerceAtLeast(min).sp
            else ready = true
        }
    )
}

@Composable
private fun Metric(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, value: String, unit: String, onClick: (() -> Unit)? = null) {
    val p = LocalPal.current
    var m = Modifier
        .fillMaxWidth()
        .heightIn(min = 22.dp)
    if (onClick != null) m = m
        .clip(RoundedCornerShape(6.dp))
        .clickable { onClick() }
    Row(m, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) { TwIcon(icon, size = 15.dp, tint = tint) }
        Spacer(Modifier.width(8.dp))
        FitText(value, p.ink, num(14.sp, FontWeight.SemiBold), min = 9f)
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(5.dp))
            // Einheit schrumpft bei Platzmangel, statt abgeschnitten zu werden
            FitText(unit, p.sub, androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp), Modifier.weight(1f, fill = false), min = 8f)
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
                Row(Modifier.heightIn(min = 46.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TwIcon(
                        if (w != null) w.look.icon else Ic.cloudSun, size = 46.dp,
                        tint = if (w != null) weatherTint(w.look.tint, w.day) else p.fill
                    )
                    Spacer(Modifier.width(8.dp))
                    FitText(
                        if (w != null) "${w.temp.roundToInt()}°" else "–", p.ink,
                        num(46.sp, FontWeight.SemiBold).copy(lineHeight = 46.sp), Modifier.weight(1f, fill = false), min = 24f
                    )
                }
                Row(Modifier.heightIn(min = 22.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TwIcon(Ic.pin, size = 13.dp, tint = p.sub)
                    Spacer(Modifier.width(4.dp))
                    FitText(
                        when {
                            WeatherRepo.homeName.isNotBlank() -> WeatherRepo.homeName
                            WeatherRepo.loading -> "Wird geladen …"
                            else -> "–"
                        },
                        p.sub, androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp), Modifier.weight(1f, fill = false), min = 10f
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

/** Wetter-Kachel: Symbol + Temperatur + Ort links, Höhe/Sonne/Taupunkt rechts. */
@Composable
fun WeatherTile(nav: Nav, store: Store, askLocation: () -> Unit) {
    Box {
        Card(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
            WeatherBlock(nav, store, askLocation)
        }
        OpenArrow()
    }
}

// ------------------------------------------------------------------ Uhr-Kachel: Wecker + Schnellzugriffe

@Composable
private fun RowScope.HeadCell(t: String, w: Float) {
    Box(Modifier.weight(w).padding(end = 6.dp), contentAlignment = Alignment.CenterStart) {
        FitText(t, LocalPal.current.sub, androidx.compose.ui.text.TextStyle(fontSize = 11.sp), min = 8f)
    }
}

@Composable
private fun RowScope.TimeCell(t: String, c: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(end = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        FitText(t, c, num(21.sp, FontWeight.SemiBold), min = 12f)
    }
}

@Composable
private fun RowScope.CityCell(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String) {
    val p = LocalPal.current
    Row(
        Modifier
            .weight(1.1f)
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TwIcon(icon, size = 14.dp, tint = p.sub)
        Spacer(Modifier.width(6.dp))
        FitText(name, p.sub, androidx.compose.ui.text.TextStyle(fontSize = 13.sp), Modifier.weight(1f, fill = false), min = 9f)
    }
}

/**
 * Zeit-Kachel als kleine Tabelle: Spalten „Jetzt“ und „Wecker“ (linksbündig über den Zeiten),
 * Zeilen eigene Stadt und zweite Stadt; rechts der Wecker-Schalter, darunter die Schnellzugriffe.
 */
@Composable
fun TimeTile(nav: Nav, store: Store) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val now by rememberNow(1000)
    val there = secondTime(store, ZonedDateTime.now())
    // Startwecker (unter Uhr → Wecker wählbar), sonst automatisch der nächste
    val homeId = store.getLong("homeAlarm", -1L).toInt()
    val next = store.alarms.firstOrNull { it.id == homeId }
        ?: store.alarms.filter { it.enabled }.minByOrNull { AlarmScheduler.nextTrigger(it).toEpochSecond() }
        ?: store.alarms.firstOrNull()
    val trigThere = next?.let { secondTime(store, AlarmScheduler.nextTrigger(it)) }
    val on = next?.enabled == true
    Box {
        Card(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                // Tabelle: jede Zeile eine Row, damit alles auch bei großer Schrift auf einer Linie bleibt
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Spacer(Modifier.weight(1.1f))
                        HeadCell("Jetzt", 1f)
                        HeadCell("Wecker", 1f)
                    }
                    val openWorld = { nav.go(Tab.UHR, 3) }
                    val openAlarm = { nav.overlay = Overlay.EditAlarm(next?.id) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CityCell(Ic.pin, shortCity(ZoneId.systemDefault().id))
                        TimeCell("${two(now.hour)}:${two(now.minute)}", p.ink, openWorld)
                        TimeCell(next?.let { "${two(it.hour)}:${two(it.minute)}" } ?: "–", if (on) p.ink else p.muted, openAlarm)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CityCell(Ic.globe, shortCity(secondZone(store)))
                        TimeCell("${two(there.hour)}:${two(there.minute)}", p.ink, openWorld)
                        TimeCell(trigThere?.let { "${two(it.hour)}:${two(it.minute)}" } ?: "–", if (on) p.sub else p.muted, openAlarm)
                    }
                }
                Box(
                    Modifier
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(p.line)
                )
                Box(Modifier.padding(end = if (showOpenArrows) 8.dp else 0.dp)) {
                    if (next != null) Toggle(next.enabled) { v ->
                        val na = next.copy(enabled = v)
                        store.upsertAlarm(na)
                        AlarmScheduler.schedule(ctx, na)
                    } else Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.track)
                            .clickable { nav.overlay = Overlay.EditAlarm(null) },
                        contentAlignment = Alignment.Center
                    ) { TwIcon(Ic.plus, size = 18.dp, desc = "Wecker anlegen") }
                }
            }
            if (HomeCfg.quickOn && HomeCfg.quick.isNotEmpty()) {
                BleedDivider(16.dp, 14.dp)
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
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (strong) p.fill else p.fill.copy(alpha = if (p.dark) 0.22f else 0.42f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (strong) p.onFill else p.ink
        if (q.icon != null) TwIcon(q.icon, size = 17.dp, tint = tint)
        else Text(q.badge ?: "", style = num(13.sp, FontWeight.Bold), color = tint)
        Spacer(Modifier.width(5.dp))
        // schrumpft bei Platzmangel, statt abgeschnitten zu werden
        FitText(
            time ?: q.label, tint,
            if (time != null) num(14.5.sp, FontWeight.Bold) else androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            Modifier.weight(1f, fill = false), min = 8f
        )
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
                    FitText(
                        time ?: dockLabel(id), tint,
                        androidx.compose.ui.text.TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold), min = 7f
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
