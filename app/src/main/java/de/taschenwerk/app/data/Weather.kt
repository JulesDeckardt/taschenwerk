package de.taschenwerk.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import de.taschenwerk.app.ui.Ic
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

data class HourW(val label: String, val temp: Double, val code: Int, val day: Boolean)
data class DayW(val label: String, val max: Double, val min: Double, val code: Int, val sunH: Double)

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
    0, 1 -> if (day) Ic.sun else Ic.moon
    2 -> if (day) Ic.cloudSun else Ic.cloud
    3 -> Ic.cloud
    45, 48 -> Ic.fog
    in 51..67, 80, 81, 82 -> Ic.rain
    in 71..77, 85, 86 -> Ic.snow
    95, 96, 99 -> Ic.storm
    else -> Ic.cloud
}

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
            "&hourly=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunshine_duration" +
            "&timezone=auto&forecast_days=7"
        val j = Net.getJson(url)
        val cur = j.getJSONObject("current")
        val curTime = cur.getString("time")
        val hourly = j.getJSONObject("hourly")
        val hTimes = hourly.getJSONArray("time")
        val hTemp = hourly.getJSONArray("temperature_2m")
        val hCode = hourly.getJSONArray("weather_code")
        val hDay = hourly.getJSONArray("is_day")
        val curHour = curTime.take(13)
        var start = 0
        for (k in 0 until hTimes.length()) {
            if (hTimes.getString(k).take(13) >= curHour) { start = k; break }
        }
        val hours = ArrayList<HourW>()
        for (k in start + 1 until minOf(start + 6, hTimes.length())) {
            val t = LocalDateTime.parse(hTimes.getString(k))
            hours.add(HourW("${t.hour} Uhr", hTemp.getDouble(k), hCode.getInt(k), hDay.getInt(k) == 1))
        }
        val daily = j.getJSONObject("daily")
        val dTimes = daily.getJSONArray("time")
        val dCode = daily.getJSONArray("weather_code")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dSun = daily.getJSONArray("sunshine_duration")
        val days = ArrayList<DayW>()
        for (k in 1 until minOf(6, dTimes.length())) {
            val d = LocalDate.parse(dTimes.getString(k))
            val label = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).replace(".", "")
            days.add(DayW(label, dMax.getDouble(k), dMin.getDouble(k), dCode.getInt(k), dSun.optDouble(k, 0.0) / 3600.0))
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
            high = dMax.getDouble(0),
            low = dMin.getDouble(0),
            sunH = dSun.optDouble(0, 0.0) / 3600.0,
            pressureMsl = cur.optDouble("pressure_msl", Double.NaN),
            hours = hours,
            days = days,
            fetched = System.currentTimeMillis()
        )
    }

    /** Standort bestimmen (falls erlaubt) und Wetter für Heimatort und alle Orte laden. */
    suspend fun refreshAll(ctx: Context, store: Store, force: Boolean = false) {
        val h = home
        if (!force && h != null && System.currentTimeMillis() - h.fetched < 15 * 60_000L) return
        if (loading) return
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
