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

    /** Zuletzt gespielter Atem-Ton: ein neuer blendet ihn kurz aus, damit immer nur ein Ton klingt. */
    private var soloTrack: AudioTrack? = null
    private val soloLock = Any()

    private fun fadeOut(t: AudioTrack) {
        thread(isDaemon = true) {
            runCatching {
                for (i in 12 downTo 0) {
                    t.setVolume(i / 12f)
                    Thread.sleep(14)
                }
                t.stop()
            }
        }
    }

    private fun out(buf: ShortArray, usage: Int = AudioAttributes.USAGE_MEDIA, solo: Boolean = false) {
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
                if (solo) synchronized(soloLock) {
                    soloTrack?.let { fadeOut(it) }
                    soloTrack = track
                }
                track.play()
                Thread.sleep(buf.size * 1000L / SR + 120)
                if (solo) synchronized(soloLock) { if (soloTrack === track) soloTrack = null }
                track.release()
            }
        }
    }

    fun play(freq: Double, vol: Double = 0.5, dur: Double = 1.6) = out(samples(freq, vol, dur))

    fun piano(freq: Double, vol: Double = 0.6, dur: Double = 7.0, solo: Boolean = false) =
        thread(isDaemon = true) { out(pianoSamples(freq, vol, dur), solo = solo) }

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
            out(buf, solo = true)
        }
    }

    /** Vorab berechnen, damit der erste Ton ohne Verzögerung kommt. */
    fun warm(vararg freqs: Double) {
        thread(isDaemon = true) { freqs.forEach { pianoSamples(it, 0.6, 7.0); pianoSamples(it, 0.42, 7.0) } }
    }

    /** Leichter Raumhall (Schroeder: 4 Kammfilter + 2 Allpässe), damit es nicht nach Computer klingt. */
    private fun reverb(x: DoubleArray, mix: Double): DoubleArray {
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

    private fun normalize(s: DoubleArray, vol: Double): ShortArray {
        var peak = 1e-9
        s.forEach { if (abs(it) > peak) peak = abs(it) }
        return ShortArray(s.size) { (s[it] / peak * vol * 32767).toInt().coerceIn(-32767, 32767).toShort() }
    }

    /**
     * Tibetische Klangschale („Bim“): unharmonische Eigenschwingungen (1 : 2,71 : 5,06 : 8,2),
     * jede als leicht verstimmtes Paar, das schwebt („wah-wah“), weicher Klöppel, langer Nachklang.
     */
    private fun bowlSamples(freq: Double, vol: Double): ShortArray = cache.getOrPut("bowl$freq/$vol") {
        val dur = 10.0
        val n = (SR * dur).toInt()
        val s = DoubleArray(n)
        val ratios = doubleArrayOf(1.0, 2.71, 5.06, 8.2)
        // große Schale: obere Teiltöne leiser (weniger schrill), längeres Ausschwingen, ruhigere Schwebung
        val amps = doubleArrayOf(1.0, 0.34, 0.10, 0.03)
        val taus = doubleArrayOf(9.0, 5.5, 3.0, 1.6)
        val beats = doubleArrayOf(0.6, 1.1, 1.7, 2.4)
        val rnd = java.util.Random(3)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var v = 0.0
            for (k in ratios.indices) {
                val f = freq * ratios[k]
                v += amps[k] * exp(-t / taus[k]) * (sin(2 * PI * f * t) + 0.8 * sin(2 * PI * (f + beats[k]) * t + 0.7)) / 1.8
            }
            // Anschlag mit dem Filzklöppel: kurzes, gedämpftes Rauschen
            if (t < 0.05) {
                lp += ((rnd.nextGaussian() * exp(-t / 0.005) * 0.06) - lp) * 0.08
                v += lp
            }
            val a = (t / 0.006).coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
            val fade = ((dur - t) / 1.0).coerceIn(0.0, 1.0)
            s[i] = v * a * fade
        }
        normalize(reverb(s, 0.22), vol)
    }

    /** Kalimba (Daumenklavier): gezupfte Metallzunge auf Holz – kurz, weich, mit leisem „Tock“. */
    private fun kalimbaSamples(freq: Double, vol: Double): ShortArray = cache.getOrPut("kal$freq/$vol") {
        val dur = 3.2
        val n = (SR * dur).toInt()
        val s = DoubleArray(n)
        val rnd = java.util.Random(5)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var v = exp(-t / 1.1) * sin(2 * PI * freq * t) +
                0.12 * exp(-t / 0.35) * sin(2 * PI * 2 * freq * t) +
                0.22 * exp(-t / 0.09) * sin(2 * PI * 5.93 * freq * t) +
                0.08 * exp(-t / 0.05) * sin(2 * PI * 13.4 * freq * t)
            if (t < 0.06) {
                lp += ((rnd.nextGaussian() * exp(-t / 0.006) * 0.25) - lp) * 0.05
                v += lp
            }
            val a = (t / 0.003).coerceIn(0.0, 1.0)
            val fade = ((dur - t) / 0.6).coerceIn(0.0, 1.0)
            s[i] = v * a * fade
        }
        normalize(reverb(s, 0.18), vol)
    }

    fun bowl(freq: Double, vol: Double = 0.55, solo: Boolean = false) { thread(isDaemon = true) { out(bowlSamples(freq, vol), solo = solo) } }
    fun kalimba(freq: Double, vol: Double = 0.6, solo: Boolean = false) { thread(isDaemon = true) { out(kalimbaSamples(freq, vol), solo = solo) } }
    fun crystal(freq: Double, vol: Double = 0.55, solo: Boolean = false) { thread(isDaemon = true) { out(crystalSamples(freq, vol), solo = solo) } }
    fun gong(freq: Double, vol: Double = 0.6, solo: Boolean = false) { thread(isDaemon = true) { out(gongSamples(freq, vol), solo = solo) } }
    fun warmCrystal(vararg f: Pair<Double, Double>) { thread(isDaemon = true) { f.forEach { crystalSamples(it.first, it.second) } } }
    fun warmGong(vararg f: Pair<Double, Double>) { thread(isDaemon = true) { f.forEach { gongSamples(it.first, it.second) } } }

    /**
     * Allgemeiner Klangkörper aus Eigenschwingungen: Verhältnisse, Lautstärken, Abklingzeiten, Schwebungen.
     * [bloom]: Obertöne blühen nach dem Anschlag erst auf (Gong).
     */
    private fun modal(
        key: String, freq: Double, vol: Double, dur: Double,
        ratios: DoubleArray, amps: DoubleArray, taus: DoubleArray, beats: DoubleArray,
        att: Double, clickTau: Double, clickAmp: Double, clickAlpha: Double, rev: Double, bloom: Double = 0.0
    ): ShortArray = cache.getOrPut("$key$freq/$vol") {
        val n = (SR * dur).toInt()
        val s = DoubleArray(n)
        val rnd = java.util.Random(7)
        var lp = 0.0
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
            if (t < 0.15) {
                lp += ((rnd.nextGaussian() * exp(-t / clickTau) * clickAmp) - lp) * clickAlpha
                v += lp
            }
            val a = (t / att).coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
            val fade = ((dur - t) / 1.0).coerceIn(0.0, 1.0)
            s[i] = v * a * fade
        }
        normalize(reverb(s, rev), vol)
    }

    /** Kristall-Klangschale: fast reiner Ton, sehr langes, ruhiges Nachschwingen. */
    private fun crystalSamples(freq: Double, vol: Double) = modal(
        "cry", freq, vol, 11.0,
        doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(1.0, 0.05, 0.015), doubleArrayOf(12.0, 5.0, 2.5), doubleArrayOf(0.4, 0.0, 0.0),
        att = 0.03, clickTau = 0.004, clickAmp = 0.02, clickAlpha = 0.12, rev = 0.26
    )

    /** Sanfter Gong: tief, Obertöne blühen nach dem Anschlag auf und rauschen lange aus. */
    private fun gongSamples(freq: Double, vol: Double) = modal(
        "gong", freq, vol, 11.0,
        doubleArrayOf(1.0, 1.52, 2.03, 2.71, 3.4, 4.2), doubleArrayOf(1.0, 0.5, 0.35, 0.25, 0.12, 0.06),
        doubleArrayOf(9.0, 7.0, 6.0, 4.5, 3.5, 2.5), doubleArrayOf(0.3, 0.5, 0.7, 0.9, 1.1, 1.4),
        att = 0.012, clickTau = 0.01, clickAmp = 0.12, clickAlpha = 0.033, rev = 0.3, bloom = 0.35
    )
    /** Ausklang mit der Kalimba: drei weiche, aufsteigende Töne. */
    fun kalimbaRun() {
        thread(isDaemon = true) {
            val notes = doubleArrayOf(293.66, 392.0, 493.88)
            notes.forEachIndexed { i, f ->
                out(kalimbaSamples(f, if (i == 2) 0.5 else 0.42))
                Thread.sleep(380)
            }
        }
    }

    fun warmBowl(vararg f: Pair<Double, Double>) { thread(isDaemon = true) { f.forEach { bowlSamples(it.first, it.second) } } }
    fun warmKalimba(vararg f: Pair<Double, Double>) { thread(isDaemon = true) { f.forEach { kalimbaSamples(it.first, it.second) } } }

    fun warmPiano(freq: Double, vol: Double, dur: Double) {
        thread(isDaemon = true) { pianoSamples(freq, vol, dur) }
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

/** Sanfte Vibrationsmuster für die Atemübung ohne Ton. */
object Haptic {
    private fun vib(ctx: android.content.Context): android.os.Vibrator? =
        if (android.os.Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator)

    private fun play(ctx: android.content.Context, times: LongArray, amps: IntArray) {
        runCatching {
            val v = vib(ctx) ?: return
            if (!v.hasVibrator()) return
            val effect = if (v.hasAmplitudeControl()) android.os.VibrationEffect.createWaveform(times, amps, -1)
            else android.os.VibrationEffect.createWaveform(times, -1)
            v.vibrate(effect)
        }
    }

    /** Einatmen: anschwellend. */
    fun inhale(ctx: android.content.Context) =
        play(ctx, longArrayOf(0, 90, 90, 90, 90), intArrayOf(0, 40, 80, 130, 170))

    /** Halten: ein kurzes, leises Antippen. */
    fun hold(ctx: android.content.Context) = play(ctx, longArrayOf(0, 35), intArrayOf(0, 90))

    /** Ausatmen: abschwellend. */
    fun exhale(ctx: android.content.Context) =
        play(ctx, longArrayOf(0, 90, 90, 90, 90), intArrayOf(0, 170, 130, 80, 40))

    /** Ende: zwei weiche Pulse. */
    fun done(ctx: android.content.Context) =
        play(ctx, longArrayOf(0, 160, 220, 160), intArrayOf(0, 110, 0, 110))
}
