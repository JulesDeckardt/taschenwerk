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
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.key
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun StartScreen(nav: Nav) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    val scope = rememberCoroutineScope()

    remember { HomeCfg.load(store); NavCfg.load(store); WeatherRepo.loadCached(store); 0 }
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

    // Nach unten ziehen lädt das Wetter neu
    var refreshing by remember { mutableStateOf(false) }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                runCatching { WeatherRepo.refreshAll(ctx, store, force = true) }
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Ohne Datums-Kachel bleiben die Einstellungen trotzdem erreichbar
            if (HomeCfg.tiles.none { it.first == "date" && it.second }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CircleButton(Ic.more, { nav.overlay = Overlay.Settings }, size = 40.dp, bg = p.bg, iconSize = 20.dp, desc = "Einstellungen")
                }
            }
            // Kacheln in der eingestellten Reihenfolge (Einstellungen → Startseite)
            val shown = HomeCfg.tiles.filter { it.second }.map { it.first }
            // Datum und direkt folgendes Wetter bilden eine gemeinsame Kopf-Kachel
            val joined = shown.indexOf("date").let { i -> i >= 0 && shown.getOrNull(i + 1) == "weather" }
            shown.forEach { tileId ->
                key(tileId) {
                    when (tileId) {
                        "date" -> HeadTile(nav, store, withWeather = joined, askLocation = askLocation)
                        "weather" -> if (!joined) WeatherTile(nav, store, askLocation)
                        "alarm" -> ClockTile(nav, store)
                        "currency" -> Box {
                            CurrencyCard(store, endPad = if (showOpenArrows) 28.dp else 16.dp)
                            if (showOpenArrows) Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(28.dp)
                                    .clickable { runQuick("currency", nav, ctx, store) }
                            ) { OpenArrow() }
                        }
                        "altitude" -> AltitudeTile(nav, CARD_MIN)
                        "breath" -> BreathMini()
                        "calc" -> CalcTile(onOpen = if (showOpenArrows) ({ nav.go(Tab.RECHNER, 0) }) else null)
                        "notes" -> NotesTile()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
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
fun CurrencyCard(store: Store, endPad: androidx.compose.ui.unit.Dp = 16.dp) {
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
    // Ergebnis aus der Rechner-Kachel übernehmen („→ MXN“)
    val pushed = CalcBridge.push
    LaunchedEffect(pushed) {
        if (pushed != null) {
            aText = pushed
            editedTop = true
            CalcBridge.push = null
        }
    }

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
        padding = PaddingValues(start = 16.dp, end = endPad, top = 6.dp, bottom = 6.dp)
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
