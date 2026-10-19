package de.taschenwerk.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.taschenwerk.app.MainActivity
import de.taschenwerk.app.data.Alarm
import de.taschenwerk.app.data.Store
import java.time.ZonedDateTime

object AlarmScheduler {
    const val ACTION_ALARM = "de.taschenwerk.app.ALARM"
    const val ACTION_TIMER = "de.taschenwerk.app.TIMER"
    const val EXTRA_ID = "id"
    const val EXTRA_SNOOZE = "snooze"
    private const val TIMER_REQ = 900_001
    private const val SNOOZE_BASE = 800_000

    fun nextTrigger(a: Alarm, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        var t = now.withHour(a.hour).withMinute(a.minute).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        if (a.days == 0) return t
        for (i in 0 until 8) {
            val bit = 1 shl (t.dayOfWeek.value - 1)
            if (a.days and bit != 0) return t
            t = t.plusDays(1)
        }
        return t
    }

    private fun am(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canExact(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31) am(ctx).canScheduleExactAlarms() else true

    private fun showIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun alarmPi(ctx: Context, req: Int, id: Int, snooze: Boolean): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_ALARM)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_SNOOZE, snooze)
        return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun timerPi(ctx: Context): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_TIMER)
        return PendingIntent.getBroadcast(ctx, TIMER_REQ, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAt(ctx: Context, at: Long, pi: PendingIntent) {
        val m = am(ctx)
        if (canExact(ctx)) {
            m.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(ctx)), pi)
        } else {
            m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun schedule(ctx: Context, a: Alarm) {
        if (!a.enabled) {
            cancel(ctx, a.id)
            return
        }
        val at = nextTrigger(a).toInstant().toEpochMilli()
        setAt(ctx, at, alarmPi(ctx, a.id, a.id, false))
    }

    fun cancel(ctx: Context, id: Int) {
        am(ctx).cancel(alarmPi(ctx, id, id, false))
        am(ctx).cancel(alarmPi(ctx, SNOOZE_BASE + id, id, true))
    }

    fun snooze(ctx: Context, id: Int, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        setAt(ctx, at, alarmPi(ctx, SNOOZE_BASE + id, id, true))
    }

    fun rescheduleAll(ctx: Context) {
        val store = Store.get(ctx)
        store.reloadAlarms()
        store.alarms.forEach { schedule(ctx, it) }
        TimerStore.restore(ctx)
    }

    fun scheduleTimer(ctx: Context, endAt: Long) = setAt(ctx, endAt, timerPi(ctx))

    fun cancelTimer(ctx: Context) = am(ctx).cancel(timerPi(ctx))
}
