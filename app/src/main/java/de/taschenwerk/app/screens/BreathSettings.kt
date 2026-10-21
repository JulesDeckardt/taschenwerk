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
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.SideEffect
import de.taschenwerk.app.util.Haptic
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
/** Größe des Balls (ganz eingeatmet) im Verhältnis zum Hof: füllt ihn ganz aus. */
private const val BALL = 1f
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
    // Klang: none, piano, bowl, kalimba, vibe (früher nur Ton an/aus)
    var klang by remember {
        mutableStateOf(store.getString("brKlang") ?: if (store.getBool("brSound", true)) "piano" else "none")
    }
    fun setKlang(k: String) {
        if (klang != "none") store.putString("brKlangLast", klang)
        klang = k
        store.putString("brKlang", k)
    }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var ver by remember { mutableIntStateOf(0) }
    val minutes = remember(tech, ver) { store.getLong("brDur_$tech", 5L).toInt() }
    val tempo = remember(tech, ver) { store.getLong("brTempo_$tech", 0L).toInt() }
    fun setDur(id: String, m: Int) { store.putLong("brDur_$id", m.toLong()); ver++ }
    fun setTempo(t: Int) { store.putLong("brTempo_$tech", t.toLong()); ver++ }
    var techOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(false) }
    var infoTech by remember { mutableStateOf<Tech?>(null) }
    val scale = remember { Animatable(SMALL) }

    // Steuerung während der Übung: ausgeblendet, ein Tipp zeigt sie für ein paar Sekunden.
    var awake by remember { mutableIntStateOf(0) }
    var awakeOn by remember { mutableStateOf(false) }
    LaunchedEffect(awake) {
        if (awake == 0) return@LaunchedEffect
        awakeOn = true
        delay(3200)
        awakeOn = false
    }
    val showControls = !running || awakeOn
    val ctrlAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (showControls) 1f else 0f, tween(if (showControls) 350 else 900), label = "ctrl"
    )
    SideEffect { de.taschenwerk.app.Zen.hide = running && !awakeOn }
    DisposableEffect(Unit) { onDispose { de.taschenwerk.app.Zen.hide = false; Chime.stopSolo() } }
    val navWake = de.taschenwerk.app.Zen.wake
    LaunchedEffect(navWake) { if (navWake > 0 && running) awake++ }

    fun persist() {
        store.putString("brTech", tech)
        store.putLong("brIn", inh.toLong())
        store.putLong("brHold", hold.toLong())
        store.putLong("brOut", exh.toLong())
        store.putLong("brPause", pause.toLong())
    }

    fun phases() = listOf(
        Phase("in", "Einatmen", inh, 1f, 0.0),
        Phase("hold", "Halten", hold, 1f, 0.0),
        Phase("out", "Ausatmen", exh, SMALL, 0.0),
        Phase("pause", "Halten", pause, SMALL, 0.0)
    ).filter { it.sec > 0 }

    /** Ton bzw. Vibration für eine Phase – immer nur ein Ton zur Zeit, alle Klänge gleich laut. */
    fun cue(key: String, k: String = klang, ms: Long = 3000L) {
        val i = when (key) { "in" -> 0; "out" -> 2; else -> 1 }
        if (k == "vibe") {
            when (i) {
                0 -> Haptic.inhale(ctx)
                1 -> Haptic.hold(ctx)
                else -> Haptic.exhale(ctx)
            }
            return
        }
        val f = TONES[k]?.get(i) ?: return
        Chime.breath(k, f, if (i == 1) HOLD_LEVEL else 1.0, ms)
    }

    fun closing() {
        when (klang) {
            "none" -> Unit
            "vibe" -> Haptic.done(ctx)
            else -> Chime.breathClosing(klang)
        }
    }

    val customs = remember { mutableStateListOf<Tech>().apply { addAll(loadCustom(store)) } }
    var editCustom by remember { mutableStateOf<Tech?>(null) }
    var newCustom by remember { mutableStateOf(false) }
    LaunchedEffect(klang) {
        TONES[klang]?.let { t -> Chime.warmBreath(klang, t[0] to 1.0, t[1] to HOLD_LEVEL, t[2] to 1.0) }
    }

    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(running) {
        if (!running) {
            if (!finished) Chime.stopSolo()
            finished = false
            scale.animateTo(SMALL, tween(1600, easing = Ease))
            return@LaunchedEffect
        }
        // Dauer läuft unsichtbar im Hintergrund; es werden nur ganze Zyklen geatmet,
        // die Übung endet nach dem letzten Ausatmen. Tempo verkürzt/verlängert alle Phasen.
        val f = (100 - tempo) / 100.0
        val phs = phases()
        val cycleMs = phs.sumOf { (it.sec * 1000 * f).toLong() }.coerceAtLeast(1L)
        val cycles = ((minutes * 60_000L + cycleMs - 1) / cycleMs).toInt().coerceAtLeast(1)
        for (c in 1..cycles) {
            for (ph in phs) {
                if (c == cycles && ph.key == "pause") break
                val ms = (ph.sec * 1000 * f).toLong()
                cue(ph.key, ms = ms)
                val anim = launch { scale.animateTo(ph.to, tween(ms.toInt(), easing = Ease)) }
                delay(ms)
                anim.cancel()
            }
        }
        delay(700)
        closing()
        finished = true
        running = false
    }

    val allTechs = TECHS + customs
    val cur = allTechs.firstOrNull { it.id == tech }
    val curName = cur?.name ?: "Eigener"
    fun select(t: Tech) {
        tech = t.id
        inh = t.v[0]; hold = t.v[1]; exh = t.v[2]; pause = t.v[3]
        persist()
    }
    val quiet = remember { MutableInteractionSource() }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 10.dp, bottom = 12.dp)
        ) {
            Row(
                Modifier
                    .padding(start = 16.dp, end = 8.dp)
                    .height(48.dp)
                    .graphicsLayer { alpha = ctrlAlpha },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (techOpen) p.track else Color.Transparent)
                        .border(1.dp, p.line, RoundedCornerShape(50))
                        .clickable(enabled = !running) { menuOpen = false; techOpen = !techOpen }
                        .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        curName, color = if (running) p.sub else p.ink, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                    TwIcon(Ic.chevDown, tint = p.sub, size = 18.dp)
                }
                Spacer(Modifier.weight(1f))
                CircleButton(
                    if (running) Ic.pause else Ic.play,
                    { techOpen = false; menuOpen = false; running = !running; if (running) awakeOn = false },
                    size = 42.dp, bg = Color.Transparent, fg = p.ink, iconSize = 20.dp,
                    desc = if (running) "Pause" else "Start"
                )
                CircleButton(
                    if (klang == "none") Ic.speakerOff else Ic.speaker,
                    {
                        if (klang == "none") setKlang(store.getString("brKlangLast") ?: "piano")
                        else setKlang("none")
                        if (running) awake++
                    },
                    size = 42.dp, bg = Color.Transparent, fg = if (klang == "none") p.muted else p.ink, iconSize = 21.dp,
                    desc = if (klang == "none") "Ton an" else "Ton aus"
                )
                CircleButton(
                    Ic.dots, { if (!running) { techOpen = false; menuOpen = !menuOpen } },
                    size = 42.dp, bg = Color.Transparent, fg = if (running) p.muted else p.ink, iconSize = 21.dp,
                    desc = "Einstellungen"
                )
            }
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val d = minOf(maxWidth - 76.dp, maxHeight - 24.dp, 300.dp)
                Box(
                    Modifier
                        .size(d)
                        .clip(CircleShape)
                        .clickable(interactionSource = quiet, indication = null) {
                            if (!running) { techOpen = false; menuOpen = false; running = true; awakeOn = false }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Sanfte Flächen: außen der Hof (ganz eingeatmet), innen eine leicht abgesetzte Scheibe (ganz ausgeatmet).
                    val halo = p.fill.copy(alpha = if (p.dark) 0.13f else 0.16f)
                    val core = if (p.dark) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.18f)
                    Canvas(Modifier.fillMaxSize()) { drawCircle(color = halo, radius = size.minDimension / 2) }
                    Box(
                        Modifier
                            .fillMaxSize(BALL)
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                            }
                            .clip(CircleShape)
                            .background(p.fill)
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(color = core, radius = size.minDimension / 2 * BALL * SMALL)
                    }
                }
            }
        }

        // Während der Übung: jeder Tipp holt erst die Steuerung zurück.
        if (running && !awakeOn) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(interactionSource = quiet, indication = null) { awake++ }
            )
        }

        if (techOpen || menuOpen) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(interactionSource = quiet, indication = null) { techOpen = false; menuOpen = false }
            )
        }

        if (techOpen) {
            Column(
                Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 62.dp)
                    .fillMaxWidth()
                    .shadow(14.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(p.card)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                allTechs.forEach { t ->
                    val sel = t.id == tech
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) p.track else Color.Transparent)
                            .clickable { select(t); techOpen = false }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            t.name, color = p.ink, fontSize = 15.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f), maxLines = 1
                        )
                        Text(rhythmText(t.v), color = p.muted, fontSize = 13.sp)
                        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterEnd) {
                            if (sel) TwIcon(Ic.check, tint = p.ink, size = 18.dp)
                        }
                    }
                }
                Divider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { techOpen = false; newCustom = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TwIcon(Ic.plus, tint = p.sub, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Eigener Rhythmus", color = p.sub, fontSize = 15.sp)
                }
            }
        }

        if (menuOpen) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(start = 12.dp, end = 12.dp, top = 62.dp)
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .shadow(14.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(p.card)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                MenuRow(Ic.clock, "Dauer") {
                    MiniStepper("$minutes Min.", { setDur(tech, (minutes - 1).coerceAtLeast(1)) }, { setDur(tech, (minutes + 1).coerceAtMost(60)) })
                }
                Divider()
                MenuRow(Ic.note, "Klang") {}
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(start = 34.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KLANGE.forEach { (id, name) ->
                        val sel = klang == id
                        Text(
                            name, color = if (sel) p.onFill else p.ink, fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) p.fill else p.track)
                                .clickable { setKlang(id); cue("in", id, 2500L) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
                Divider()
                MenuRow(Ic.pulse, "Rhythmus", onClick = {
                    menuOpen = false
                    val c = customs.firstOrNull { it.id == tech }
                    if (c != null) editCustom = c else infoTech = TECHS.firstOrNull { it.id == tech }
                }) {
                    Text("${rhythmText(listOf(inh, hold, exh, pause))}  ›", color = p.sub, fontSize = 14.sp)
                }
                Divider()
                MenuRow(Ic.gauge, "Tempo") {
                    MiniStepper(
                        when {
                            tempo == 0 -> "normal"
                            tempo > 0 -> "$tempo % schneller"
                            else -> "${-tempo} % langsamer"
                        },
                        { setTempo((tempo - 5).coerceAtLeast(-15)) }, { setTempo((tempo + 5).coerceAtMost(15)) },
                        labelWidth = 116
                    )
                }
                Divider()
                MenuRow(Ic.info, "Anleitung", onClick = { menuOpen = false; guideOpen = true }) {
                    Text("›", color = p.sub, fontSize = 16.sp)
                }
            }
        }
    }

    if (guideOpen) {
        AlertDialog(
            onDismissRequest = { guideOpen = false },
            containerColor = p.card,
            title = { Text(curName, color = p.ink) },
            text = {
                Column {
                    Text(breathGuide(tech), color = p.ink, fontSize = 15.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Der Ball wächst beim Einatmen, bleibt beim Halten stehen und wird beim Ausatmen kleiner. " +
                            "Antippen startet die Übung. Während der Übung holt ein Tipp die Steuerung kurz zurück.",
                        color = p.sub, fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
            },
            confirmButton = { TextButton({ guideOpen = false }) { Text("OK", color = p.ink, fontWeight = FontWeight.SemiBold) } }
        )
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
                select(saved)
                newCustom = false; editCustom = null
            },
            onDelete = if (base != null) ({
                customs.removeAll { it.id == base.id }
                saveCustom(store, customs)
                if (tech == base.id) select(TECHS.first { it.id == "box" })
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
                select(t)
                infoTech = null
            },
            onDelete = null,
            onClose = { infoTech = null }
        )
    }
}

/** Tonhöhen je Klang: Einatmen, Halten, Ausatmen – im Zweifel tiefer. */
private val TONES = mapOf(
    "piano" to doubleArrayOf(196.0, 261.63, 146.83),
    "bowl" to doubleArrayOf(349.23, 293.66, 261.63),
    "crystal" to doubleArrayOf(261.63, 220.0, 196.0),
    "gong" to doubleArrayOf(174.61, 146.83, 130.81),
    "kalimba" to doubleArrayOf(349.23, 293.66, 261.63)
)

/** Halten-Ton leiser als Ein-/Ausatmen. */
private const val HOLD_LEVEL = 0.62

private val KLANGE = listOf(
    "none" to "Kein Klang", "piano" to "Klavier", "bowl" to "Klangschale", "crystal" to "Kristallschale",
    "gong" to "Sanfter Gong", "kalimba" to "Kalimba", "vibe" to "Vibration"
)

private fun rhythmText(v: List<Int>) = v.filter { it > 0 }.joinToString(" · ")

private fun breathGuide(id: String): String = when (id) {
    "478" -> "Durch die Nase 4 Sekunden einatmen, 7 Sekunden die Luft anhalten, 8 Sekunden langsam durch den Mund ausatmen. Beruhigt stark und hilft beim Einschlafen. Wird dir schwindelig, das Tempo etwas schneller stellen."
    "koh" -> "Gleichmäßig 5 Sekunden ein und 5 Sekunden aus, ohne Pausen – sechs Atemzüge pro Minute. Bringt Herzschlag und Atmung in Einklang. Ideal: dreimal am Tag 5 Minuten."
    "box" -> "4 Sekunden ein, 4 halten, 4 aus, 4 halten – ruhig durch die Nase. Hilft, unter Stress klar und gelassen zu bleiben."
    "zwerch" -> "Eine Hand auf den Bauch: Beim Einatmen hebt sich der Bauch, die Brust bleibt ruhig. Dann länger und locker ausatmen. Vertieft die Atmung und entspannt."
    "lip" -> "Kurz durch die Nase ein, doppelt so lang mit locker gespitzten Lippen aus – als würdest du eine Kerze zum Flackern bringen, ohne sie auszublasen. Erleichtert das Ausatmen bei Atemnot."
    "copd" -> "3 Sekunden ein, 6 Sekunden langsam aus, am besten mit Lippenbremse. Das lange Ausatmen hält die Atemwege offen und senkt die Atemfrequenz."
    "relax" -> "4 Sekunden ein, 8 Sekunden aus. Das lange Ausatmen beruhigt den Puls und wirkt entspannend."
    else -> "Dein eigener Rhythmus. Unter „Rhythmus“ im Menü stellst du die Sekunden für Ein, Halten, Aus und Pause ein."
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    val p = LocalPal.current
    var m = Modifier
        .fillMaxWidth()
        .height(52.dp)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m, verticalAlignment = Alignment.CenterVertically) {
        TwIcon(icon, tint = p.sub, size = 20.dp)
        Spacer(Modifier.width(14.dp))
        Text(title, color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun MiniStepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit, labelWidth: Int = 64) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Ic.minus, onMinus, size = 30.dp, bg = p.track, iconSize = 15.dp, desc = "weniger")
        Text(
            label, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(labelWidth.dp)
        )
        CircleButton(Ic.plus, onPlus, size = 30.dp, bg = p.track, iconSize = 15.dp, desc = "mehr")
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
                                .padding(4.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(s.swatch)
                            if (sel) m = Modifier
                                .size(44.dp)
                                .border(2.dp, p.ink, CircleShape)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(s.swatch)
                            Box(m, contentAlignment = Alignment.Center) {
                                if (sel) TwIcon(Ic.check, tint = if (s == Scheme.SW) Color.White else Color(0xFF161618), size = 18.dp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                s.label, color = if (sel) p.ink else p.sub, fontSize = 11.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1
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

            AppIconCard()

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
                "Tipp für OPPO, Realme, Xiaomi & Co.: In den App-Infos von Polytool unter „Akku“ die Hintergrundaktivität erlauben und „Autostart“ einschalten. Dann klingelt der Wecker auch nach langer Ruhezeit sicher.",
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
private fun AppIconCard() {
    val ctx = LocalContext.current
    val p = LocalPal.current
    var cur by remember { mutableStateOf(de.taschenwerk.app.util.AppIcon.current(ctx)) }
    var ask by remember { mutableStateOf<de.taschenwerk.app.util.AppIcon?>(null) }
    Card {
        Text("App-Symbol", color = p.ink, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        de.taschenwerk.app.util.AppIcon.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { ic ->
                    val sel = ic == cur
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { if (!sel) ask = ic }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(62.dp)
                                .border(2.dp, if (sel) p.ink else Color.Transparent, RoundedCornerShape(18.dp))
                                .padding(5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                androidx.compose.ui.res.painterResource(ic.preview), ic.label,
                                Modifier.size(52.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            ic.label, color = if (sel) p.ink else p.sub, fontSize = 11.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    ask?.let { ic ->
        AlertDialog(
            onDismissRequest = { ask = null },
            containerColor = p.card,
            title = { Text("Symbol wechseln?", color = p.ink) },
            text = {
                Text(
                    "Das neue Symbol erscheint nach ein paar Sekunden. Manche Launcher entfernen dabei das Symbol vom Startbildschirm, dann einfach neu aus der App-Liste ablegen.",
                    color = p.sub
                )
            },
            confirmButton = {
                TextButton({
                    de.taschenwerk.app.util.AppIcon.apply(ctx, ic)
                    cur = ic
                    ask = null
                }) { Text("Wechseln", color = p.ink, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton({ ask = null }) { Text("Abbrechen", color = p.sub) } }
        )
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
