package online.bus.bmarian.conexiuniclujmobile.ui.map

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle

enum class StopHighlight { Green, Purple, Red, Gray, Amber }

class MapShape(val tripId: String, val shortName: String, val color: String, val points: List<LatLon>)

data class LatLon(val lat: Double, val lon: Double)

class MapVehicle(
    val id: String,
    val vehicle: Vehicle,
    val shortName: String,
    val color: String,
    val heading: Double,
)

class MapPin(val lat: Double, val lon: Double, val label: String)

class NavScene(
    val shapes: List<MapShape>,
    val walks: List<List<LatLon>>,
    val highlights: Map<Int, StopHighlight>,
    val vehicles: List<MapVehicle>,
    val destination: MapPin?,
)

/** top and bottom in px. */
data class NavCamera(val zoom: Double, val tilt: Double, val top: Float, val bottom: Float, val epoch: Int = 0)

@Stable
class MapController {
    var shapes by mutableStateOf<List<MapShape>>(emptyList())
        private set
    var shapesVersion by mutableIntStateOf(0)
        private set
    var vehicles by mutableStateOf<List<MapVehicle>>(emptyList())
    var highlightedStops by mutableStateOf<Map<Int, StopHighlight>>(emptyMap())
    var selectedStopId by mutableStateOf<Int?>(null)
    var walkingPolylines by mutableStateOf<List<List<LatLon>>>(emptyList())
    var fitWalking by mutableStateOf(false)
    var fitNewShapes = true
    var directionArrowAtStart by mutableStateOf(false)

    var pinnedLocation by mutableStateOf<MapPin?>(null)
    var customOrigin by mutableStateOf<MapPin?>(null)
    var onPinDragged: ((LatLon) -> Unit)? = null
    var onOriginDragged: ((LatLon) -> Unit)? = null

    var fitRequest by mutableIntStateOf(0)
        private set

    var navigation by mutableStateOf<NavScene?>(null)
    var navCamera by mutableStateOf<NavCamera?>(null)
    var trackingLost by mutableStateOf(false)
    var overviewRequest by mutableIntStateOf(0)
        private set
    internal var handledOverview = 0
    var flyTo by mutableStateOf<Pair<LatLon, Int>?>(null)
        private set
    var centerRequest by mutableIntStateOf(0)
        private set

    internal var hasFittedForContent = false
    internal var firstFixHandled = false
    internal var pendingCenter = false

    // Handled requests, so a recreated map doesn't replay them.
    internal var handledShapes = 0
    internal var handledFit = 0
    internal var handledFly = 0
    internal var handledCenter = 0

    private var shapesKey = ""
    private var owner: Any? = null

    /** The next screen can take the map before the last one leaves, so only the owner clears it. */
    fun claim(owner: Any) {
        if (this.owner == owner) return
        this.owner = owner
        clear()
    }

    fun release(owner: Any) {
        if (this.owner != owner) return
        this.owner = null
        clear()
    }

    private fun clear() {
        showShapes(emptyList())
        vehicles = emptyList()
        highlightedStops = emptyMap()
        selectedStopId = null
        walkingPolylines = emptyList()
        fitWalking = false
        fitNewShapes = true
        directionArrowAtStart = false
        pinnedLocation = null
        customOrigin = null
        onPinDragged = null
        onOriginDragged = null
    }

    fun showShapes(list: List<MapShape>) {
        val key = list.joinToString("|") { "${it.tripId}:${it.points.size}" }
        if (key == shapesKey) return
        shapesKey = key
        shapes = list
        shapesVersion++
    }

    fun fitShapes() {
        fitRequest++
    }

    fun flyTo(lat: Double, lon: Double) {
        flyTo = LatLon(lat, lon) to ((flyTo?.second ?: 0) + 1)
    }

    fun centerOnUser() {
        centerRequest++
    }

    fun overview() {
        overviewRequest++
    }
}
