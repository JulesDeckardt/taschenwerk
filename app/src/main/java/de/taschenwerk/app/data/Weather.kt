package de.taschenwerk.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import de.taschenwerk.app.ui.Ic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

data class HourW(val label: String, val time: String, val temp: Double, val code: Int, val day: Boolean, val past: Boolean)
data class DayW(
    val label: String, val max: Double, val min: Double, val code: Int, val sunH: Double,
    val dayLenH: Double = 12.0, val precip: Double = 0.0,
    /** mittlere Bewölkung tagsüber in % */
    val cloud: Double = Double.NaN,
    /** max. Regenwahrscheinlichkeit in % */
    val precipProb: Double = Double.NaN
)

data class WeatherData(
    val name: String,
    val temp: Double,
    val feels: Double,
    val dew: Double,
    val humidity: Double,
    val wind: Double,
    val code: Int,
    val day: Boolean,
    val high: Double,
    val low: Double,
    val sunH: Double,
    val pressureMsl: Double,
    val hours: List<HourW>,
    val days: List<DayW>,
    val fetched: Long
)

data class GeoHit(val name: String, val detail: String, val lat: Double, val lon: Double)

fun weatherText(code: Int): String = when (code) {
    0 -> "Klar"
    1 -> "Überwiegend klar"
    2 -> "Teilweise bewölkt"
    3 -> "Bedeckt"
    45, 48 -> "Nebel"
    51, 53, 55, 56, 57 -> "Nieselregen"
    61, 63, 65, 66, 67 -> "Regen"
    71, 73, 75, 77 -> "Schnee"
    80, 81, 82 -> "Regenschauer"
    85, 86 -> "Schneeschauer"
    95, 96, 99 -> "Gewitter"
    else -> "–"
}

fun weatherIcon(code: Int, day: Boolean = true): ImageVector = when (code) {
    0 -> if (day) Ic.sun else Ic.moon
    1 -> if (day) Ic.sunCloudSmall else Ic.moon
    2 -> if (day) Ic.cloudSun else Ic.moonCloud
    3 -> Ic.cloud
    45, 48 -> Ic.fog
    in 51..67, 80, 81, 82 -> Ic.rain
    in 71..77, 85, 86 -> Ic.snow
    95, 96, 99 -> Ic.storm
    else -> Ic.cloud
}

private fun isPrecip(code: Int) = code in 51..67 || code in 71..77 || code in 80..86 || code in 95..99

/**
 * Symbol für einen ganzen Tag: richtet sich nach dem Anteil der Sonnenstunden am Tageslicht.
 * Niederschlag nur, wenn wirklich nennenswert etwas fällt. Liefert (Symbol, Code für die Farbe).
 */
fun dayLook(d: DayW): Pair<ImageVector, Int> {
    // Regen/Gewitter nur, wenn es wirklich wahrscheinlich ist und nennenswert fällt.
    val rainLikely = d.precipProb.isNaN() || d.precipProb >= 50.0
    if (isPrecip(d.code) && d.precip >= 2.0 && rainLikely) return weatherIcon(d.code) to d.code
    // Sonst aus derselben Bewölkung, aus der auch die Sonnenstunden berechnet sind.
    val r = if (!d.cloud.isNaN()) 1.0 - d.cloud / 100.0
    else if (d.dayLenH > 0) d.sunH / d.dayLenH else 0.0
    return when {
        r >= 0.8 -> Ic.sun to 0
        r >= 0.6 -> Ic.sunCloudSmall to 1
        r >= 0.4 -> Ic.cloudSun to 2
        r >= 0.2 -> Ic.cloudSunMost to 2
        d.code == 45 || d.code == 48 -> Ic.fog to 45
        else -> Ic.cloud to 3
    }
}

/** Sonnenstunden als ganze Zahl: ab ,6 aufrunden, bis einschließlich ,5 abrunden. */
fun sunHours(h: Double): Int = if (h.isNaN()) 0 else kotlin.math.ceil(h - 0.5).toInt().coerceAtLeast(0)

object WeatherRepo {
    var home by mutableStateOf<WeatherData?>(null)
    var homeLat by mutableStateOf(19.4326)
    var homeLon by mutableStateOf(-99.1332)
    var homeName by mutableStateOf("Ciudad de México")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val others = mutableStateMapOf<String, WeatherData>()

    private fun f(d: Double) = String.format(Locale.US, "%.4f", d)

    suspend fun fetch(lat: Double, lon: Double, name: String): WeatherData {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${f(lat)}&longitude=${f(lon)}" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m,weather_code,wind_speed_10m,is_day,pressure_msl" +
            "&hourly=temperature_2m,weather_code,is_day,cloud_cover" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunshine_duration,daylight_duration,precipitation_sum,precipitation_probability_max" +
            "&timezone=auto&past_days=1&forecast_days=7"
        val j = Net.getJson(url)
        val cur = j.getJSONObject("current")
        val curTime = cur.getString("time")
        val now = LocalDateTime.parse(curTime)
        val hourly = j.getJSONObject("hourly")
        val hTimes = hourly.getJSONArray("time")
        val hTemp = hourly.getJSONArray("temperature_2m")
        val hCode = hourly.getJSONArray("weather_code")
        val hDay = hourly.getJSONArray("is_day")
        val hCloud = hourly.optJSONArray("cloud_cover")
        val hIndex = HashMap<String, Int>()
        for (k in 0 until hTimes.length()) hIndex[hTimes.getString(k).take(13)] = k

        // Sonnenstunden je Tag aus der stündlichen Bewölkung während des Tageslichts:
        // jede helle Stunde zählt mit ihrem wolkenfreien Anteil. Das deckt sich mit den Symbolen
        // und ist zurückhaltender als Open-Meteos Strahlungs-Schätzung.
        val sunByDate = HashMap<String, Double>()
        val cloudSum = HashMap<String, Double>()
        val cloudN = HashMap<String, Int>()
        if (hCloud != null) {
            for (k in 0 until hTimes.length()) {
                if (hDay.optInt(k, 0) != 1) continue
                val cc = hCloud.optDouble(k, Double.NaN)
                if (cc.isNaN()) continue
                val date = hTimes.getString(k).take(10)
                sunByDate[date] = (sunByDate[date] ?: 0.0) + (1.0 - cc / 100.0).coerceIn(0.0, 1.0)
                cloudSum[date] = (cloudSum[date] ?: 0.0) + cc
                cloudN[date] = (cloudN[date] ?: 0) + 1
            }
        }
        fun sunFor(date: String, fallback: Double) = sunByDate[date] ?: fallback
        fun cloudFor(date: String) = cloudN[date]?.let { n -> (cloudSum[date] ?: 0.0) / n } ?: Double.NaN

        // Tagesverlauf: feste Tageszeiten; der „Tag“ läuft bis 3 Uhr nachts.
        val base = if (now.hour < 3) now.toLocalDate().minusDays(1) else now.toLocalDate()
        val slots = listOf(
            Triple("Morgens", base, 7), Triple("Mittags", base, 12), Triple("Nachm.", base, 16),
            Triple("Abends", base, 20), Triple("Nachts", base.plusDays(1), 3)
        )
        val hours = ArrayList<HourW>()
        for ((label, date, hour) in slots) {
            val t = date.atTime(hour, 0)
            val k = hIndex[t.toString().take(13)] ?: continue
            hours.add(
                HourW(
                    label, "$hour Uhr", hTemp.getDouble(k), hCode.getInt(k), hDay.getInt(k) == 1,
                    past = t.plusMinutes(59).isBefore(now)
                )
            )
        }

        val daily = j.getJSONObject("daily")
        val dTimes = daily.getJSONArray("time")
        val dCode = daily.getJSONArray("weather_code")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dSun = daily.getJSONArray("sunshine_duration")
        val dLen = daily.optJSONArray("daylight_duration")
        val dPre = daily.optJSONArray("precipitation_sum")
        val dProb = daily.optJSONArray("precipitation_probability_max")
        val todayStr = now.toLocalDate().toString()
        var ti = 0
        for (k in 0 until dTimes.length()) if (dTimes.getString(k) == todayStr) { ti = k; break }
        val days = ArrayList<DayW>()
        for (k in ti + 1 until minOf(ti + 6, dTimes.length())) {
            val ds = dTimes.getString(k)
            val d = LocalDate.parse(ds)
            val label = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).replace(".", "")
            days.add(
                DayW(
                    label, dMax.getDouble(k), dMin.getDouble(k), dCode.getInt(k),
                    sunFor(ds, dSun.optDouble(k, 0.0) / 3600.0),
                    (dLen?.optDouble(k, 43200.0) ?: 43200.0) / 3600.0, dPre?.optDouble(k, 0.0) ?: 0.0,
                    cloudFor(ds), dProb?.optDouble(k, Double.NaN) ?: Double.NaN
                )
            )
        }
        return WeatherData(
            name = name,
            temp = cur.getDouble("temperature_2m"),
            feels = cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
            dew = cur.optDouble("dew_point_2m", Double.NaN),
            humidity = cur.optDouble("relative_humidity_2m", Double.NaN),
            wind = cur.optDouble("wind_speed_10m", Double.NaN),
            code = cur.optInt("weather_code", 0),
            day = cur.optInt("is_day", 1) == 1,
            high = dMax.getDouble(ti),
            low = dMin.getDouble(ti),
            sunH = sunFor(todayStr, dSun.optDouble(ti, 0.0) / 3600.0),
            pressureMsl = cur.optDouble("pressure_msl", Double.NaN),
            hours = hours,
            days = days,
            fetched = System.currentTimeMillis()
        )
    }

    /** Standort bestimmen (falls erlaubt) und Wetter für Heimatort und alle Orte laden. */
    // Läuft unabhängig vom Bildschirm weiter, damit ein Tab-Wechsel das Laden nicht abbricht.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    suspend fun refreshAll(ctx: Context, store: Store, force: Boolean = false) {
        val h = home
        if (!force && h != null && System.currentTimeMillis() - h.fetched < 15 * 60_000L) return
        val running = job?.takeIf { it.isActive }
        if (running != null) { running.join(); return }
        val app = ctx.applicationContext
        val j = scope.launch { doRefresh(app, store) }
        job = j
        j.join()
    }

    /** Einen einzelnen Ort laden (z. B. direkt nach dem Hinzufügen). */
    fun refreshPlace(p: Place) {
        scope.launch { runCatching { others[p.name] = fetch(p.lat, p.lon, p.name) } }
    }

    private suspend fun doRefresh(ctx: Context, store: Store) {
        loading = true
        error = null
        try {
            val loc = Loc.current(ctx)
            if (loc != null) {
                homeLat = loc.latitude
                homeLon = loc.longitude
                homeName = Loc.cityName(ctx, loc.latitude, loc.longitude) ?: "Mein Standort"
                store.putDouble("homeLat", homeLat)
                store.putDouble("homeLon", homeLon)
                store.putString("homeName", homeName)
            } else {
                homeLat = store.getDouble("homeLat", homeLat)
                homeLon = store.getDouble("homeLon", homeLon)
                homeName = store.getString("homeName") ?: homeName
            }
            home = fetch(homeLat, homeLon, homeName)
            for (p in store.places.toList()) {
                runCatching { others[p.name] = fetch(p.lat, p.lon, p.name) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Wetter konnte nicht geladen werden"
        } finally {
            loading = false
        }
    }

    suspend fun search(q: String): List<GeoHit> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(q, "UTF-8") + "&count=8&language=de&format=json"
        val j = Net.getJson(url)
        val arr = j.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            GeoHit(
                o.getString("name"),
                listOfNotNull(o.optString("admin1").ifEmpty { null }, o.optString("country").ifEmpty { null }).joinToString(", "),
                o.getDouble("latitude"),
                o.getDouble("longitude")
            )
        }
    }

    suspend fun elevation(lat: Double, lon: Double): Double? = runCatching {
        val j = Net.getJson("https://api.open-meteo.com/v1/elevation?latitude=${f(lat)}&longitude=${f(lon)}")
        j.getJSONArray("elevation").getDouble(0)
    }.getOrNull()
}
