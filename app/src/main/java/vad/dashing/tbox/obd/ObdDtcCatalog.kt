package vad.dashing.tbox.obd

import android.content.Context
import vad.dashing.tbox.BuildConfig
import java.util.concurrent.atomic.AtomicReference

/**
 * Generic SAE-style DTC text catalog, loaded from assets.
 *
 * - `ru` flavor → [ASSET_RU]
 * - `en` flavor → [ASSET_EN]
 *
 * English source: mytrile/obd-trouble-codes (MIT).
 * Russian: glossary translation of that catalog (`tools/translate_obd_dtc_ru.py`).
 */
object ObdDtcCatalog {
    private const val ASSET_EN = "obd/dtc_en.tsv"
    private const val ASSET_RU = "obd/dtc_ru.tsv"

    private val descriptions = AtomicReference<Map<String, String>?>(null)

    fun isLoaded(): Boolean = descriptions.get() != null

    fun size(): Int = descriptions.get()?.size ?: 0

    fun assetPathForFlavor(flavor: String = BuildConfig.FLAVOR): String =
        if (flavor.equals("ru", ignoreCase = true)) ASSET_RU else ASSET_EN

    fun ensureLoaded(context: Context) {
        if (descriptions.get() != null) return
        synchronized(this) {
            if (descriptions.get() != null) return
            val assets = context.applicationContext.assets
            val primary = assetPathForFlavor()
            val map = runCatching {
                assets.open(primary).bufferedReader().use { parseTsv(it.lineSequence()) }
            }.getOrElse {
                if (primary != ASSET_EN) {
                    assets.open(ASSET_EN).bufferedReader().use { parseTsv(it.lineSequence()) }
                } else {
                    throw it
                }
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
