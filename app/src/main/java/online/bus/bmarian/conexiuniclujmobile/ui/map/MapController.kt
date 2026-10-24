package online.bus.bmarian.conexiuniclujmobile.ui.map

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle

enum class StopHighlight { Green, Purple, Red, Gray, Amber, Favorite }

class MapShape(val tripId: String, val shortName: String, val color: String, val points: List<LatLon>)

data class LatLon(val lat: Double, val lon: Double)

class MapVehicle(
    val id: String,
    val vehicle: Vehicle,
    val shortName: String,
    val color: String,
    val heading: Double,
    /** On the other direction now, counted on coming back. */
    val faded: Boolean = false,
)

class MapPin(val lat: Double, val lon: Double, val label: String)

class NavScene(
    val shapes: List<MapShape>,
    val walks: List<List<LatLon>>,
    val highlights: Map<Int, StopHighlight>,
    val vehicles: List<MapVehicle>,
    val destination: MapPin?,
)

/** The colour of the line the traveller rides: their location arrow takes it while on board. */
data class RideAs(val color: String)

/** top and bottom in px. */
data class NavCamera(val zoom: Double, val tilt: Double, val top: Float, val bottom: Float, val epoch: Int = 0)

/** [padding] left, top, right, bottom in px. */
class CameraView(val center: LatLon, val zoom: Double, val bearing: Double, val padding: DoubleArray?)

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
    var rideAs by mutableStateOf<RideAs?>(null)
    /** During navigation, where to draw the traveller instead of the raw fix. */
    var navMe by mutableStateOf<Fix?>(null)
    var trackingLost by mutableStateOf(false)
    var overviewRequest by mutableIntStateOf(0)
        private set
    internal var handledOverview = 0
    var flyTo by mutableStateOf<Pair<LatLon, Int>?>(null)
        private set
    var centerRequest by mutableIntStateOf(0)
        private set
    var cameraBack by mutableStateOf<Pair<CameraView, Int>?>(null)
        private set
    var bearing by mutableStateOf(0.0)
        internal set
    internal var resting: CameraView? = null

    internal var hasFittedForContent = false
    internal var firstFixHandled = false
    internal var pendingCenter = false

    // Handled requests, so a recreated map doesn't replay them.
    internal var handledShapes = 0
    internal var handledFit = 0
    internal var handledFly = 0
    internal var handledCenter = 0
    internal var handledCameraBack = 0

    private var shapesKey = ""
    private var owner: Any? = null
    /** Screens still shown under the owner, with what they had on the map. */
    private val covered = ArrayList<Pair<Any, Owned>>()

    /** The next screen can take the map before the last one leaves, so only the owner clears it. */
    fun claim(owner: Any) {
        if (this.owner == owner) return
        this.owner?.let { covered += it to save() }
        this.owner = owner
        clear()
    }

    fun release(owner: Any) {
        if (covered.removeAll { it.first == owner }) return
        if (this.owner != owner) return
        // The page behind a cancelled back gesture leaves before the one it covered, which gets its map back.
        val (back, owned) = covered.removeLastOrNull() ?: run {
            this.owner = null
            clear()
            return
        }
        this.owner = back
        restore(owned)
    }

    private class Owned(
        val shapes: List<MapShape>,
        val vehicles: List<MapVehicle>,
        val highlightedStops: Map<Int, StopHighlight>,
        val selectedStopId: Int?,
        val walkingPolylines: List<List<LatLon>>,
        val fitWalking: Boolean,
        val fitNewShapes: Boolean,
        val directionArrowAtStart: Boolean,
        val pinnedLocation: MapPin?,
        val customOrigin: MapPin?,
        val onPinDragged: ((LatLon) -> Unit)?,
        val onOriginDragged: ((LatLon) -> Unit)?,
        val camera: CameraView?,
    )

    private fun save() = Owned(
        shapes, vehicles, highlightedStops, selectedStopId, walkingPolylines, fitWalking, fitNewShapes,
        directionArrowAtStart, pinnedLocation, customOrigin, onPinDragged, onOriginDragged, resting,
    )

    private fun restore(owned: Owned) {
        showShapes(owned.shapes)
        // Fitted when they were first shown; the camera goes back to where it rested.
        handledShapes = shapesVersion
        owned.camera?.let { cameraBack = it to ((cameraBack?.second ?: 0) + 1) }
        vehicles = owned.vehicles
        highlightedStops = owned.highlightedStops
        selectedStopId = owned.selectedStopId
        walkingPolylines = owned.walkingPolylines
        fitWalking = owned.fitWalking
        fitNewShapes = owned.fitNewShapes
        directionArrowAtStart = owned.directionArrowAtStart
        pinnedLocation = owned.pinnedLocation
        customOrigin = owned.customOrigin
        onPinDragged = owned.onPinDragged
        onOriginDragged = owned.onOriginDragged
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
