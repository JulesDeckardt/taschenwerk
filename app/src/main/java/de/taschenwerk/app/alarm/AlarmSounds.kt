package de.taschenwerk.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri

/** Wecktöne des Systems: Liste, Namen, Vorschau. */
object AlarmSounds {
    data class Snd(val title: String, val uri: String)

    private var preview: Ringtone? = null

    fun resolve(ctx: Context, uri: String): Uri {
        if (uri.isNotBlank()) runCatching { return Uri.parse(uri) }
        return RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }

    private fun query(ctx: Context, type: Int): List<Snd> = runCatching {
        val rm = RingtoneManager(ctx)
        rm.setType(type)
        val c = rm.cursor
        val out = ArrayList<Snd>()
        while (c.moveToNext()) {
            val title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
            val u = rm.getRingtoneUri(c.position)?.toString() ?: continue
            out.add(Snd(title, u))
        }
        out
    }.getOrDefault(emptyList())

    /** Wecktöne zuerst, danach Klingeltöne (ohne Doppelte). */
    fun list(ctx: Context): Pair<List<Snd>, List<Snd>> {
        val alarms = query(ctx, RingtoneManager.TYPE_ALARM)
        val names = alarms.map { it.title }.toHashSet()
        val rings = query(ctx, RingtoneManager.TYPE_RINGTONE).filter { it.title !in names }
        return alarms to rings
    }

    fun title(ctx: Context, uri: String): String {
        if (uri.isBlank()) {
            val t = runCatching { RingtoneManager.getRingtone(ctx, resolve(ctx, ""))?.getTitle(ctx) }.getOrNull()
            return if (t.isNullOrBlank()) "Standard" else "Standard · $t"
        }
        return runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(uri))?.getTitle(ctx) }.getOrNull() ?: "Weckton"
    }

    fun play(ctx: Context, uri: String) {
        stop()
        runCatching {
            preview = RingtoneManager.getRingtone(ctx, resolve(ctx, uri))?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        }
    }

    fun stop() {
        runCatching { preview?.stop() }
        preview = null
    }
}
