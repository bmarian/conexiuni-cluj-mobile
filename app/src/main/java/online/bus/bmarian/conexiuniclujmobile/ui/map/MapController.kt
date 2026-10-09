package online.bus.bmarian.conexiuniclujmobile.ui.map

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle

/** Highlight colours a screen can give a stop; the website's green, purple, red, gray and amber. */
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

/** The navigation screen's map: the trip, in place of the page's own layers while it is up. */
class NavScene(
    val shapes: List<MapShape>,
    val walks: List<List<LatLon>>,
    val highlights: Map<Int, StopHighlight>,
    val vehicles: List<MapVehicle>,
    val destination: MapPin?,
)

/**
 * How the camera follows you while navigating: how close, how tilted, and the space the screen's
 * banner and panel take (px). A new [epoch] re-centres after the map was moved by hand.
 */
data class NavCamera(val zoom: Double, val tilt: Double, val top: Float, val bottom: Float, val epoch: Int = 0)

/**
 * What the screens want on the map, like the website's map store: each screen fills it in
 * while it is open and the one map behind the sheet draws it.
 */
@Stable
class MapController {
    var shapes by mutableStateOf<List<MapShape>>(emptyList())
        private set
    // Bumped when a screen puts different shapes on the map; the map fits them if allowed.
    var shapesVersion by mutableIntStateOf(0)
        private set
    var vehicles by mutableStateOf<List<MapVehicle>>(emptyList())
    var highlightedStops by mutableStateOf<Map<Int, StopHighlight>>(emptyMap())
    // The stop whose page is open: green, bouncing, shown at every zoom.
    var selectedStopId by mutableStateOf<Int?>(null)
    var walkingPolylines by mutableStateOf<List<List<LatLon>>>(emptyList())
    // The planner asks the next walking-line render to fit lines and shapes together.
    var fitWalking by mutableStateOf(false)
    var directionArrowAtStart by mutableStateOf(false)

    // Where the planner's destination and custom origin are; both can be dragged.
    var pinnedLocation by mutableStateOf<MapPin?>(null)
    var customOrigin by mutableStateOf<MapPin?>(null)
    var onPinDragged: ((LatLon) -> Unit)? = null
    var onOriginDragged: ((LatLon) -> Unit)? = null

    var fitRequest by mutableIntStateOf(0)
        private set

    var navigation by mutableStateOf<NavScene?>(null)
    var navCamera by mutableStateOf<NavCamera?>(null)
    // The map was moved by hand while it followed you: the screen offers Re-centre.
    var trackingLost by mutableStateOf(false)
    var overviewRequest by mutableIntStateOf(0)
        private set
    internal var handledOverview = 0
    var flyTo by mutableStateOf<Pair<LatLon, Int>?>(null)
        private set
    var centerRequest by mutableIntStateOf(0)
        private set

    // Per map lifetime, like MapComponent's flags.
    internal var hasFittedForContent = false
    internal var firstFixHandled = false
    internal var pendingCenter = false

    // The requests the map has acted on; a map rebuilt after rotation must not act on them again.
    internal var handledShapes = 0
    internal var handledFit = 0
    internal var handledFly = 0
    internal var handledCenter = 0

    private var shapesKey = ""
    private var owner: Any? = null

    /**
     * A screen takes the map over: whatever the last one left on it goes. The screen that
     * opens may arrive before the one it replaces has gone, so only the owner can clear it.
     * [owner] is one screen's own token: the same stop opened twice is two owners.
     */
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
        directionArrowAtStart = false
        pinnedLocation = null
        customOrigin = null
        onPinDragged = null
        onOriginDragged = null
    }

    /** Puts [list] on the map; the same trips again leave the map where it is. */
    fun showShapes(list: List<MapShape>) {
        val key = list.joinToString("|") { "${it.tripId}:${it.points.size}" }
        if (key == shapesKey) return
        shapesKey = key
        shapes = list
        shapesVersion++
    }

    /** The fit button: zoom to the shapes on the map. */
    fun fitShapes() {
        fitRequest++
    }

    fun flyTo(lat: Double, lon: Double) {
        flyTo = LatLon(lat, lon) to ((flyTo?.second ?: 0) + 1)
    }

    fun centerOnUser() {
        centerRequest++
    }

    /** The whole trip at once, as Maps' overview button shows it. */
    fun overview() {
        overviewRequest++
    }
}
