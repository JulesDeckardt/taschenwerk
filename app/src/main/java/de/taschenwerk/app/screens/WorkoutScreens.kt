package de.taschenwerk.app.screens

import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.taschenwerk.app.Nav
import de.taschenwerk.app.Overlay
import de.taschenwerk.app.data.Step
import de.taschenwerk.app.data.Store
import de.taschenwerk.app.data.Workout
import de.taschenwerk.app.ui.Card
import de.taschenwerk.app.ui.CircleButton
import de.taschenwerk.app.ui.Divider
import de.taschenwerk.app.ui.Ic
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.PillButton
import de.taschenwerk.app.ui.Segmented
import de.taschenwerk.app.ui.SmallButton
import de.taschenwerk.app.ui.StepperBox
import de.taschenwerk.app.ui.Toggle
import de.taschenwerk.app.ui.TwIcon
import de.taschenwerk.app.ui.clickableNoRipple
import de.taschenwerk.app.ui.num
import de.taschenwerk.app.util.Beep
import de.taschenwerk.app.util.Chime
import de.taschenwerk.app.util.Voice
import de.taschenwerk.app.util.hms
import de.taschenwerk.app.util.two
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun WorkoutEditScreen(nav: Nav, id: String?) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    val existing = remember(id) { store.workouts.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(existing?.name ?: "Neues Workout") }
    var rounds by remember { mutableIntStateOf(existing?.rounds ?: 3) }
    var prep by remember { mutableIntStateOf(existing?.prep ?: 10) }
    val steps = remember {
        mutableStateListOf<Step>().apply {
            addAll(existing?.steps ?: listOf(Step("Übung 1", 30, false), Step("Pause", 15, true)))
        }
    }
    var announce by remember { mutableStateOf(store.getBool("announce", true)) }
    var voiceLang by remember { mutableStateOf(store.getString("voiceLang") ?: "auto") }
    var showGuide by remember { mutableStateOf(false) }
    val wid = remember { existing?.id ?: ("w" + System.currentTimeMillis()) }

    fun build() = Workout(wid, name.ifBlank { "Workout" }, rounds, prep, steps.toList())
    fun save() {
        store.upsertWorkout(build())
    }

    val total = prep + rounds * steps.sumOf { it.sec }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleButton(Ic.chevLeft, { nav.overlay = null }, size = 44.dp, bg = p.bg, desc = "Zurück")
            BasicTextField(
                value = name, onValueChange = { name = it.take(30) }, singleLine = true,
                textStyle = TextStyle(color = p.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(p.ink),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            CircleButton(Ic.info, { showGuide = true }, size = 40.dp, bg = p.bg, iconSize = 20.dp, fg = p.sub, desc = "Anleitung")
            if (existing != null) {
                CircleButton(Ic.trash, {
                    store.removeWorkout(existing.id)
                    nav.overlay = null
                }, size = 44.dp, bg = p.bg, iconSize = 20.dp, desc = "Workout löschen")
            }
            Text(
                "Speichern", color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickableNoRipple {
                        save()
                        nav.overlay = null
                    }
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(padding = PaddingValues(vertical = 12.dp, horizontal = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gesamt", color = p.sub, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(hms(total.toLong(), false), style = num(22.sp), color = p.ink)
                    }
                    StepperBox("Runden", "$rounds", { rounds = (rounds - 1).coerceAtLeast(1) }, { rounds = (rounds + 1).coerceAtMost(99) }, Modifier.weight(1f))
                    StepperBox("Vorlauf", "$prep s", { prep = (prep - 5).coerceAtLeast(0) }, { prep = (prep + 5).coerceAtMost(60) }, Modifier.weight(1f))
                }
            }

            Text(
                "Ablauf pro Runde", color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
            var exNo = 0
            steps.forEachIndexed { i, st ->
                if (!st.pause) exNo++
                val no = exNo
                Card(padding = PaddingValues(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), radius = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            TwIcon(Ic.chevUp, tint = if (i > 0) p.sub else p.line, size = 20.dp, modifier = Modifier.clickableNoRipple {
                                if (i > 0) { val t = steps[i - 1]; steps[i - 1] = steps[i]; steps[i] = t }
                            })
                            TwIcon(Ic.chevDown, tint = if (i < steps.size - 1) p.sub else p.line, size = 20.dp, modifier = Modifier.clickableNoRipple {
                                if (i < steps.size - 1) { val t = steps[i + 1]; steps[i + 1] = steps[i]; steps[i] = t }
                            })
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (st.pause) p.track else p.fill),
                            contentAlignment = Alignment.Center
                        ) {
                            if (st.pause) TwIcon(Ic.pause, tint = p.sub, size = 14.dp)
                            else Text("$no", color = p.onFill, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        if (st.pause) {
                            Text(st.name, color = p.sub, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        } else {
                            BasicTextField(
                                value = st.name, onValueChange = { v -> steps[i] = st.copy(name = v.take(24)) },
                                singleLine = true,
                                textStyle = TextStyle(color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                                cursorBrush = SolidColor(p.ink),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        CircleButton(Ic.minus, { steps[i] = st.copy(sec = (st.sec - 5).coerceAtLeast(5)) }, size = 34.dp, bg = p.track, iconSize = 16.dp, desc = "kürzer")
                        Text(
                            "${st.sec} s", style = num(15.sp, FontWeight.SemiBold), color = p.ink,
                            textAlign = TextAlign.Center, modifier = Modifier.width(48.dp)
                        )
                        CircleButton(Ic.plus, { steps[i] = st.copy(sec = (st.sec + 5).coerceAtMost(600)) }, size = 34.dp, bg = p.track, iconSize = 16.dp, desc = "länger")
                        Spacer(Modifier.width(4.dp))
                        TwIcon(Ic.close, tint = p.sub, size = 18.dp, modifier = Modifier.clickableNoRipple {
                            if (steps.size > 1) steps.removeAt(i)
                        })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallButton("+ Übung", Modifier.weight(1f)) {
                    steps.add(Step("Übung ${steps.count { !it.pause } + 1}", 30, false))
                }
                SmallButton("+ Pause", Modifier.weight(1f)) { steps.add(Step("Pause", 15, true)) }
            }
            Card(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nächste Übung ansagen", color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Toggle(announce) { announce = it; store.putBool("announce", it) }
                }
                if (announce) {
                    Spacer(Modifier.height(8.dp))
                    Text("Sprache der Stimme", color = p.sub, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    val modes = listOf("auto", "de", "en")
                    Segmented(
                        listOf("Automatisch", "Deutsch", "Englisch"),
                        modes.indexOf(voiceLang).coerceAtLeast(0),
                        { voiceLang = modes[it]; store.putString("voiceLang", modes[it]) }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (voiceLang == "auto") "Englische Übungsnamen wie Crunches oder Squats werden englisch gesprochen."
                        else if (voiceLang == "en") "Alle Ansagen auf Englisch." else "Alle Ansagen auf Deutsch.",
                        color = p.sub, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            PillButton("Start", Ic.play, true, Modifier.fillMaxWidth()) {
                save()
                nav.overlay = Overlay.RunWorkout(wid)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (showGuide) GuideDialog(build()) { showGuide = false }
}

private enum class Kind { PREP, WORK, PAUSE }

private data class Seg(val name: String, val sec: Int, val kind: Kind, val round: Int, val exIdx: Int, val exCount: Int, val stepIdx: Int)

private fun segments(w: Workout): List<Seg> {
    val out = ArrayList<Seg>()
    val exCount = w.steps.count { !it.pause }
    if (w.prep > 0) out.add(Seg("Vorbereitung", w.prep, Kind.PREP, 1, 0, exCount, -1))
    for (r in 1..w.rounds) {
        var ex = 0
        w.steps.forEachIndexed { si, s ->
            if (!s.pause) ex++
            out.add(Seg(s.name, s.sec, if (s.pause) Kind.PAUSE else Kind.WORK, r, ex, exCount, si))
        }
    }
    while (out.isNotEmpty() && out.last().kind == Kind.PAUSE) out.removeAt(out.size - 1)
    return out
}

@Composable
fun WorkoutRunScreen(nav: Nav, id: String) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    val w = remember(id) { store.workouts.firstOrNull { it.id == id } }
    if (w == null) {
        LaunchedEffect(Unit) { nav.overlay = null }
        return
    }
    val segs = remember(w) { segments(w) }
    var announce by remember { mutableStateOf(store.getBool("announce", true)) }
    var showGuide by remember { mutableStateOf(false) }
    val voiceMode = remember { store.getString("voiceLang") ?: "auto" }
    val scope = rememberCoroutineScope()

    var idx by remember { mutableIntStateOf(0) }
    var segEnd by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var pausedLeft by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        Chime.warmBell()
        onDispose { view.keepScreenOn = false }
    }
    val tts = remember { arrayOfNulls<TextToSpeech>(1) }
    val enOk = remember { booleanArrayOf(false) }
    DisposableEffect(Unit) {
        var t: TextToSpeech? = null
        t = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) runCatching {
                val r = t?.isLanguageAvailable(Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED
                enOk[0] = r >= TextToSpeech.LANG_AVAILABLE
                t?.setLanguage(Locale.GERMAN)
            }
        }
        tts[0] = t
        onDispose {
            runCatching { t?.stop(); t?.shutdown() }
        }
    }

    val english = voiceMode == "en"
    fun nameLocale(n: String): Locale = when (voiceMode) {
        "en" -> Locale.US
        "de" -> Locale.GERMAN
        else -> if (Voice.isEnglish(n)) Locale.US else Locale.GERMAN
    }

    /** Spricht mehrere Teile nacheinander, jeden in seiner Sprache. */
    fun say(vararg parts: Pair<String, Locale>) {
        if (!announce) return
        val t = tts[0] ?: return
        runCatching {
            parts.filter { it.first.isNotBlank() }.forEachIndexed { i, (txt, loc) ->
                val l = if (loc == Locale.US && !enOk[0]) Locale.GERMAN else loc
                t.setLanguage(l)
                t.speak(txt, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "tw$i")
            }
        }
    }
    val lang = if (english) Locale.US else Locale.GERMAN

    fun go(i: Int) {
        val prev = if (!finished && i == idx + 1) segs.getOrNull(idx) else null
        val bell = prev?.kind == Kind.WORK
        if (bell) Chime.bell()
        val wait = if (bell) 1100L else 0L
        if (i >= segs.size) {
            finished = true
            scope.launch {
                delay(wait)
                Chime.finish()
                delay(1900)
                say((if (english) "Well done" else "Geschafft") to lang)
            }
            return
        }
        idx = i.coerceAtLeast(0)
        val s = segs[idx]
        segEnd = SystemClock.elapsedRealtime() + s.sec * 1000L
        paused = false
        if (!bell) Beep.long()
        scope.launch {
            delay(wait)
            when (s.kind) {
                Kind.WORK -> say(s.name to nameLocale(s.name))
                Kind.PAUSE -> {
                    val next = segs.drop(idx + 1).firstOrNull { it.kind == Kind.WORK }
                    if (s.name != "Pause") say(s.name to nameLocale(s.name))
                    else if (next != null) say((if (english) "Rest. Next up" else "Pause. Danach") to lang, next.name to nameLocale(next.name))
                    else say((if (english) "Rest" else "Pause") to lang)
                }
                Kind.PREP -> {
                    val first = segs.getOrNull(idx + 1)?.name ?: ""
                    say((if (english) "Get ready. First up" else "Gleich geht's los. Zuerst") to lang, first to nameLocale(first))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        go(0)
        var lastSec = -1
        while (isActive) {
            nowMs = SystemClock.elapsedRealtime()
            if (!paused && !finished) {
                val left = segEnd - nowMs
                val secLeft = ((left + 999) / 1000).toInt()
                if (secLeft != lastSec) {
                    lastSec = secLeft
                    if (secLeft in 1..3) Beep.short()
                }
                if (left <= 0) {
                    go(idx + 1)
                    lastSec = -1
                }
            }
            delay(50)
        }
    }

    if (finished) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(p.fill), contentAlignment = Alignment.Center
            ) { TwIcon(Ic.check, tint = p.onFill, size = 44.dp) }
            Spacer(Modifier.height(20.dp))
            Text("Geschafft!", color = p.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("${w.name} · ${hms(w.totalSec.toLong(), false)}", color = p.sub, fontSize = 16.sp)
            Spacer(Modifier.height(32.dp))
            PillButton("Fertig", null, true, Modifier.fillMaxWidth()) { nav.overlay = null }
        }
        return
    }

    val s = segs[idx]
    val left = if (paused) pausedLeft else (segEnd - nowMs).coerceAtLeast(0L)
    val secLeft = ((left + 999) / 1000)
    val remainingTotal = secLeft + segs.drop(idx + 1).sumOf { it.sec }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleButton(Ic.chevLeft, { nav.overlay = null }, size = 44.dp, bg = p.bg, desc = "Beenden")
            Text(w.name, color = p.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp))
            Text("Runde ${s.round} / ${w.rounds}", color = p.sub, fontSize = 15.sp, modifier = Modifier.padding(end = 2.dp))
            CircleButton(Ic.info, { showGuide = true }, size = 36.dp, bg = p.bg, fg = p.sub, iconSize = 18.dp, desc = "Anleitung")
            CircleButton(
                if (announce) Ic.speaker else Ic.speakerOff,
                {
                    announce = !announce
                    store.putBool("announce", announce)
                    if (!announce) runCatching { tts[0]?.stop() }
                },
                size = 36.dp, bg = p.bg, fg = if (announce) p.ink else p.muted, iconSize = 18.dp,
                desc = if (announce) "Stimme aus" else "Stimme an"
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            val pill = when (s.kind) {
                Kind.PREP -> "VORBEREITUNG"
                Kind.PAUSE -> s.name.uppercase()
                Kind.WORK -> if (w.id.startsWith("apnea")) "HALTEN ${s.exIdx} / ${s.exCount}"
                    else if (s.exCount > 1) "ÜBUNG ${s.exIdx} / ${s.exCount}" else "ÜBUNG"
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (s.kind == Kind.WORK) p.fill else p.track)
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    pill, color = if (s.kind == Kind.WORK) p.onFill else p.ink,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                s.name,
                color = p.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1
            )
            Text(
                buildAnnotatedString {
                    append("${two((secLeft / 60).toInt())}:")
                    withStyle(SpanStyle(color = p.accent)) { append(two((secLeft % 60).toInt())) }
                },
                style = num(108.sp, FontWeight.SemiBold), color = p.ink
            )
            Spacer(Modifier.height(14.dp))

            // Fortschritt der aktuellen Runde
            val roundSegs = segs.filter { it.round == s.round && it.kind != Kind.PREP }
            if (roundSegs.isNotEmpty() && s.kind != Kind.PREP) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    roundSegs.forEach { rs ->
                        val doneFrac = when {
                            rs.stepIdx < s.stepIdx -> 1f
                            rs.stepIdx == s.stepIdx -> 1f - left.toFloat() / (rs.sec * 1000f)
                            else -> 0f
                        }.coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .weight(rs.sec.toFloat())
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(p.line)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(doneFrac)
                                    .background(if (rs.kind == Kind.PAUSE) p.fill.copy(alpha = 0.5f) else p.fill)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Als Nächstes", color = p.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("noch ${hms(remainingTotal, false)} gesamt", color = p.sub, fontSize = 13.sp)
                }
                val next = segs.drop(idx + 1).take(4)
                if (next.isEmpty()) {
                    Divider()
                    Text("Letzte Einheit", color = p.sub, fontSize = 15.sp, modifier = Modifier.padding(vertical = 11.dp))
                }
                next.forEach { n ->
                    Divider()
                    Row(Modifier.height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            n.name,
                            color = if (n.kind == Kind.PAUSE) p.sub else p.ink,
                            fontSize = 16.sp, fontWeight = if (n.kind == Kind.PAUSE) FontWeight.Normal else FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${n.sec} s", color = if (n.kind == Kind.PAUSE) p.sub else p.ink, fontSize = 16.sp)
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleButton(Ic.skipPrev, { go(idx - 1) }, desc = "Zurück")
            CircleButton(
                if (paused) Ic.play else Ic.pause,
                {
                    if (paused) {
                        segEnd = SystemClock.elapsedRealtime() + pausedLeft
                        paused = false
                    } else {
                        pausedLeft = (segEnd - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                        paused = true
                    }
                },
                size = 80.dp, bg = p.fill, fg = p.onFill, iconSize = 30.dp, elevated = true,
                desc = if (paused) "Weiter" else "Pause"
            )
            CircleButton(Ic.skipNext, { go(idx + 1) }, bg = p.track, desc = "Überspringen")
        }
    }
    if (showGuide) GuideDialog(w) { showGuide = false }
}
