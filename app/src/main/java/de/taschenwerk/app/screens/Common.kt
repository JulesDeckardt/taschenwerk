package de.taschenwerk.app.screens

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import de.taschenwerk.app.alarm.AlarmScheduler
import de.taschenwerk.app.ui.CircleButton
import de.taschenwerk.app.ui.Ic
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.num
import de.taschenwerk.app.util.two
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDateTime
import kotlin.math.abs

@Composable
fun rememberNow(periodMs: Long = 1000L): State<LocalDateTime> = produceState(LocalDateTime.now(), periodMs) {
    while (true) {
        value = LocalDateTime.now()
        val wait = periodMs - (System.currentTimeMillis() % periodMs)
        delay(wait.coerceAtLeast(16L))
    }
}

@Composable
fun rememberTick(periodMs: Long): State<Long> = produceState(System.currentTimeMillis(), periodMs) {
    while (true) {
        value = System.currentTimeMillis()
        delay(periodMs)
    }
}

/** Kopfzeile für Unterseiten: Zurück, Titel, optionale Aktion. */
@Composable
fun SubHeader(title: String, onBack: () -> Unit, action: String? = null, onAction: () -> Unit = {}) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleButton(Ic.chevLeft, onBack, size = 44.dp, bg = p.bg, desc = "Zurück")
        Text(
            title, color = p.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp), maxLines = 1
        )
        if (action != null) {
            Text(
                action, color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * Zahlenrad: gleitet weich, rastet mittig ein. Größe und Deckkraft der Ziffern
 * werden stufenlos aus dem Abstand zur Mitte berechnet (keine Sprünge).
 */
@Composable
fun NumberWheel(
    count: Int,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 46.dp,
    big: TextUnit = 38.sp,
    width: Dp = 72.dp
) {
    val p = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val total = count * 400
    val startIndex = remember(count) { count * 200 + value - 1 }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val center by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + 1 + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
        }
    }
    val cb by rememberUpdatedState(onChange)
    LaunchedEffect(state) {
        var first = true
        snapshotFlow { center }.distinctUntilChanged().collect {
            cb(((it % count) + count) % count)
            if (!first) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            first = false
        }
    }
    val style = num(big, FontWeight.SemiBold)
    Box(modifier.width(width).height(itemHeight * 3), contentAlignment = Alignment.Center) {
        // dezente Auswahlfläche hinter der mittleren Zeile
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(p.track.copy(alpha = 0.7f))
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(total) { idx ->
                val v = ((idx % count) + count) % count
                Box(
                    Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val li = state.layoutInfo
                            val info = li.visibleItemsInfo.firstOrNull { it.index == idx }
                            val mid = (li.viewportStartOffset + li.viewportEndOffset) / 2f
                            val d = if (info == null) 1f
                            else (abs(info.offset + info.size / 2f - mid) / info.size).coerceAtMost(1.4f)
                            val t = (1f - d).coerceIn(0f, 1f)
                            val sc = 0.62f + 0.38f * t
                            scaleX = sc
                            scaleY = sc
                            alpha = 0.28f + 0.72f * t
                            rotationX = (if (info != null && info.offset + info.size / 2f < mid) 1f else -1f) * d * 18f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(two(v), style = style, color = p.ink, maxLines = 1)
                }
            }
        }
    }
}

object Perms {
    fun notifications(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    fun exact(ctx: Context): Boolean = AlarmScheduler.canExact(ctx)
    fun fullScreen(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }

    fun battery(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    private fun open(ctx: Context, i: Intent) {
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun openNotifications(ctx: Context) = open(
        ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    )

    fun openExact(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 31) {
            open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
        }
    }

    fun openFullScreen(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 34) {
            open(ctx, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.packageName)))
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    fun openBattery(ctx: Context) = open(
        ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
    )

    fun allOk(ctx: Context) = notifications(ctx) && exact(ctx) && fullScreen(ctx) && battery(ctx)
}

/** Weltzeit: bekannte Städte → Zeitzone. */
val CITIES: List<Pair<String, String>> = listOf(
    "Mexiko-Stadt" to "America/Mexico_City", "Cancún" to "America/Cancun", "Tijuana" to "America/Tijuana",
    "Monterrey" to "America/Monterrey", "Guadalajara" to "America/Mexico_City",
    "Berlin" to "Europe/Berlin", "Hamburg" to "Europe/Berlin", "München" to "Europe/Berlin",
    "Wien" to "Europe/Vienna", "Zürich" to "Europe/Zurich", "London" to "Europe/London",
    "Paris" to "Europe/Paris", "Madrid" to "Europe/Madrid", "Barcelona" to "Europe/Madrid",
    "Rom" to "Europe/Rome", "Amsterdam" to "Europe/Amsterdam", "Lissabon" to "Europe/Lisbon",
    "Athen" to "Europe/Athens", "Istanbul" to "Europe/Istanbul", "Moskau" to "Europe/Moscow",
    "Stockholm" to "Europe/Stockholm", "Warschau" to "Europe/Warsaw", "Prag" to "Europe/Prague",
    "New York" to "America/New_York", "Miami" to "America/New_York", "Chicago" to "America/Chicago",
    "Denver" to "America/Denver", "Los Angeles" to "America/Los_Angeles", "San Francisco" to "America/Los_Angeles",
    "Vancouver" to "America/Vancouver", "Toronto" to "America/Toronto", "Havanna" to "America/Havana",
    "Bogotá" to "America/Bogota", "Lima" to "America/Lima", "Santiago de Chile" to "America/Santiago",
    "Buenos Aires" to "America/Argentina/Buenos_Aires", "São Paulo" to "America/Sao_Paulo",
    "Rio de Janeiro" to "America/Sao_Paulo", "Guatemala-Stadt" to "America/Guatemala",
    "San José (CR)" to "America/Costa_Rica", "Panama-Stadt" to "America/Panama", "Honolulu" to "Pacific/Honolulu",
    "Anchorage" to "America/Anchorage", "Reykjavík" to "Atlantic/Reykjavik", "Kairo" to "Africa/Cairo",
    "Kapstadt" to "Africa/Johannesburg", "Nairobi" to "Africa/Nairobi", "Marrakesch" to "Africa/Casablanca",
    "Dubai" to "Asia/Dubai", "Tel Aviv" to "Asia/Jerusalem", "Delhi" to "Asia/Kolkata", "Mumbai" to "Asia/Kolkata",
    "Kathmandu" to "Asia/Kathmandu", "Bangkok" to "Asia/Bangkok", "Hanoi" to "Asia/Ho_Chi_Minh",
    "Bali" to "Asia/Makassar", "Jakarta" to "Asia/Jakarta", "Singapur" to "Asia/Singapore",
    "Kuala Lumpur" to "Asia/Kuala_Lumpur", "Manila" to "Asia/Manila", "Hongkong" to "Asia/Hong_Kong",
    "Peking" to "Asia/Shanghai", "Shanghai" to "Asia/Shanghai", "Seoul" to "Asia/Seoul", "Tokio" to "Asia/Tokyo",
    "Sydney" to "Australia/Sydney", "Melbourne" to "Australia/Melbourne", "Perth" to "Australia/Perth",
    "Auckland" to "Pacific/Auckland"
)

fun cityForZone(zone: String): String =
    CITIES.firstOrNull { it.second == zone }?.first ?: zone.substringAfterLast('/').replace('_', ' ')
