package de.taschenwerk.app.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.taschenwerk.app.data.Store
import de.taschenwerk.app.ui.Card
import de.taschenwerk.app.ui.Divider
import de.taschenwerk.app.ui.Ic
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.TwIcon
import de.taschenwerk.app.ui.num
import de.taschenwerk.app.util.Expr
import de.taschenwerk.app.util.fmt
import de.taschenwerk.app.util.parseNum
import org.json.JSONArray
import org.json.JSONObject

// ------------------------------------------------------------------ Gemeinsam

/** Kleiner runder Knopf zum Auf- und Zuklappen einer Kachel. */
@Composable
fun FoldButton(open: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(p.track)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        TwIcon(if (open) Ic.chevUp else Ic.chevDown, size = 16.dp, desc = if (open) "Zuklappen" else "Aufklappen")
    }
}

@Composable
private fun SmallChip(text: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Text(
        text, color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(p.track)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// ------------------------------------------------------------------ Mini-Rechner

private const val OPS = "÷×−+"

private fun calcEval(e: String): Double? = if (e.isBlank()) null
else runCatching { Expr(e.replace(',', '.').replace('−', '-'), true).eval() }.getOrNull()
    ?.takeIf { !it.isNaN() && !it.isInfinite() }

/** Eingabe von der Handy-Tastatur säubern: nur Ziffern, Komma und Rechenzeichen. */
private fun cleanCalc(s: String): String {
    val sb = StringBuilder()
    for (c in s) {
        val m: Char? = when (c) {
            in '0'..'9' -> c
            ',', '.' -> ','
            '-', '−' -> '−'
            '+' -> '+'
            '*', '×', 'x', 'X' -> '×'
            '/', '÷', ':' -> '÷'
            '%' -> '%'
            else -> null
        }
        if (m != null) sb.append(m)
    }
    return sb.toString().take(40)
}

/**
 * Rechner-Kachel. Zugeklappt: Feld (Zahlen kommen von der Handy-Tastatur) und darunter nur ÷ × − + =.
 * Aufgeklappt: zusätzlich eine flache Zahlenreihe 1–0 und eine Reihe , ÷ × − + ⌫ = (⌫ lang drücken löscht alles).
 * Der Klapp-Zustand wird gemerkt.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun CalcTile() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    val focusManager = LocalFocusManager.current
    var open by remember { mutableStateOf(store.getBool("calcOpen", false)) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var lastExpr by remember { mutableStateOf("") }
    var justEval by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    fun set(t: String) {
        field = TextFieldValue(t, TextRange(t.length))
    }

    val expr = field.text
    val hasOp = expr.drop(1).any { it in OPS || it == '%' }
    val preview = if (!justEval && hasOp) calcEval(expr) else null
    val current = if (justEval) calcEval(expr) else preview ?: calcEval(expr)

    // Summe aus der Notiz übernehmen
    val incoming = CalcBridge.toCalc
    LaunchedEffect(incoming) {
        if (incoming != null) {
            set(incoming)
            lastExpr = ""
            justEval = false
            CalcBridge.toCalc = null
        }
    }

    fun press(k: String) {
        val e = field.text
        val last = e.lastOrNull()
        when (k) {
            "AC" -> { set(""); lastExpr = ""; justEval = false }
            "⌫" -> { set(e.dropLast(1)); justEval = false }
            "=" -> {
                val op = e.drop(1).any { it in OPS || it == '%' }
                calcEval(e)?.let { r ->
                    if (op) {
                        lastExpr = e
                        set(fmt(r, 8).replace(".", ""))
                        justEval = true
                    }
                }
            }
            "÷", "×", "−", "+" -> {
                justEval = false
                set(
                    when {
                        e.isEmpty() -> if (k == "−") "−" else ""
                        last != null && last in OPS -> e.dropLast(1) + k
                        else -> e + k
                    }
                )
            }
            "," -> {
                if (justEval) { set("0,"); justEval = false; return }
                val seg = e.takeLastWhile { it.isDigit() || it == ',' }
                if (!seg.contains(',')) set(e + if (seg.isEmpty()) "0," else ",")
            }
            else -> {
                set(if (justEval) k else (e + k).take(40))
                justEval = false
            }
        }
    }

    fun onType(v: TextFieldValue) {
        val clean = cleanCalc(v.text)
        if (clean == field.text) {
            if (v.text == clean) field = v
            return
        }
        var t = clean
        if (justEval) {
            // Nach „=“: eine neue Zahl beginnt eine neue Rechnung, ein Rechenzeichen rechnet mit dem Ergebnis weiter
            val old = field.text
            if (t.length > old.length && t.startsWith(old)) {
                val add = t.substring(old.length)
                if (add[0].isDigit()) t = add
                else if (add[0] == ',') t = "0$add"
            }
            justEval = false
        }
        field = if (t == v.text) v else TextFieldValue(t, TextRange(t.length))
    }

    // Beim Tippen ins Feld die ganze Kachel über die Tastatur schieben
    val bring = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused) {
            kotlinx.coroutines.delay(60)
            bring.bringIntoView()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bring)
            .animateContentSize(),
        padding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Rechner", color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp)
            )
            if (current != null) {
                SmallChip("→ ${store.mainCur}") { CalcBridge.push = fmt(current, 2) }
                Spacer(Modifier.width(8.dp))
            }
            FoldButton(open) {
                open = !open
                store.putBool("calcOpen", open)
                focusManager.clearFocus()
            }
        }
        Spacer(Modifier.height(8.dp))

        // Eingabefeld
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(shape)
                .background(p.bg)
                .border(if (focused) 2.dp else 1.dp, if (focused) p.accent else p.line, shape)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End
        ) {
            BasicTextField(
                value = field,
                onValueChange = { onType(it) },
                singleLine = true,
                readOnly = open,
                textStyle = num(28.sp, FontWeight.SemiBold).copy(color = p.ink, textAlign = TextAlign.End),
                cursorBrush = SolidColor(p.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { press("=") }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        if (field.text.isEmpty()) Text("Zahl eingeben", color = p.muted, fontSize = 17.sp)
                        inner()
                    }
                }
            )
            val small = when {
                justEval && lastExpr.isNotEmpty() -> "$lastExpr ="
                preview != null -> fmt(preview, 8)
                else -> ""
            }
            Text(small, color = p.sub, style = num(15.sp, FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))

        if (open) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                "1234567890".forEach { c ->
                    CalcKey(c.toString(), Modifier.weight(1f), 40.dp) { press(c.toString()) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(",", "÷", "×", "−", "+", "⌫", "=").forEach { k ->
                    CalcKey(k, Modifier.weight(1f), 42.dp, onLong = if (k == "⌫") ({ press("AC") }) else null) { press(k) }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("÷", "×", "−", "+", "=").forEach { k ->
                    CalcKey(k, Modifier.weight(1f), 42.dp) { press(k) }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CalcKey(k: String, modifier: Modifier, h: Dp, onLong: (() -> Unit)? = null, onTap: () -> Unit) {
    val p = LocalPal.current
    val digit = k[0].isDigit() || k == ","
    val bg = when {
        k == "=" -> p.fill
        digit -> p.card
        else -> p.track
    }
    val shape = RoundedCornerShape(50)
    var m = modifier
        .height(h)
        .clip(shape)
        .background(bg)
    if (digit && p.cardRing != Color.Transparent) m = m.border(1.dp, p.cardRing, shape)
    Box(m.combinedClickable(onLongClick = onLong, onClick = onTap), contentAlignment = Alignment.Center) {
        if (k == "⌫") TwIcon(Ic.backspace, size = 19.dp, desc = "Löschen")
        else Text(
            k, color = if (k == "=") p.onFill else p.ink,
            fontSize = if (digit) 18.sp else 19.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

// ------------------------------------------------------------------ Notiz

data class NoteItem(val id: Long, val text: String, val done: Boolean)

object Notes {
    var items by mutableStateOf<List<NoteItem>>(emptyList())
    private var loaded = false

    fun load(store: Store) {
        if (loaded) return
        loaded = true
        store.getString("notes")?.let { raw ->
            runCatching {
                val a = JSONArray(raw)
                items = (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    NoteItem(o.optLong("id", i.toLong()), o.optString("t"), o.optBoolean("d"))
                }
            }
        }
    }

    fun save(store: Store) {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("t", it.text).put("d", it.done)) }
        store.putString("notes", a.toString())
    }
}

/** Betrag am Ende eines Eintrags („Milch 25“, „Brot 18,50“, „Kaffee 1.200 $“). */
private val AMOUNT = Regex("""(?<![\d.,])(\d{1,3}(?:\.\d{3})+(?:,\d+)?|\d+(?:[.,]\d+)?)\s*[€$]?\s*$""")

/** Teilt einen Eintrag in Text und Betrag. */
fun splitAmount(t: String): Pair<String, Double?> {
    val s = t.trim()
    val m = AMOUNT.find(s) ?: return s to null
    val v = parseNum(m.groupValues[1]) ?: return s to null
    return s.substring(0, m.range.first).trim().trimEnd(':', '-', '–', '=').trim() to v
}

/**
 * Notiz-Kachel: kleine Häkchen-Liste (z. B. Einkaufszettel). Zugeklappt ein schmaler Streifen mit den
 * offenen Einträgen und der Summe, aufgeklappt die Liste. Zahlen am Ende eines Eintrags werden als Betrag
 * erkannt und unten zusammengezählt; „→ Rechner“ gibt die Summe an die Rechner-Kachel.
 */
@Composable
fun NotesTile() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    remember { Notes.load(store); 0 }
    var open by remember { mutableStateOf(store.getBool("notesOpen", false)) }
    val items = Notes.items
    fun update(list: List<NoteItem>) {
        Notes.items = list
        Notes.save(store)
    }
    fun toggleOpen() {
        open = !open
        store.putBool("notesOpen", open)
    }

    val parsed = items.map { it to splitAmount(it.text) }
    val amounts = parsed.mapNotNull { it.second.second }
    val sum = if (amounts.isEmpty()) null else amounts.sum()
    val openCount = items.count { !it.done }
    val calcOn = HomeCfg.tiles.any { it.first == "calc" && it.second }

    if (!open) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { toggleOpen() },
            padding = PaddingValues(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Row(Modifier.heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Notiz", color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                val preview = parsed.filter { !it.first.done }
                    .map { it.second.first.ifEmpty { it.first.text.trim() } }
                    .joinToString(" · ")
                Text(
                    preview.ifEmpty { if (items.isEmpty()) "Antippen zum Schreiben" else "Alles erledigt" },
                    color = if (preview.isEmpty()) p.muted else p.ink, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (openCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("$openCount offen", color = p.sub, fontSize = 12.sp, maxLines = 1)
                }
                if (sum != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(fmt(sum, 2), color = p.ink, style = num(15.sp, FontWeight.SemiBold), maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                FoldButton(false) { toggleOpen() }
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            padding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)
        ) {
            Row(Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Notiz", color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp)
                )
                if (items.any { it.done }) Text(
                    "Erledigte löschen", color = p.sub, fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { update(items.filter { !it.done }) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
                Spacer(Modifier.width(4.dp))
                FoldButton(true) { toggleOpen() }
            }
            // Offene zuerst, Abgehaktes rutscht nach unten
            val sorted = parsed.filter { !it.first.done } + parsed.filter { it.first.done }
            sorted.forEach { (item, split) ->
                key(item.id) {
                    NoteRow(
                        item, split.first, split.second,
                        onToggle = { update(Notes.items.map { if (it.id == item.id) it.copy(done = !it.done) else it }) },
                        onEdit = { t ->
                            update(
                                if (t.isBlank()) Notes.items.filter { it.id != item.id }
                                else Notes.items.map { if (it.id == item.id) it.copy(text = t.trim()) else it }
                            )
                        }
                    )
                }
            }
            NewNoteRow { t ->
                val id = maxOf(System.currentTimeMillis(), (Notes.items.maxOfOrNull { it.id } ?: 0L) + 1)
                update(Notes.items + NoteItem(id, t, false))
            }
            if (sum != null) {
                Spacer(Modifier.height(4.dp))
                Divider()
                Row(Modifier.padding(start = 6.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Summe", color = p.sub, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(fmt(sum, 2), color = p.ink, style = num(20.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
                    if (calcOn) SmallChip("→ Rechner") { CalcBridge.toCalc = fmt(sum, 2).replace(".", "") }
                }
            }
        }
    }
}

@Composable
private fun CheckMark(done: Boolean, onToggle: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(shape)
                .then(if (done) Modifier.background(p.fill) else Modifier.border(2.dp, p.muted, shape)),
            contentAlignment = Alignment.Center
        ) {
            if (done) TwIcon(Ic.check, tint = p.onFill, size = 15.dp)
        }
    }
}

@Composable
private fun NoteRow(item: NoteItem, label: String, amount: Double?, onToggle: () -> Unit, onEdit: (String) -> Unit) {
    val p = LocalPal.current
    val focusManager = LocalFocusManager.current
    var editing by remember { mutableStateOf(false) }
    val color = if (item.done) p.muted else p.ink
    val deco = if (item.done) TextDecoration.LineThrough else null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckMark(item.done, onToggle)
        Spacer(Modifier.width(8.dp))
        if (editing) {
            var tf by remember { mutableStateOf(TextFieldValue(item.text, TextRange(item.text.length))) }
            val fr = remember { FocusRequester() }
            var had by remember { mutableStateOf(false) }
            BasicTextField(
                value = tf,
                onValueChange = { tf = it },
                singleLine = true,
                textStyle = TextStyle(color = p.ink, fontSize = 15.sp),
                cursorBrush = SolidColor(p.ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
                    .focusRequester(fr)
                    .onFocusChanged {
                        if (it.isFocused) had = true
                        else if (had) {
                            editing = false
                            onEdit(tf.text)
                        }
                    }
            )
            LaunchedEffect(Unit) { fr.requestFocus() }
        } else {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clickable { editing = true }
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label, color = color, fontSize = 15.sp, textDecoration = deco,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (amount != null) Text(
                    fmt(amount, 2), color = color, textDecoration = deco,
                    style = num(15.sp, if (item.done) FontWeight.Medium else FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
private fun NewNoteRow(onAdd: (String) -> Unit) {
    val p = LocalPal.current
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf("") }
    var had by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { TwIcon(Ic.plus, tint = p.muted, size = 20.dp) }
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it.replace("\n", "") },
            singleLine = true,
            textStyle = TextStyle(color = p.ink, fontSize = 15.sp),
            cursorBrush = SolidColor(p.ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            // „Fertig“ legt den Eintrag an und lässt die Tastatur offen für den nächsten
            keyboardActions = KeyboardActions(onDone = {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                } else focusManager.clearFocus()
            }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged {
                    if (it.isFocused) had = true
                    else if (had && text.isNotBlank()) {
                        onAdd(text.trim())
                        text = ""
                    }
                },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("Neuer Eintrag", color = p.muted, fontSize = 15.sp)
                    inner()
                }
            }
        )
    }
}
