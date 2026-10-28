package online.bus.bmarian.conexiuniclujmobile.ui.map

import android.content.Context
import androidx.core.content.edit
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.cityPrefs
import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import org.maplibre.android.camera.CameraPosition

const val DEFAULT_ZOOM = 15.0
const val STOP_ZOOM = 15.0
const val CLUSTER_ZOOM_STEP = 0.25
const val MIN_ZOOM = 8.0
const val MAX_ZOOM = 19.0
const val FIT_MAX_ZOOM = 15.0
// Cluj's, until the server's list first loads.
val DEFAULT_CENTER = LatLon(46.7712, 23.6236)

class MapCamera(val city: String, val center: LatLon, val zoom: Double) {
    companion object {
        private const val PREFS = "map"

        /** Where [city] was last seen, else its centre. */
        fun restore(context: Context, city: String): MapCamera {
            val prefs = cityPrefs(context, PREFS, city)
            if (prefs.contains("lat")) {
                val lat = Double.fromBits(prefs.getLong("lat", 0))
                val lon = Double.fromBits(prefs.getLong("lon", 0))
                // Views saved by the old canvas map used Leaflet levels.
                val zoom = if (prefs.contains("glZoom")) prefs.getFloat("glZoom", DEFAULT_ZOOM.toFloat()).toDouble()
                else prefs.getFloat("zoom", DEFAULT_ZOOM.toFloat() + 1) - 1.0
                if (lat.isFinite() && lon.isFinite() && Cities.contains(lat, lon, city)) {
                    return MapCamera(city, LatLon(lat, lon), zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
                }
            }
            return MapCamera(city, Cities.info(city)?.let { LatLon(it.lat, it.lon) } ?: DEFAULT_CENTER, DEFAULT_ZOOM)
        }

        fun save(context: Context, position: CameraPosition) {
            val target = position.target ?: return
            val city = Cities.current.value
            // A flight between cities cut short can stop over another one.
            if (!Cities.contains(target.latitude, target.longitude, city)) return
            cityPrefs(context, PREFS, city).edit {
                putLong("lat", target.latitude.toRawBits())
                putLong("lon", target.longitude.toRawBits())
                putFloat("glZoom", position.zoom.toFloat())
            }
        }
    }
}
