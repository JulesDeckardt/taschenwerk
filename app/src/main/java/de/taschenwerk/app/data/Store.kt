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
    val label: String = "",
    /** Weckton-URI, leer = Standard-Weckton des Systems. */
    val sound: String = ""
)

data class Step(val name: String, val sec: Int, val pause: Boolean)

data class Workout(
    val id: String,
    val name: String,
    val rounds: Int,
    val prep: Int,
    val steps: List<Step>
) {
    /** Gesamtdauer wie tatsächlich gelaufen: Pausen am Ende der letzten Runde entfallen. */
    val totalSec: Int get() {
        if (steps.none { !it.pause }) return 0
        val trailing = steps.reversed().takeWhile { it.pause }.sumOf { it.sec }
        return prep + rounds * steps.sumOf { it.sec } - trailing
    }
}

data class TimerPreset(val name: String, val sec: Int)

data class Place(val name: String, val lat: Double, val lon: Double)

/** Zentraler Speicher für Einstellungen und Listen (SharedPreferences, beobachtbar für Compose). */
class Store private constructor(ctx: Context) {
    // Gerätegeschützter Speicher: Wecker funktionieren auch nach einem Neustart vor dem Entsperren.
    private val sp: SharedPreferences = run {
        val dctx = ctx.createDeviceProtectedStorageContext()
        val meta = dctx.getSharedPreferences("tw_meta", Context.MODE_PRIVATE)
        val um = ctx.getSystemService(Context.USER_SERVICE) as android.os.UserManager
        if (!meta.getBoolean("migDp", false) && um.isUserUnlocked) {
            val moved = runCatching { dctx.moveSharedPreferencesFrom(ctx, "taschenwerk") }.getOrDefault(false)
            if (moved) meta.edit().putBoolean("migDp", true).commit()
        }
        dctx.getSharedPreferences("taschenwerk", Context.MODE_PRIVATE)
    }

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
        migrateWorkouts()
        loadPresets()
    }

    /** Einmalig: Tabata-Vorlage raus, Hauptworkout heißt „Workout“, Apnoe-Tabellen dazu. */
    private fun migrateWorkouts() {
        if (sp.getBoolean("mig_apnea1", false)) return
        workouts.removeAll { it.id == "tabata" && it.name == "Tabata" }
        val i = workouts.indexOfFirst { it.id == "mein" }
        if (i >= 0 && workouts[i].name == "Mein Workout") workouts[i] = workouts[i].copy(name = "Workout")
        if (workouts.none { it.id == "apnea_co2" }) workouts.add(apneaCo2())
        if (workouts.none { it.id == "apnea_o2" }) workouts.add(apneaO2())
        saveWorkouts()
        sp.edit().putBoolean("mig_apnea1", true).apply()
    }

    companion object Tables {
        /** CO2-Tabelle für 2:00 Bestzeit: Halten konstant 1:00 (50 %), Atmen 2:00 → 0:30 in 15-s-Schritten. */
        fun apneaCo2(): Workout {
            val rests = listOf(120, 105, 90, 75, 60, 45, 30, 30)
            return Workout("apnea_co2", "Apnoe CO2", 1, 10, rests.flatMap { listOf(Step("Atmen", it, true), Step("Luft anhalten", 60, false)) })
        }

        /** O2-Tabelle für 2:00 Bestzeit: Atmen konstant 2:00, Halten 0:40 → 1:35 (max. 80 %). */
        fun apneaO2(): Workout {
            val holds = listOf(40, 50, 60, 70, 80, 90, 95, 95)
            return Workout("apnea_o2", "Apnoe O2", 1, 10, holds.flatMap { listOf(Step("Atmen", 120, true), Step("Luft anhalten", it, false)) })
        }

        @Volatile private var inst: Store? = null
        fun get(ctx: Context): Store = inst ?: synchronized(this) {
            inst ?: Store(ctx.applicationContext).also { inst = it }
        }
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
                        o.optBoolean("on", true), o.optString("l", ""), o.optString("s", "")
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
                    .put("d", it.days).put("on", it.enabled).put("l", it.label).put("s", it.sound)
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
                    "mein", "Workout", 3, 10,
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

}
