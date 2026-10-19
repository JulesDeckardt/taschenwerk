package de.taschenwerk.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

val CURRENCIES: List<Pair<String, String>> = listOf(
    "MXN" to "Mexikanischer Peso", "EUR" to "Euro", "USD" to "US-Dollar", "GBP" to "Britisches Pfund",
    "CHF" to "Schweizer Franken", "CAD" to "Kanadischer Dollar", "JPY" to "Japanischer Yen",
    "CNY" to "Chinesischer Yuan", "THB" to "Thailändischer Baht", "BRL" to "Brasilianischer Real",
    "ARS" to "Argentinischer Peso", "COP" to "Kolumbianischer Peso", "CLP" to "Chilenischer Peso",
    "PEN" to "Peruanischer Sol", "GTQ" to "Guatemaltekischer Quetzal", "CRC" to "Costa-Rica-Colón",
    "AUD" to "Australischer Dollar", "NZD" to "Neuseeland-Dollar", "SEK" to "Schwedische Krone",
    "NOK" to "Norwegische Krone", "DKK" to "Dänische Krone", "PLN" to "Polnischer Złoty",
    "CZK" to "Tschechische Krone", "HUF" to "Ungarischer Forint", "TRY" to "Türkische Lira",
    "INR" to "Indische Rupie", "IDR" to "Indonesische Rupiah", "VND" to "Vietnamesischer Dong",
    "PHP" to "Philippinischer Peso", "MYR" to "Malaysischer Ringgit", "SGD" to "Singapur-Dollar",
    "HKD" to "Hongkong-Dollar", "KRW" to "Südkoreanischer Won", "ZAR" to "Südafrikanischer Rand",
    "AED" to "VAE-Dirham", "ILS" to "Israelischer Schekel", "EGP" to "Ägyptisches Pfund"
)

fun currencyName(code: String): String = CURRENCIES.firstOrNull { it.first == code }?.second ?: code

/** Wechselkurse (Basis USD), mit Zwischenspeicher für den Offline-Fall. */
object Rates {
    var rates by mutableStateOf<Map<String, Double>>(emptyMap())
        private set
    var updated by mutableLongStateOf(0L)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var loaded = false

    private fun parse(o: JSONObject): Map<String, Double> {
        val m = HashMap<String, Double>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            m[k] = o.getDouble(k)
        }
        return m
    }

    fun loadCache(store: Store) {
        if (loaded) return
        loaded = true
        val raw = store.getString("rates") ?: return
        runCatching {
            rates = parse(JSONObject(raw))
            updated = store.getLong("ratesTime")
        }
    }

    suspend fun refresh(store: Store, force: Boolean = false) {
        loadCache(store)
        if (loading) return
        if (!force && rates.isNotEmpty() && System.currentTimeMillis() - updated < 3 * 3600_000L) return
        loading = true
        error = null
        try {
            val m: Map<String, Double> = try {
                val j = Net.getJson("https://open.er-api.com/v6/latest/USD")
                parse(j.getJSONObject("rates"))
            } catch (e: Exception) {
                val j = Net.getJson("https://api.frankfurter.app/latest?from=USD")
                parse(j.getJSONObject("rates")) + ("USD" to 1.0)
            }
            rates = m
            updated = System.currentTimeMillis()
            val o = JSONObject()
            m.forEach { (k, v) -> o.put(k, v) }
            store.putString("rates", o.toString())
            store.putLong("ratesTime", updated)
        } catch (e: Exception) {
            error = "Keine Verbindung – zeige gespeicherte Kurse"
        } finally {
            loading = false
        }
    }

    fun convert(amount: Double, from: String, to: String): Double? {
        val f = rates[from] ?: return null
        val t = rates[to] ?: return null
        return amount / f * t
    }
}
