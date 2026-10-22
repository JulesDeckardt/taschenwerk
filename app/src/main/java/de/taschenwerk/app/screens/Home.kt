package de.taschenwerk.app.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.taschenwerk.app.Nav
import de.taschenwerk.app.Overlay
import de.taschenwerk.app.Tab
import de.taschenwerk.app.alarm.TimerStore
import de.taschenwerk.app.data.Store
import de.taschenwerk.app.data.WeatherRepo
import de.taschenwerk.app.ui.Card
import de.taschenwerk.app.ui.CircleButton
import de.taschenwerk.app.ui.Divider
import de.taschenwerk.app.ui.Ic
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.RowItem
import de.taschenwerk.app.ui.Toggle
import de.taschenwerk.app.ui.TwIcon
import de.taschenwerk.app.ui.num
import de.taschenwerk.app.util.Expr
import de.taschenwerk.app.util.fmt
import kotlin.math.roundToInt

// ------------------------------------------------------------------ Einstellungen der Startseite

/** Welche Kacheln die Startseite zeigt (in dieser Reihenfolge) und wie die Schnellzugriffe belegt sind. */
object HomeCfg {
    val ALL_TILES = listOf("weather", "alarm", "currency", "altitude", "breath", "calc")
    private val DEFAULT_ON = setOf("weather", "alarm", "currency")

    var tiles by mutableStateOf(ALL_TILES.map { it to (it in DEFAULT_ON) })
    var quick by mutableStateOf(listOf("timerX", "workout", "timer", "stopwatch"))
    var timerMin by mutableIntStateOf(60)
    private var loaded = false

    fun load(store: Store) {
        if (loaded) return
        loaded = true
        store.getString("homeTiles")?.let { raw ->
            val parsed = raw.split(",").mapNotNull { e ->
                val parts = e.split(":")
                if (parts.size == 2 && parts[0] in ALL_TILES) parts[0] to (parts[1] == "1") else null
            }
            // Neue Kachel-Arten, die es beim Speichern noch nicht gab, hinten anhängen (aus)
            tiles = parsed + ALL_TILES.filter { id -> parsed.none { it.first == id } }.map { it to false }
        }
        store.getString("homeQuick")?.split(",")?.takeIf { it.size == 4 }?.let { quick = it }
        timerMin = store.getLong("homeTimerMin", 60L).toInt()
    }

    fun save(store: Store) {
        store.putString("homeTiles", tiles.joinToString(",") { "${it.first}:${if (it.second) 1 else 0}" })
        store.putString("homeQuick", quick.joinToString(","))
        store.putLong("homeTimerMin", timerMin.toLong())
    }

    fun tileName(id: String) = when (id) {
        "weather" -> "Wetter"
        "alarm" -> "Wecker"
        "currency" -> "Währung"
        "altitude" -> "Höhe"
        "breath" -> "Breath"
        else -> "Rechner"
    }

    fun tileIcon(id: String): ImageVector = when (id) {
        "weather" -> Ic.cloud
        "alarm" -> Ic.alarm
        "currency" -> Ic.money
        "altitude" -> Ic.mountain
        "breath" -> Ic.breath
        else -> Ic.calc
    }
}

/** Ein möglicher Schnellzugriff. */
class QuickDef(val id: String, val label: String, val icon: ImageVector?, val badge: String? = null)

fun quickDef(id: String): QuickDef = when {
    id == "timerX" -> QuickDef(id, "${HomeCfg.timerMin} Min.", null, "${HomeCfg.timerMin}")
    id == "timer" -> QuickDef(id, "Timer", Ic.hourglass)
    id == "stopwatch" -> QuickDef(id, "Stoppuhr", Ic.stopwatch)
    id == "workout" -> QuickDef(id, "Workout", Ic.pulse)
    id == "calc" -> QuickDef(id, "Rechner", Ic.calc)
    id == "percent" -> QuickDef(id, "Prozent", Ic.percent)
    id == "conv" -> QuickDef(id, "Umrechner", Ic.swapH)
    id == "currency" -> QuickDef(id, "Währung", Ic.money)
    id == "breath" -> QuickDef(id, "Breath", Ic.breath)
    id == "altitude" -> QuickDef(id, "Höhe", Ic.mountain)
    id.startsWith("conv:") -> {
        val c = CATEGORIES.getOrNull(id.removePrefix("conv:").toIntOrNull() ?: -1)
        QuickDef(id, c?.name ?: "Umrechner", c?.icon ?: Ic.swapH)
    }
    else -> QuickDef(id, "–", Ic.plus)
}

val QUICK_BASE = listOf("timerX", "timer", "stopwatch", "workout", "calc", "percent", "conv", "currency", "breath", "altitude")

/** Schnellzugriff ausführen. */
fun runQuick(id: String, nav: Nav, ctx: Context, store: Store) {
    fun openConv(cat: Int) {
        if (ConvState.cat != cat) {
            ConvState.cat = cat
            ConvState.from = 0
            ConvState.to = 1
        }
        nav.go(Tab.RECHNER, 2)
    }
    when {
        id == "timerX" -> {
            TimerStore.start(ctx, HomeCfg.timerMin * 60_000L, "${HomeCfg.timerMin} Minuten")
            nav.go(Tab.UHR, 2)
        }
        id == "timer" -> nav.go(Tab.UHR, 2)
        id == "stopwatch" -> nav.go(Tab.UHR, 1)
        id == "workout" -> {
            val main = store.workouts.firstOrNull { it.id == "mein" }
                ?: store.workouts.firstOrNull { !it.id.startsWith("apnea") }
                ?: store.workouts.firstOrNull()
            if (main != null) nav.overlay = Overlay.RunWorkout(main.id) else nav.go(Tab.UHR, 2)
        }
        id == "calc" -> nav.go(Tab.RECHNER, 0)
        id == "percent" -> nav.go(Tab.RECHNER, 1)
        id == "conv" -> nav.go(Tab.RECHNER, 2)
        id == "currency" -> openConv(0)
        id == "breath" -> nav.go(Tab.BREATH)
        id == "altitude" -> nav.go(Tab.HOEHE)
        id.startsWith("conv:") -> openConv(id.removePrefix("conv:").toIntOrNull() ?: 1)
    }
}

// ------------------------------------------------------------------ Kacheln

/** Höhe kompakt: Barometer (mit Luftdruck vom Wetter) oder Geländehöhe am Standort. */
@Composable
fun AltitudeTile(nav: Nav, minHeight: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    var pressure by remember { mutableStateOf<Float?>(null) }
    var terrain by remember { mutableStateOf<Double?>(null) }
    var base by remember { mutableStateOf(store.getDouble("altBase", Double.NaN)) }
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
    LaunchedEffect(Unit) {
        terrain = runCatching { WeatherRepo.elevation(WeatherRepo.homeLat, WeatherRepo.homeLon) }.getOrNull()
    }
    val msl = WeatherRepo.home?.pressureMsl?.takeIf { !it.isNaN() }
    val baroAlt = pressure?.let { SensorManager.getAltitude((msl ?: 1013.25).toFloat(), it).toDouble() }
    val main = terrain ?: baroAlt
    val diff = if (!base.isNaN() && baroAlt != null) baroAlt - base else null

    Card(
        modifier = Modifier.heightIn(min = minHeight),
        padding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        onClick = { nav.go(Tab.HOEHE) }
    ) {
        Row(Modifier.heightIn(min = minHeight - 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(p.fill.copy(alpha = if (p.dark) 0.25f else 0.45f)),
                contentAlignment = Alignment.Center
            ) { TwIcon(Ic.mountain, size = 21.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Höhe", color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (main != null) "${fmt(main, 0)} m" else "–",
                    style = num(30.sp, FontWeight.SemiBold).copy(lineHeight = 34.sp), color = p.ink, maxLines = 1
                )
                Text(
                    when {
                        diff != null -> "${if (diff >= 0) "+" else ""}${diff.roundToInt()} m seit Nullpunkt"
                        pressure != null -> "${pressure!!.roundToInt()} hPa Luftdruck"
                        else -> "Geländehöhe am Standort"
                    },
                    color = p.sub, fontSize = 13.sp, maxLines = 1
                )
            }
            if (baroAlt != null) {
                Text(
                    "Nullpunkt", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(p.track)
                        .clickable {
                            base = baroAlt
                            store.putDouble("altBase", baroAlt)
                            store.putString(
                                "altBaseTime",
                                java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/** Brücke von der Rechner-Kachel zur Währungskachel. */
object CalcBridge {
    var push by mutableStateOf<String?>(null)
}

/**
 * Rechner kompakt mit eigenem Ziffernblock (keine Handy-Tastatur, damit die Währung darunter sichtbar bleibt).
 * Anzeige wie gewohnt: beim Tippen die Rechnung groß und das Ergebnis klein und grau darunter,
 * nach „=“ das Ergebnis groß und die Rechnung klein darüber.
 */
@Composable
fun CalcTile() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    var expr by remember { mutableStateOf("") }
    var lastExpr by remember { mutableStateOf("") }
    var justEval by remember { mutableStateOf(false) }
    val ops = "÷×−+"
    fun eval(e: String) = if (e.isBlank()) null
    else runCatching { Expr(e.replace(',', '.').replace('−', '-'), true).eval() }.getOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
    val hasOp = expr.drop(1).any { it in ops || it == '%' }
    val preview = if (!justEval && hasOp) eval(expr) else null
    val current = if (justEval) eval(expr) else preview ?: eval(expr)

    fun press(k: String) {
        val last = expr.lastOrNull()
        when (k) {
            "AC" -> { expr = ""; lastExpr = ""; justEval = false }
            "⌫" -> { expr = expr.dropLast(1); justEval = false }
            "=" -> eval(expr)?.let { r ->
                if (hasOp) {
                    lastExpr = expr
                    expr = fmt(r, 8).replace(".", "")
                    justEval = true
                }
            }
            "÷", "×", "−", "+" -> {
                justEval = false
                expr = when {
                    expr.isEmpty() -> if (k == "−") "−" else ""
                    last != null && last in ops -> expr.dropLast(1) + k
                    else -> expr + k
                }
            }
            "%" -> if (last != null && last.isDigit()) { expr += "%"; justEval = false }
            "," -> {
                if (justEval) { expr = "0,"; justEval = false; return }
                val seg = expr.takeLastWhile { it.isDigit() || it == ',' }
                if (!seg.contains(',')) expr += if (seg.isEmpty()) "0," else ","
            }
            else -> {
                expr = if (justEval) k else (expr + k).take(40)
                justEval = false
            }
        }
    }

    Card(padding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rechner", color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 2.dp))
            if (current != null) Text(
                "→ ${store.mainCur}", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(p.track)
                    .clickable { CalcBridge.push = fmt(current, 2) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        // Anzeige mit fester Höhe, damit beim Wechsel nichts springt
        Column(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            if (justEval) {
                Text(lastExpr, style = num(16.sp, FontWeight.Normal), color = p.sub, maxLines = 1)
                Text(current?.let { fmt(it, 8) } ?: expr, style = num(30.sp), color = p.ink, maxLines = 1)
            } else {
                Text(expr.ifEmpty { "0" }, style = num(30.sp), color = if (expr.isEmpty()) p.muted else p.ink, maxLines = 1)
                Text(preview?.let { fmt(it, 8) } ?: "", style = num(16.sp, FontWeight.Normal), color = p.sub, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        val rows = listOf(
            listOf("7", "8", "9", "÷", "⌫"),
            listOf("4", "5", "6", "×", "AC"),
            listOf("1", "2", "3", "−", "%"),
            listOf("0", "00", ",", "+", "=")
        )
        rows.forEachIndexed { ri, r ->
            if (ri > 0) Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.forEach { k ->
                    val digit = k[0].isDigit() || k == ","
                    val bg = when {
                        k == "=" -> p.fill
                        digit -> p.card
                        else -> p.track
                    }
                    val shape = RoundedCornerShape(50)
                    var m = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(shape)
                        .background(bg)
                    if (digit && p.cardRing != androidx.compose.ui.graphics.Color.Transparent) {
                        m = m.border(1.dp, p.cardRing, shape)
                    }
                    Box(m.clickable { press(k) }, contentAlignment = Alignment.Center) {
                        if (k == "⌫") TwIcon(Ic.backspace, size = 20.dp)
                        else Text(
                            k, color = if (k == "=") p.onFill else p.ink,
                            fontSize = if (digit) 19.sp else 17.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Einstellungen → Startseite

@Composable
fun HomeSettingsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    remember { HomeCfg.load(store); 0 }
    BackHandler { onBack() }
    var slot by remember { mutableIntStateOf(-1) }

    Column(Modifier.fillMaxSize()) {
        SubHeader("Startseite", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingLabel("Kacheln · halten und ziehen zum Sortieren")
            Card(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) { TileList(store) }

            SettingLabel("Schnellzugriffe")
            Card(padding = PaddingValues(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HomeCfg.quick.forEachIndexed { i, id ->
                        val d = quickDef(id)
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(p.track.copy(alpha = 0.55f))
                                .clickable { slot = i }
                                .padding(vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            QuickBadge(d, 30.dp)
                            Spacer(Modifier.height(5.dp))
                            Text(d.label, color = p.ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
                Text("Antippen zum Ändern", color = p.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            SettingLabel("Weitere")
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                val steps = listOf(5, 10, 15, 20, 25, 30, 45, 60, 90, 120)
                RowItem("Timer-Knopf", trailing = {
                    CircleButton(Ic.minus, {
                        HomeCfg.timerMin = steps.lastOrNull { it < HomeCfg.timerMin } ?: steps.first(); HomeCfg.save(store)
                    }, size = 30.dp, bg = p.track, iconSize = 15.dp, desc = "kürzer")
                    Text(
                        "${HomeCfg.timerMin} Min.", color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.width(66.dp)
                    )
                    CircleButton(Ic.plus, {
                        HomeCfg.timerMin = steps.firstOrNull { it > HomeCfg.timerMin } ?: steps.last(); HomeCfg.save(store)
                    }, size = 30.dp, bg = p.track, iconSize = 15.dp, desc = "länger")
                })
                Divider()
                var on by remember { mutableStateOf(store.getBool("clock2", false)) }
                var zone by remember { mutableStateOf(store.getString("clock2Zone") ?: "Europe/Berlin") }
                RowItem("Zweite Uhr", trailing = { Toggle(on) { on = it; store.putBool("clock2", it) } })
                if (on) {
                    (listOf("Europe/Berlin") + store.zones).distinct().forEach { z ->
                        Divider()
                        RowItem(cityForZone(z), onClick = { zone = z; store.putString("clock2Zone", z) }, trailing = {
                            if (z == zone) TwIcon(Ic.check, tint = p.ink, size = 18.dp)
                        })
                    }
                }
            }
            Text(
                "Die zweite Uhr steht klein unter der großen Uhr. Weitere Städte fügst du unter Uhr → Weltzeit hinzu.",
                color = p.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (slot >= 0) {
        AlertDialog(
            onDismissRequest = { slot = -1 },
            containerColor = p.card,
            title = { Text("Schnellzugriff ${slot + 1}", color = p.ink) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    fun pick(id: String) {
                        HomeCfg.quick = HomeCfg.quick.toMutableList().also { it[slot] = id }
                        HomeCfg.save(store)
                        slot = -1
                    }
                    QUICK_BASE.forEach { id ->
                        val d = quickDef(id)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { pick(id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QuickBadge(d, 30.dp)
                            Text(
                                if (id == "timerX") "Timer · ${d.label}" else d.label, color = p.ink, fontSize = 15.sp,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .weight(1f)
                            )
                            if (HomeCfg.quick.getOrNull(slot) == id) TwIcon(Ic.check, tint = p.ink, size = 18.dp)
                        }
                    }
                    Text("Umrechner direkt öffnen", color = p.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CATEGORIES.forEachIndexed { i, c ->
                            if (i == 0) return@forEachIndexed
                            val id = "conv:$i"
                            val sel = HomeCfg.quick.getOrNull(slot) == id
                            Text(
                                c.name, color = if (sel) p.onFill else p.ink, fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (sel) p.fill else p.track)
                                    .clickable { pick(id) }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton({ slot = -1 }) { Text("Schließen", color = p.sub) } }
        )
    }
}

/** Liste der Kacheln: Schalter an/aus, Reihenfolge durch langes Drücken und Ziehen. */
@Composable
private fun TileList(store: Store) {
    val p = LocalPal.current
    val rowH = 50.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOff by remember { mutableFloatStateOf(0f) }
    Column {
        HomeCfg.tiles.forEachIndexed { i, (id, on) ->
            val dragging = dragId == id
            Column(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOff else 0f
                        shadowElevation = if (dragging) 8f else 0f
                    }
                    .background(if (dragging) p.card else androidx.compose.ui.graphics.Color.Transparent)
                    .pointerInput(id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragId = id; dragOff = 0f },
                            onDrag = { change, d ->
                                change.consume()
                                dragOff += d.y
                                val list = HomeCfg.tiles
                                val from = list.indexOfFirst { it.first == id }
                                val to = (from + (dragOff / rowPx).roundToInt()).coerceIn(0, list.lastIndex)
                                if (to != from) {
                                    HomeCfg.tiles = list.toMutableList().apply { add(to, removeAt(from)) }
                                    dragOff -= (to - from) * rowPx
                                }
                            },
                            onDragEnd = { dragId = null; dragOff = 0f; HomeCfg.save(store) },
                            onDragCancel = { dragId = null; dragOff = 0f; HomeCfg.save(store) }
                        )
                    }
            ) {
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().height(rowH - 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    TwIcon(Ic.grip, tint = p.muted, size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    TwIcon(HomeCfg.tileIcon(id), tint = p.sub, size = 19.dp)
                    Text(HomeCfg.tileName(id), color = p.ink, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    Toggle(on) { v ->
                        HomeCfg.tiles = HomeCfg.tiles.map { if (it.first == id) id to v else it }
                        HomeCfg.save(store)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickBadge(d: QuickDef, size: androidx.compose.ui.unit.Dp) {
    val p = LocalPal.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(p.fill.copy(alpha = if (p.dark) 0.25f else 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        if (d.icon != null) TwIcon(d.icon, size = size * 0.56f)
        else Text(d.badge ?: "", style = num(if (size > 32.dp) 14.sp else 12.sp, FontWeight.Bold), color = p.ink)
    }
}

@Composable
private fun SettingLabel(s: String) {
    Text(
        s, color = LocalPal.current.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp)
    )
}
