package de.taschenwerk.app

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import de.taschenwerk.app.data.Store
import de.taschenwerk.app.screens.AlarmEditScreen
import de.taschenwerk.app.screens.AltitudeScreen
import de.taschenwerk.app.screens.BreathScreen
import de.taschenwerk.app.screens.CalcTab
import de.taschenwerk.app.screens.ClockTab
import de.taschenwerk.app.screens.SettingsScreen
import de.taschenwerk.app.screens.StartScreen
import de.taschenwerk.app.screens.WeatherScreen
import de.taschenwerk.app.screens.WorkoutEditScreen
import de.taschenwerk.app.screens.WorkoutRunScreen
import de.taschenwerk.app.ui.AppTheme
import de.taschenwerk.app.ui.Ic
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.TwIcon

enum class Tab(val label: String) {
    START("Start"), UHR("Uhr"), RECHNER("Rechner"), WETTER("Wetter"), HOEHE("Höhe"), BREATH("Breath");

    val icon: ImageVector
        get() = when (this) {
            START -> Ic.home
            UHR -> Ic.alarm
            RECHNER -> Ic.calc
            WETTER -> Ic.cloud
            HOEHE -> Ic.mountain
            BREATH -> Ic.breath
        }
}

sealed interface Overlay {
    data object Settings : Overlay
    data class EditWorkout(val id: String?) : Overlay
    data class RunWorkout(val id: String) : Overlay
    data class EditAlarm(val id: Int?) : Overlay
}

class Nav {
    var tab by mutableStateOf(Tab.START)
    var uhrSub by mutableIntStateOf(0)
    var rechnerSub by mutableIntStateOf(0)
    var overlay by mutableStateOf<Overlay?>(null)

    fun go(t: Tab, sub: Int = -1) {
        tab = t
        overlay = null
        if (sub >= 0) {
            if (t == Tab.UHR) uhrSub = sub
            if (t == Tab.RECHNER) rechnerSub = sub
        }
    }
}

@Composable
fun TaschenwerkApp() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    AppTheme(store.scheme, store.darkMode) {
        val p = LocalPal.current
        val view = LocalView.current
        SideEffect {
            val w = (view.context as? Activity)?.window
            if (w != null) {
                val c = WindowCompat.getInsetsController(w, view)
                c.isAppearanceLightStatusBars = !p.dark
                c.isAppearanceLightNavigationBars = !p.dark
            }
        }

        val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 && !store.getBool("askedNotif", false)) {
                store.putBool("askedNotif", true)
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Klingelt gerade etwas (z. B. ohne Benachrichtigungs-Erlaubnis), Stopp-Bildschirm zeigen.
        val ringing by de.taschenwerk.app.alarm.RingService.active.collectAsState()
        LaunchedEffect(ringing) {
            if (ringing) runCatching {
                ctx.startActivity(
                    android.content.Intent(ctx, de.taschenwerk.app.alarm.RingActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        val nav = remember { Nav() }
        BackHandler(enabled = nav.overlay != null || nav.tab != Tab.START) {
            if (nav.overlay != null) nav.overlay = null else nav.tab = Tab.START
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(p.bg)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    when (nav.tab) {
                        Tab.START -> StartScreen(nav)
                        Tab.UHR -> ClockTab(nav)
                        Tab.RECHNER -> CalcTab(nav)
                        Tab.WETTER -> WeatherScreen()
                        Tab.HOEHE -> AltitudeScreen()
                        Tab.BREATH -> BreathScreen()
                    }
                }
                NavBar(nav.tab) { nav.go(it) }
            }
            val ov = nav.overlay
            if (ov != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(p.bg)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    when (ov) {
                        Overlay.Settings -> SettingsScreen(nav)
                        is Overlay.EditWorkout -> WorkoutEditScreen(nav, ov.id)
                        is Overlay.RunWorkout -> WorkoutRunScreen(nav, ov.id)
                        is Overlay.EditAlarm -> AlarmEditScreen(nav, ov.id)
                    }
                }
            }
        }
    }
}

@Composable
fun NavBar(cur: Tab, onSelect: (Tab) -> Unit) {
    val p = LocalPal.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.nav)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(p.line)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp)
        ) {
            Tab.entries.forEach { t ->
                val sel = t == cur
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    TwIcon(t.icon, tint = if (sel) p.ink else p.sub, size = 24.dp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        t.label,
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) p.ink else p.sub,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
