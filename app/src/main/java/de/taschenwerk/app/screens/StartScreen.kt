package de.taschenwerk.app.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
                    style = num(50.sp), color = p.ink
                )
            }
            CircleButton(Ic.more, { nav.overlay = Overlay.Settings }, size = 44.dp, bg = p.bg, desc = "Einstellungen")
        }

        // Wetter
        val w = WeatherRepo.home
        Card(
            onClick = { nav.go(Tab.WETTER) },
            padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TwIcon(if (w != null) weatherIcon(w.code, w.day) else Ic.cloudSun, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(WeatherRepo.homeName, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        when {
                            w != null -> "${weatherText(w.code)} · ${fmt(w.sunH, 1)} Std. Sonne"
                            WeatherRepo.loading -> "Wird geladen …"
                            !Loc.hasPermission(ctx) -> "Tippen für Standort-Wetter"
                            else -> WeatherRepo.error ?: "–"
                        },
                        color = p.sub, fontSize = 13.sp, maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (w != null) "${w.temp.roundToInt()}°" else "–", style = num(32.sp), color = p.ink)
                    if (w != null && !w.dew.isNaN()) {
                        Text("Taupunkt ${w.dew.roundToInt()}°", color = p.sub, fontSize = 13.sp)
                    }
                }
            }
            if (!Loc.hasPermission(ctx)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Standort erlauben", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { askLocation() }
                )
            }
        }

        CurrencyCard(store)

        // Wecker
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Text("Wecker", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "Alle", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { nav.go(Tab.UHR, 0) }
                        .padding(4.dp)
                )
            }
            val list = store.alarms.take(3)
            if (list.isEmpty()) {
                Text("Noch kein Wecker", color = p.sub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
            }
            list.forEachIndexed { i, a ->
                if (i > 0) Divider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .clickable { nav.overlay = Overlay.EditAlarm(a.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${two(a.hour)}:${two(a.minute)}", style = num(28.sp), color = if (a.enabled) p.ink else p.muted)
                        Text(alarmSubtitle(a), color = p.sub, fontSize = 13.sp, maxLines = 1)
                    }
                    Toggle(a.enabled) { on ->
                        val na = a.copy(enabled = on)
                        store.upsertAlarm(na)
                        AlarmScheduler.schedule(ctx, na)
                    }
                }
            }
        }

        // Schnellzugriff
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile("5 Min.", Ic.hourglass, Modifier.weight(1f)) {
                TimerStore.start(ctx, 5 * 60_000L, "5 Minuten")
                nav.go(Tab.UHR, 3)
            }
            QuickTile("Workout", Ic.pulse, Modifier.weight(1f)) { nav.go(Tab.UHR, 3) }
            QuickTile("Stoppuhr", Ic.stopwatch, Modifier.weight(1f)) { nav.go(Tab.UHR, 2) }
            QuickTile("Prozent", Ic.percent, Modifier.weight(1f)) { nav.go(Tab.RECHNER, 1) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QuickTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Card(modifier = modifier.height(78.dp), radius = 18.dp, padding = PaddingValues(6.dp), onClick = onClick) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(p.fill.copy(alpha = if (p.dark) 0.25f else 0.45f)),
                contentAlignment = Alignment.Center
            ) { TwIcon(icon, size = 19.dp) }
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
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return if (h >= 24) "in ${h / 24} T. ${h % 24} Std." else "in $h Std. $m Min."
}

fun alarmSubtitle(a: de.taschenwerk.app.data.Alarm): String {
    val base = daysText(a.days) + if (a.label.isNotBlank()) " · ${a.label}" else ""
    return if (a.enabled) "$base · ${untilText(a)}" else base
}

/** Währungsrechner mit zwei Zeilen (auch auf der Startseite). */
@Composable
fun CurrencyCard(store: Store) {
    val p = LocalPal.current
    LaunchedEffect(Unit) { Rates.refresh(store) }
    var from by remember { mutableStateOf(store.mainCur) }
    var to by remember { mutableStateOf(store.secondCur) }
    var aText by remember { mutableStateOf("1.000") }
    var bText by remember { mutableStateOf("") }
    var editedTop by remember { mutableStateOf(true) }

    val rates = Rates.rates
    val aVal = parseNum(aText)
    val bVal = parseNum(bText)
    val shownB = if (editedTop) aVal?.let { Rates.convert(it, from, to) }?.let { fmt(it, 2) } ?: "" else bText
    val shownA = if (!editedTop) bVal?.let { Rates.convert(it, to, from) }?.let { fmt(it, 2) } ?: "" else aText

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Währung", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val one = Rates.convert(1.0, to, from)
            Text(
                when {
                    Rates.loading && rates.isEmpty() -> "Kurse werden geladen …"
                    one != null -> "1 $to = ${fmt(one, 4)} $from"
                    else -> Rates.error ?: "Keine Kurse"
                },
                color = p.sub, fontSize = 12.sp
            )
        }
        CurrencyRow(from, shownA, { from = it; store.setMainCur(it) }) {
            aText = it; editedTop = true
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Divider() }
            CircleButton(
                Ic.swap,
                {
                    val f = from
                    from = to
                    to = f
                    store.setMainCur(from)
                    store.setSecondCur(to)
                },
                size = 36.dp, iconSize = 18.dp, fg = p.accent,
                modifier = Modifier.padding(horizontal = 10.dp), desc = "Währungen tauschen"
            )
            Box(Modifier.weight(1f)) { Divider() }
        }
        CurrencyRow(to, shownB, { to = it; store.setSecondCur(it) }, accent = true) {
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
            .height(60.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Row(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.track)
                    .clickable { open = true }
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(code, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                TwIcon(Ic.chevDown, size = 16.dp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                CURRENCIES.forEach { (c, n) ->
                    DropdownMenuItem(text = { Text("$c  ·  $n") }, onClick = { onPick(c); open = false })
                }
            }
        }
        Text(
            currencyName(code), color = p.sub, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f)
        )
        NumberField(
            value = value, onChange = onValue,
            modifier = Modifier.width(150.dp),
            style = num(28.sp), align = TextAlign.End, boxed = false,
            color = if (accent) p.accent else p.ink
        )
    }
}
