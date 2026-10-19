package de.taschenwerk.app.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import de.taschenwerk.app.data.Store
import de.taschenwerk.app.ui.AppTheme
import de.taschenwerk.app.ui.Ic
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.PillButton
import de.taschenwerk.app.ui.num
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val store = Store.get(this)
        setContent {
            AppTheme(store.scheme, store.darkMode) {
                RingScreen(
                    title = RingService.title,
                    sub = RingService.subtitle,
                    isAlarm = RingService.kind == RingService.KIND_ALARM,
                    snoozeMin = store.snoozeMin,
                    onSnooze = {
                        RingService.command(this, RingService.ACTION_SNOOZE)
                        finish()
                    },
                    onStop = {
                        RingService.command(this, RingService.ACTION_STOP)
                        finish()
                    }
                )
            }
        }
        lifecycleScope.launch {
            RingService.active.first { it }
            RingService.active.first { !it }
            finish()
        }
    }
}

@Composable
private fun RingScreen(
    title: String,
    sub: String,
    isAlarm: Boolean,
    snoozeMin: Int,
    onSnooze: () -> Unit,
    onStop: () -> Unit
) {
    val p = LocalPal.current
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1000)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .systemBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Text(if (isAlarm) "Wecker" else "Timer", color = p.sub, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = num(88.sp), color = p.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                if (sub.isNotEmpty()) sub else title,
                color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1.2f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton(
                    text = if (isAlarm) "Schlummern $snoozeMin Min." else "+1 Min.",
                    icon = Ic.alarm,
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = onSnooze
                )
                PillButton(
                    text = "Stopp",
                    icon = Ic.close,
                    filled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onStop
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
