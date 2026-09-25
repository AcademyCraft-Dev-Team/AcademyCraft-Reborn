package org.academy.api.client.gui.text.shape

import org.academy.api.client.gui.text.model.TextBlob
import org.academy.api.client.gui.text.model.TextShapingOptions

object ShapingCache {
    private const val MAX_ENTRIES = 512

    private data class Key(val text: String, val size: Float, val options: TextShapingOptions)

    private val cache = object : LinkedHashMap<Key, TextBlob>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, TextBlob>): Boolean =
            size > MAX_ENTRIES
    }

    fun blob(text: CharSequence, size: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): TextBlob {
        val key = Key(text.toString(), size, options)
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val shaped = TextShaper.shape(key.text, size, options)
        synchronized(cache) { cache[key] = shaped }
        return shaped
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
    }

    fun size(): Int = synchronized(cache) { cache.size }
}
