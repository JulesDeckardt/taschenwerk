package de.taschenwerk.app.screens

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import de.taschenwerk.app.ui.CircleButton
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

// ------------------------------------------------------------------ Datum + Uhrzeiten

/** Schmale Kachel ganz oben: Datum und KW, rechts klein die eigene Zeit und die zweite Stadt, dazu ⋮. */
@Composable
fun DateTile(nav: Nav, store: Store) {
    val p = LocalPal.current
    val now by rememberNow(1000)
    val here = ZonedDateTime.now()
    val there = secondTime(store, here)
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { nav.go(Tab.UHR, 3) },
        padding = PaddingValues(start = 18.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(Modifier.heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    now.format(DateTimeFormatter.ofPattern("EE, d. MMMM", Locale.GERMAN)),
                    color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
                Text("KW ${now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", color = p.sub, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                ClockLine(shortCity(ZoneId.systemDefault().id), "${two(now.hour)}:${two(now.minute)}")
                ClockLine(shortCity(secondZone(store)), "${two(there.hour)}:${two(there.minute)}")
            }
            CircleButton(Ic.more, { nav.overlay = Overlay.Settings }, size = 40.dp, bg = p.card, iconSize = 20.dp, desc = "Einstellungen")
        }
    }
}

@Composable
private fun ClockLine(city: String, time: String) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(city, color = p.sub, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(time, color = p.ink, style = num(15.sp, FontWeight.SemiBold))
    }
}

// ------------------------------------------------------------------ Wetter + Ort + Höhe

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
fun WeatherTile(nav: Nav, store: Store, askLocation: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val w = WeatherRepo.home
    val alt = rememberHomeAltitude(store)
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { nav.go(Tab.WETTER) },
        padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            TwIcon(
                if (w != null) w.look.icon else Ic.cloudSun, size = 38.dp,
                tint = if (w != null) weatherTint(w.look.tint, w.day) else p.fill
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    WeatherRepo.homeName, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        w != null -> w.look.text
                        WeatherRepo.loading -> "Wird geladen …"
                        !Loc.hasPermission(ctx) -> "Tippen für Standort-Wetter"
                        else -> WeatherRepo.error ?: "–"
                    },
                    color = p.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (alt != null) {
                        // Höhe antippen öffnet den Höhenmesser
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { nav.go(Tab.HOEHE) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TwIcon(Ic.mountain, size = 13.dp, tint = p.sub)
                            Text(
                                "${fmt(alt, 0)} m", color = p.ink, style = num(12.5.sp, FontWeight.SemiBold),
                                modifier = Modifier.padding(start = 4.dp), maxLines = 1
                            )
                        }
                    }
                    if (alt != null && w != null) Box(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .size(width = 1.dp, height = 11.dp)
                            .background(p.line)
                    )
                    if (w != null) {
                        TwIcon(Ic.sun, size = 13.dp, tint = p.accent)
                        Text(
                            "${sunHours(w.sunH)} Std. Sonne", color = p.accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp), maxLines = 1, softWrap = false
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (w != null) "${w.temp.roundToInt()}°" else "–", style = num(34.sp, FontWeight.SemiBold), color = p.ink,
                maxLines = 1, softWrap = false
            )
        }
        if (!Loc.hasPermission(ctx)) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Standort erlauben", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { askLocation() }
            )
        }
    }
}

// ------------------------------------------------------------------ Schnellzugriffe

private fun clockText(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "$h:${two(m.toInt())}:${two(s.toInt())}" else "${two(m.toInt())}:${two(s.toInt())}"
}

/** Reihe mit den Schnellzugriffen (Standard: Timer · Intervall · Stoppuhr). Läuft Timer oder Stoppuhr, zählt der Knopf live. */
@Composable
fun QuickRow(nav: Nav, store: Store) {
    val ctx = LocalContext.current
    remember { TimerStore.load(ctx); Stopwatch.load(store); 0 }
    val live = TimerStore.running || TimerStore.paused || Stopwatch.running || Stopwatch.acc > 0L
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(live) {
        while (live) {
            tick = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeCfg.quick.forEach { id ->
            val q = quickDef(id)
            // tick lesen, damit die Zeit mitläuft
            val nowMs = if (tick >= 0L) System.currentTimeMillis() else 0L
            val time: String? = when {
                id == "timer" && TimerStore.running -> clockText(TimerStore.endAt - nowMs + 999)
                id == "timer" && TimerStore.paused -> clockText(TimerStore.pausedLeft + 999)
                id == "stopwatch" && (Stopwatch.running || Stopwatch.acc > 0L) -> clockText(Stopwatch.elapsed())
                else -> null
            }
            QuickPill(q, time, Modifier.weight(1f)) { runQuick(id, nav, ctx, store) }
        }
    }
}

@Composable
private fun QuickPill(q: QuickDef, time: String?, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val strong = time != null
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (strong) p.fill else p.fill.copy(alpha = if (p.dark) 0.22f else 0.42f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (strong) p.onFill else p.ink
        if (q.icon != null) TwIcon(q.icon, size = 19.dp, tint = tint)
        else Text(q.badge ?: "", style = num(14.sp, FontWeight.Bold), color = tint)
        Spacer(Modifier.width(7.dp))
        if (time != null) Text(time, style = num(16.sp, FontWeight.Bold), color = tint, maxLines = 1, softWrap = false)
        else Text(
            q.label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ------------------------------------------------------------------ Wecker einzeilig

@Composable
fun AlarmTile(nav: Nav, store: Store) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    // Startwecker (unter Uhr → Wecker wählbar), sonst automatisch der nächste
    val homeId = store.getLong("homeAlarm", -1L).toInt()
    val next = store.alarms.firstOrNull { it.id == homeId }
        ?: store.alarms.filter { it.enabled }.minByOrNull { AlarmScheduler.nextTrigger(it).toEpochSecond() }
        ?: store.alarms.firstOrNull()
    Card(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        onClick = { nav.overlay = Overlay.EditAlarm(next?.id) }
    ) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (next == null) {
                Text("Wecker", color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Text("Noch keiner – tippen zum Anlegen", color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
            } else {
                Text(
                    "${two(next.hour)}:${two(next.minute)}",
                    style = num(30.sp, FontWeight.SemiBold), color = if (next.enabled) p.ink else p.muted,
                    modifier = Modifier.offset(y = (-1).dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val first = (next.label.ifBlank { daysText(next.days) }) + if (next.enabled) " · ${untilText(next)}" else ""
                    Text(first, color = p.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sec = alarmSecondText(store, next)
                    val city = sec.substringBeforeLast(' ')
                    Row {
                        Text("$city ", color = p.sub, fontSize = 13.sp, maxLines = 1)
                        Text(sec.substringAfterLast(' '), color = if (next.enabled) p.ink else p.muted, style = num(13.sp, FontWeight.SemiBold))
                    }
                }
                Toggle(next.enabled) { on ->
                    val na = next.copy(enabled = on)
                    store.upsertAlarm(na)
                    AlarmScheduler.schedule(ctx, na)
                }
            }
        }
    }
}
