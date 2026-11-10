package online.bus.bmarian.conexiuniclujmobile.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.BigDecimal
import java.math.RoundingMode

/** The record rules of protocol.md §4: a strict reader and the canonical writer. Port of utils/sync/records.ts. */
object SyncRecords {
    const val CITY_CAP = 50
    const val NEWS_CAP = 200
    const val DEVICE_CAP = 10
    const val RECENT_CAP = 10
    const val GROUP_LINES_CAP = 10
    const val HOME_CAP = 20
    private const val MAX_INT = Int.MAX_VALUE.toLong()
    private const val GROUPS_WITH_STOPS = 2L
    val validId = 1L..MAX_INT
    val LAT = 43.6..48.3
    val LON = 20.2..29.8
    val KINDS = listOf("web", "android", "wear")

    data class Spot(val name: String, val lat: Double, val lon: Double)
    data class Place(val name: String, val label: String, val lat: Double, val lon: Double, val origin: Spot? = null)
    data class Line(val route: Long, val dir: String)
    data class Ride(val route: Long, val dir: String, val from: Long? = null, val to: Long? = null) {
        val hasStops get() = from != null || to != null
    }
    data class City(val stops: List<Long>, val lines: List<Line>, val places: List<Place>, val followed: List<String>) {
        val isEmpty get() = stops.isEmpty() && lines.isEmpty() && places.isEmpty() && followed.isEmpty()
    }
    data class News(val seen: List<Long>)
    sealed interface RecentItem {
        data class Stop(val id: Long) : RecentItem
        data class Line(val id: Long) : RecentItem
        data class Spot(val place: Place) : RecentItem
        data class Group(val lines: List<SyncRecords.Line>) : RecentItem
    }
    data class Recents(val recent: List<RecentItem>)
    data class Group(val lines: List<Ride>, val name: String, val to: Spot? = null)
    data class Groups(val groups: List<Group>)
    data class Home(val order: List<String>, val collapsed: List<String>)
    data class Options(val colorfulLines: Boolean, val city: String, val journeyVoice: Boolean?, val fullScreenAlarms: Boolean?, val traffic: Boolean?)
    data class Device(val id: String, val kind: String, val label: String, val pub: String)
    data class Devices(val devices: List<Device>)
    data class Settings(
        val theme: String,
        val locale: String,
        val autoCenterOnMe: Boolean,
        val autoFitMap: Boolean,
        val showVehicleExtras: Boolean,
        val greenFriday: Boolean,
    )

    val emptyCity = City(emptyList(), emptyList(), emptyList(), emptyList())

    /** Newer: a later version this device leaves alone. */
    sealed interface Read<out T> {
        data class Ok<T>(val value: T) : Read<T>
        data object Refused : Read<Nothing>
        data object Newer : Read<Nothing>
    }

    private class Refuse : Exception()

    private fun refuse(): Nothing = throw Refuse()

    private val strict = Json

    fun forbidden(cp: Int) = cp <= 0x1f || cp in 0x7f..0x9f || cp == 0x061c || cp == 0x200b || cp == 0x200e || cp == 0x200f ||
        cp in 0x2028..0x202e || cp == 0x2060 || cp in 0x2066..0x2069 || cp == 0xfeff || cp == 0xfffd || cp == 0xfffe || cp == 0xffff ||
        cp in 0xd800..0xdfff

    fun blankEnd(cp: Int) = cp == 0x20 || cp == 0xa0 || cp == 0x1680 || cp in 0x2000..0x200a || cp == 0x202f || cp == 0x205f || cp == 0x3000

    fun validText(value: String, min: Int, max: Int): Boolean {
        val points = value.codePoints().toArray()
        if (points.size !in min..max || points.any(::forbidden)) return false
        return points.isEmpty() || (!blankEnd(points.first()) && !blankEnd(points.last()))
    }

    private fun text(e: JsonElement?, min: Int, max: Int): String {
        val p = e as? JsonPrimitive ?: refuse()
        if (!p.isString || !validText(p.content, min, max)) refuse()
        return p.content
    }

    private fun number(e: JsonElement?): Double {
        val p = e as? JsonPrimitive ?: refuse()
        if (p.isString) refuse()
        val value = p.content.toDoubleOrNull() ?: refuse()
        if (!value.isFinite()) refuse()
        return value
    }

    private fun integer(e: JsonElement?, lo: Long, hi: Long): Long {
        val value = number(e)
        if (value != Math.floor(value) || value < lo || value > hi) refuse()
        return value.toLong()
    }

    fun roundCoordinate(x: Double) = Math.floor(x * 1e6 + 0.5) / 1e6

    private fun coordinate(e: JsonElement?, range: ClosedFloatingPointRange<Double>): Double {
        val value = number(e)
        if (value !in range || roundCoordinate(value) != value) refuse()
        return value
    }

    private fun bool(e: JsonElement?): Boolean {
        val p = e as? JsonPrimitive ?: refuse()
        if (p.isString) refuse()
        return p.booleanOrNull ?: refuse()
    }

    private fun list(e: JsonElement?, cap: Int): JsonArray {
        val array = e as? JsonArray ?: refuse()
        if (array.size > cap) refuse()
        return array
    }

    private fun obj(e: JsonElement?, vararg keys: String): JsonObject {
        val o = e as? JsonObject ?: refuse()
        if (o.size != keys.size || keys.any { it !in o }) refuse()
        return o
    }

    private fun <T> unique(items: List<T>, id: (T) -> String) {
        if (items.map(id).toSet().size != items.size) refuse()
    }

    fun stopId(id: Long) = id.toString()
    fun lineId(l: Line) = "${l.route}/${l.dir}"
    fun placeId(p: Place) = listOf(p.lat, p.lon, p.origin?.lat ?: "", p.origin?.lon ?: "").joinToString(",")
    fun deviceId(d: Device) = d.id
    fun recentId(r: RecentItem) = when (r) {
        is RecentItem.Stop -> "stop:${r.id}"
        is RecentItem.Line -> "line:${r.id}"
        is RecentItem.Spot -> "place:${placeId(r.place)}"
        is RecentItem.Group -> "group:${linesId(r.lines)}"
    }
    private fun linesId(lines: List<Line>) = lines.joinToString(",", transform = ::lineId)
    private fun rideId(l: Ride) = "${l.route}/${l.dir}" + if (l.hasStops) "/${l.from ?: ""}/${l.to ?: ""}" else ""
    fun groupId(g: Group) = g.lines.joinToString(",", transform = ::rideId)

    /** Parses, checks the rules and the canonical form; the version comes first, so a later shape is newer, not refused. */
    private fun <T> read(raw: String, parse: (JsonElement) -> T, write: (T) -> String, latest: Long = 1): Read<T> = try {
        val record = strict.parseToJsonElement(raw)
        if (integer((record as? JsonObject)?.get("v"), 1, MAX_INT) > latest) Read.Newer
        else {
            val value = parse(record)
            if (write(value) == raw) Read.Ok(value) else Read.Refused
        }
    } catch (_: Refuse) {
        Read.Refused
    } catch (_: IllegalArgumentException) {
        Read.Refused
    }

    private fun parsePlace(f: List<JsonElement>): Place {
        if (f.size != 4 && f.size != 7) refuse()
        return Place(
            name = text(f[0], 1, 100),
            label = text(f[1], 0, 40),
            lat = coordinate(f[2], LAT),
            lon = coordinate(f[3], LON),
            origin = if (f.size == 7) parseSpot(f.drop(4)) else null,
        )
    }

    private fun parseSpot(f: List<JsonElement>) = Spot(text(f[0], 1, 100), coordinate(f[1], LAT), coordinate(f[2], LON))

    private fun <T> parseGroupLines(e: JsonElement?, parse: (JsonElement) -> T, route: (T) -> Long): List<T> {
        val lines = list(e, GROUP_LINES_CAP).map(parse)
        if (lines.isEmpty()) refuse()
        unique(lines) { route(it).toString() }
        return lines
    }

    private fun parseRide(l: JsonElement): Ride {
        val f = list(l, 4)
        if (f.size != 2 && f.size != 4) refuse()
        val line = parseLine(JsonArray(f.take(2)))
        if (f.size == 2) return Ride(line.route, line.dir)
        val from = integer(f[2], 0, MAX_INT)
        val to = integer(f[3], 0, MAX_INT)
        if (from == to) refuse()
        return Ride(line.route, line.dir, from.takeIf { it > 0 }, to.takeIf { it > 0 })
    }

    private fun parseLine(l: JsonElement): Line {
        val pair = list(l, 2)
        val dir = (pair.getOrNull(1) as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (pair.size != 2 || (dir != "0" && dir != "1")) refuse()
        return Line(integer(pair[0], 1, MAX_INT), dir)
    }

    private fun parseCity(record: JsonElement): City {
        val m = obj(record, "v", "stops", "lines", "places", "followed")
        val stops = list(m["stops"], CITY_CAP).map { integer(it, 1, MAX_INT) }
        val lines = list(m["lines"], CITY_CAP).map(::parseLine)
        val places = list(m["places"], CITY_CAP).map { parsePlace(list(it, 7)) }
        val followed = list(m["followed"], CITY_CAP).map { text(it, 1, 16) }
        unique(stops, ::stopId)
        unique(lines, ::lineId)
        unique(places, ::placeId)
        unique(followed) { it }
        return City(stops, lines, places, followed)
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun coord(x: Double): String = BigDecimal(x).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun <T> joined(items: List<T>, write: (T) -> String) = items.joinToString(",", "[", "]", transform = write)

    private fun spotFields(s: Spot?) = s?.let { listOf(quote(it.name), coord(it.lat), coord(it.lon)) }.orEmpty()

    private fun writePlace(p: Place) = (listOf(quote(p.name), quote(p.label), coord(p.lat), coord(p.lon)) + spotFields(p.origin)).joinToString(",", "[", "]")

    private fun writeLine(l: Line) = "[${l.route},${quote(l.dir)}]"

    private fun writeRide(l: Ride) = if (l.hasStops) "[${l.route},${quote(l.dir)},${l.from ?: 0},${l.to ?: 0}]" else writeLine(Line(l.route, l.dir))

    fun writeCity(r: City): String =
        "{\"v\":1,\"stops\":" + joined(r.stops, ::stopId) +
            ",\"lines\":" + joined(r.lines, ::writeLine) +
            ",\"places\":" + joined(r.places, ::writePlace) +
            ",\"followed\":" + joined(r.followed, ::quote) + "}"

    fun readCity(raw: String): Read<City> = read(raw, ::parseCity, ::writeCity)

    val emptyNews = News(emptyList())

    fun newestSeen(ids: Collection<Long>) = ids.sortedDescending().take(NEWS_CAP)

    private fun parseNews(record: JsonElement): News {
        val seen = list(obj(record, "v", "seen")["seen"], NEWS_CAP).map { integer(it, 1, MAX_INT) }
        if (seen.zipWithNext().any { (a, b) -> b >= a }) refuse()
        return News(seen)
    }

    fun writeNews(r: News): String = "{\"v\":1,\"seen\":" + joined(r.seen, ::stopId) + "}"

    fun readNews(raw: String): Read<News> = read(raw, ::parseNews, ::writeNews)

    val emptyRecents = Recents(emptyList())

    private fun parseRecents(record: JsonElement): Recents {
        val recent = list(obj(record, "v", "recent")["recent"], RECENT_CAP).map { item ->
            val f = list(item, 8)
            val kind = (f.getOrNull(0) as? JsonPrimitive)?.takeIf { it.isString }?.content
            when {
                kind == "place" -> RecentItem.Spot(parsePlace(f.drop(1)))
                kind == "stop" && f.size == 2 -> RecentItem.Stop(integer(f[1], 1, MAX_INT))
                kind == "line" && f.size == 2 -> RecentItem.Line(integer(f[1], 1, MAX_INT))
                kind == "group" && f.size == 2 -> RecentItem.Group(parseGroupLines(f[1], ::parseLine, Line::route))
                else -> refuse()
            }
        }
        unique(recent, ::recentId)
        return Recents(recent)
    }

    fun writeRecents(r: Recents): String = "{\"v\":1,\"recent\":" + joined(r.recent) {
        when (it) {
            is RecentItem.Stop -> "[\"stop\",${it.id}]"
            is RecentItem.Line -> "[\"line\",${it.id}]"
            is RecentItem.Spot -> "[\"place\"," + writePlace(it.place).drop(1)
            is RecentItem.Group -> "[\"group\"," + joined(it.lines, ::writeLine) + "]"
        }
    } + "}"

    fun readRecents(raw: String): Read<Recents> = read(raw, ::parseRecents, ::writeRecents)

    val emptyGroups = Groups(emptyList())
    val emptyHome = Home(emptyList(), emptyList())

    private fun parseGroups(record: JsonElement): Groups {
        val groups = list(obj(record, "v", "groups")["groups"], CITY_CAP).map { item ->
            val f = list(item, 5)
            if (f.size != 2 && f.size != 5) refuse()
            Group(parseGroupLines(f[0], ::parseRide, Ride::route), text(f[1], 0, 40), if (f.size == 5) parseSpot(f.drop(2)) else null)
        }
        unique(groups, ::groupId)
        return Groups(groups)
    }

    /** Version 1 while no line has stops, so apps that only know it keep syncing. */
    fun writeGroups(r: Groups): String {
        val v = if (r.groups.any { g -> g.lines.any { it.hasStops } }) GROUPS_WITH_STOPS else 1
        return "{\"v\":$v,\"groups\":" + joined(r.groups) {
            (listOf(joined(it.lines, ::writeRide), quote(it.name)) + spotFields(it.to)).joinToString(",", "[", "]")
        } + "}"
    }

    fun readGroups(raw: String): Read<Groups> = read(raw, ::parseGroups, ::writeGroups, GROUPS_WITH_STOPS)

    private val ID_TEXT = Regex("^[A-Za-z0-9_-]{21}[AQgw]$")

    private fun parseDevices(record: JsonElement): Devices {
        val m = obj(record, "v", "devices")
        val devices = list(m["devices"], DEVICE_CAP).map { item ->
            val d = obj(item, "id", "kind", "label", "pub")
            val id = (d["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(ID_TEXT::matches) ?: refuse()
            val kind = (d["kind"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it in KINDS } ?: refuse()
            val pub = (d["pub"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: refuse()
            SyncCrypto.fromB64(pub, SyncCrypto.SPKI_SIZE)?.let(SyncCrypto::publicKey) ?: refuse()
            Device(id, kind, text(d["label"], 1, 40), pub)
        }
        unique(devices, ::deviceId)
        return Devices(devices)
    }

    fun writeDevices(r: Devices): String = "{\"v\":1,\"devices\":" + joined(r.devices) {
        "{\"id\":${quote(it.id)},\"kind\":${quote(it.kind)},\"label\":${quote(it.label)},\"pub\":${quote(it.pub)}}"
    } + "}"

    fun readDevices(raw: String): Read<Devices> = read(raw, ::parseDevices, ::writeDevices)

    private fun parseSettings(record: JsonElement): Settings {
        val m = obj(record, "v", "theme", "locale", "autoCenterOnMe", "autoFitMap", "showVehicleExtras", "regions")
        val theme = (m["theme"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it in listOf("light", "dark", "system") } ?: refuse()
        val locale = (m["locale"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it == "ro" || it == "en" } ?: refuse()
        val cluj = obj(obj(m["regions"], "cluj")["cluj"], "showGreenFriday")
        return Settings(theme, locale, bool(m["autoCenterOnMe"]), bool(m["autoFitMap"]), bool(m["showVehicleExtras"]), bool(cluj["showGreenFriday"]))
    }

    fun writeSettings(r: Settings): String =
        "{\"v\":1,\"theme\":${quote(r.theme)},\"locale\":${quote(r.locale)},\"autoCenterOnMe\":${r.autoCenterOnMe}" +
            ",\"autoFitMap\":${r.autoFitMap},\"showVehicleExtras\":${r.showVehicleExtras}" +
            ",\"regions\":{\"cluj\":{\"showGreenFriday\":${r.greenFriday}}}}"

    fun readSettings(raw: String): Read<Settings> = read(raw, ::parseSettings, ::writeSettings)

    private fun maybeBool(e: JsonElement?): Boolean? = if (e is JsonNull) null else bool(e)

    private val optionKeys = arrayOf("v", "colorfulLines", "city", "journeyVoice", "fullScreenAlarms")

    private fun parseOptions(record: JsonElement, withTraffic: Boolean = true): Options {
        val m = if (withTraffic) obj(record, *optionKeys, "traffic") else obj(record, *optionKeys)
        return Options(
            bool(m["colorfulLines"]), text(m["city"], 0, 40), maybeBool(m["journeyVoice"]), maybeBool(m["fullScreenAlarms"]),
            if (withTraffic) maybeBool(m["traffic"]) else null,
        )
    }

    fun writeOptions(r: Options, withTraffic: Boolean = true): String =
        "{\"v\":1,\"colorfulLines\":${r.colorfulLines},\"city\":${quote(r.city)}" +
            ",\"journeyVoice\":${r.journeyVoice},\"fullScreenAlarms\":${r.fullScreenAlarms}" +
            (if (withTraffic) ",\"traffic\":${r.traffic}}" else "}")

    /** A record from before traffic existed reads as traffic unknown; the next write adds the key. */
    fun readOptions(raw: String): Read<Options> =
        when (val current = read(raw, { parseOptions(it) }, { writeOptions(it) })) {
            Read.Refused -> read(raw, { parseOptions(it, false) }, { writeOptions(it, false) })
            else -> current
        }

    private fun sections(e: JsonElement?): List<String> = list(e, HOME_CAP).map { text(it, 1, 20) }.also { ids -> unique(ids) { it } }

    private fun parseHome(record: JsonElement): Home {
        val m = obj(record, "v", "order", "collapsed")
        return Home(sections(m["order"]), sections(m["collapsed"]))
    }

    fun writeHome(r: Home): String = "{\"v\":1,\"order\":" + joined(r.order, ::quote) + ",\"collapsed\":" + joined(r.collapsed, ::quote) + "}"

    fun readHome(raw: String): Read<Home> = read(raw, ::parseHome, ::writeHome)
}
