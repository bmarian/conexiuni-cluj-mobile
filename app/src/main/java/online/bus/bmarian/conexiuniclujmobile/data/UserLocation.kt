package online.bus.bmarian.conexiuniclujmobile.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class Fix(val lat: Double, val lon: Double, val accuracyMeters: Float, val bearing: Float? = null, val speed: Float? = null)

const val LOCATION_UPDATE_MS = 10_000L
private const val RECENT_MS = 60 * 60_000L
private const val SAVE_EVERY_MS = 60_000L

object UserLocation {
    private val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun permitted(context: Context) = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun prefs(context: Context) = context.getSharedPreferences("location", Context.MODE_PRIVATE)

    /** Null [city] for anywhere. */
    fun recent(context: Context, city: String? = Cities.current.value): Fix? {
        val prefs = prefs(context)
        if (!permitted(context) || System.currentTimeMillis() - prefs.getLong("at", 0) > RECENT_MS) return null
        return Fix(
            Double.fromBits(prefs.getLong("lat", 0)),
            Double.fromBits(prefs.getLong("lon", 0)),
            prefs.getFloat("accuracy", 0f),
        ).takeIf { city == null || Cities.contains(it.lat, it.lon, city) }
    }

    fun save(context: Context, fix: Fix) {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("at", 0) < SAVE_EVERY_MS) return
        prefs.edit {
            putLong("lat", fix.lat.toRawBits())
            putLong("lon", fix.lon.toRawBits())
            putFloat("accuracy", fix.accuracyMeters)
            putLong("at", now)
        }
    }

    /** Null unless the app is open, or away from [city]. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context, city: String = Cities.current.value): Fix? {
        val open = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (!open || !permitted(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = runCatching { client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() }.getOrNull()
            ?: runCatching { client.lastLocation.await() }.getOrNull()
        return location?.takeIf { Cities.contains(it.latitude, it.longitude, city) }?.let { Fix(it.latitude, it.longitude, it.accuracy) }
    }

    @SuppressLint("MissingPermission")
    fun updates(context: Context, intervalMs: Long = LOCATION_UPDATE_MS): Flow<Fix?> = callbackFlow {
        if (!permitted(context)) {
            send(null)
            awaitClose {}
            return@callbackFlow
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        fun offer(location: Location?) {
            trySend(
                location?.let {
                    Fix(it.latitude, it.longitude, it.accuracy, it.bearing.takeIf { _ -> it.hasBearing() }, it.speed.takeIf { _ -> it.hasSpeed() })
                },
            )
        }
        client.lastLocation.addOnSuccessListener { if (it != null) offer(it) }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) = offer(result.lastLocation)
        }
        client.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs).setMinUpdateIntervalMillis(intervalMs / 2).build(),
            callback,
            Looper.getMainLooper(),
        )
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
