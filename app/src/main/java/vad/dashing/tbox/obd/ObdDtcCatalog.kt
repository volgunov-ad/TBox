package vad.dashing.tbox.obd

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

/**
 * Generic SAE-style DTC text catalog (English), loaded from `assets/obd/dtc_en.tsv`.
 *
 * Source: mytrile/obd-trouble-codes (MIT). Manufacturer-specific codes may be absent.
 */
object ObdDtcCatalog {
    private const val ASSET_PATH = "obd/dtc_en.tsv"

    private val descriptions = AtomicReference<Map<String, String>?>(null)

    fun isLoaded(): Boolean = descriptions.get() != null

    fun size(): Int = descriptions.get()?.size ?: 0

    fun ensureLoaded(context: Context) {
        if (descriptions.get() != null) return
        synchronized(this) {
            if (descriptions.get() != null) return
            val map = context.applicationContext.assets.open(ASSET_PATH).bufferedReader().use { reader ->
                parseTsv(reader.lineSequence())
            }
            descriptions.set(map)
        }
    }

    /** Lookup generic description; null if catalog not loaded or code unknown. */
    fun description(code: String): String? {
        val key = code.trim().uppercase()
        if (key.isEmpty()) return null
        return descriptions.get()?.get(key)
    }

    fun resetForTests() {
        descriptions.set(null)
    }

    fun loadForTests(tsvLines: Sequence<String>) {
        descriptions.set(parseTsv(tsvLines))
    }

    internal fun parseTsv(lines: Sequence<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>(4096)
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val code = line.substring(0, tab).trim().uppercase()
            val desc = line.substring(tab + 1).trim()
            if (code.length != 5 || desc.isEmpty()) continue
            out.putIfAbsent(code, desc)
        }
        return out
    }
}
