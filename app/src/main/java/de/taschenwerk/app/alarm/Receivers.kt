package de.taschenwerk.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.taschenwerk.app.data.Store

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_ALARM -> {
                val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
                val snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false)
                val store = Store.get(ctx)
                store.reloadAlarms()
                val a = store.alarms.firstOrNull { it.id == id }
                if (!snooze) {
                    if (a == null || !a.enabled) return
                    if (a.days == 0) {
                        store.upsertAlarm(a.copy(enabled = false))
                    } else {
                        AlarmScheduler.schedule(ctx, a)
                    }
                }
                val title = if (a != null) "%02d:%02d".format(a.hour, a.minute) else "Wecker"
                RingService.start(ctx, RingService.KIND_ALARM, id, title, a?.label ?: "")
            }
            AlarmScheduler.ACTION_TIMER -> {
                TimerStore.fired(ctx)
                RingService.start(ctx, RingService.KIND_TIMER, -1, "Timer abgelaufen", TimerStore.label)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(ctx)
    }
}
