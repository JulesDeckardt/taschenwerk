package de.taschenwerk.app.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Synthetisierte Klänge (keine Audiodateien nötig). */
object Chime {
    private const val SR = 44100
    private val cache = ConcurrentHashMap<String, ShortArray>()

    private fun samples(freq: Double, vol: Double, dur: Double): ShortArray {
        val key = "c$freq/$vol/$dur"
        return cache.getOrPut(key) {
            val n = (SR * dur).toInt()
            val out = ShortArray(n)
            val attack = 0.015
            for (i in 0 until n) {
                val t = i.toDouble() / SR
                val env = if (t < attack) t / attack else exp(-(t - attack) * 5.0 / dur)
                val s = sin(2 * PI * freq * t) + 0.22 * sin(2 * PI * 2 * freq * t) + 0.08 * sin(2 * PI * 3 * freq * t)
                out[i] = (s / 1.3 * env * vol * 32767).toInt().coerceIn(-32767, 32767).toShort()
            }
            out
        }
    }

    /**
     * Weich angeschlagene, mitteltiefe Klaviertaste, die lange ausschwingt.
     * Leicht gespreizte Teiltöne (Saitensteifigkeit), zwei leicht verstimmte Saiten
     * für Wärme, höhere Teiltöne klingen schneller ab, zweistufiges Ausklingen.
     */
    private fun pianoSamples(freq: Double, vol: Double, dur: Double): ShortArray {
        val key = "p$freq/$vol/$dur"
        return cache.getOrPut(key) {
            val n = (SR * dur).toInt()
            val out = ShortArray(n)
            val b = 0.00035
            val amps = doubleArrayOf(1.0, 0.42, 0.20, 0.11, 0.06, 0.035, 0.02)
            val parts = amps.indices.map { k ->
                val h = k + 1
                freq * h * sqrt(1 + b * h * h)
            }
            val detune = 1.0012
            val attack = 0.012
            val fade = 0.8
            var norm = 0.0
            amps.forEach { norm += it }
            for (i in 0 until n) {
                val t = i.toDouble() / SR
                var s = 0.0
                for (k in amps.indices) {
                    val f = parts[k]
                    val decay = exp(-t * (0.42 + 0.5 * k))
                    s += amps[k] * decay * (sin(2 * PI * f * t) + 0.6 * sin(2 * PI * f * detune * t)) / 1.6
                }
                val body = 0.55 * exp(-t / 1.1) + 0.45 * exp(-t / 4.2)
                val att = if (t < attack) (t / attack).let { it * it * (3 - 2 * it) } else 1.0
                val tail = if (t > dur - fade) ((dur - t) / fade).coerceIn(0.0, 1.0) else 1.0
                out[i] = (s / norm * body * att * tail * vol * 32767).toInt().coerceIn(-32767, 32767).toShort()
            }
            out
        }
    }

    /** Boxring-Glocke: drei kurze, metallische Schläge („din, din, din“). */
    private fun bellSamples(vol: Double): ShortArray {
        val key = "bell$vol"
        return cache.getOrPut(key) {
            val dur = 2.2
            val n = (SR * dur).toInt()
            val buf = DoubleArray(n)
            val f = 830.0
            val ratios = doubleArrayOf(1.0, 1.506, 2.0, 2.74, 3.43, 4.17, 5.40)
            val amps = doubleArrayOf(1.0, 0.55, 0.45, 0.35, 0.22, 0.14, 0.08)
            val decays = doubleArrayOf(2.2, 3.0, 3.6, 4.6, 6.0, 7.5, 9.0)
            val rnd = java.util.Random(7)
            val strikes = doubleArrayOf(0.0, 0.30, 0.60)
            for (st in strikes) {
                val s0 = (st * SR).toInt()
                for (i in s0 until n) {
                    val t = (i - s0).toDouble() / SR
                    var s = 0.0
                    for (k in ratios.indices) s += amps[k] * exp(-t * decays[k]) * sin(2 * PI * f * ratios[k] * t)
                    // kurzer Anschlag (Klöppel)
                    if (t < 0.012) s += (rnd.nextDouble() * 2 - 1) * 0.35 * (1 - t / 0.012)
                    val att = if (t < 0.002) t / 0.002 else 1.0
                    buf[i] += s * att
                }
            }
            var peak = 0.0
            buf.forEach { if (abs(it) > peak) peak = abs(it) }
            val out = ShortArray(n)
            for (i in 0 until n) {
                val tail = if (i > n - SR / 4) (n - i).toDouble() / (SR / 4) else 1.0
                out[i] = (buf[i] / peak * tail * vol * 32767).toInt().coerceIn(-32767, 32767).toShort()
            }
            out
        }
    }

    private fun out(buf: ShortArray, usage: Int = AudioAttributes.USAGE_MEDIA) {
        thread(isDaemon = true) {
            runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SR)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buf.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, buf.size)
                track.play()
                Thread.sleep(buf.size * 1000L / SR + 120)
                track.release()
            }
        }
    }

    fun play(freq: Double, vol: Double = 0.5, dur: Double = 1.6) = out(samples(freq, vol, dur))

    fun piano(freq: Double, vol: Double = 0.6, dur: Double = 7.0) = out(pianoSamples(freq, vol, dur))

    /** Entspanntes Abschluss-Motiv „dim, dim, dim, diiim“ (aufsteigend, weich ausklingend). */
    fun finish(vol: Double = 0.5) {
        thread(isDaemon = true) {
            val buf = cache.getOrPut("finish$vol") {
                val notes = doubleArrayOf(392.0, 493.88, 587.33, 783.99)
                val gap = 0.42
                val noteDur = 4.5
                val n = (SR * (gap * (notes.size - 1) + noteDur)).toInt()
                val mix = DoubleArray(n)
                notes.forEachIndexed { i, f ->
                    val one = pianoSamples(f, 1.0, noteDur)
                    val off = (i * gap * SR).toInt()
                    val g = if (i == notes.size - 1) 0.9 else 0.7
                    for (k in one.indices) if (off + k < n) mix[off + k] += one[k] / 32767.0 * g
                }
                var peak = 0.0
                mix.forEach { if (abs(it) > peak) peak = abs(it) }
                ShortArray(n) { (mix[it] / peak * vol * 32767).toInt().coerceIn(-32767, 32767).toShort() }
            }
            out(buf)
        }
    }

    fun bell(vol: Double = 0.75) = out(bellSamples(vol))

    /** Ausklang am Ende einer Atemübung: tiefer, weicher Akkord, langsam gebrochen. */
    fun closing(vol: Double = 0.5) {
        thread(isDaemon = true) {
            val buf = cache.getOrPut("closing$vol") {
                val notes = doubleArrayOf(146.83, 220.0, 293.66, 369.99)
                val gap = 0.55
                val noteDur = 8.0
                val n = (SR * (gap * (notes.size - 1) + noteDur)).toInt()
                val mix = DoubleArray(n)
                notes.forEachIndexed { i, f ->
                    val one = pianoSamples(f, 1.0, noteDur)
                    val off = (i * gap * SR).toInt()
                    val g = doubleArrayOf(1.0, 0.75, 0.6, 0.45)[i]
                    for (k in one.indices) if (off + k < n) mix[off + k] += one[k] / 32767.0 * g
                }
                var peak = 0.0
                mix.forEach { if (abs(it) > peak) peak = abs(it) }
                ShortArray(n) { (mix[it] / peak * vol * 32767).toInt().coerceIn(-32767, 32767).toShort() }
            }
            out(buf)
        }
    }

    /** Vorab berechnen, damit der erste Ton ohne Verzögerung kommt. */
    fun warm(vararg freqs: Double) {
        thread(isDaemon = true) { freqs.forEach { pianoSamples(it, 0.6, 7.0); pianoSamples(it, 0.17, 7.0) } }
    }

    fun warmBell() {
        thread(isDaemon = true) { bellSamples(0.75) }
    }
}

/** Kurze Pieptöne für Workouts. */
object Beep {
    private var tg: ToneGenerator? = null

    private fun gen(): ToneGenerator? {
        if (tg == null) tg = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()
        return tg
    }

    fun short() { runCatching { gen()?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) } }
    fun long() { runCatching { gen()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 450) } }
}
