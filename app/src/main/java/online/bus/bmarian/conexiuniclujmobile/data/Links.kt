package online.bus.bmarian.conexiuniclujmobile.data

import android.net.Uri
import androidx.core.net.toUri
import online.bus.bmarian.conexiuniclujmobile.BuildConfig

/** A page's address under its city; [path] starts with "/". */
object Links {
    const val SCHEME = "conexiunicluj"
    const val HOST = "bus.bmarian.online"

    fun web(path: String, city: String = Cities.current.value) = "${BuildConfig.SITE_ORIGIN}/$city$path"

    fun app(path: String, city: String = Cities.current.value): Uri = "$SCHEME://$HOST/$city$path".toUri()
}
