package de.taschenwerk.app.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Sanfte, synthetisierte Glockentöne (keine Audiodateien nötig). */
object Chime {
    private const val SR = 44100
    private val cache = ConcurrentHashMap<String, ShortArray>()

    private fun samples(freq: Double, vol: Double, dur: Double): ShortArray {
        val key = "$freq/$vol/$dur"
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

    fun play(freq: Double, vol: Double = 0.5, dur: Double = 1.6) {
        thread(isDaemon = true) {
            runCatching {
                val buf = samples(freq, vol, dur)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
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
                Thread.sleep((dur * 1000).toLong() + 80)
                track.release()
            }
        }
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
