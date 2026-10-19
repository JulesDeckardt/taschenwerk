package de.taschenwerk.app.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.taschenwerk.app.Nav
import de.taschenwerk.app.data.CURRENCIES
import de.taschenwerk.app.data.Store
import de.taschenwerk.app.ui.Card
import de.taschenwerk.app.ui.Chip
import de.taschenwerk.app.ui.CircleButton
import de.taschenwerk.app.ui.Divider
import de.taschenwerk.app.ui.Ic
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.RowItem
import de.taschenwerk.app.ui.Scheme
import de.taschenwerk.app.ui.Segmented
import de.taschenwerk.app.ui.StepperBox
import de.taschenwerk.app.ui.Toggle
import de.taschenwerk.app.ui.TwIcon
import de.taschenwerk.app.ui.num
import de.taschenwerk.app.util.Chime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Breath

data class Tech(val id: String, val name: String, val v: List<Int>)

val TECHS = listOf(
    Tech("478", "4-7-8 (Dr. Weil)", listOf(4, 7, 8, 0)),
    Tech("koh", "Herzkohärenz", listOf(5, 0, 5, 0)),
    Tech("box", "Boxatmung", listOf(4, 4, 4, 4)),
    Tech("zwerch", "Zwerchfell", listOf(4, 0, 6, 0)),
    Tech("lip", "Lippenbremse", listOf(2, 0, 4, 0)),
    Tech("copd", "COPD-Atmung", listOf(3, 0, 6, 0)),
    Tech("relax", "Relaxator", listOf(4, 0, 8, 0))
)

private data class Phase(val key: String, val label: String, val sec: Int, val to: Float, val freq: Double)

private const val SMALL = 0.42f
private val Ease = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

@Composable
fun BreathScreen() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current

    var tech by remember { mutableStateOf(store.getString("brTech") ?: "box") }
    var inh by remember { mutableIntStateOf(store.getLong("brIn", 4L).toInt()) }
    var hold by remember { mutableIntStateOf(store.getLong("brHold", 4L).toInt()) }
    var exh by remember { mutableIntStateOf(store.getLong("brOut", 4L).toInt()) }
    var pause by remember { mutableIntStateOf(store.getLong("brPause", 4L).toInt()) }
    var sound by remember { mutableStateOf(store.getBool("brSound", true)) }
    var running by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("Bereit") }
    var count by remember { mutableIntStateOf(0) }
    var cycle by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(SMALL) }

    fun persist() {
        store.putString("brTech", tech)
        store.putLong("brIn", inh.toLong())
        store.putLong("brHold", hold.toLong())
        store.putLong("brOut", exh.toLong())
        store.putLong("brPause", pause.toLong())
    }

    fun phases() = listOf(
        Phase("in", "Einatmen", inh, 1f, 528.0),
        Phase("hold", "Halten", hold, 1f, 440.0),
        Phase("out", "Ausatmen", exh, SMALL, 396.0),
        Phase("pause", "Pause", pause, SMALL, 352.0)
    ).filter { it.sec > 0 }

    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(running) {
        if (!running) {
            label = "Bereit"
            count = 0
            scale.animateTo(SMALL, tween(900, easing = Ease))
            return@LaunchedEffect
        }
        cycle = 1
        while (isActive) {
            for (ph in phases()) {
                label = ph.label
                if (sound) Chime.play(ph.freq, 0.45, 1.8)
                val anim = launch { scale.animateTo(ph.to, tween(ph.sec * 1000, easing = Ease)) }
                for (s in ph.sec downTo 1) {
                    count = s
                    if (s != ph.sec && sound) Chime.play(587.0, 0.07, 0.35)
                    delay(1000)
                }
                anim.cancel()
            }
            cycle++
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 14.dp, bottom = 12.dp)
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
            Text("Breath", color = p.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("$inh · $hold · $exh · $pause s", color = p.sub, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TECHS.forEach { t ->
                Chip(t.name, tech == t.id, {
                    tech = t.id
                    inh = t.v[0]; hold = t.v[1]; exh = t.v[2]; pause = t.v[3]
                    persist()
                })
            }
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val d = minOf(maxWidth - 60.dp, maxHeight - 16.dp, 300.dp)
            Box(Modifier.size(d), contentAlignment = Alignment.Center) {
                val ring = p.line
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = ring, radius = size.minDimension / 2 - 2.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                        .clip(CircleShape)
                        .background(p.fill)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = p.onFill, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    if (running) Text("$count", style = num(44.sp), color = p.onFill)
                }
            }
        }
        Card(
            modifier = Modifier.padding(horizontal = 16.dp),
            padding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)
        ) {
            Row {
                StepperBox("Ein", "$inh s", { inh = (inh - 1).coerceAtLeast(1); tech = "custom"; persist() }, { inh = (inh + 1).coerceAtMost(30); tech = "custom"; persist() }, Modifier.weight(1f))
                StepperBox("Halten", "$hold s", { hold = (hold - 1).coerceAtLeast(0); tech = "custom"; persist() }, { hold = (hold + 1).coerceAtMost(30); tech = "custom"; persist() }, Modifier.weight(1f))
                StepperBox("Aus", "$exh s", { exh = (exh - 1).coerceAtLeast(1); tech = "custom"; persist() }, { exh = (exh + 1).coerceAtMost(30); tech = "custom"; persist() }, Modifier.weight(1f))
                StepperBox("Pause", "$pause s", { pause = (pause - 1).coerceAtLeast(0); tech = "custom"; persist() }, { pause = (pause + 1).coerceAtMost(30); tech = "custom"; persist() }, Modifier.weight(1f))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleButton(
                if (sound) Ic.speaker else Ic.speakerOff,
                { sound = !sound; store.putBool("brSound", sound) },
                size = 56.dp, desc = if (sound) "Ton aus" else "Ton an"
            )
            CircleButton(
                if (running) Ic.pause else Ic.play, { running = !running },
                size = 78.dp, bg = p.fill, fg = p.onFill, iconSize = 30.dp, elevated = true,
                desc = if (running) "Pause" else "Start"
            )
            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Zyklus", color = p.sub, fontSize = 12.sp)
                Text(if (running) "$cycle" else "–", style = num(20.sp), color = p.ink)
            }
        }
    }
}

// ------------------------------------------------------------------ Einstellungen

@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    var check by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { check++ }

    Column(Modifier.fillMaxSize()) {
        SubHeader("Einstellungen", { nav.overlay = null })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Label("Darstellung")
            Card {
                Text("Farbschema", color = p.ink, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Row {
                    Scheme.entries.forEach { s ->
                        val sel = store.scheme == s
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { store.setScheme(s) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            var m = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(s.swatch)
                            if (sel) m = Modifier
                                .size(50.dp)
                                .border(2.dp, p.ink, CircleShape)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(s.swatch)
                            Box(m, contentAlignment = Alignment.Center) {
                                if (sel) TwIcon(Ic.check, tint = androidx.compose.ui.graphics.Color(0xFF161618), size = 18.dp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                s.label, color = if (sel) p.ink else p.sub, fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Divider()
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dunkelmodus", color = p.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    val idx = when (store.darkMode) {
                        1 -> 0
                        2 -> 1
                        else -> 2
                    }
                    Segmented(listOf("Hell", "Dunkel", "System"), idx, { i ->
                        store.setDarkMode(
                            when (i) {
                                0 -> 1
                                1 -> 2
                                else -> 0
                            }
                        )
                    }, Modifier.width(210.dp))
                }
            }

            Label("Währung")
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                CurrencyPick("Hauptwährung", store.mainCur) { store.setMainCur(it) }
                Divider()
                CurrencyPick("Zweitwährung", store.secondCur) { store.setSecondCur(it) }
            }

            Label("Wecker & Timer")
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                RowItem("Schlummern", "${store.snoozeMin} Min.", onClick = {
                    val opts = listOf(5, 10, 15, 20)
                    store.setSnooze(opts[(opts.indexOf(store.snoozeMin) + 1) % opts.size])
                })
                Divider()
                RowItem("Langsam lauter werden", trailing = { Toggle(store.rampVolume) { store.setRamp(it) } })
                Divider()
                RowItem("Vibration", trailing = { Toggle(store.vibrate) { store.setVibrate(it) } })
            }

            Label("Zuverlässigkeit des Weckers")
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                if (check >= 0) {
                    PermStatus("Benachrichtigungen", Perms.notifications(ctx)) { Perms.openNotifications(ctx) }
                    Divider()
                    PermStatus("Wecker & Erinnerungen", Perms.exact(ctx)) { Perms.openExact(ctx) }
                    Divider()
                    PermStatus("Vollbild-Alarm", Perms.fullScreen(ctx)) { Perms.openFullScreen(ctx) }
                    Divider()
                    PermStatus("Akku-Optimierung aus", Perms.battery(ctx)) { Perms.openBattery(ctx) }
                }
            }
            Text(
                "Tipp für OPPO, Realme, Xiaomi & Co.: In den App-Infos von Taschenwerk unter „Akku“ die Hintergrundaktivität erlauben und „Autostart“ einschalten. Dann klingelt der Wecker auch nach langer Ruhezeit sicher.",
                color = p.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)
            )

            Label("Info")
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                val ver = remember {
                    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "–"
                }
                RowItem("Version", ver)
                Divider()
                RowItem("Wetter & Höhe", "Open-Meteo")
                Divider()
                RowItem("Wechselkurse", "ExchangeRate-API")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Label(s: String) {
    Text(
        s, color = LocalPal.current.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp)
    )
}

@Composable
private fun PermStatus(title: String, ok: Boolean, onFix: () -> Unit) {
    val p = LocalPal.current
    RowItem(title, if (ok) "Erlaubt" else "Antippen", onClick = if (ok) null else onFix, trailing = {
        Box(
            Modifier
                .padding(start = 10.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(if (ok) p.fill else androidx.compose.ui.graphics.Color(0xFFE5484D))
        )
    })
}

@Composable
private fun CurrencyPick(title: String, code: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RowItem(title, code, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CURRENCIES.forEach { (c, n) ->
                DropdownMenuItem(text = { Text("$c  ·  $n") }, onClick = { onPick(c); open = false })
            }
        }
    }
}
