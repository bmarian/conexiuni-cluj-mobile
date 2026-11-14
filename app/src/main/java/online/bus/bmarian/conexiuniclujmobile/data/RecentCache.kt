package online.bus.bmarian.conexiuniclujmobile.data

class RecentCache<T>(private val size: Int) {
    private val values = object : LinkedHashMap<String, T>(size, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?) = this.size > this@RecentCache.size
    }

    operator fun get(key: String): T? = synchronized(values) { values[key] }

    operator fun set(key: String, value: T) {
        synchronized(values) { values[key] = value }
    }
}
