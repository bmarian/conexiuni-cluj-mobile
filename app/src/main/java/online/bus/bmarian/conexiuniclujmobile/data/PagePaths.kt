package online.bus.bmarian.conexiuniclujmobile.data

// Port of utils/pagePaths.ts.
fun NetworkIndex?.stopPath(stopId: Int): String =
    this?.stop(stopId)?.slug?.takeIf { it.isNotEmpty() }?.let { "/statia/$it" } ?: "/stop/$stopId"

fun NetworkIndex?.linePath(routeId: Int, direction: String): String {
    val slug = this?.route(routeId)?.slug?.takeIf { it.isNotEmpty() } ?: return "/route/$routeId/$direction"
    return "/linia/$slug" + if (direction == "1") "/intors" else ""
}
