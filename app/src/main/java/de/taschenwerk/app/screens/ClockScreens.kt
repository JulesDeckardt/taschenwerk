package de.taschenwerk.app.screens

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import de.taschenwerk.app.alarm.AlarmSounds
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.taschenwerk.app.Nav
import de.taschenwerk.app.Overlay
import de.taschenwerk.app.alarm.AlarmScheduler
import de.taschenwerk.app.alarm.TimerStore
import de.taschenwerk.app.data.Alarm
import de.taschenwerk.app.data.Store
import de.taschenwerk.app.data.TimerPreset
import de.taschenwerk.app.ui.Card
import de.taschenwerk.app.ui.CircleButton
import de.taschenwerk.app.ui.Divider
import de.taschenwerk.app.ui.Fab
import de.taschenwerk.app.ui.Ic
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.SectionTitle
import de.taschenwerk.app.ui.Segmented
import de.taschenwerk.app.ui.SmallButton
import de.taschenwerk.app.ui.Toggle
import de.taschenwerk.app.ui.TwIcon
import de.taschenwerk.app.ui.num
import de.taschenwerk.app.util.hms
import de.taschenwerk.app.util.two
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun ClockTab(nav: Nav) {
    Column(Modifier.fillMaxSize()) {
        Segmented(
            listOf("Wecker", "Stoppuhr", "Timer", "Weltzeit"), nav.uhrSub, { nav.uhrSub = it },
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
        )
        Box(Modifier.weight(1f)) {
            when (nav.uhrSub) {
                0 -> AlarmsPage(nav)
                1 -> StopwatchPage()
                2 -> TimerPage(nav)
                else -> WorldPage()
            }
        }
    }
}

// ------------------------------------------------------------------ Wecker

@Composable
fun AlarmsPage(nav: Nav) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    var check by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { check++ }
    val tick by rememberNow(30_000)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text("Wecker", color = p.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                val next = store.alarms.filter { it.enabled }.minByOrNull { AlarmScheduler.nextTrigger(it).toEpochSecond() }
                key(tick) {
                    Text(
                        if (next == null) "Alle Wecker ausgeschaltet" else "Nächster Wecker ${untilText(next)}",
                        color = p.sub, fontSize = 15.sp
                    )
                }
            }

            if (check >= 0 && !Perms.allOk(ctx)) ReliabilityCard()

            // Welcher Wecker auf der Startseite steht (Tippen dort öffnet ihn direkt zum Einstellen)
            if (store.alarms.isNotEmpty()) {
                var homeId by remember { mutableIntStateOf(store.getLong("homeAlarm", -1L).toInt()) }
                var pickHome by remember { mutableStateOf(false) }
                val chosen = store.alarms.firstOrNull { it.id == homeId }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { pickHome = true }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TwIcon(Ic.home, tint = p.sub, size = 18.dp)
                    Text("Startwecker", color = p.sub, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    Text(
                        chosen?.let { "${two(it.hour)}:${two(it.minute)}" + if (it.label.isNotBlank()) " · ${it.label}" else "" } ?: "Automatisch",
                        color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    TwIcon(Ic.chevRight, tint = p.muted, size = 16.dp, modifier = Modifier.padding(start = 4.dp))
                }
                if (pickHome) {
                    AlertDialog(
                        onDismissRequest = { pickHome = false },
                        containerColor = p.card,
                        title = { Text("Wecker auf der Startseite", color = p.ink) },
                        text = {
                            Column {
                                fun set(id: Int) { homeId = id; store.putLong("homeAlarm", id.toLong()); pickHome = false }
                                SoundRow("Automatisch (nächster Wecker)", homeId == -1 || chosen == null) { set(-1) }
                                store.alarms.forEach { a ->
                                    SoundRow(
                                        "${two(a.hour)}:${two(a.minute)}" + if (a.label.isNotBlank()) " · ${a.label}" else "",
                                        a.id == homeId
                                    ) { set(a.id) }
                                }
                            }
                        },
                        confirmButton = { TextButton({ pickHome = false }) { Text("Schließen", color = p.sub) } }
                    )
                }
            }

            store.alarms.forEach { a ->
                Card(onClick = { nav.overlay = Overlay.EditAlarm(a.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${two(a.hour)}:${two(a.minute)}", style = num(40.sp), color = if (a.enabled) p.ink else p.muted)
                            Text(alarmSubtitle(a), color = p.sub, fontSize = 14.sp, maxLines = 1)
                        }
                        Toggle(a.enabled) { on ->
                            val na = a.copy(enabled = on)
                            store.upsertAlarm(na)
                            AlarmScheduler.schedule(ctx, na)
                            if (on) Toast.makeText(ctx, "Klingelt ${untilText(na)}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            Spacer(Modifier.height(90.dp))
        }
        Fab(
            Ic.plus, { nav.overlay = Overlay.EditAlarm(null) },
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp), desc = "Wecker hinzufügen"
        )
    }
}

@Composable
fun ReliabilityCard() {
    val ctx = LocalContext.current
    val p = LocalPal.current
    Card {
        Text("Damit der Wecker sicher klingelt", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        if (!Perms.notifications(ctx)) PermRow("Benachrichtigungen erlauben") { Perms.openNotifications(ctx) }
        if (!Perms.exact(ctx)) PermRow("Wecker & Erinnerungen erlauben") { Perms.openExact(ctx) }
        if (!Perms.fullScreen(ctx)) PermRow("Vollbild-Alarm erlauben") { Perms.openFullScreen(ctx) }
        if (!Perms.battery(ctx)) PermRow("Akku-Optimierung ausschalten") { Perms.openBattery(ctx) }
    }
}

@Composable
private fun PermRow(text: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(p.fill)
        )
        Text(text, color = p.ink, fontSize = 15.sp, modifier = Modifier
            .padding(start = 10.dp)
            .weight(1f))
        TwIcon(Ic.chevRight, tint = p.sub, size = 18.dp)
    }
}

@Composable
fun AlarmEditScreen(nav: Nav, id: Int?) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    val existing = remember(id) { store.alarms.firstOrNull { it.id == id } }
    var hour by remember { mutableIntStateOf(existing?.hour ?: 7) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: 0) }
    var days by remember { mutableIntStateOf(existing?.days ?: 0) }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var sound by remember { mutableStateOf(existing?.sound ?: "") }
    var pickSound by remember { mutableStateOf(false) }
    val tick by rememberNow(15_000)

    fun save() {
        val a = Alarm(existing?.id ?: store.newAlarmId(), hour, minute, days, true, label.trim(), sound)
        store.upsertAlarm(a)
        AlarmScheduler.schedule(ctx, a)
        Toast.makeText(ctx, "Wecker klingelt ${untilText(a)}", Toast.LENGTH_SHORT).show()
        nav.overlay = null
    }

    Column(Modifier.fillMaxSize()) {
        SubHeader(if (existing == null) "Neuer Wecker" else "Wecker bearbeiten", { nav.overlay = null }, "Speichern") { save() }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Die Uhrzeit ist das Zentrum: große Räder in der Mitte
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberWheel(24, hour, { hour = it }, width = 112.dp, big = 64.sp, itemHeight = 78.dp)
                Text(":", style = num(52.sp), color = p.ink, modifier = Modifier.padding(horizontal = 6.dp))
                NumberWheel(60, minute, { minute = it }, width = 112.dp, big = 64.sp, itemHeight = 78.dp)
            }

            // Wie lange noch bis zum Klingeln – klein und ruhig
            key(tick) {
                val preview = Alarm(0, hour, minute, days, true)
                val next = AlarmScheduler.nextTrigger(preview)
                val d = java.time.Duration.between(ZonedDateTime.now(), next)
                val totalMin = (d.toMinutes() + if (d.seconds % 60 > 0) 1 else 0).coerceAtLeast(0)
                val hh = totalMin / 60
                val mm = totalMin % 60
                val inTxt = when {
                    hh >= 24 -> "${hh / 24} T. ${hh % 24} Std."
                    hh > 0 -> "$hh Std. $mm Min."
                    else -> "$mm Min."
                }
                Text(
                    buildAnnotatedString {
                        append("Klingelt in ")
                        withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight.SemiBold)) { append(inTxt) }
                        append(" · " + next.format(DateTimeFormatter.ofPattern("EE, HH:mm", Locale.GERMAN)))
                    },
                    color = p.sub, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 14.dp)
                )
            }

            Card(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEachIndexed { i, d ->
                        val on = days and (1 shl i) != 0
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            DayCircle(d, on) { days = days xor (1 shl i) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniChip("Einmal", days == 0) { days = 0 }
                    MiniChip("Mo–Fr", days == 0b0011111) { days = 0b0011111 }
                    MiniChip("Täglich", days == 0b1111111) { days = 0b1111111 }
                }
            }
            Card(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clickable { pickSound = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Weckton", color = p.sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(remember(sound) { AlarmSounds.title(ctx, sound) }, color = p.ink, fontSize = 15.sp, maxLines = 1)
                    TwIcon(Ic.chevRight, tint = p.muted, size = 16.dp, modifier = Modifier.padding(start = 4.dp))
                }
                Divider()
                Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bezeichnung", color = p.sub, fontSize = 14.sp)
                    BasicTextField(
                        value = label, onValueChange = { label = it.take(40) }, singleLine = true,
                        textStyle = TextStyle(color = p.ink, fontSize = 15.sp, textAlign = TextAlign.End),
                        cursorBrush = SolidColor(p.ink),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterEnd) {
                                if (label.isEmpty()) Text("z. B. Arbeit", color = p.muted, fontSize = 15.sp)
                                inner()
                            }
                        }
                    )
                }
            }
            if (existing != null) {
                Text(
                    "Wecker löschen", color = p.sub, fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            AlarmScheduler.cancel(ctx, existing.id)
                            store.removeAlarm(existing.id)
                            nav.overlay = null
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickSound) {
        SoundDialog(sound, onPick = { sound = it }, onClose = { pickSound = false; AlarmSounds.stop() })
    }
}

@Composable
private fun DayCircle(text: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .widthIn(max = 34.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (on) p.fill else p.track)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (on) p.onFill else p.sub,
            fontSize = 12.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
            )
        )
    }
}

@Composable
private fun SmallTitle(title: String, action: String, onAction: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = p.sub, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(
            action, color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(4.dp)
        )
    }
}

@Composable
private fun MiniChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Text(
        text, color = if (selected) p.onFill else p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) p.fill else p.track)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    )
}

@Composable
private fun SoundDialog(current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val lists = remember { AlarmSounds.list(ctx) }
    var sel by remember { mutableStateOf(current) }
    DisposableEffect(Unit) { onDispose { AlarmSounds.stop() } }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = p.card,
        title = { Text("Weckton", color = p.ink) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { SoundRow(AlarmSounds.title(ctx, ""), sel.isEmpty()) { sel = ""; AlarmSounds.play(ctx, "") } }
                if (lists.first.isNotEmpty()) item { SoundHeader("Wecktöne") }
                items(lists.first) { s -> SoundRow(s.title, sel == s.uri) { sel = s.uri; AlarmSounds.play(ctx, s.uri) } }
                if (lists.second.isNotEmpty()) item { SoundHeader("Klingeltöne") }
                items(lists.second) { s -> SoundRow(s.title, sel == s.uri) { sel = s.uri; AlarmSounds.play(ctx, s.uri) } }
            }
        },
        confirmButton = { TextButton({ onPick(sel); onClose() }) { Text("Übernehmen", color = p.ink, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClose) { Text("Abbrechen", color = p.sub) } }
    )
}

@Composable
private fun SoundHeader(t: String) {
    val p = LocalPal.current
    Text(t, color = p.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun SoundRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) p.fill else p.track),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(p.onFill))
        }
        Text(title, color = p.ink, fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
    }
}

// ------------------------------------------------------------------ Weltzeit

@Composable
fun WorldPage() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    val now by rememberNow(1000)
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }
    val here = ZoneId.systemDefault()
    val hereNow = ZonedDateTime.now(here)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    buildAnnotatedString {
                        append("${two(now.hour)}:")
                        withStyle(SpanStyle(color = p.accent)) { append("${two(now.minute)}:${two(now.second)}") }
                    },
                    style = num(62.sp), color = p.ink
                )
                Text(
                    "${cityForZone(here.id)} · " + now.format(DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN)),
                    color = p.sub, fontSize = 14.sp
                )
            }
            Divider()
            Spacer(Modifier.height(2.dp))
            store.zones.forEach { z ->
                val zid = runCatching { ZoneId.of(z) }.getOrNull() ?: return@forEach
                val t = ZonedDateTime.now(zid)
                val diffMin = (t.offset.totalSeconds - hereNow.offset.totalSeconds) / 60
                val dayDiff = ChronoUnit.DAYS.between(hereNow.toLocalDate(), t.toLocalDate())
                val dayTxt = when {
                    dayDiff > 0 -> "Morgen"
                    dayDiff < 0 -> "Gestern"
                    else -> "Heute"
                }
                val off = if (diffMin == 0) "" else {
                    val h = diffMin / 60.0
                    val s = if (diffMin % 60 == 0) "${diffMin / 60}" else String.format(Locale.GERMANY, "%.1f", h)
                    ", ${if (diffMin > 0) "+" else ""}$s Std."
                }
                Card(
                    modifier = Modifier.pointerInput(z) { detectTapGestures(onLongPress = { removing = z }) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cityForZone(z), color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text(dayTxt + off, color = p.sub, fontSize = 14.sp)
                        }
                        Text("${two(t.hour)}:${two(t.minute)}", style = num(34.sp), color = p.ink)
                    }
                }
            }
            Text(
                "Lange drücken zum Entfernen", color = p.muted, fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.height(90.dp))
        }
        Fab(
            Ic.plus, { adding = true },
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp), desc = "Stadt hinzufügen"
        )
    }

    if (adding) {
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            confirmButton = { TextButton({ adding = false }) { Text("Schließen", color = p.ink) } },
            containerColor = p.card,
            title = { Text("Stadt hinzufügen", color = p.ink) },
            text = {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.track)
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = q, onValueChange = { q = it }, singleLine = true,
                            textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
                            cursorBrush = SolidColor(p.ink), modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner -> if (q.isEmpty()) Text("Suchen …", color = p.muted); inner() }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val list = CITIES.filter { it.first.contains(q, ignoreCase = true) && it.second !in store.zones }
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(list) { (name, zone) ->
                            Text(
                                name, color = p.ink, fontSize = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        store.zones.add(zone)
                                        store.saveZones()
                                        adding = false
                                    }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        )
    }
    val rm = removing
    if (rm != null) {
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = p.card,
            title = { Text("${cityForZone(rm)} entfernen?", color = p.ink) },
            confirmButton = {
                TextButton({
                    store.zones.remove(rm)
                    store.saveZones()
                    removing = null
                }) { Text("Entfernen", color = p.ink) }
            },
            dismissButton = { TextButton({ removing = null }) { Text("Abbrechen", color = p.sub) } }
        )
    }
}

// ------------------------------------------------------------------ Stoppuhr

object Stopwatch {
    var running by mutableStateOf(false)
    var startAt by mutableLongStateOf(0L)
    var acc by mutableLongStateOf(0L)
    val laps = mutableStateListOf<Long>()
    private var loaded = false

    fun elapsed(): Long = acc + if (running) SystemClock.elapsedRealtime() - startAt else 0L

    fun load(s: Store) {
        if (loaded) return
        loaded = true
        running = s.getBool("swRun", false)
        startAt = s.getLong("swStart")
        acc = s.getLong("swAcc")
        laps.clear()
        s.getString("swLaps")?.split(';')?.mapNotNull { it.toLongOrNull() }?.let { laps.addAll(it) }
        // Startzeit über die Uhrzeit neu berechnen (übersteht auch einen Neustart).
        val wall = s.getLong("swWall")
        if (running && wall > 0L) {
            val since = (System.currentTimeMillis() - wall).coerceAtLeast(0L)
            startAt = SystemClock.elapsedRealtime() - since
        } else if (running && startAt > SystemClock.elapsedRealtime()) {
            running = false; startAt = 0L
        }
    }

    private fun save(s: Store) {
        s.putBool("swRun", running)
        s.putLong("swStart", startAt)
        s.putLong("swWall", if (running) System.currentTimeMillis() - (SystemClock.elapsedRealtime() - startAt) else 0L)
        s.putLong("swAcc", acc)
        s.putString("swLaps", laps.joinToString(";"))
    }

    fun toggle(s: Store) {
        if (running) {
            acc += SystemClock.elapsedRealtime() - startAt
            running = false
        } else {
            startAt = SystemClock.elapsedRealtime()
            running = true
        }
        save(s)
    }

    fun lap(s: Store) {
        laps.add(elapsed())
        save(s)
    }

    fun reset(s: Store) {
        running = false
        acc = 0L
        startAt = 0L
        laps.clear()
        save(s)
    }
}

fun swText(ms: Long): Pair<String, String> {
    val cs = (ms / 10) % 100
    val totalS = ms / 1000
    val s = totalS % 60
    val m = (totalS / 60) % 60
    val h = totalS / 3600
    return if (h > 0) "$h:${two(m.toInt())}:" to "${two(s.toInt())},${two(cs.toInt())}"
    else "${two(m.toInt())}:" to "${two(s.toInt())},${two(cs.toInt())}"
}

@Composable
fun StopwatchPage() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    remember { Stopwatch.load(store); 0 }
    val p = LocalPal.current
    val t by rememberTick(if (Stopwatch.running) 31L else 500L)
    val el = Stopwatch.elapsed() + t * 0
    val (a, b) = swText(el)
    val laps = Stopwatch.laps
    val lapTimes = laps.mapIndexed { i, total -> total - (if (i == 0) 0L else laps[i - 1]) }
    val fastest = if (lapTimes.size >= 2) lapTimes.indices.minByOrNull { lapTimes[it] } else null
    val slowest = if (lapTimes.size >= 2) lapTimes.indices.maxByOrNull { lapTimes[it] } else null

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                buildAnnotatedString {
                    append(a)
                    withStyle(SpanStyle(color = p.accent)) { append(b) }
                },
                style = num(if (a.length > 4) 62.sp else 74.sp), color = p.ink
            )
            if (laps.isNotEmpty()) {
                val cur = el - laps.last()
                val (ca, cb) = swText(cur)
                Text("Runde ${laps.size + 1} · $ca$cb", color = p.sub, fontSize = 15.sp)
            }
        }
        if (laps.isNotEmpty()) {
            Card(modifier = Modifier.weight(1f, fill = false), padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Runde", color = p.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Rundenzeit", color = p.sub, fontSize = 12.sp, modifier = Modifier.weight(1.4f))
                    Text("Gesamt", color = p.sub, fontSize = 12.sp, modifier = Modifier.weight(1.4f))
                }
                LazyColumn {
                    items(laps.size) { k ->
                        val i = laps.size - 1 - k
                        val (la, lb) = swText(lapTimes[i])
                        val (ta, tb) = swText(laps[i])
                        Divider()
                        Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(two(i + 1), color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(
                                la + lb, style = num(16.sp, if (i == fastest) FontWeight.Bold else FontWeight.Normal),
                                color = when (i) {
                                    fastest -> p.accent
                                    slowest -> p.sub
                                    else -> p.ink
                                },
                                modifier = Modifier.weight(1.4f)
                            )
                            Text(ta + tb, style = num(16.sp, FontWeight.Normal), color = p.sub, modifier = Modifier.weight(1.4f))
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (Stopwatch.running) {
                CircleButton(Ic.flag, { Stopwatch.lap(store) }, bg = p.track, desc = "Runde")
            } else {
                CircleButton(Ic.reset, { Stopwatch.reset(store) }, desc = "Zurücksetzen")
            }
            CircleButton(
                if (Stopwatch.running) Ic.pause else Ic.play, { Stopwatch.toggle(store) },
                size = 80.dp, bg = p.fill, fg = p.onFill, iconSize = 30.dp, elevated = true,
                desc = if (Stopwatch.running) "Pause" else "Start"
            )
            CircleButton(Ic.reset, { Stopwatch.reset(store) }, desc = "Zurücksetzen")
        }
    }
}

// ------------------------------------------------------------------ Timer

@Composable
fun TimerPage(nav: Nav) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    remember { TimerStore.load(ctx); 0 }
    val active = TimerStore.running || TimerStore.paused
    if (active) {
        RunningTimer()
        return
    }
    var h by remember { mutableIntStateOf(0) }
    var m by remember { mutableIntStateOf(5) }
    var s by remember { mutableIntStateOf(0) }
    var addPreset by remember { mutableStateOf(false) }
    var removePreset by remember { mutableStateOf<TimerPreset?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NumberWheel(24, h, { h = it }, width = 90.dp, big = 52.sp, itemHeight = 64.dp)
                    Text("Std.", color = p.sub, fontSize = 12.sp)
                }
                Text(":", style = num(40.sp), color = p.ink, modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 18.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NumberWheel(60, m, { m = it }, width = 90.dp, big = 52.sp, itemHeight = 64.dp)
                    Text("Min.", color = p.sub, fontSize = 12.sp)
                }
                Text(":", style = num(40.sp), color = p.ink, modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 18.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NumberWheel(60, s, { s = it }, width = 90.dp, big = 52.sp, itemHeight = 64.dp)
                    Text("Sek.", color = p.sub, fontSize = 12.sp)
                }
            }

            SmallTitle("Meine Timer", "Hinzufügen") { addPreset = true }
            if (store.presets.isNotEmpty()) Card(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                store.presets.forEachIndexed { i, pr ->
                    if (i > 0) Divider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .pointerInput(pr) { detectTapGestures(onLongPress = { removePreset = pr }, onTap = { TimerStore.start(ctx, pr.sec * 1000L, pr.name) }) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pr.name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(hms(pr.sec.toLong()), style = num(16.sp, FontWeight.Normal), color = p.sub)
                    }
                }
            }

            SmallTitle("Intervall", "Neu") { nav.overlay = Overlay.EditWorkout(null) }
            if (store.workouts.isNotEmpty()) Card(padding = PaddingValues(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)) {
                store.workouts.forEachIndexed { i, w ->
                    if (i > 0) Divider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clickable { nav.overlay = Overlay.EditWorkout(w.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(w.name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            val ex = w.steps.count { !it.pause }
                            Text("$ex Übung${if (ex == 1) "" else "en"} · ${w.rounds} Runden", color = p.sub, fontSize = 12.sp)
                        }
                        Text(hms(w.totalSec.toLong(), false), style = num(16.sp, FontWeight.Normal), color = p.sub)
                        CircleButton(Ic.play, { nav.overlay = Overlay.RunWorkout(w.id) }, size = 38.dp, bg = p.card, iconSize = 16.dp, desc = "${w.name} starten")
                    }
                }
            }
            Spacer(Modifier.height(90.dp))
        }
        Fab(
            Ic.play, {
                val ms = (h * 3600L + m * 60L + s) * 1000L
                if (ms > 0) TimerStore.start(ctx, ms, "Timer")
            },
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp), desc = "Timer starten"
        )
    }

    if (addPreset) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addPreset = false },
            containerColor = p.card,
            title = { Text("Timer speichern", color = p.ink) },
            text = {
                Column {
                    Text("Dauer: ${hms(h * 3600L + m * 60L + s)}", color = p.sub)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.track)
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = name, onValueChange = { name = it.take(30) }, singleLine = true,
                            textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
                            cursorBrush = SolidColor(p.ink), modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner -> if (name.isEmpty()) Text("Name, z. B. Eier", color = p.muted); inner() }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton({
                    val sec = h * 3600 + m * 60 + s
                    if (sec > 0) {
                        store.presets.add(TimerPreset(name.ifBlank { "Timer" }, sec))
                        store.savePresets()
                    }
                    addPreset = false
                }) { Text("Speichern", color = p.ink) }
            },
            dismissButton = { TextButton({ addPreset = false }) { Text("Abbrechen", color = p.sub) } }
        )
    }
    val rp = removePreset
    if (rp != null) {
        AlertDialog(
            onDismissRequest = { removePreset = null },
            containerColor = p.card,
            title = { Text("„${rp.name}“ löschen?", color = p.ink) },
            confirmButton = {
                TextButton({
                    store.presets.remove(rp)
                    store.savePresets()
                    removePreset = null
                }) { Text("Löschen", color = p.ink) }
            },
            dismissButton = { TextButton({ removePreset = null }) { Text("Abbrechen", color = p.sub) } }
        )
    }
}

@Composable
private fun RunningTimer() {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val t by rememberTick(200)
    val left = TimerStore.remainingMs() + t * 0
    val total = TimerStore.totalMs.coerceAtLeast(1L)
    val frac = (left.toFloat() / total).coerceIn(0f, 1f)
    val secLeft = (left + 999) / 1000

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.6f))
        Box(Modifier.size(290.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 10.dp.toPx()
                val d = size.minDimension - sw
                drawArc(
                    color = p.line, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(sw / 2, sw / 2), size = Size(d, d), style = Stroke(sw)
                )
                drawArc(
                    color = p.fill, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                    topLeft = Offset(sw / 2, sw / 2), size = Size(d, d), style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(TimerStore.label, color = p.sub, fontSize = 15.sp)
                Text(hms(secLeft, secLeft >= 3600), style = num(if (secLeft >= 3600) 50.sp else 62.sp, FontWeight.SemiBold), color = p.ink)
                if (TimerStore.paused) Text("Pausiert", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleButton(Ic.close, { TimerStore.reset(ctx) }, desc = "Timer abbrechen")
            CircleButton(
                if (TimerStore.running) Ic.pause else Ic.play,
                { if (TimerStore.running) TimerStore.pause(ctx) else TimerStore.resume(ctx) },
                size = 80.dp, bg = p.fill, fg = p.onFill, iconSize = 30.dp, elevated = true,
                desc = if (TimerStore.running) "Pause" else "Weiter"
            )
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(p.track)
                    .clickable {
                        val l = TimerStore.remainingMs() + 60_000L
                        if (TimerStore.running) TimerStore.start(ctx, l, TimerStore.label)
                    },
                contentAlignment = Alignment.Center
            ) { Text("+1", color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
