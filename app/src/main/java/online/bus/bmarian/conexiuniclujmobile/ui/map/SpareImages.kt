package online.bus.bmarian.conexiuniclujmobile.ui.map

import android.app.ActivityManager
import android.content.Context

// Android's bitmap cache guideline.
private const val MEMORY_CLASS_SHARE = 8

/** Unused map images kept for zooming back, the least recently dropped evicted first. */
internal class SpareImages(context: Context) {
    private val budget = context.getSystemService(ActivityManager::class.java).memoryClass * 1024L * 1024 / MEMORY_CLASS_SHARE
    private val spare = LinkedHashMap<String, Int>()
    private var bytes = 0L

    fun take(name: String) {
        spare.remove(name)?.let { bytes -= it }
    }

    fun drop(name: String, size: Int): List<String> {
        take(name)
        spare[name] = size
        bytes += size
        val evicted = ArrayList<String>()
        val oldest = spare.entries.iterator()
        while (bytes > budget && oldest.hasNext()) {
            val (gone, goneSize) = oldest.next()
            oldest.remove()
            bytes -= goneSize
            evicted += gone
        }
        return evicted
    }

    fun clear() {
        spare.clear()
        bytes = 0
    }
}
