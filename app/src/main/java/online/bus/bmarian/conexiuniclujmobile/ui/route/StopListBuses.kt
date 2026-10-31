package online.bus.bmarian.conexiuniclujmobile.ui.route

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle
import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.logic.RoutePath
import online.bus.bmarian.conexiuniclujmobile.logic.SWEEP_MS
import online.bus.bmarian.conexiuniclujmobile.logic.VehicleTrack
import online.bus.bmarian.conexiuniclujmobile.logic.fixMs
import online.bus.bmarian.conexiuniclujmobile.logic.paced
import online.bus.bmarian.conexiuniclujmobile.logic.rowAlong
import online.bus.bmarian.conexiuniclujmobile.ui.ModeGlyphs
import online.bus.bmarian.conexiuniclujmobile.ui.map.motionOn
import online.bus.bmarian.conexiuniclujmobile.ui.popped
import online.bus.bmarian.conexiuniclujmobile.ui.rememberPop
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Port of StopListBuses.vue. */
@Stable
class ListBuses {
    var ids by mutableStateOf(emptyList<Int>())
        private set
    private val rows = HashMap<Int, MutableFloatState>()
    var from by mutableFloatStateOf(Float.NaN)
        private set
    var to by mutableFloatStateOf(Float.NaN)
        private set

    fun row(id: Int): Float? = rows[id]?.floatValue

    /** The painted part of [row]'s rail, in row heights from its top. */
    fun paintIn(row: Int): ClosedFloatingPointRange<Float>? {
        if (from.isNaN()) return null
        val top = max(from, row - 0.5f)
        val bottom = min(to, row + 0.5f)
        return if (bottom > top) (top - row + 0.5f)..(bottom - row + 0.5f) else null
    }

    internal fun place(id: Int, row: Float) {
        rows.getOrPut(id) { mutableFloatStateOf(row) }.floatValue = row
    }

    internal fun keep(alive: Set<Int>) {
        rows.keys.retainAll(alive)
        val now = rows.keys.sorted()
        if (now != ids) ids = now
    }

    internal fun paint(lead: Float?, target: Int, grow: Float) {
        from = lead ?: Float.NaN
        to = if (lead == null) Float.NaN else lead + (target - lead) * grow
    }
}

@Composable
fun rememberListBuses(path: RoutePath?, stopsAt: List<Double?>, vehicles: List<Vehicle>, target: Int): ListBuses {
    val buses = remember { ListBuses() }
    val tracks = remember(path) { HashMap<Int, VehicleTrack?>() }
    LaunchedEffect(path, vehicles) {
        if (path == null) return@LaunchedEffect tracks.clear()
        val now = System.currentTimeMillis()
        for (v in vehicles) {
            val track = tracks[v.id] ?: VehicleTrack(path)
            val fixMs = v.fixMs
            tracks[v.id] = track.takeIf { motionOn() && fixMs != null && it.fix(v.latitude, v.longitude, fixMs, v.speed, v.heading, now) }
        }
        tracks.keys.retainAll(vehicles.map { it.id }.toSet())
    }
    LaunchedEffect(path, stopsAt, target, vehicles) {
        if (path == null) return@LaunchedEffect buses.keep(emptySet())
        val byId = vehicles.associateBy { it.id }
        var since: Long? = null
        while (true) {
            withFrameMillis {
                val now = System.currentTimeMillis()
                var lead: Float? = null
                val alive = HashSet<Int>()
                for ((id, track) in tracks) {
                    track?.step(now)
                    val s = track?.s ?: byId[id]?.let { path.locate(it.latitude, it.longitude)?.s } ?: continue
                    val row = rowAlong(stopsAt, s)
                    buses.place(id, row)
                    alive += id
                    if (target >= 0 && row <= target && (lead == null || row > lead)) lead = row
                }
                buses.keep(alive)
                if (lead == null) since = null else if (since == null) since = now
                val grow = since?.takeIf { motionOn() }?.let { EaseInOutQuad.transform(((now - it).toFloat() / SWEEP_MS).coerceIn(0f, 1f)) } ?: 1f
                buses.paint(lead, target, grow)
            }
        }
    }
    return buses
}

/** Where [row] (fractional) sits in the list, from its laid-out stop rows. */
private fun LazyListState.rowCenter(keys: List<String>, row: Float): Float? {
    val items = layoutInfo.visibleItemsInfo
    val i = floor(row).toInt()
    val f = row - i
    val a = items.firstOrNull { it.key == keys.getOrNull(i) }
    val b = items.firstOrNull { it.key == keys.getOrNull(i + 1) }
    val ca = a?.let { it.offset + it.size / 2f }
    val cb = b?.let { it.offset + it.size / 2f }
    return when {
        ca != null && cb != null -> ca + (cb - ca) * f
        ca != null -> ca + f * a.size
        cb != null -> cb - (1 - f) * b.size
        else -> null
    }
}

@Composable
fun StopListBuses(
    buses: ListBuses,
    list: LazyListState,
    keys: List<String>,
    target: Int,
    mark: Color?,
    color: Color,
    mode: Mode,
    modifier: Modifier = Modifier,
) {
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier.clipToBounds().drawBehind {
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == keys.getOrNull(target) }
            if (mark != null && item != null && flash.value > 0f) {
                drawRect(mark.copy(alpha = 0.3f * flash.value), Offset(0f, item.offset.toFloat()), Size(size.width, item.size.toFloat()))
            }
        },
    ) {
        for (id in buses.ids) {
            key(id) {
                val pop = rememberPop()
                LaunchedEffect(target) {
                    var was = buses.row(id)
                    snapshotFlow { buses.row(id) }.collect { row ->
                        val before = was
                        was = row
                        if (row == null || before == null || floor(row) <= floor(before) || !motionOn()) return@collect
                        launch { pop.squash() }
                        if (before < target && row >= target && mark != null) {
                            scope.launch {
                                flash.snapTo(1f)
                                flash.animateTo(0f, tween(paced(955)))
                            }
                        }
                    }
                }
                Box(
                    Modifier
                        .offset {
                            val y = buses.row(id)?.let { list.rowCenter(keys, it) } ?: -RAIL_X.toPx()
                            IntOffset((RAIL_X - BUS / 2).roundToPx(), (y - (BUS / 2).toPx()).roundToInt())
                        }
                        .size(BUS)
                        .popped(pop)
                        .clip(CircleShape)
                        .background(color)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ModeGlyphs.vector(mode), null, Modifier.size(12.dp), tint = Color.White)
                }
            }
        }
    }
}

private val RAIL_X = RAIL_START + RAIL_WIDTH / 2
private val BUS = 22.dp
