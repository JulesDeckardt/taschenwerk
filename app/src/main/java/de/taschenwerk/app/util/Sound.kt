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
            val fade = 0.35
            var norm = 0.0
            amps.forEach { norm += it }
            for (i in 0 until n) {
                val t = i.toDouble() / SR
                var s = 0.0
                for (k in amps.indices) {
                    val f = parts[k]
                    val decay = exp(-t * (0.9 + 0.75 * k))
                    s += amps[k] * decay * (sin(2 * PI * f * t) + 0.6 * sin(2 * PI * f * detune * t)) / 1.6
                }
                val body = 0.72 * exp(-t / 0.55) + 0.28 * exp(-t / 2.6)
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

    fun piano(freq: Double, vol: Double = 0.4, dur: Double = 4.0) = out(pianoSamples(freq, vol, dur))

    fun bell(vol: Double = 0.75) = out(bellSamples(vol))

    /** Vorab berechnen, damit der erste Ton ohne Verzögerung kommt. */
    fun warm(vararg freqs: Double) {
        thread(isDaemon = true) { freqs.forEach { pianoSamples(it, 0.4, 4.0) } }
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
