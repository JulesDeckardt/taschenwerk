package de.taschenwerk.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import de.taschenwerk.app.MainActivity
import de.taschenwerk.app.R
import de.taschenwerk.app.data.Store

/** Countdown-Timer. Das Ende wird als Systemalarm geplant, damit er auch bei geschlossener App klingelt. */
object TimerStore {
    private const val NOTIF_ID = 42
    const val CHANNEL = "timer"

    /** Endzeitpunkt (System.currentTimeMillis), 0 = läuft nicht. */
    var endAt by mutableLongStateOf(0L)
        private set
    /** Restzeit bei Pause in ms, 0 = keine Pause. */
    var pausedLeft by mutableLongStateOf(0L)
        private set
    var totalMs by mutableLongStateOf(0L)
        private set
    var label by mutableStateOf("Timer")
        private set

    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        val s = Store.get(ctx)
        endAt = s.getLong("timerEnd")
        pausedLeft = s.getLong("timerPaused")
        totalMs = s.getLong("timerTotal")
        label = s.getString("timerLabel") ?: "Timer"
        if (endAt in 1..System.currentTimeMillis()) endAt = 0L
        loaded = true
    }

    private fun persist(ctx: Context) {
        val s = Store.get(ctx)
        s.putLong("timerEnd", endAt)
        s.putLong("timerPaused", pausedLeft)
        s.putLong("timerTotal", totalMs)
        s.putString("timerLabel", label)
    }

    val running: Boolean get() = endAt > 0L
    val paused: Boolean get() = endAt == 0L && pausedLeft > 0L

    fun start(ctx: Context, ms: Long, name: String = "Timer") {
        if (ms <= 0) return
        label = name
        totalMs = ms
        pausedLeft = 0L
        endAt = System.currentTimeMillis() + ms
        AlarmScheduler.scheduleTimer(ctx, endAt)
        persist(ctx)
        notifyRunning(ctx)
    }

    fun pause(ctx: Context) {
        if (!running) return
        pausedLeft = (endAt - System.currentTimeMillis()).coerceAtLeast(1000L)
        endAt = 0L
        AlarmScheduler.cancelTimer(ctx)
        persist(ctx)
        cancelNotif(ctx)
    }

    fun resume(ctx: Context) {
        if (!paused) return
        endAt = System.currentTimeMillis() + pausedLeft
        pausedLeft = 0L
        AlarmScheduler.scheduleTimer(ctx, endAt)
        persist(ctx)
        notifyRunning(ctx)
    }

    fun reset(ctx: Context) {
        endAt = 0L
        pausedLeft = 0L
        totalMs = 0L
        AlarmScheduler.cancelTimer(ctx)
        persist(ctx)
        cancelNotif(ctx)
    }

    fun fired(ctx: Context) {
        load(ctx)
        endAt = 0L
        pausedLeft = 0L
        totalMs = 0L
        persist(ctx)
        cancelNotif(ctx)
    }

    /** Nach Neustart: laufenden Timer neu planen. */
    fun restore(ctx: Context) {
        loaded = false
        load(ctx)
        if (running) {
            AlarmScheduler.scheduleTimer(ctx, endAt)
            notifyRunning(ctx)
        }
    }

    fun remainingMs(): Long = when {
        running -> (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        paused -> pausedLeft
        else -> 0L
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Laufender Timer", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notifyRunning(ctx: Context) {
        runCatching {
            ensureChannel(ctx)
            val open = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_tw)
                .setContentTitle(label)
                .setContentText("Timer läuft")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(endAt)
                .setShowWhen(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .build()
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, n)
        }
    }

    private fun cancelNotif(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
    }
}
