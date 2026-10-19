package de.taschenwerk.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Net {
    suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Taschenwerk/1.0 (Android)")
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } finally {
            c.disconnect()
        }
    }
}
