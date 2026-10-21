package de.taschenwerk.app.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.taschenwerk.app.Nav
import de.taschenwerk.app.Overlay
import de.taschenwerk.app.Tab
import de.taschenwerk.app.alarm.AlarmScheduler
import de.taschenwerk.app.alarm.TimerStore
import de.taschenwerk.app.data.CURRENCIES
import de.taschenwerk.app.data.Loc
import de.taschenwerk.app.data.Rates
import de.taschenwerk.app.data.Store
import de.taschenwerk.app.data.WeatherRepo
import de.taschenwerk.app.data.currencyName
import de.taschenwerk.app.data.weatherIcon
import de.taschenwerk.app.data.weatherText
import de.taschenwerk.app.data.sunHours
import de.taschenwerk.app.ui.Card
import de.taschenwerk.app.ui.CircleButton
import de.taschenwerk.app.ui.Divider
import de.taschenwerk.app.ui.Ic
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.NumberField
import de.taschenwerk.app.ui.Toggle
import de.taschenwerk.app.ui.TwIcon
import de.taschenwerk.app.ui.num
import de.taschenwerk.app.util.fmt
import de.taschenwerk.app.util.parseNum
import de.taschenwerk.app.util.two
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun rememberLocationAsk(onResult: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    return {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

@Composable
fun StartScreen(nav: Nav) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    val now by rememberNow(1000)
    val scope = rememberCoroutineScope()

    val askLocation = rememberLocationAsk {
        scope.launch { WeatherRepo.refreshAll(ctx, store, force = true) }
    }
    LaunchedEffect(Unit) {
        if (!Loc.hasPermission(ctx) && !store.getBool("askedLoc", false)) {
            store.putBool("askedLoc", true)
            askLocation()
        } else {
            WeatherRepo.refreshAll(ctx, store)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Kopf: Datum + Uhrzeit
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    now.format(DateTimeFormatter.ofPattern("EE, d. MMMM", Locale.GERMAN)),
                    color = p.sub, fontSize = 14.sp
                )
                Text(
                    buildAnnotatedString {
                        append("${two(now.hour)}:")
                        withStyle(SpanStyle(color = p.accent)) { append(two(now.minute)) }
                    },
                    style = num(84.sp).copy(lineHeight = 90.sp), color = p.ink
                )
                // Zweite Uhr (Einstellung): klein und grau unter der großen Uhr
                if (store.getBool("clock2", false)) {
                    val z = store.getString("clock2Zone") ?: "Europe/Berlin"
                    val t = runCatching { java.time.ZonedDateTime.now(java.time.ZoneId.of(z)) }.getOrNull()
                    if (t != null) Text(
                        "${two(t.hour)}:${two(t.minute)}  ${cityForZone(z)}",
                        color = p.sub, fontSize = 15.sp, style = num(15.sp, FontWeight.Normal)
                    )
                }
            }
            CircleButton(Ic.more, { nav.overlay = Overlay.Settings }, size = 44.dp, bg = p.bg, desc = "Einstellungen")
        }

        // Wetter
        val w = WeatherRepo.home
        Card(
            modifier = Modifier.heightIn(min = CARD_MIN),
            onClick = { nav.go(Tab.WETTER) },
            padding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(Modifier.heightIn(min = CARD_MIN - 24.dp), verticalAlignment = Alignment.CenterVertically) {
                TwIcon(
                    if (w != null) w.look.icon else Ic.cloudSun, size = 40.dp,
                    tint = if (w != null) weatherTint(w.look.tint, w.day) else p.fill
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(WeatherRepo.homeName, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        when {
                            w != null -> w.look.text
                            WeatherRepo.loading -> "Wird geladen …"
                            !Loc.hasPermission(ctx) -> "Tippen für Standort-Wetter"
                            else -> WeatherRepo.error ?: "–"
                        },
                        color = p.sub, fontSize = 14.sp, maxLines = 1
                    )
                    if (w != null) {
                        SunDewLine(
                            sun = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TwIcon(Ic.sun, size = 14.dp, tint = p.accent)
                                    Text(
                                        "${sunHours(w.sunH)} Std.", color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 4.dp), maxLines = 1, softWrap = false
                                    )
                                }
                            },
                            sep = {
                                Box(
                                    Modifier
                                        .padding(horizontal = 9.dp)
                                        .size(width = 1.dp, height = 12.dp)
                                        .background(p.line)
                                )
                            },
                            dew = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TwIcon(Ic.droplet, size = 13.dp, tint = p.fill)
                                    Text(
                                        if (w.dew.isNaN()) "Taupunkt –" else "Taupunkt ${w.dew.roundToInt()}°",
                                        color = p.sub, fontSize = 13.sp,
                                        modifier = Modifier.padding(start = 4.dp), maxLines = 1, softWrap = false
                                    )
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (w != null) "${w.temp.roundToInt()}°" else "–", style = num(36.sp, FontWeight.SemiBold), color = p.ink,
                    maxLines = 1, softWrap = false
                )
            }
            if (!Loc.hasPermission(ctx)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Standort erlauben", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { askLocation() }
                )
            }
        }

        // Wecker – kompakt: nur der nächste Wecker, Tippen öffnet alle
        val next = store.alarms.filter { it.enabled }.minByOrNull { AlarmScheduler.nextTrigger(it).toEpochSecond() }
            ?: store.alarms.firstOrNull()
        Card(
            modifier = Modifier.heightIn(min = CARD_MIN),
            padding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            onClick = { if (next == null) nav.overlay = Overlay.EditAlarm(null) else nav.go(Tab.UHR, 1) }
        ) {
            Row(Modifier.heightIn(min = CARD_MIN - 20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (next == null) {
                    Column(Modifier.weight(1f)) {
                        Text("Wecker", color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Noch kein Wecker – tippen zum Anlegen", color = p.ink, fontSize = 15.sp)
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text("Wecker", color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            "${two(next.hour)}:${two(next.minute)}",
                            style = num(32.sp, FontWeight.SemiBold).copy(lineHeight = 36.sp),
                            color = if (next.enabled) p.ink else p.muted
                        )
                        Text(alarmSubtitle(next), color = p.sub, fontSize = 13.sp, maxLines = 1)
                    }
                    Toggle(next.enabled) { on ->
                        val na = next.copy(enabled = on)
                        store.upsertAlarm(na)
                        AlarmScheduler.schedule(ctx, na)
                    }
                }
            }
        }

        // Währung – kompakt
        CurrencyCard(store)

        // Schnellzugriff
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile("60 Min.", null, Modifier.weight(1f), badge = "60") {
                TimerStore.start(ctx, 60 * 60_000L, "60 Minuten")
                nav.go(Tab.UHR, 3)
            }
            QuickTile("Workout", Ic.pulse, Modifier.weight(1f)) {
                val main = store.workouts.firstOrNull { it.id == "mein" }
                    ?: store.workouts.firstOrNull { !it.id.startsWith("apnea") }
                    ?: store.workouts.firstOrNull()
                if (main != null) nav.overlay = Overlay.RunWorkout(main.id) else nav.go(Tab.UHR, 3)
            }
            QuickTile("Timer", Ic.hourglass, Modifier.weight(1f)) { nav.go(Tab.UHR, 3) }
            QuickTile("Stoppuhr", Ic.stopwatch, Modifier.weight(1f)) { nav.go(Tab.UHR, 2) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Sonnenstunden | Taupunkt in einer Zeile; passt es nicht, rutscht der Taupunkt in eine eigene Zeile. */
/** Gemeinsame Mindesthöhe der Kacheln Wetter, Wecker und Währung. */
private val CARD_MIN = 92.dp

@Composable
private fun SunDewLine(sun: @Composable () -> Unit, sep: @Composable () -> Unit, dew: @Composable () -> Unit) {
    Layout(contents = listOf(sun, sep, dew)) { (sunM, sepM, dewM), c ->
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val a = sunM.first().measure(loose)
        val s = sepM.first().measure(loose)
        val d = dewM.first().measure(loose)
        if (a.width + s.width + d.width <= c.maxWidth) {
            val h = maxOf(a.height, s.height, d.height)
            layout(a.width + s.width + d.width, h) {
                a.place(0, (h - a.height) / 2)
                s.place(a.width, (h - s.height) / 2)
                d.place(a.width + s.width, (h - d.height) / 2)
            }
        } else {
            val gap = 2.dp.roundToPx()
            layout(maxOf(a.width, d.width).coerceAtMost(c.maxWidth), a.height + gap + d.height) {
                a.place(0, 0)
                d.place(0, a.height + gap)
            }
        }
    }
}

@Composable
private fun QuickTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, modifier: Modifier, badge: String? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    Card(modifier = modifier.height(78.dp), radius = 18.dp, padding = PaddingValues(6.dp), onClick = onClick) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(p.fill.copy(alpha = if (p.dark) 0.25f else 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) TwIcon(icon, size = 19.dp)
                else Text(badge ?: "", style = num(14.sp, FontWeight.Bold), color = p.ink)
            }
            Spacer(Modifier.height(5.dp))
            Text(label, color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private val dayShort = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

fun daysText(days: Int): String = when (days) {
    0 -> "Einmal"
    0b1111111 -> "Täglich"
    0b0011111 -> "Mo – Fr"
    0b1100000 -> "Sa, So"
    else -> (0..6).filter { days and (1 shl it) != 0 }.joinToString(", ") { dayShort[it] }
}

fun untilText(a: de.taschenwerk.app.data.Alarm): String {
    val next = AlarmScheduler.nextTrigger(a)
    val d = Duration.between(ZonedDateTime.now(), next)
    val totalMin = d.toMinutes() + if (d.seconds % 60 > 0) 1 else 0
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h >= 24) "in ${h / 24} T. ${h % 24} Std." else "in $h Std. $m Min."
}

fun alarmSubtitle(a: de.taschenwerk.app.data.Alarm): String {
    val base = daysText(a.days) + if (a.label.isNotBlank()) " · ${a.label}" else ""
    return if (a.enabled) "$base · ${untilText(a)}" else base
}

/** Währungsrechner mit zwei Zeilen (auch auf der Startseite). */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun CurrencyCard(store: Store) {
    val p = LocalPal.current
    // Beim Antippen eines Betrags die ganze Karte über die Tastatur schieben,
    // damit das Ergebnis darunter sofort sichtbar ist.
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(focused, imeBottom) {
        if (focused) {
            kotlinx.coroutines.delay(60)
            bring.bringIntoView()
        }
    }
    LaunchedEffect(Unit) { Rates.refresh(store) }
    val from = store.mainCur
    val to = store.secondCur
    var aText by remember { mutableStateOf("1.000") }
    var bText by remember { mutableStateOf("") }
    var editedTop by remember { mutableStateOf(true) }

    val rates = Rates.rates
    val aVal = parseNum(aText)
    val bVal = parseNum(bText)
    val shownB = if (editedTop) aVal?.let { Rates.convert(it, from, to) }?.let { fmt(it, 2) } ?: "" else bText
    val shownA = if (!editedTop) bVal?.let { Rates.convert(it, to, from) }?.let { fmt(it, 2) } ?: "" else aText

    Card(
        modifier = Modifier
            .heightIn(min = CARD_MIN)
            .bringIntoViewRequester(bring)
            .onFocusChanged { focused = it.hasFocus },
        padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
    ) {
        CurrencyRow(from, shownA, { store.applyMainCur(it) }) {
            aText = it; editedTop = true
        }
        Divider()
        CurrencyRow(to, shownB, { store.applySecondCur(it) }, accent = true) {
            bText = it; editedTop = false
        }
    }
}

@Composable
private fun CurrencyRow(code: String, value: String, onPick: (String) -> Unit, accent: Boolean = false, onValue: (String) -> Unit) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(39.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Row(
                Modifier
                    .size(width = 72.dp, height = 30.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.track)
                    .clickable { open = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(code, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.width(2.dp))
                TwIcon(Ic.chevDown, size = 16.dp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                CURRENCIES.forEach { (c, n) ->
                    DropdownMenuItem(text = { Text("$c  ·  $n") }, onClick = { onPick(c); open = false })
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        NumberField(
            value = value, onChange = onValue,
            modifier = Modifier.weight(1f),
            style = num(21.sp, FontWeight.SemiBold), align = TextAlign.End, boxed = false,
            color = if (accent) p.accent else p.ink
        )
    }
}
