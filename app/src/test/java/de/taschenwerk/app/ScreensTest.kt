package de.taschenwerk.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import de.taschenwerk.app.data.Alarm
import de.taschenwerk.app.data.Store
import de.taschenwerk.app.data.WeatherData
import de.taschenwerk.app.data.WeatherRepo
import de.taschenwerk.app.data.hourLook
import de.taschenwerk.app.screens.DateHeader
import de.taschenwerk.app.screens.HomeCfg
import de.taschenwerk.app.screens.MiniDock
import de.taschenwerk.app.screens.NavCfg
import de.taschenwerk.app.screens.TimeTile
import de.taschenwerk.app.screens.WeatherTile
import de.taschenwerk.app.ui.AppTheme
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.Scheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Bildschirmfotos der Startseiten-Kacheln bei normaler und großer Systemschrift –
 * zum Prüfen, dass nichts abgeschnitten wird.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi")
class ScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setup() {
        WeatherRepo.homeName = "Ciudad de México"
        WeatherRepo.home = WeatherData(
            name = "Ciudad de México", temp = 21.0, feels = 21.0, dew = 5.0, humidity = 40.0, wind = 5.0,
            code = 0, day = true, high = 24.0, low = 10.0, sunH = 10.0, pressureMsl = 1015.0,
            look = hourLook(0, 0.0, 0.0, true), hours = emptyList(), days = emptyList(), fetched = System.currentTimeMillis()
        )
    }

    @Composable
    private fun Page(scale: Float, mode: String) {
        val ctx = LocalContext.current
        val store = Store.get(ctx)
        if (store.alarms.isEmpty()) store.upsertAlarm(Alarm(1, 7, 40, 0, true))
        HomeCfg.load(store)
        NavCfg.load(store)
        NavCfg.mode = mode
        val nav = Nav()
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
            AppTheme(Scheme.GELB, 1) {
                val p = LocalPal.current
                Column(
                    Modifier
                        .width(360.dp)
                        .background(p.bg)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DateHeader(nav)
                    WeatherTile(nav, store) {}
                    TimeTile(nav, store)
                }
                if (mode == NavCfg.MINI) Column(Modifier.width(360.dp).background(p.bg)) { MiniDock(nav) }
            }
        }
    }

    private fun shot(name: String, scale: Float, mode: String) {
        setup()
        // Uhren und Ticker laufen endlos – Zeit nur von Hand weiterdrehen
        rule.mainClock.autoAdvance = false
        rule.setContent { Column { Page(scale, mode) } }
        repeat(20) { rule.mainClock.advanceTimeByFrame() }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun normal() = shot("start-schrift-100", 1.0f, NavCfg.MINI)
    @Test fun gross() = shot("start-schrift-130", 1.3f, NavCfg.MINI)
    @Test fun sehrGross() = shot("start-schrift-150", 1.5f, NavCfg.MINI)
}
