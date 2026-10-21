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
import kotlin.math.cos
import kotlin.math.sin

data class HourW(
    val label: String, val time: String, val temp: Double, val code: Int, val day: Boolean, val past: Boolean,
    val look: Look = hourLook(code, Double.NaN, Double.NaN, day)
)

/** Was angezeigt wird: Symbol, Farbcode (für weatherTint) und Text. */
data class Look(val icon: ImageVector, val tint: Int, val text: String)
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
    val look: Look,
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
 * Symbol und Text für einen Zeitpunkt (aktuell oder eine Stunde) – aus der Bewölkung,
 * Niederschlag nur, wenn wirklich etwas fällt. Gleiche Stufen wie bei den Tagen.
 */
fun hourLook(code: Int, cloud: Double, precip: Double, day: Boolean): Look {
    val storm = code in 95..99
    val snow = code in 71..77 || code in 85..86
    val cc = if (!cloud.isNaN()) cloud else when (code) {
        0 -> 5.0; 1 -> 30.0; 2 -> 50.0; 3 -> 90.0; else -> 70.0
    }
    val wet = storm || (isPrecip(code) && (precip.isNaN() || precip >= 0.5))
    if (wet) {
        val sunny = day && cc < 60
        return when {
            storm -> Look(if (sunny) Ic.stormSun else Ic.storm, 95, "Gewitter")
            snow -> Look(Ic.snow, 71, "Schnee")
            sunny -> Look(Ic.showerSun, 80, "Regenschauer")
            !precip.isNaN() && precip < 1.0 -> Look(Ic.rain, 61, "Leichter Regen")
            else -> Look(Ic.rain, 61, "Regen")
        }
    }
    if (code == 45 || code == 48) return Look(Ic.fog, 45, "Nebel")
    return when {
        cc < 20 -> Look(if (day) Ic.sun else Ic.moon, 0, "Klar")
        cc < 40 -> Look(if (day) Ic.sunCloudSmall else Ic.moon, 1, if (day) "Überwiegend sonnig" else "Überwiegend klar")
        cc < 60 -> Look(if (day) Ic.cloudSun else Ic.moonCloud, 2, "Teils bewölkt")
        cc < 80 -> Look(if (day) Ic.cloudSunMost else Ic.moonCloud, 2, "Überwiegend bewölkt")
        else -> Look(Ic.cloud, 3, "Bedeckt")
    }
}

/**
 * Symbol für einen ganzen Tag: richtet sich nach dem Anteil der Sonnenstunden am Tageslicht.
 * Niederschlag nur, wenn wirklich nennenswert etwas fällt. Liefert (Symbol, Code für die Farbe).
 */
fun dayLook(d: DayW): Pair<ImageVector, Int> {
    // Regen/Gewitter nur, wenn es wirklich wahrscheinlich ist und nennenswert fällt.
    val rainLikely = d.precipProb.isNaN() || d.precipProb >= 50.0
    // Anteil Sonne am Tag – aus derselben Bewölkung, aus der auch die Sonnenstunden berechnet sind.
    val r = if (d.dayLenH > 0 && !d.sunH.isNaN()) (d.sunH / d.dayLenH).coerceIn(0.0, 1.0)
    else if (!d.cloud.isNaN()) 1.0 - d.cloud / 100.0 else 0.0
    if (isPrecip(d.code) && d.precip >= 2.0 && rainLikely) {
        val storm = d.code in 95..99
        val snow = d.code in 71..77 || d.code in 85..86
        return when {
            snow -> Ic.snow to 71
            r >= 0.4 -> (if (storm) Ic.stormSun else Ic.showerSun) to 80   // sonnig mit Schauer/Gewitter
            storm -> Ic.storm to 95
            else -> Ic.rain to 61
        }
    }
    return when {
        r >= 0.8 -> Ic.sun to 0
        r >= 0.6 -> Ic.sunCloudSmall to 1
        r >= 0.4 -> Ic.cloudSun to 2
        r >= 0.2 -> Ic.cloudSunMost to 2
        else -> Ic.cloud to 3   // Nebel nie für den ganzen Tag – nur in einzelnen Stunden
    }
}

/**
 * Wirksame Bewölkung in %: tiefe Wolken voll, mittlere zu 80 %, hohe (dünne Schleier-)Wolken nur zu 20 % –
 * durch Schleierwolken scheint die Sonne praktisch ungehindert.
 * Ebenen überlagern sich zufällig. Fehlen die Ebenen, wird die Gesamtbewölkung genommen.
 */
fun effCloud(low: Double, mid: Double, high: Double, total: Double): Double {
    if (low.isNaN() || mid.isNaN() || high.isNaN()) return total
    val clear = (1 - low / 100.0) * (1 - 0.8 * mid / 100.0) * (1 - 0.2 * high / 100.0)
    return ((1 - clear) * 100.0).coerceIn(0.0, 100.0)
}

/**
 * Bewölkung fürs Symbol: Die Modell-Bewölkung ist in Mexiko-Stadt (2240 m) oft viel zu hoch –
 * „tiefe Wolken“ bei vollem Sonnenschein. Scheint laut Strahlung die Sonne (Anteil f der Stunde),
 * wird die Bewölkung entsprechend auf höchstens 35 % („überwiegend sonnig“) gedrückt.
 */
fun iconCloud(cloud: Double, sunFrac: Double, day: Boolean): Double {
    if (!day || sunFrac.isNaN() || cloud.isNaN()) return cloud
    val f = sunFrac.coerceIn(0.0, 1.0)
    return cloud * (1 - f) + minOf(cloud, 35.0) * f
}

/** Sonnenhöhe in Radiant (NOAA-Näherung) für einen UTC-Zeitpunkt. */
private fun solarElevation(lat: Double, lon: Double, utc: java.time.ZonedDateTime): Double {
    val n = utc.dayOfYear
    val hr = utc.hour + utc.minute / 60.0
    val g = 2 * Math.PI / 365 * (n - 1 + (hr - 12) / 24)
    val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
        0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
    val eot = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
    val ha = Math.toRadians((hr * 60 + eot + 4 * lon) / 4 - 180)
    val la = Math.toRadians(lat)
    return kotlin.math.asin(sin(la) * sin(decl) + cos(la) * cos(decl) * cos(ha))
}

/** Direkte Strahlung auf die Horizontale bei wolkenlosem Himmel (Meinel-Modell mit Höhenkorrektur), W/m². */
private fun clearSkyDirect(lat: Double, lon: Double, elevKm: Double, utc: java.time.ZonedDateTime): Double {
    val e = solarElevation(lat, lon, utc)
    if (e <= 0.02) return 0.0
    val am = 1 / (sin(e) + 0.50572 * Math.pow(Math.toDegrees(e) + 6.07995, -1.6364))
    val h = elevKm.coerceIn(0.0, 5.0)
    val dni = 1361 * ((1 - 0.14 * h) * Math.pow(0.7, Math.pow(am, 0.678)) + 0.14 * h)
    return dni * sin(e)
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
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m,weather_code,wind_speed_10m,is_day,pressure_msl,cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,precipitation" +
            "&hourly=temperature_2m,weather_code,is_day,cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,precipitation,sunshine_duration,direct_radiation" +
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
        val hCloudTot = hourly.optJSONArray("cloud_cover")
        val hLow = hourly.optJSONArray("cloud_cover_low")
        val hMid = hourly.optJSONArray("cloud_cover_mid")
        val hHigh = hourly.optJSONArray("cloud_cover_high")
        // Wirksame Bewölkung je Stunde (hohe Schleierwolken zählen nur halb)
        val hCloud = org.json.JSONArray().apply {
            for (k in 0 until hTimes.length()) put(
                effCloud(
                    hLow?.optDouble(k, Double.NaN) ?: Double.NaN, hMid?.optDouble(k, Double.NaN) ?: Double.NaN,
                    hHigh?.optDouble(k, Double.NaN) ?: Double.NaN, hCloudTot?.optDouble(k, Double.NaN) ?: Double.NaN
                ).let { if (it.isNaN()) org.json.JSONObject.NULL else it }
            )
        }
        val hPre = hourly.optJSONArray("precipitation")
        // Sonnenanteil je Stunde aus der direkten Sonnenstrahlung im Verhältnis zum wolkenlosen Himmel.
        // Werte gelten für die Stunde *vor* dem Zeitstempel. Unter 20 % des Klarhimmel-Werts: keine Sonne,
        // ab 70 %: volle Sonne, dazwischen anteilig. Ohne Strahlungswerte: Open-Meteos Sonnenscheindauer.
        val hSun = hourly.optJSONArray("sunshine_duration")
        val hDir = hourly.optJSONArray("direct_radiation")
        val offset = java.time.ZoneOffset.ofTotalSeconds(j.optInt("utc_offset_seconds", 0))
        val elevKm = j.optDouble("elevation", 0.0).let { if (it.isNaN()) 0.0 else it / 1000.0 }
        val fracCache = HashMap<Int, Double>()
        fun sunFrac(k: Int): Double = fracCache.getOrPut(k) {
            if (k < 0 || k >= hTimes.length()) return@getOrPut Double.NaN
            val dr = if (hDir == null || hDir.isNull(k)) Double.NaN else hDir.optDouble(k, Double.NaN)
            if (!dr.isNaN()) {
                val end = LocalDateTime.parse(hTimes.getString(k)).atOffset(offset).toZonedDateTime()
                    .withZoneSameInstant(java.time.ZoneOffset.UTC)
                var cs = 0.0
                for (m in listOf(55L, 45L, 35L, 25L, 15L, 5L)) cs += clearSkyDirect(lat, lon, elevKm, end.minusMinutes(m))
                cs /= 6
                if (cs < 20) 0.0 else ((dr / cs - 0.2) / 0.5).coerceIn(0.0, 1.0)
            } else if (hSun != null && !hSun.isNull(k)) {
                (hSun.optDouble(k, Double.NaN) / 3600.0).coerceIn(0.0, 1.0)
            } else Double.NaN
        }
        val hIndex = HashMap<String, Int>()
        for (k in 0 until hTimes.length()) hIndex[hTimes.getString(k).take(13)] = k

        // Sonnenstunden je Tag: aus der Sonneneinstrahlung (WMO: direkte Strahlung > 120 W/m²).
        // Die Modell-Bewölkung liegt hier oft daneben (tiefe Wolken bei strahlendem Sonnenschein),
        // die Strahlung trifft den echten Sonnenschein deutlich besser. Nur wenn sie fehlt: aus der Bewölkung.
        val sunByDate = HashMap<String, Double>()
        val cloudSum = HashMap<String, Double>()
        val cloudN = HashMap<String, Int>()
        val hasSun = hSun != null || hDir != null
        for (k in 0 until hTimes.length()) {
            val date = hTimes.getString(k).take(10)
            if (hasSun) {
                // Wert um 7:00 gilt für 6–7 Uhr → dem Datum der Startstunde zuordnen
                val f = sunFrac(k)
                if (!f.isNaN()) {
                    val d0 = LocalDateTime.parse(hTimes.getString(k)).minusMinutes(30).toLocalDate().toString()
                    sunByDate[d0] = (sunByDate[d0] ?: 0.0) + f
                }
            }
            if (hDay.optInt(k, 0) != 1) continue
            val cc = iconCloud(hCloud.optDouble(k, Double.NaN), sunFrac(k + 1), true)
            if (cc.isNaN()) continue
            if (!hasSun) sunByDate[date] = (sunByDate[date] ?: 0.0) + (1.0 - cc / 100.0).coerceIn(0.0, 1.0)
            cloudSum[date] = (cloudSum[date] ?: 0.0) + cc
            cloudN[date] = (cloudN[date] ?: 0) + 1
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
                    past = t.plusMinutes(59).isBefore(now),
                    look = hourLook(
                        hCode.getInt(k), iconCloud(hCloud.optDouble(k, Double.NaN), sunFrac(k + 1), hDay.getInt(k) == 1),
                        hPre?.optDouble(k, Double.NaN) ?: Double.NaN, hDay.getInt(k) == 1
                    )
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
            look = hourLook(
                cur.optInt("weather_code", 0),
                iconCloud(
                    effCloud(
                        cur.optDouble("cloud_cover_low", Double.NaN), cur.optDouble("cloud_cover_mid", Double.NaN),
                        cur.optDouble("cloud_cover_high", Double.NaN), cur.optDouble("cloud_cover", Double.NaN)
                    ),
                    hIndex[now.withMinute(0).plusHours(1).toString().take(13)]?.let { sunFrac(it) } ?: Double.NaN,
                    cur.optInt("is_day", 1) == 1
                ),
                cur.optDouble("precipitation", Double.NaN), cur.optInt("is_day", 1) == 1
            ),
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
