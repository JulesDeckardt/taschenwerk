package de.taschenwerk.app.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reine Klangsynthese ohne Android-Abhängigkeiten (lässt sich so auch am Rechner prüfen).
 * Alle Atem-Klänge werden auf die gleiche wahrgenommene Lautstärke gebracht.
 */
object Synth {
    const val SR = 44100

    /** Ziel-Lautheit (gewichteter Effektivwert der ersten 2 s) für level = 1.0. */
    const val TARGET = 0.085

    /** Nie lauter als das: Abstand zur Übersteuerung, damit nichts kratzt. */
    private const val PEAK_MAX = 0.80

    /** Leichter Raumhall (Schroeder: 4 Kammfilter + 2 Allpässe), damit es nicht nach Computer klingt. */
    fun reverb(x: DoubleArray, mix: Double): DoubleArray {
        val n = x.size
        val wet = DoubleArray(n)
        val combs = arrayOf(1557 to 0.84, 1617 to 0.83, 1491 to 0.82, 1422 to 0.81)
        for ((d, g) in combs) {
            val b = DoubleArray(n)
            for (i in 0 until n) {
                b[i] = x[i] + (if (i >= d) g * b[i - d] else 0.0)
                wet[i] += b[i] * 0.25
            }
        }
        var y = wet
        for ((d, g) in arrayOf(225 to 0.5, 556 to 0.5)) {
            val z = DoubleArray(n)
            for (i in 0 until n) {
                val xd = if (i >= d) y[i - d] else 0.0
                val zd = if (i >= d) z[i - d] else 0.0
                z[i] = -g * y[i] + xd + g * zd
            }
            y = z
        }
        return DoubleArray(n) { x[it] * (1 - mix) + y[it] * mix }
    }

    /**
     * Wahrgenommene Lautheit: Effektivwert der ersten 2 Sekunden nach einem Hochpass (~200 Hz),
     * weil Handy-Lautsprecher tiefe Töne kaum wiedergeben.
     */
    fun loudness(s: DoubleArray): Double {
        val n = minOf(s.size, 2 * SR)
        val a = exp(-2 * PI * 200.0 / SR)
        var prevX = 0.0
        var prevY = 0.0
        var sum = 0.0
        for (i in 0 until n) {
            val y = a * (prevY + s[i] - prevX)
            prevX = s[i]; prevY = y
            sum += y * y
        }
        return sqrt(sum / n.coerceAtLeast(1))
    }

    /** Auf gleiche Lautheit bringen (level 1.0 = Ein-/Ausatmen, kleiner = leiser), Spitzen begrenzt. */
    fun level(s: DoubleArray, level: Double): ShortArray {
        val l = loudness(s).coerceAtLeast(1e-9)
        var peak = 1e-9
        s.forEach { if (abs(it) > peak) peak = abs(it) }
        var g = TARGET * level / l
        if (peak * g > PEAK_MAX) g = PEAK_MAX / peak
        return ShortArray(s.size) { (s[it] * g * 32767).toInt().coerceIn(-32767, 32767).toShort() }
    }

    /** Weicher Einsatz (S-Kurve) – verhindert ein Knacken am Anfang. */
    private fun attack(t: Double, a: Double): Double = (t / a).coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }

    /** Weiches Ende (Kosinus) über [fade] Sekunden vor [dur]. */
    private fun tail(t: Double, dur: Double, fade: Double): Double {
        val left = dur - t
        if (left >= fade) return 1.0
        if (left <= 0) return 0.0
        return 0.5 - 0.5 * cos(PI * left / fade)
    }

    /** Puffer auf [ms] kürzen und mit weicher Kosinus-Blende enden lassen. */
    fun trimmed(buf: ShortArray, ms: Long): ShortArray {
        val n = (ms * SR / 1000).toInt().coerceIn(1, buf.size)
        val fadeN = (minOf(700L, ms / 3) * SR / 1000).toInt().coerceAtLeast(1)
        return ShortArray(n) { i ->
            val left = n - 1 - i
            if (left >= fadeN) buf[i]
            else (buf[i] * (0.5 - 0.5 * cos(PI * left / fadeN))).toInt().toShort()
        }
    }

    /**
     * Klavier: weich angeschlagene, mitteltiefe Taste. Leicht gespreizte Teiltöne,
     * zwei leicht verstimmte Saiten, höhere Teiltöne klingen schneller ab.
     */
    fun piano(freq: Double, dur: Double): DoubleArray {
        val n = (SR * dur).toInt()
        val out = DoubleArray(n)
        val b = 0.00035
        val amps = doubleArrayOf(1.0, 0.42, 0.20, 0.11, 0.06, 0.035, 0.02)
        val parts = amps.indices.map { k -> val h = k + 1; freq * h * sqrt(1 + b * h * h) }
        val detune = 1.0012
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var s = 0.0
            for (k in amps.indices) {
                val f = parts[k]
                s += amps[k] * exp(-t * (0.42 + 0.5 * k)) * (sin(2 * PI * f * t) + 0.6 * sin(2 * PI * f * detune * t)) / 1.6
            }
            val body = 0.55 * exp(-t / 1.1) + 0.45 * exp(-t / 4.2)
            out[i] = s * body * attack(t, 0.012) * tail(t, dur, 0.8)
        }
        return out
    }

    /**
     * Allgemeiner Klangkörper aus Eigenschwingungen: Verhältnisse, Lautstärken, Abklingzeiten, Schwebungen.
     * Kein Rausch-Anschlag mehr (der war als Knacken hörbar), nur ein weicher Einsatz.
     * [bloom]: Obertöne blühen nach dem Anschlag erst auf (Gong).
     */
    private fun modal(
        freq: Double, dur: Double,
        ratios: DoubleArray, amps: DoubleArray, taus: DoubleArray, beats: DoubleArray,
        att: Double, rev: Double, bloom: Double = 0.0
    ): DoubleArray {
        val n = (SR * dur).toInt()
        val s = DoubleArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var v = 0.0
            for (k in ratios.indices) {
                val f = freq * ratios[k]
                var e = exp(-t / taus[k])
                if (bloom > 0 && ratios[k] > 1.3) e *= 1 - exp(-t / bloom)
                v += amps[k] * e * if (beats[k] > 0)
                    (sin(2 * PI * f * t) + 0.8 * sin(2 * PI * (f + beats[k]) * t + 0.7)) / 1.8
                else sin(2 * PI * f * t)
            }
            s[i] = v * attack(t, att) * tail(t, dur, 1.0)
        }
        // Hall erst nach der Hüllkurve; danach noch einmal weich ausblenden, damit auch der Hall bei 0 endet.
        val r = reverb(s, rev)
        for (i in 0 until n) r[i] *= tail(i.toDouble() / SR, dur, 0.4)
        return r
    }

    /** Große tibetische Klangschale: unharmonische Teiltöne (1 : 2,71 : 5,06 : 8,2), jede schwebt leicht. */
    fun bowl(freq: Double) = modal(
        freq, 10.0,
        doubleArrayOf(1.0, 2.71, 5.06, 8.2), doubleArrayOf(1.0, 0.34, 0.10, 0.03),
        doubleArrayOf(9.0, 5.5, 3.0, 1.6), doubleArrayOf(0.6, 1.1, 1.7, 2.4),
        att = 0.012, rev = 0.22
    )

    /** Kristall-Klangschale: fast reiner Ton, ruhiges Nachschwingen. */
    fun crystal(freq: Double) = modal(
        freq, 6.0,
        doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(1.0, 0.04, 0.01),
        doubleArrayOf(3.2, 2.0, 1.2), doubleArrayOf(0.4, 0.0, 0.0),
        att = 0.04, rev = 0.18
    )

    /** Sanfter Gong: tief, Obertöne blühen nach dem Anschlag auf. */
    fun gong(freq: Double) = modal(
        freq, 6.5,
        doubleArrayOf(1.0, 1.52, 2.03, 2.71, 3.4, 4.2), doubleArrayOf(1.0, 0.45, 0.3, 0.2, 0.09, 0.04),
        doubleArrayOf(3.6, 3.0, 2.6, 2.2, 1.8, 1.4), doubleArrayOf(0.3, 0.5, 0.7, 0.9, 1.1, 1.4),
        att = 0.03, rev = 0.22, bloom = 0.25
    )

    /** Kalimba: gezupfte Metallzunge – kurz, weich (ohne Anschlag-Geräusch). */
    fun kalimba(freq: Double) = modal(
        freq, 3.2,
        doubleArrayOf(1.0, 2.0, 5.93), doubleArrayOf(1.0, 0.12, 0.07),
        doubleArrayOf(1.1, 0.35, 0.09), doubleArrayOf(0.0, 0.0, 0.0),
        att = 0.012, rev = 0.18
    )

    /** Mehrere Töne zeitversetzt zu einem Klang mischen (Ausklang). */
    fun arpeggio(notes: List<DoubleArray>, gapSec: Double, gains: DoubleArray): DoubleArray {
        val gap = (gapSec * SR).toInt()
        val n = notes.indices.maxOf { it * gap + notes[it].size }
        val mix = DoubleArray(n)
        notes.forEachIndexed { i, one ->
            val off = i * gap
            for (k in one.indices) mix[off + k] += one[k] * gains[i]
        }
        return mix
    }
}
