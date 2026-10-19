package de.taschenwerk.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object Loc {
    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun lm(ctx: Context) = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun lastKnown(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val m = lm(ctx)
        return runCatching {
            m.getProviders(true).mapNotNull { p -> runCatching { m.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun single(ctx: Context, provider: String, timeoutMs: Long): Location? {
        val m = lm(ctx)
        if (!runCatching { m.isProviderEnabled(provider) }.getOrDefault(false)) return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { m.removeUpdates(this) }
                        if (cont.isActive) cont.resume(location)
                    }

                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                runCatching {
                    m.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.onFailure { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { runCatching { m.removeUpdates(listener) } }
            }
        }
    }

    /** Aktueller Standort: frisch genug aus dem Cache, sonst neue Messung (Netz, dann GPS). */
    suspend fun current(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val last = lastKnown(ctx)
        if (last != null && System.currentTimeMillis() - last.time < 20 * 60_000L) return last
        val fresh = single(ctx, LocationManager.NETWORK_PROVIDER, 8_000L)
            ?: single(ctx, LocationManager.GPS_PROVIDER, 15_000L)
        return fresh ?: last
    }

    /** GPS-Messung mit Höhe. */
    suspend fun gps(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        return single(ctx, LocationManager.GPS_PROVIDER, 25_000L)
    }

    /** Ortsname mit Viertel, z. B. „Roma Norte, Ciudad de México“. */
    suspend fun cityName(ctx: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val list = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 5).orEmpty()
            val a = list.firstOrNull() ?: return@runCatching null
            val city = a.locality ?: a.subAdminArea ?: a.adminArea
            val hood = list.firstNotNullOfOrNull { it.subLocality?.takeIf { s -> s.isNotBlank() && s != city } }
            listOfNotNull(hood, city).distinct().joinToString(", ").ifBlank { null }
        }.getOrNull()
    }
}
