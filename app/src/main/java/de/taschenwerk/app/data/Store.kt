package de.taschenwerk.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.taschenwerk.app.ui.Scheme
import org.json.JSONArray
import org.json.JSONObject

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    /** Bit 0 = Montag … Bit 6 = Sonntag. 0 = einmalig. */
    val days: Int,
    val enabled: Boolean,
    val label: String = ""
)

data class Step(val name: String, val sec: Int, val pause: Boolean)

data class Workout(
    val id: String,
    val name: String,
    val rounds: Int,
    val prep: Int,
    val steps: List<Step>
) {
    val totalSec: Int get() = prep + rounds * steps.sumOf { it.sec }
}

data class TimerPreset(val name: String, val sec: Int)

data class Place(val name: String, val lat: Double, val lon: Double)

/** Zentraler Speicher für Einstellungen und Listen (SharedPreferences, beobachtbar für Compose). */
class Store private constructor(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("taschenwerk", Context.MODE_PRIVATE)

    var scheme by mutableStateOf(runCatching { Scheme.valueOf(sp.getString("scheme", "GELB")!!) }.getOrDefault(Scheme.GELB))
        private set
    var darkMode by mutableIntStateOf(sp.getInt("darkMode", 0))
        private set
    var mainCur by mutableStateOf(sp.getString("mainCur", "MXN")!!)
        private set
    var secondCur by mutableStateOf(sp.getString("secondCur", "EUR")!!)
        private set
    var snoozeMin by mutableIntStateOf(sp.getInt("snoozeMin", 5))
        private set
    var rampVolume by mutableStateOf(sp.getBoolean("rampVolume", true))
        private set
    var vibrate by mutableStateOf(sp.getBoolean("vibrate", true))
        private set

    val alarms = mutableStateListOf<Alarm>()
    val zones = mutableStateListOf<String>()
    val places = mutableStateListOf<Place>()
    val workouts = mutableStateListOf<Workout>()
    val presets = mutableStateListOf<TimerPreset>()

    init {
        loadAlarms()
        loadZones()
        loadPlaces()
        loadWorkouts()
        loadPresets()
    }

    fun applyScheme(s: Scheme) { scheme = s; sp.edit().putString("scheme", s.name).apply() }
    fun applyDarkMode(m: Int) { darkMode = m; sp.edit().putInt("darkMode", m).apply() }
    fun applyMainCur(c: String) { mainCur = c; sp.edit().putString("mainCur", c).apply() }
    fun applySecondCur(c: String) { secondCur = c; sp.edit().putString("secondCur", c).apply() }
    fun setSnooze(m: Int) { snoozeMin = m; sp.edit().putInt("snoozeMin", m).apply() }
    fun setRamp(b: Boolean) { rampVolume = b; sp.edit().putBoolean("rampVolume", b).apply() }
    fun applyVibrate(b: Boolean) { vibrate = b; sp.edit().putBoolean("vibrate", b).apply() }

    fun getString(key: String): String? = sp.getString(key, null)
    fun putString(key: String, value: String?) { sp.edit().putString(key, value).apply() }
    fun getLong(key: String, def: Long = 0L): Long = sp.getLong(key, def)
    fun putLong(key: String, value: Long) { sp.edit().putLong(key, value).apply() }
    fun getDouble(key: String, def: Double): Double = sp.getString(key, null)?.toDoubleOrNull() ?: def
    fun putDouble(key: String, value: Double) { sp.edit().putString(key, value.toString()).apply() }
    fun putBool(key: String, value: Boolean) { sp.edit().putBoolean(key, value).apply() }
    fun getBool(key: String, def: Boolean): Boolean = sp.getBoolean(key, def)

    // ---------- Wecker ----------
    private fun loadAlarms() {
        alarms.clear()
        val raw = sp.getString("alarms", null)
        if (raw == null) {
            alarms.add(Alarm(1, 6, 30, 0b0011111, false, ""))
            saveAlarms()
            return
        }
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                alarms.add(
                    Alarm(
                        o.getInt("id"), o.getInt("h"), o.getInt("m"), o.optInt("d", 0),
                        o.optBoolean("on", true), o.optString("l", "")
                    )
                )
            }
        }
    }

    fun reloadAlarms() = loadAlarms()

    fun saveAlarms() {
        val arr = JSONArray()
        alarms.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute)
                    .put("d", it.days).put("on", it.enabled).put("l", it.label)
            )
        }
        sp.edit().putString("alarms", arr.toString()).commit()
    }

    fun upsertAlarm(a: Alarm) {
        val list = alarms.filter { it.id != a.id } + a
        val sorted = list.sortedWith(compareBy<Alarm>({ it.hour }, { it.minute }))
        alarms.clear()
        alarms.addAll(sorted)
        saveAlarms()
    }

    fun removeAlarm(id: Int) {
        alarms.removeAll { it.id == id }
        saveAlarms()
    }

    fun newAlarmId(): Int = (alarms.maxOfOrNull { it.id } ?: 0) + 1

    // ---------- Weltzeit ----------
    private fun loadZones() {
        zones.clear()
        val raw = sp.getString("zones", null)
        if (raw == null) {
            zones.addAll(listOf("Europe/Berlin", "America/New_York", "Asia/Bangkok"))
            saveZones()
        } else {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) zones.add(arr.getString(i))
            }
        }
    }

    fun saveZones() {
        val arr = JSONArray()
        zones.forEach { arr.put(it) }
        sp.edit().putString("zones", arr.toString()).apply()
    }

    // ---------- Wetterorte ----------
    private fun loadPlaces() {
        places.clear()
        val raw = sp.getString("places", null)
        if (raw == null) {
            places.add(Place("Berlin", 52.52, 13.405))
            places.add(Place("Bangkok", 13.7563, 100.5018))
            savePlaces()
        } else {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    places.add(Place(o.getString("n"), o.getDouble("a"), o.getDouble("o")))
                }
            }
        }
    }

    fun savePlaces() {
        val arr = JSONArray()
        places.forEach { arr.put(JSONObject().put("n", it.name).put("a", it.lat).put("o", it.lon)) }
        sp.edit().putString("places", arr.toString()).apply()
    }

    // ---------- Workouts ----------
    private fun loadWorkouts() {
        workouts.clear()
        val raw = sp.getString("workouts", null)
        if (raw == null) {
            workouts.add(
                Workout(
                    "tabata", "Tabata", 8, 10,
                    listOf(Step("Arbeit", 20, false), Step("Pause", 10, true))
                )
            )
            workouts.add(
                Workout(
                    "mein", "Mein Workout", 3, 10,
                    listOf(
                        Step("Liegestütze", 30, false), Step("Pause", 20, true),
                        Step("Crunches", 30, false), Step("Pause", 20, true),
                        Step("Squats", 60, false), Step("Pause", 60, true)
                    )
                )
            )
            saveWorkouts()
            return
        }
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val st = o.getJSONArray("s")
                val steps = (0 until st.length()).map {
                    val s = st.getJSONObject(it)
                    Step(s.getString("n"), s.getInt("t"), s.optBoolean("p", false))
                }
                workouts.add(Workout(o.getString("id"), o.getString("n"), o.getInt("r"), o.optInt("prep", 10), steps))
            }
        }
    }

    fun saveWorkouts() {
        val arr = JSONArray()
        workouts.forEach { w ->
            val st = JSONArray()
            w.steps.forEach { st.put(JSONObject().put("n", it.name).put("t", it.sec).put("p", it.pause)) }
            arr.put(JSONObject().put("id", w.id).put("n", w.name).put("r", w.rounds).put("prep", w.prep).put("s", st))
        }
        sp.edit().putString("workouts", arr.toString()).apply()
    }

    fun upsertWorkout(w: Workout) {
        val i = workouts.indexOfFirst { it.id == w.id }
        if (i >= 0) workouts[i] = w else workouts.add(w)
        saveWorkouts()
    }

    fun removeWorkout(id: String) {
        workouts.removeAll { it.id == id }
        saveWorkouts()
    }

    // ---------- Timer-Vorlagen ----------
    private fun loadPresets() {
        presets.clear()
        val raw = sp.getString("presets", null)
        if (raw == null) {
            presets.addAll(listOf(TimerPreset("Tee", 240), TimerPreset("Pasta", 540), TimerPreset("Powernap", 1200)))
            savePresets()
        } else {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    presets.add(TimerPreset(o.getString("n"), o.getInt("t")))
                }
            }
        }
    }

    fun savePresets() {
        val arr = JSONArray()
        presets.forEach { arr.put(JSONObject().put("n", it.name).put("t", it.sec)) }
        sp.edit().putString("presets", arr.toString()).apply()
    }

    companion object {
        @Volatile private var inst: Store? = null
        fun get(ctx: Context): Store = inst ?: synchronized(this) {
            inst ?: Store(ctx.applicationContext).also { inst = it }
        }
    }
}
