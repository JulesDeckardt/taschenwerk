package de.taschenwerk.app.util

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Rechenausdruck-Parser für den Taschenrechner.
 * Unterstützt + − × ÷ ^ %, Klammern, √, sin cos tan ln log, π, e und !.
 * "a + b%" rechnet wie ein Taschenrechner: a + a·b/100.
 */
class Expr(src: String, private val degrees: Boolean) {
    private val s = src.replace(" ", "")
    private var i = 0

    class ParseError : Exception()

    private data class V(val v: Double, val pct: Boolean = false)

    fun eval(): Double {
        if (s.isEmpty()) throw ParseError()
        val r = expr()
        if (i < s.length) throw ParseError()
        return r
    }

    private fun peek(): Char? = if (i < s.length) s[i] else null

    private fun expr(): Double {
        val first = term()
        var left = if (first.pct) first.v / 100.0 else first.v
        while (true) {
            val c = peek() ?: break
            if (c != '+' && c != '−' && c != '-') break
            i++
            val r = term()
            val rv = if (r.pct) left * r.v / 100.0 else r.v
            left = if (c == '+') left + rv else left - rv
        }
        return left
    }

    private fun term(): V {
        var left = power()
        while (true) {
            val c = peek() ?: break
            if (c == '×' || c == '*' || c == '÷' || c == '/') {
                i++
                val r = power()
                val rv = if (r.pct) r.v / 100.0 else r.v
                val lv = if (left.pct) left.v / 100.0 else left.v
                left = V(if (c == '×' || c == '*') lv * rv else lv / rv)
            } else if (c == '(' || c == 'π' || c == 'e' || c == '√' || c.isLetter()) {
                // implizite Multiplikation: 2π, 3(4)
                val r = power()
                val lv = if (left.pct) left.v / 100.0 else left.v
                left = V(lv * r.v)
            } else break
        }
        return left
    }

    private fun power(): V {
        val base = unary()
        if (peek() == '^') {
            i++
            val ex = power()
            return V(base.v.pow(ex.v))
        }
        return base
    }

    private fun unary(): V {
        val c = peek()
        if (c == '−' || c == '-') {
            i++
            val u = unary()
            return V(-u.v, u.pct)
        }
        if (c == '+') {
            i++
            return unary()
        }
        return postfix()
    }

    private fun postfix(): V {
        var v = primary()
        var pct = false
        while (true) {
            when (peek()) {
                '!' -> { i++; v = fact(v) }
                '%' -> { i++; pct = true }
                else -> return V(v, pct)
            }
        }
    }

    private fun fact(x: Double): Double {
        if (x < 0 || x != Math.floor(x) || x > 170) throw ParseError()
        var r = 1.0
        var k = 2
        while (k <= x) { r *= k; k++ }
        return r
    }

    private fun trigIn(x: Double) = if (degrees) Math.toRadians(x) else x

    private fun primary(): Double {
        val c = peek() ?: throw ParseError()
        if (c == '(') {
            i++
            val v = expr()
            if (peek() == ')') i++ // fehlende Klammer am Ende tolerieren
            return v
        }
        if (c == 'π') { i++; return PI }
        if (c == '√') { i++; return sqrt(postfixValue()) }
        if (c.isDigit() || c == ',' || c == '.') return number()
        if (c.isLetter()) {
            val start = i
            while (i < s.length && s[i].isLetter()) i++
            val name = s.substring(start, i)
            if (name == "e") return E
            val arg = postfixValue()
            return when (name) {
                "sin" -> sin(trigIn(arg))
                "cos" -> cos(trigIn(arg))
                "tan" -> tan(trigIn(arg))
                "ln" -> ln(arg)
                "log" -> log10(arg)
                else -> throw ParseError()
            }
        }
        throw ParseError()
    }

    private fun postfixValue(): Double {
        val p = postfix()
        return if (p.pct) p.v / 100.0 else p.v
    }

    private fun number(): Double {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] == ',' || s[i] == '.')) i++
        val raw = s.substring(start, i).replace(".", "").replace(',', '.')
        return raw.toDoubleOrNull() ?: throw ParseError()
    }

    companion object {
        fun tryEval(src: String, degrees: Boolean): Double? = runCatching {
            val r = Expr(src, degrees).eval()
            if (r.isNaN()) null else r
        }.getOrNull()
    }
}
