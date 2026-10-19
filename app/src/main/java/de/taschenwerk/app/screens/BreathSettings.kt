package de.taschenwerk.app.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
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
    Tech("478", "4-7-8 Dr. Weil", listOf(4, 7, 8, 0)),
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
    var done by remember { mutableStateOf(false) }
    var durVersion by remember { mutableIntStateOf(0) }
    val minutes = remember(tech, durVersion) { store.getLong("brDur_$tech", 5L).toInt() }
    fun setDur(id: String, m: Int) {
        store.putLong("brDur_$id", m.toLong())
        durVersion++
    }
    var durDialog by remember { mutableStateOf(false) }
    var infoTech by remember { mutableStateOf<Tech?>(null) }
    val scale = remember { Animatable(SMALL) }

    fun persist() {
        store.putString("brTech", tech)
        store.putLong("brIn", inh.toLong())
        store.putLong("brHold", hold.toLong())
        store.putLong("brOut", exh.toLong())
        store.putLong("brPause", pause.toLong())
    }

    // Weiche Klaviertöne: Einatmen G3, Ausatmen D3, Halten/Pause dieselbe Taste ganz leise.
    fun phases() = listOf(
        Phase("in", "Einatmen", inh, 1f, 196.0),
        Phase("hold", "Halten", hold, 1f, -196.0),
        Phase("out", "Ausatmen", exh, SMALL, 146.83),
        Phase("pause", "Pause", pause, SMALL, -146.83)
    ).filter { it.sec > 0 }

    val customs = remember { mutableStateListOf<Tech>().apply { addAll(loadCustom(store)) } }
    var editCustom by remember { mutableStateOf<Tech?>(null) }
    var newCustom by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Chime.warm(196.0, 146.83) }

    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(running) {
        if (!running) {
            label = if (done) "Fertig" else "Bereit"
            done = false
            scale.animateTo(SMALL, tween(1600, easing = Ease))
            return@LaunchedEffect
        }
        // Dauer läuft unsichtbar im Hintergrund; es werden nur ganze Zyklen geatmet,
        // die Übung endet nach dem letzten Ausatmen.
        val phs = phases()
        val cycleSec = phs.sumOf { it.sec }.coerceAtLeast(1)
        val cycles = ((minutes * 60 + cycleSec - 1) / cycleSec).coerceAtLeast(1)
        for (c in 1..cycles) {
            for (ph in phs) {
                if (c == cycles && ph.key == "pause") break
                label = ph.label
                if (sound) {
                    if (ph.freq > 0) Chime.piano(ph.freq, 0.6) else Chime.piano(-ph.freq, 0.17)
                }
                val anim = launch { scale.animateTo(ph.to, tween(ph.sec * 1000, easing = Ease)) }
                delay(ph.sec * 1000L)
                anim.cancel()
            }
        }
        delay(700)
        if (sound) Chime.closing()
        done = true
        running = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 14.dp, bottom = 12.dp)
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Breath", color = p.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            CircleButton(
                if (sound) Ic.speaker else Ic.speakerOff,
                { sound = !sound; store.putBool("brSound", sound) },
                size = 40.dp, bg = p.bg, fg = if (sound) p.ink else p.muted, iconSize = 21.dp,
                desc = if (sound) "Ton aus" else "Ton an"
            )
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
                TechChip(t.name, tech == t.id, onTap = {
                    if (!running) {
                        tech = t.id
                        inh = t.v[0]; hold = t.v[1]; exh = t.v[2]; pause = t.v[3]
                        persist()
                    }
                }, onLong = { if (!running) infoTech = t })
            }
            customs.forEach { t ->
                TechChip(t.name, tech == t.id, onTap = {
                    tech = t.id
                    inh = t.v[0]; hold = t.v[1]; exh = t.v[2]; pause = t.v[3]
                    persist()
                }, onLong = { if (!running) editCustom = t })
            }
            AddChip { if (!running) newCustom = true }
        }
        Text(
            "Lange drücken für Rhythmus und Dauer", color = p.muted, fontSize = 11.sp,
            modifier = Modifier.padding(start = 20.dp, top = 6.dp)
        )
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val d = minOf(maxWidth - 60.dp, maxHeight - 16.dp, 300.dp)
            Box(Modifier.size(d), contentAlignment = Alignment.Center) {
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
                // Zwei zarte, durchgehende Kreise: ganz eingeatmet (außen) und ganz ausgeatmet (innen)
                val ringCol = p.ink.copy(alpha = if (p.dark) 0.22f else 0.13f)
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 1.3.dp.toPx()
                    val r = size.minDimension / 2
                    drawCircle(color = ringCol, radius = r - sw / 2, style = Stroke(width = sw))
                    drawCircle(color = ringCol, radius = r * SMALL, style = Stroke(width = sw))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = p.onFill, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(64.dp))
            CircleButton(
                if (running) Ic.pause else Ic.play, { running = !running },
                size = 78.dp, bg = p.fill, fg = p.onFill, iconSize = 30.dp, elevated = true,
                desc = if (running) "Stopp" else "Start"
            )
            Column(
                Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !running) { durDialog = true }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Dauer", color = p.sub, fontSize = 12.sp)
                Text("$minutes Min.", style = num(17.sp, FontWeight.SemiBold), color = p.ink, maxLines = 1)
            }
        }
    }

    if (newCustom || editCustom != null) {
        val base = editCustom
        TechDialog(
            initial = base ?: Tech("", "Mein Rhythmus", listOf(inh, hold, exh, pause)),
            editable = true,
            isNew = base == null,
            minutes = if (base != null) store.getLong("brDur_${base.id}", 5L).toInt() else 5,
            onSave = { t, m ->
                val saved = if (base == null) t.copy(id = "c" + System.currentTimeMillis()) else t.copy(id = base.id)
                val k = customs.indexOfFirst { it.id == saved.id }
                if (k >= 0) customs[k] = saved else customs.add(saved)
                saveCustom(store, customs)
                setDur(saved.id, m)
                tech = saved.id
                inh = saved.v[0]; hold = saved.v[1]; exh = saved.v[2]; pause = saved.v[3]
                persist()
                newCustom = false; editCustom = null
            },
            onDelete = if (base != null) ({
                customs.removeAll { it.id == base.id }
                saveCustom(store, customs)
                if (tech == base.id) tech = "custom"
                persist()
                editCustom = null
            }) else null,
            onClose = { newCustom = false; editCustom = null }
        )
    }
    val it0 = infoTech
    if (it0 != null) {
        TechDialog(
            initial = it0, editable = false, isNew = false,
            minutes = store.getLong("brDur_${it0.id}", 5L).toInt(),
            onSave = { t, m ->
                setDur(t.id, m)
                tech = t.id
                inh = t.v[0]; hold = t.v[1]; exh = t.v[2]; pause = t.v[3]
                persist()
                infoTech = null
            },
            onDelete = null,
            onClose = { infoTech = null }
        )
    }
    if (durDialog) {
        DurationDialog(minutes, onDone = { setDur(tech, it); durDialog = false }, onClose = { durDialog = false })
    }
}


private fun loadCustom(store: Store): List<Tech> = runCatching {
    val arr = org.json.JSONArray(store.getString("brCustom") ?: "[]")
    (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        val v = o.getJSONArray("v")
        Tech(o.getString("id"), o.getString("n"), (0 until 4).map { k -> v.optInt(k, 0) })
    }
}.getOrDefault(emptyList())

private fun saveCustom(store: Store, list: List<Tech>) {
    val arr = org.json.JSONArray()
    list.forEach { t ->
        arr.put(org.json.JSONObject().put("id", t.id).put("n", t.name).put("v", org.json.JSONArray(t.v)))
    }
    store.putString("brCustom", arr.toString())
}

@Composable
private fun TechChip(text: String, selected: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val p = LocalPal.current
    val tap = androidx.compose.runtime.rememberUpdatedState(onTap)
    val long = androidx.compose.runtime.rememberUpdatedState(onLong)
    val shape = RoundedCornerShape(19.dp)
    var m = Modifier
        .height(38.dp)
        .clip(shape)
        .background(if (selected) p.fill else p.card)
    if (!selected && p.cardRing != Color.Transparent) m = m.border(1.dp, p.cardRing, shape)
    Box(
        m
            .pointerInput(text) { detectTapGestures(onTap = { tap.value() }, onLongPress = { long.value() }) }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (selected) p.onFill else p.ink, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1
        )
    }
}

@Composable
private fun AddChip(onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(19.dp)
    Row(
        Modifier
            .height(38.dp)
            .clip(shape)
            .background(p.fill.copy(alpha = if (p.dark) 0.18f else 0.28f))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TwIcon(Ic.plus, size = 16.dp, tint = p.ink)
        Text("Eigenen", color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun MinutesStepper(m: Int, onChange: (Int) -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CircleButton(Ic.minus, { onChange((m - 1).coerceAtLeast(1)) }, size = 44.dp, bg = p.track, iconSize = 18.dp, desc = "kürzer")
        Text(
            "$m Min.", style = num(26.sp, FontWeight.SemiBold), color = p.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(110.dp)
        )
        CircleButton(Ic.plus, { onChange((m + 1).coerceAtMost(60)) }, size = 44.dp, bg = p.track, iconSize = 18.dp, desc = "länger")
    }
}

@Composable
private fun DurationDialog(initial: Int, onDone: (Int) -> Unit, onClose: () -> Unit) {
    val p = LocalPal.current
    var m by remember { mutableIntStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = p.card,
        title = { Text("Dauer", color = p.ink) },
        text = { MinutesStepper(m) { m = it } },
        confirmButton = { TextButton({ onDone(m) }) { Text("Fertig", color = p.ink, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClose) { Text("Abbrechen", color = p.sub) } }
    )
}

@Composable
private fun TechDialog(
    initial: Tech,
    editable: Boolean,
    isNew: Boolean,
    minutes: Int,
    onSave: (Tech, Int) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit
) {
    val p = LocalPal.current
    var name by remember { mutableStateOf(initial.name) }
    val v = remember { mutableStateListOf<Int>().apply { addAll(initial.v) } }
    var m by remember { mutableIntStateOf(minutes) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = p.card,
        title = {
            Text(
                when {
                    !editable -> initial.name
                    isNew -> "Eigener Rhythmus"
                    else -> "Rhythmus bearbeiten"
                },
                color = p.ink
            )
        },
        text = {
            Column {
                if (editable) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.track)
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                            textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
                            cursorBrush = SolidColor(p.ink), modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner -> if (name.isEmpty()) Text("Name", color = p.muted); inner() }
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
                val labels = listOf("Ein", "Halten", "Aus", "Pause")
                val mins = listOf(1, 0, 1, 0)
                Row {
                    labels.forEachIndexed { k, l ->
                        if (editable) {
                            StepperBox(
                                l, "${v[k]} s",
                                { v[k] = (v[k] - 1).coerceAtLeast(mins[k]) },
                                { v[k] = (v[k] + 1).coerceAtMost(30) },
                                Modifier.weight(1f)
                            )
                        } else {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(l, color = p.sub, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("${v[k]} s", style = num(20.sp, FontWeight.SemiBold), color = if (v[k] > 0) p.ink else p.muted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Dauer", color = p.sub, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(6.dp))
                MinutesStepper(m) { m = it }
                if (onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Rhythmus löschen", color = p.sub, fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDelete)
                            .padding(vertical = 6.dp, horizontal = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton({
                val t = if (editable) Tech(initial.id, name.ifBlank { "Mein Rhythmus" }.trim(), v.toList()) else initial
                onSave(t, m)
            }) {
                Text("Fertig", color = p.ink, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClose) { Text("Abbrechen", color = p.sub) } }
    )
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
                                .clickable { store.applyScheme(s) },
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
                    val idx = when (store.darkMode) {
                        1 -> 0
                        2 -> 1
                        else -> 2
                    }
                    Segmented(listOf("Hell", "Dunkel", "System"), idx, { i ->
                        store.applyDarkMode(
                            when (i) {
                                0 -> 1
                                1 -> 2
                                else -> 0
                            }
                        )
                    }, Modifier.fillMaxWidth())
                }
            }

            Label("Währung")
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                CurrencyPick("Hauptwährung", store.mainCur) { store.applyMainCur(it) }
                Divider()
                CurrencyPick("Zweitwährung", store.secondCur) { store.applySecondCur(it) }
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
                RowItem("Vibration", trailing = { Toggle(store.vibrate) { store.applyVibrate(it) } })
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
