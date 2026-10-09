package online.bus.bmarian.conexiuniclujmobile.data

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import online.bus.bmarian.conexiuniclujmobile.MainActivity
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink

object Shortcuts {
    private const val MAX_STOPS = 2

    private fun stopIntent(context: Context, stopId: Int) = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData("${WearLink.APP_SCHEME}://bus.bmarian.online/stop/$stopId".toUri())

    private fun stopShortcut(context: Context, stopId: Int, name: String) =
        ShortcutInfoCompat.Builder(context, "stop-$stopId")
            .setShortLabel(name.take(24))
            .setLongLabel(name)
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_stop))
            .setIntent(stopIntent(context, stopId))
            .build()

    fun updateFavoriteStops(context: Context, stopIds: List<Int>, stops: List<Stop>) {
        val byId = stops.associateBy { it.stopId }
        val shortcuts = stopIds.mapNotNull { id -> byId[id]?.let { stopShortcut(context, id, it.name) } }.take(MAX_STOPS)
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    fun pinStop(context: Context, stopId: Int, name: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, stopShortcut(context, stopId, name), null) }.getOrDefault(false)
    }
}
