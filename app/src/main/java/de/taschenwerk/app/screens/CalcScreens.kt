package de.taschenwerk.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.taschenwerk.app.Nav
import de.taschenwerk.app.data.CURRENCIES
import de.taschenwerk.app.data.Rates
import de.taschenwerk.app.data.Store
import de.taschenwerk.app.ui.Card
import de.taschenwerk.app.ui.CircleButton
import de.taschenwerk.app.ui.Divider
import de.taschenwerk.app.ui.Ic
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.NumberField
import de.taschenwerk.app.ui.Segmented
import de.taschenwerk.app.ui.TwIcon
import de.taschenwerk.app.ui.num
import de.taschenwerk.app.util.Expr
import de.taschenwerk.app.util.fmt
import de.taschenwerk.app.util.fmtSig
import de.taschenwerk.app.util.parseNum
import java.math.BigDecimal
import java.math.MathContext

@Composable
fun CalcTab(nav: Nav) {
    Column(Modifier.fillMaxSize()) {
        Segmented(
            listOf("Rechner", "Prozent", "Umrechner"), nav.rechnerSub, { nav.rechnerSub = it },
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
        )
        Box(Modifier.weight(1f)) {
            when (nav.rechnerSub) {
                0 -> CalcPage()
                1 -> PercentPage()
                else -> ConverterPage()
            }
        }
    }
}

// ------------------------------------------------------------------ Taschenrechner

object CalcState {
    var expr by mutableStateOf("")
    var sci by mutableStateOf(false)
    var deg by mutableStateOf(true)
    var justEval by mutableStateOf(false)
}

private val OPS = setOf('+', '−', '×', '÷', '^')

private fun plain(v: Double): String {
    if (v.isInfinite() || v.isNaN()) return ""
    val bd = BigDecimal(v).round(MathContext(12)).stripTrailingZeros()
    val s = if (bd.abs() >= BigDecimal("1E15") || (bd.signum() != 0 && bd.abs() < BigDecimal("1E-9"))) bd.toString() else bd.toPlainString()
    return s.replace('.', ',').replace("-", "−")
}

private fun press(k: String) {
    val e = CalcState.expr
    val last = e.lastOrNull()
    when (k) {
        "AC" -> { CalcState.expr = ""; CalcState.justEval = false }
        "⌫" -> {
            val fn = listOf("sin(", "cos(", "tan(", "log(", "ln(", "√(")
            val f = fn.firstOrNull { e.endsWith(it) }
            CalcState.expr = if (f != null) e.dropLast(f.length) else e.dropLast(1)
            CalcState.justEval = false
        }
        "=" -> {
            val r = Expr.tryEval(e.replace('−', '-'), CalcState.deg)
            if (r != null) {
                CalcState.expr = plain(r)
                CalcState.justEval = true
            }
        }
        "+", "−", "×", "÷", "^" -> {
            CalcState.justEval = false
            CalcState.expr = when {
                e.isEmpty() -> if (k == "−") "−" else ""
                last != null && last in OPS -> e.dropLast(1) + k
                else -> e + k
            }
        }
        "%" -> if (last != null && (last.isDigit() || last == ')')) CalcState.expr = e + "%"
        "," -> {
            if (CalcState.justEval) { CalcState.expr = "0,"; CalcState.justEval = false; return }
            val seg = e.takeLastWhile { it.isDigit() || it == ',' }
            if (!seg.contains(',')) CalcState.expr = if (seg.isEmpty()) e + "0," else e + ","
        }
        "x²" -> if (last != null && (last.isDigit() || last == ')' || last == 'π' || last == 'e')) CalcState.expr = e + "^2"
        "1/x" -> { CalcState.expr = if (CalcState.justEval || e.isEmpty()) "1÷(" + e + ")" else e + "1÷("; CalcState.justEval = false }
        "sin", "cos", "tan", "ln", "log" -> {
            CalcState.expr = (if (CalcState.justEval) "" else e) + "$k("
            CalcState.justEval = false
        }
        "√" -> { CalcState.expr = (if (CalcState.justEval) "" else e) + "√("; CalcState.justEval = false }
        "RAD", "DEG" -> CalcState.deg = !CalcState.deg
        else -> {
            // Ziffern, 00, Klammern, π, e, !
            if (CalcState.justEval && (k[0].isDigit() || k == "π" || k == "e" || k == "(")) {
                CalcState.expr = k
            } else {
                CalcState.expr = e + k
            }
            CalcState.justEval = false
        }
    }
}

@Composable
fun CalcPage() {
    val p = LocalPal.current
    val e = CalcState.expr
    val preview = if (e.isNotEmpty() && !CalcState.justEval) Expr.tryEval(e.replace('−', '-'), CalcState.deg) else null
    val big = when {
        preview != null -> fmt(preview, 10)
        e.isEmpty() -> "0"
        CalcState.justEval -> parseNum(e.replace('−', '-'))?.let { fmt(it, 10) } ?: e
        else -> e
    }
    val small = if (preview != null || CalcState.justEval) e else ""

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val sciRows = if (CalcState.sci) 3 else 0
        val sciH = 46.dp
        val gap = 10.dp
        val topH = 44.dp
        val minDisplay = 120.dp
        val avail = maxHeight - topH - minDisplay - (sciH + 8.dp) * sciRows - gap * 4
        val keyH: Dp = (avail / 5).coerceIn(48.dp, 74.dp)

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.height(topH), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (CalcState.deg) "DEG" else "RAD",
                    color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (CalcState.sci) p.fill else p.track)
                        .clickable { CalcState.sci = !CalcState.sci }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "f(x)", color = if (CalcState.sci) p.onFill else p.ink,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                if (small.isNotEmpty()) {
                    Text(small, color = p.sub, fontSize = 22.sp, maxLines = 2, textAlign = TextAlign.End)
                }
                val size = when {
                    big.length > 16 -> 34.sp
                    big.length > 11 -> 44.sp
                    big.length > 8 -> 54.sp
                    else -> 64.sp
                }
                Text(big, style = num(size), color = p.ink, maxLines = 2, textAlign = TextAlign.End)
            }
            if (CalcState.sci) {
                val rows = listOf(
                    listOf("sin", "cos", "tan", "ln", "log"),
                    listOf("√", "^", "(", ")", "!"),
                    listOf("π", "e", "x²", "1/x", if (CalcState.deg) "RAD" else "DEG")
                )
                rows.forEach { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        r.forEach { k ->
                            Key(k, Modifier.weight(1f), sciH, p.card, p.ink, 17.sp, FontWeight.Medium) { press(k) }
                        }
                    }
                }
            }
            val rows = listOf(
                listOf("AC", "%", "⌫", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf("00", "0", ",", "=")
            )
            rows.forEachIndexed { ri, r ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.padding(top = if (ri == 0) 0.dp else gap)) {
                    r.forEach { k ->
                        val isOp = k in listOf("AC", "%", "⌫", "÷", "×", "−", "+")
                        val isEq = k == "="
                        val bg = when {
                            isEq -> p.fill
                            isOp -> p.track
                            else -> p.card
                        }
                        val fg = if (isEq) p.onFill else p.ink
                        Key(k, Modifier.weight(1f), keyH, bg, fg, if (isOp || isEq) 26.sp else 30.sp, FontWeight.SemiBold) { press(k) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, h: Dp, bg: Color, fg: Color, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, onClick: () -> Unit) {
    val p = LocalPal.current
    var m = modifier
        .height(h)
        .clip(RoundedCornerShape(h / 2))
        .background(bg)
    if (bg == p.card && p.cardRing != Color.Transparent) m = m.border(1.dp, p.cardRing, RoundedCornerShape(h / 2))
    Box(m.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (label == "⌫") TwIcon(Ic.backspace, tint = fg, size = 28.dp)
        else Text(label, color = fg, fontSize = size, fontWeight = weight)
    }
}

// ------------------------------------------------------------------ Prozent

@Composable
fun PercentPage() {
    val p = LocalPal.current
    var a1 by remember { mutableStateOf("15") }
    var b1 by remember { mutableStateOf("850") }
    var a2 by remember { mutableStateOf("45") }
    var b2 by remember { mutableStateOf("300") }
    var a3 by remember { mutableStateOf("80") }
    var b3 by remember { mutableStateOf("90") }
    var a4 by remember { mutableStateOf("850") }
    var b4 by remember { mutableStateOf("15") }
    var plus4 by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text("Prozent", color = p.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Feld antippen, Zahl eintippen – fertig.", color = p.sub, fontSize = 14.sp)
        }
        PercentCard("Wie viel sind X % von Y?") {
            NumberField(a1, { a1 = it }, Modifier.width(66.dp))
            Lbl("% von")
            NumberField(b1, { b1 = it }, Modifier.width(84.dp))
            val r = parseNum(a1)?.let { x -> parseNum(b1)?.let { y -> x * y / 100.0 } }
            Res(r?.let { fmt(it, 2) })
        }
        PercentCard("X ist wie viel % von Y?") {
            NumberField(a2, { a2 = it }, Modifier.width(66.dp))
            Lbl("von")
            NumberField(b2, { b2 = it }, Modifier.width(84.dp))
            val r = parseNum(a2)?.let { x -> parseNum(b2)?.takeIf { it != 0.0 }?.let { y -> x / y * 100.0 } }
            Res(r?.let { fmt(it, 2) + " %" })
        }
        PercentCard("Veränderung von X auf Y") {
            NumberField(a3, { a3 = it }, Modifier.width(66.dp))
            TwIcon(Ic.chevRight, tint = p.sub, size = 18.dp)
            NumberField(b3, { b3 = it }, Modifier.width(84.dp))
            val r = parseNum(a3)?.takeIf { it != 0.0 }?.let { x -> parseNum(b3)?.let { y -> (y - x) / x * 100.0 } }
            Res(r?.let { (if (it > 0) "+" else "") + fmt(it, 2) + " %" })
        }
        PercentCard("Rabatt oder Aufschlag") {
            NumberField(a4, { a4 = it }, Modifier.width(76.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.track)
                    .padding(3.dp)
            ) {
                listOf(false to "−", true to "+").forEach { (v, s) ->
                    Box(
                        Modifier
                            .size(width = 28.dp, height = 38.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (plus4 == v) p.card else Color.Transparent)
                            .clickable { plus4 = v },
                        contentAlignment = Alignment.Center
                    ) { Text(s, color = if (plus4 == v) p.ink else p.sub, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
            }
            NumberField(b4, { b4 = it }, Modifier.width(56.dp))
            Lbl("%")
            val r = parseNum(a4)?.let { x -> parseNum(b4)?.let { y -> x * (1 + (if (plus4) y else -y) / 100.0) } }
            Res(r?.let { fmt(it, 2) })
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun PercentCard(title: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val p = LocalPal.current
    Card {
        Text(title, color = p.sub, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun Lbl(s: String) {
    Text(s, color = LocalPal.current.sub, fontSize = 15.sp)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Res(s: String?) {
    Text(
        s ?: "–", style = num(24.sp), color = LocalPal.current.accent,
        textAlign = TextAlign.End, maxLines = 1,
        modifier = Modifier.weight(1f)
    )
}

// ------------------------------------------------------------------ Umrechner

data class UnitDef(val name: String, val factor: Double)

data class Category(val name: String, val icon: ImageVector, val units: List<UnitDef>, val kind: Int = 0)

private const val K_LINEAR = 0
private const val K_TEMP = 1
private const val K_BASE = 2
private const val K_CUR = 3

val CATEGORIES: List<Category> by lazy {
    listOf(
        Category("Währung", Ic.coin, CURRENCIES.map { UnitDef(it.first, 1.0) }, K_CUR),
        Category(
            "Länge", Ic.ruler, listOf(
                UnitDef("Millimeter", 0.001), UnitDef("Zentimeter", 0.01), UnitDef("Meter", 1.0),
                UnitDef("Kilometer", 1000.0), UnitDef("Zoll", 0.0254), UnitDef("Fuß", 0.3048),
                UnitDef("Yard", 0.9144), UnitDef("Meile", 1609.344), UnitDef("Seemeile", 1852.0)
            )
        ),
        Category(
            "Fläche", Ic.area, listOf(
                UnitDef("Quadratzentimeter", 1e-4), UnitDef("Quadratmeter", 1.0), UnitDef("Ar", 100.0),
                UnitDef("Hektar", 1e4), UnitDef("Quadratkilometer", 1e6), UnitDef("Quadratfuß", 0.09290304),
                UnitDef("Acre", 4046.8564224), UnitDef("Quadratmeile", 2589988.110336)
            )
        ),
        Category(
            "Volumen", Ic.cube, listOf(
                UnitDef("Milliliter", 0.001), UnitDef("Liter", 1.0), UnitDef("Kubikmeter", 1000.0),
                UnitDef("Teelöffel", 0.00492892), UnitDef("Esslöffel", 0.0147868), UnitDef("Tasse (US)", 0.236588),
                UnitDef("Flüssigunze (US)", 0.0295735), UnitDef("Gallone (US)", 3.785411784),
                UnitDef("Gallone (UK)", 4.54609), UnitDef("Kubikfuß", 28.3168466)
            )
        ),
        Category(
            "Gewicht", Ic.weight, listOf(
                UnitDef("Milligramm", 1e-6), UnitDef("Gramm", 0.001), UnitDef("Kilogramm", 1.0),
                UnitDef("Tonne", 1000.0), UnitDef("Unze", 0.028349523125), UnitDef("Pfund", 0.45359237),
                UnitDef("Stone", 6.35029318)
            )
        ),
        Category("Temperatur", Ic.thermo, listOf(UnitDef("Celsius", 1.0), UnitDef("Fahrenheit", 1.0), UnitDef("Kelvin", 1.0)), K_TEMP),
        Category(
            "Tempo", Ic.speed, listOf(
                UnitDef("Meter/Sekunde", 1.0), UnitDef("Kilometer/Stunde", 1 / 3.6), UnitDef("Meilen/Stunde", 0.44704),
                UnitDef("Knoten", 0.514444444), UnitDef("Fuß/Sekunde", 0.3048)
            )
        ),
        Category(
            "Druck", Ic.pressure, listOf(
                UnitDef("Pascal", 1.0), UnitDef("Hektopascal", 100.0), UnitDef("Kilopascal", 1000.0),
                UnitDef("Bar", 1e5), UnitDef("Millibar", 100.0), UnitDef("Atmosphäre", 101325.0),
                UnitDef("PSI", 6894.757293168), UnitDef("mmHg", 133.322387415)
            )
        ),
        Category(
            "Leistung", Ic.bolt, listOf(
                UnitDef("Watt", 1.0), UnitDef("Kilowatt", 1000.0), UnitDef("PS", 735.49875),
                UnitDef("hp (mech.)", 745.699872), UnitDef("BTU/Stunde", 0.29307107)
            )
        ),
        Category(
            "Zahlensystem", Ic.binary,
            listOf(UnitDef("Binär", 2.0), UnitDef("Oktal", 8.0), UnitDef("Dezimal", 10.0), UnitDef("Hexadezimal", 16.0)), K_BASE
        )
    )
}

private fun toCelsius(v: Double, u: Int) = when (u) {
    0 -> v
    1 -> (v - 32) * 5 / 9
    else -> v - 273.15
}

private fun fromCelsius(c: Double, u: Int) = when (u) {
    0 -> c
    1 -> c * 9 / 5 + 32
    else -> c + 273.15
}

object ConvState {
    var cat by mutableIntStateOf(1)
    var from by mutableIntStateOf(2)
    var to by mutableIntStateOf(5)
    var input by mutableStateOf("1")
}

@Composable
fun ConverterPage() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val p = LocalPal.current
    var sheet by remember { mutableStateOf(false) }
    val cat = CATEGORIES[ConvState.cat]
    val units = cat.units
    val fromI = ConvState.from.coerceIn(0, units.size - 1)
    val toI = ConvState.to.coerceIn(0, units.size - 1)
    if (cat.kind == K_CUR) LaunchedEffect(Unit) { Rates.refresh(store) }

    fun convert(text: String, a: Int, b: Int): String? = when (cat.kind) {
        K_BASE -> runCatching {
            val n = java.lang.Long.parseLong(text.trim().uppercase(), units[a].factor.toInt())
            java.lang.Long.toString(n, units[b].factor.toInt()).uppercase()
        }.getOrNull()
        K_TEMP -> parseNum(text)?.let { fmtSig(fromCelsius(toCelsius(it, a), b)) }
        K_CUR -> parseNum(text)?.let { v -> Rates.convert(v, units[a].name, units[b].name)?.let { fmt(it, 2) } }
        else -> parseNum(text)?.let { fmtSig(it * units[a].factor / units[b].factor) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(onClick = { sheet = true }, padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), radius = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(p.track),
                        contentAlignment = Alignment.Center
                    ) { TwIcon(cat.icon, size = 20.dp) }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text("Kategorie", color = p.sub, fontSize = 12.sp)
                        Text(cat.name, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TwIcon(Ic.chevDown, size = 20.dp)
                }
            }

            Card(padding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                UnitRow(units, fromI, { ConvState.from = it }, ConvState.input, { ConvState.input = it }, cat.kind == K_BASE, false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Divider() }
                    CircleButton(
                        Ic.swap,
                        {
                            val out = convert(ConvState.input, fromI, toI)
                            val f = ConvState.from
                            ConvState.from = ConvState.to
                            ConvState.to = f
                            if (out != null) ConvState.input = out
                        },
                        size = 36.dp, iconSize = 18.dp, fg = p.accent,
                        modifier = Modifier.padding(horizontal = 10.dp), desc = "Einheiten tauschen"
                    )
                    Box(Modifier.weight(1f)) { Divider() }
                }
                UnitRow(units, toI, { ConvState.to = it }, convert(ConvState.input, fromI, toI) ?: "–", {}, cat.kind == K_BASE, true)
            }

            if (cat.kind == K_CUR && Rates.error != null) {
                Text(Rates.error ?: "", color = p.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }

            Card(padding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                Text("${ConvState.input} ${units[fromI].name} sind", color = p.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                units.forEachIndexed { i, u ->
                    if (i != fromI) {
                        Divider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable { ConvState.to = i },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(u.name, color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text(convert(ConvState.input, fromI, i) ?: "–", style = num(17.sp, FontWeight.SemiBold), color = p.ink)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (sheet) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x52161618))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sheet = false }
            ) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(p.card)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(16.dp)
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(p.line)
                    )
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Kategorie", color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        CircleButton(Ic.close, { sheet = false }, size = 40.dp, bg = p.card, desc = "Schließen")
                    }
                    CATEGORIES.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                            row.forEach { c ->
                                val idx = CATEGORIES.indexOf(c)
                                val sel = idx == ConvState.cat
                                var m = Modifier
                                    .weight(1f)
                                    .height(88.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (sel) p.card else p.track)
                                if (sel) m = m.border(2.dp, p.fill, RoundedCornerShape(18.dp))
                                Column(
                                    m.clickable {
                                        ConvState.cat = idx
                                        ConvState.from = if (c.kind == K_CUR) CURRENCIES.indexOfFirst { it.first == store.mainCur }.coerceAtLeast(0) else 0
                                        ConvState.to = if (c.kind == K_CUR) CURRENCIES.indexOfFirst { it.first == store.secondCur }.coerceAtLeast(1)
                                        else minOf(1, c.units.size - 1)
                                        ConvState.input = if (c.kind == K_BASE) "255" else "1"
                                        sheet = false
                                    },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    TwIcon(c.icon, tint = if (sel) p.accent else p.ink, size = 26.dp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        c.name, color = if (sel) p.accent else p.ink, fontSize = 13.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1
                                    )
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun UnitRow(
    units: List<UnitDef>,
    selected: Int,
    onPick: (Int) -> Unit,
    value: String,
    onValue: (String) -> Unit,
    textInput: Boolean,
    readOnly: Boolean
) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp), verticalAlignment = Alignment.CenterVertically
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
                Text(units[selected].name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                TwIcon(Ic.chevDown, size = 16.dp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                units.forEachIndexed { i, u ->
                    DropdownMenuItem(text = { Text(u.name) }, onClick = { onPick(i); open = false })
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        if (readOnly) {
            Text(
                value, style = num(26.sp), color = p.accent, textAlign = TextAlign.End, maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        } else if (textInput) {
            de.taschenwerk.app.ui.SelectAllField(
                value = value,
                onValueChange = onValue,
                filter = { it.filter { c -> c.isLetterOrDigit() }.take(40) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                textStyle = num(26.sp).copy(color = p.ink, textAlign = TextAlign.End),
                modifier = Modifier.weight(1f)
            )
        } else {
            NumberField(value, onValue, Modifier.weight(1f), num(26.sp), TextAlign.End, boxed = false)
        }
    }
}

