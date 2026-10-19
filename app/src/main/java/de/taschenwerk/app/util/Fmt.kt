package de.taschenwerk.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

private val sym = DecimalFormatSymbols(Locale.GERMANY)

/** Deutsche Zahl mit Tausenderpunkt und bis zu [maxDec] Nachkommastellen. */
fun fmt(v: Double, maxDec: Int = 2, minDec: Int = 0): String {
    if (v.isNaN()) return "–"
    if (v.isInfinite()) return if (v > 0) "∞" else "−∞"
    val a = abs(v)
    if (a != 0.0 && (a >= 1e15 || a < 1e-6)) {
        val f = DecimalFormat("0.######E0", sym)
        return f.format(v)
    }
    val pattern = StringBuilder("#,##0")
    if (maxDec > 0) {
        pattern.append('.')
        repeat(minDec) { pattern.append('0') }
        repeat((maxDec - minDec).coerceAtLeast(0)) { pattern.append('#') }
    }
    val f = DecimalFormat(pattern.toString(), sym)
    f.roundingMode = RoundingMode.HALF_UP
    val s = f.format(v)
    return if (s == "-0") "0" else s
}

/** Signifikante Darstellung für Umrechnungen. */
fun fmtSig(v: Double): String {
    val a = abs(v)
    val dec = when {
        a == 0.0 -> 0
        a >= 1000 -> 2
        a >= 1 -> 4
        a >= 0.01 -> 6
        else -> 8
    }
    return fmt(v, dec)
}

/** Liest "1.234,5" oder "1234.5" als Zahl. */
fun parseNum(s: String): Double? {
    val t = s.trim()
    if (t.isEmpty() || t == "-") return null
    val norm = if (t.contains(',')) t.replace(".", "").replace(',', '.') else {
        // Nur Punkte: wenn mehrere oder genau 3 Ziffern danach -> Tausendertrenner
        val parts = t.split('.')
        val intPart = parts[0].trimStart('-')
        if (parts.size > 2 || (parts.size == 2 && parts[1].length == 3 && intPart.length in 1..3 && intPart != "0")) t.replace(".", "") else t
    }
    return norm.toDoubleOrNull()
}

fun two(n: Int): String = if (n < 10) "0$n" else n.toString()

fun hms(totalSec: Long, alwaysHours: Boolean = true): String {
    val s = totalSec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (alwaysHours || h > 0) "${two(h.toInt())}:${two(m.toInt())}:${two(sec.toInt())}" else "${two(m.toInt())}:${two(sec.toInt())}"
}

fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()
