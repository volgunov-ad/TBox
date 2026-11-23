package vad.dashing.store.dexopt

/**
 * ART compiler-filter helpers. Commands mirror TBox Monitor's `AppDexoptCodec` (bench-tested
 * on A9/A10); see docs/HEAD_UNITS_DEVICE_NOTES_RU.md §3.
 */
object DexoptCodec {
    const val FILTER_SPEED = "speed"

    /**
     * dex2oat in installd is aborted at ~570 s; Yandex Maps / Navigator (300+ MB APKs) never
     * finished `speed` on the A10 HU and were left at `run-from-apk`.
     */
    const val SPEED_MAX_APK_BYTES = 250L * 1024 * 1024

    /** `speed-profile` is not enough on HUs that never ran bg-dexopt (empty profile). */
    private val FULLY_COMPILED_FILTERS = setOf(FILTER_SPEED, "everything")

    private val STATUS_REGEX = Regex("""\[status=([A-Za-z0-9_-]+)]""")
    private val OAT_FILTER_KEY = "compiler-filter\u0000".toByteArray(Charsets.US_ASCII)

    fun statusCommand(packageName: String): String =
        "dumpsys package ${packageName.trim()} | grep 'status='"

    /**
     * Detached: dex2oat runs in installd and survives the shell and an adbd restart, while
     * compiling takes minutes. `trap '' HUP` before forking: on the v1 `shell:` pty the hangup
     * at session close races `nohup` and killed ~40% of background children in bench runs.
     */
    fun compileCommand(packageName: String): String =
        "trap '' HUP; nohup cmd package compile -m $FILTER_SPEED -f ${packageName.trim()} </dev/null >/dev/null 2>&1 &"

    /** Compiler filters per ABI from `dumpsys package <pkg>` (`arm64: [status=verify] [reason=boot]`). */
    fun parseFilters(output: String): List<String> =
        STATUS_REGEX.findAll(output).map { it.groupValues[1] }.toList()

    fun isFullyCompiled(filter: String): Boolean = filter in FULLY_COMPILED_FILTERS

    fun isFullyCompiled(filters: List<String>): Boolean =
        filters.isNotEmpty() && filters.all { it in FULLY_COMPILED_FILTERS }

    /**
     * The OAT header (start of `.rodata` in `base.odex`) holds a NUL-separated key/value store
     * with `compiler-filter`. Readable by any app, so no ADB session just to show the status.
     */
    fun parseOatCompilerFilter(bytes: ByteArray, length: Int = bytes.size): String? {
        val key = OAT_FILTER_KEY
        val end = length.coerceAtMost(bytes.size)
        var i = 0
        outer@ while (i <= end - key.size) {
            for (k in key.indices) {
                if (bytes[i + k] != key[k]) {
                    i++
                    continue@outer
                }
            }
            val start = i + key.size
            var stop = start
            while (stop < end && bytes[stop] != 0.toByte()) stop++
            if (stop == end || stop == start || stop - start > 32) return null
            val value = String(bytes, start, stop - start, Charsets.US_ASCII)
            return value.takeIf { it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
        }
        return null
    }
}
