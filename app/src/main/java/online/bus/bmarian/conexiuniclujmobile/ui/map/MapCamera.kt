package online.bus.bmarian.conexiuniclujmobile.ui.map

import android.content.Context
import androidx.core.content.edit
import org.maplibre.android.camera.CameraPosition
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

// MapLibre's zoom levels: one below the website's Leaflet ones, which count 256 px tiles.
const val DEFAULT_ZOOM = 15.0
// Plain stops show from here in.
const val STOP_ZOOM = 15f
const val MIN_ZOOM = 8.0
const val MAX_ZOOM = 19.0
const val FIT_MAX_ZOOM = 15.0
val DEFAULT_CENTER = LatLon(46.7712, 23.6236)
val CLUJ_SW = LatLon(46.38, 22.75)
val CLUJ_NE = LatLon(47.50, 24.27)

fun isInClujCounty(lat: Double, lon: Double) =
    lat >= CLUJ_SW.lat && lat <= CLUJ_NE.lat && lon >= CLUJ_SW.lon && lon <= CLUJ_NE.lon

fun mercatorX(lon: Double) = (lon + 180.0) / 360.0
fun mercatorY(lat: Double): Double {
    val rad = Math.toRadians(lat)
    return (1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / PI) / 2.0
}

fun lonOf(x: Double) = x * 360.0 - 180.0
fun latOf(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))

/** Where the map opens: the last view, like the website's map.lastView. */
class MapCamera(val center: LatLon, val zoom: Double, val bearing: Double) {
    companion object {
        private const val PREFS = "map"

        fun restore(context: Context): MapCamera {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.contains("lat")) {
                val lat = Double.fromBits(prefs.getLong("lat", 0))
                val lon = Double.fromBits(prefs.getLong("lon", 0))
                // Views saved by the old canvas map counted Leaflet's levels.
                val zoom = if (prefs.contains("glZoom")) prefs.getFloat("glZoom", DEFAULT_ZOOM.toFloat()).toDouble()
                else prefs.getFloat("zoom", DEFAULT_ZOOM.toFloat() + 1) - 1.0
                val bearing = prefs.getFloat("bearing", 0f).toDouble()
                if (lat.isFinite() && lon.isFinite() && isInClujCounty(lat, lon)) {
                    return MapCamera(LatLon(lat, lon), zoom.coerceIn(MIN_ZOOM, MAX_ZOOM), bearing)
                }
            }
            return MapCamera(DEFAULT_CENTER, DEFAULT_ZOOM, 0.0)
        }

        fun save(context: Context, position: CameraPosition) {
            val target = position.target ?: return
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putLong("lat", target.latitude.toRawBits())
                putLong("lon", target.longitude.toRawBits())
                putFloat("glZoom", position.zoom.toFloat())
                putFloat("bearing", position.bearing.toFloat())
            }
        }
    }
}
