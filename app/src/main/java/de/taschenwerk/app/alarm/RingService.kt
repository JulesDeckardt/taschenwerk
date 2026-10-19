package de.taschenwerk.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.taschenwerk.app.R
import de.taschenwerk.app.data.Store
import kotlinx.coroutines.flow.MutableStateFlow

/** Klingelt im Vordergrund: Alarm-Ton (Wecker-Lautstärke), Vibration, Vollbild-Benachrichtigung. */
class RingService : Service() {

    companion object {
        const val KIND_ALARM = 0
        const val KIND_TIMER = 1
        const val ACTION_START = "de.taschenwerk.app.RING_START"
        const val ACTION_STOP = "de.taschenwerk.app.RING_STOP"
        const val ACTION_SNOOZE = "de.taschenwerk.app.RING_SNOOZE"
        private const val CHANNEL = "ring"
        private const val NOTIF_ID = 7

        val active = MutableStateFlow(false)
        var title: String = ""
            private set
        var subtitle: String = ""
            private set
        var kind: Int = KIND_ALARM
            private set
        var alarmId: Int = -1
            private set

        fun start(ctx: Context, kind: Int, id: Int, title: String, sub: String) {
            val i = Intent(ctx, RingService::class.java)
                .setAction(ACTION_START)
                .putExtra("kind", kind)
                .putExtra("id", id)
                .putExtra("title", title)
                .putExtra("sub", sub)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun command(ctx: Context, action: String) {
            ctx.startService(Intent(ctx, RingService::class.java).setAction(action))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var volume = 0.15f
    private var ringing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging()
            }
            ACTION_SNOOZE -> {
                if (kind == KIND_ALARM && alarmId >= 0) {
                    AlarmScheduler.snooze(this, alarmId, Store.get(this).snoozeMin)
                } else if (kind == KIND_TIMER) {
                    TimerStore.start(this, 60_000L, subtitle.ifEmpty { "Timer" })
                }
                stopRinging()
            }
            else -> {
                kind = intent?.getIntExtra("kind", KIND_ALARM) ?: KIND_ALARM
                alarmId = intent?.getIntExtra("id", -1) ?: -1
                title = intent?.getStringExtra("title") ?: "Wecker"
                subtitle = intent?.getStringExtra("sub") ?: ""
                goForeground()
                startRinging()
            }
        }
        return START_NOT_STICKY
    }

    private fun pi(action: String, req: Int): PendingIntent = PendingIntent.getService(
        this, req, Intent(this, RingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Wecker und Timer", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)
            ch.enableVibration(false)
            ch.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
        val fullIntent = Intent(this, RingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val full = PendingIntent.getActivity(
            this, 2, fullIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = subtitle.ifEmpty { if (kind == KIND_ALARM) "Wecker" else "Timer" }
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tw)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .addAction(0, if (kind == KIND_ALARM) "Schlummern" else "+1 Min.", pi(ACTION_SNOOZE, 11))
            .addAction(0, "Stopp", pi(ACTION_STOP, 12))
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private fun startRinging() {
        if (ringing) stopSound()
        ringing = true
        active.value = true
        val store = Store.get(this)

        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "taschenwerk:ring").apply {
                acquire(11 * 60 * 1000L)
            }
        }

        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        volume = if (store.rampVolume) 0.15f else 1f
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@RingService, uri)
                isLooping = true
                setVolume(volume, volume)
                prepare()
                start()
            }
        }.onFailure {
            runCatching { RingtoneManager.getRingtone(this, uri)?.play() }
        }
        if (store.rampVolume) handler.postDelayed(rampRunnable, 2000)

        if (store.vibrate) {
            runCatching {
                vibrator = if (Build.VERSION.SDK_INT >= 31) {
                    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                @Suppress("DEPRECATION")
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0), attrs)
            }
        }
        // Nach 10 Minuten automatisch aufhören.
        handler.postDelayed({ stopRinging() }, 10 * 60 * 1000L)
    }

    private val rampRunnable = object : Runnable {
        override fun run() {
            volume = (volume + 0.06f).coerceAtMost(1f)
            runCatching { player?.setVolume(volume, volume) }
            if (volume < 1f) handler.postDelayed(this, 2000)
        }
    }

    private fun stopSound() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun stopRinging() {
        stopSound()
        ringing = false
        active.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSound()
        active.value = false
        super.onDestroy()
    }
}
