package online.bus.bmarian.conexiuniclujmobile.ui.map

import androidx.compose.animation.core.EaseIn
import com.google.gson.JsonArray
import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import online.bus.bmarian.conexiuniclujmobile.logic.SpiderLeg
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

internal class SpiderItem(val stopId: Int, val img: String, val leg: SpiderLeg, val shift: Float)

/** A group's stations spread around the spot they share. */
internal class MapSpider(val key: String, val at: LatLon, val legsImg: String, val items: List<SpiderItem>, private val opened: Long) {
    var closed: Long? = null
        private set

    val images get() = items.map { it.img } + legsImg

    fun close(now: Long) {
        closed = now
    }

    private fun opening(i: Int, now: Long) = Snappy.transform(progress(opened + i * SPIDER_STEP_MS * 1_000_000, now, SPIDER_OPEN_MS))

    private fun folding(now: Long) = closed?.let { 1 - EaseIn.transform(progress(it, now, SPIDER_CLOSE_MS)) } ?: 1f

    private fun reach(i: Int, now: Long) = opening(i, closed?.coerceAtMost(now) ?: now) * folding(now)

    fun moving(now: Long): Boolean =
        closed?.let { progress(it, now, SPIDER_CLOSE_MS) < 1f } ?: (progress(opened, now, SPIDER_OPEN_MS + SPIDER_STEP_MS * (items.size - 1)) < 1f)

    fun item(stopId: Int): SpiderItem? = items.firstOrNull { it.stopId == stopId }

    fun features(now: Long): List<Feature> {
        val point = Point.fromLngLat(at.lon, at.lat)
        val legs = reach(0, now)
        val hub = Feature.fromGeometry(point).apply {
            addStringProperty("img", legsImg)
            addNumberProperty("z", 0)
            addNumberProperty("opacity", legs)
            addNumberProperty("size", legs)
            addProperty("off", JsonArray().apply { add(0); add(0) })
        }
        return listOf(hub) + items.mapIndexed { i, item ->
            val k = reach(i, now)
            val scale = 0.5f + 0.5f * k
            val shift = if (item.leg.left) -item.shift else item.shift
            Feature.fromGeometry(point).apply {
                addNumberProperty("id", item.stopId)
                addStringProperty("img", item.img)
                addNumberProperty("z", 1 + i)
                addNumberProperty("opacity", k)
                addNumberProperty("size", scale)
                addProperty("off", JsonArray().apply { add((item.leg.x * k + shift) / scale); add(item.leg.y * k / scale) })
                if (closed != null) addBooleanProperty("closing", true)
            }
        }
    }
}
