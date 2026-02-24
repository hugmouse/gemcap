package mysh.dev.gemcap.ui.managers
 
import mysh.dev.gemcap.domain.StableByteArray
 
class EmbeddedMediaCache(
    private val maxBytes: Int,
    private val ttlMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis
) {
    class Entry(
        val data: StableByteArray,
        val mimeType: String,
        var lastAccessMillis: Long,
        val sizeBytes: Int
    )
 
    private val cache = LinkedHashMap<String, Entry>(0, 0.75f, true)
    private var totalBytes = 0
 
    @Synchronized
    fun get(key: String): Entry? {
        val entry = cache[key] ?: return null
        val now = clock()
        if (now - entry.lastAccessMillis > ttlMillis) {
            removeInternal(key, entry)
            return null
        }
        entry.lastAccessMillis = now
        return entry
    }
 
    @Synchronized
    fun put(key: String, data: StableByteArray, mimeType: String) {
        val now = clock()
        val entry = Entry(
            data = data,
            mimeType = mimeType,
            lastAccessMillis = now,
            sizeBytes = data.bytes.size
        )
        cache[key]?.let { existing ->
            totalBytes -= existing.sizeBytes
        }
        cache[key] = entry
        totalBytes += entry.sizeBytes
        prune(now)
    }
 
    @Synchronized
    fun clear() {
        cache.clear()
        totalBytes = 0
    }

    @Synchronized
    private fun prune(now: Long) {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (now - entry.lastAccessMillis > ttlMillis) {
                totalBytes -= entry.sizeBytes
                iterator.remove()
            }
        }
        val lruIterator = cache.entries.iterator()
        while (totalBytes > maxBytes && lruIterator.hasNext()) {
            val entry = lruIterator.next().value
            totalBytes -= entry.sizeBytes
            lruIterator.remove()
        }
    }
 
    @Synchronized
    private fun removeInternal(key: String, entry: Entry) {
        if (cache.remove(key) != null) {
            totalBytes -= entry.sizeBytes
        }
    }
}
