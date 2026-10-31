package vad.dashing.tbox.dexopt

internal object AppDexoptCodec {
    const val FILTER_SPEED = "speed"
    const val FILTER_SPEED_PROFILE = "speed-profile"
    const val FILTER_VERIFY = "verify"
    const val STATUS_RUN_FROM_APK = "run-from-apk"

    /**
     * dex2oat in installd is aborted at ~570 s; Yandex Maps / Navigator (300+ MB APKs) never
     * finished `speed` on the A10 HU and were left at `run-from-apk`.
     */
    const val SPEED_MAX_APK_BYTES = 250L * 1024 * 1024

    /** Filters that leave no hot UI code in the interpreter. `speed-profile` is not enough on HUs that never ran bg-dexopt. */
    private val FULLY_COMPILED_FILTERS = setOf(FILTER_SPEED, "everything")

    private val STATUS_REGEX = Regex("""\[status=([A-Za-z0-9_-]+)]""")
    private val DUMP_PACKAGE_REGEX = Regex("""^ {2}\[([^\]\s]+)]\s*$""")
    private val DUMP_STATUS_REGEX = Regex("""^\s*([A-Za-z0-9_]+): \[status=([A-Za-z0-9_-]+)]""")

    const val DEXOPT_DUMP_COMMAND = "dumpsys package dexopt"

    fun statusCommand(packageName: String): String =
        "dumpsys package ${packageName.trim()} | grep 'status='"

    /**
     * Detached: dex2oat runs in installd and survives the shell (and adbd restart when the
     * session restores TCP off), while the ADB read timeout is far below the ~2 min compile.
     * `trap '' HUP` before forking: on the v1 `shell:` pty the hangup at session close races
     * `nohup` and killed ~40% of background children in bench runs.
     */
    fun compileCommand(packageName: String, filter: String = FILTER_SPEED): String =
        "trap '' HUP; nohup cmd package compile -m $filter -f ${packageName.trim()} </dev/null >/dev/null 2>&1 &"

    /** Compiler filters per ABI from `Dexopt state` lines (`arm64: [status=verify] [reason=boot]`). */
    fun parseFilters(output: String): List<String> =
        STATUS_REGEX.findAll(output).map { it.groupValues[1] }.toList()

    fun isFullyCompiled(output: String): Boolean {
        val filters = parseFilters(output)
        return filters.isNotEmpty() && filters.all { it in FULLY_COMPILED_FILTERS }
    }

    /** Whether a per-package status dump shows [filter] as the primary (first) status. */
    fun hasReachedFilter(output: String, filter: String): Boolean {
        val primary = parseFilters(output).firstOrNull() ?: return false
        return primary == filter || (filter == FILTER_SPEED && primary in FULLY_COMPILED_FILTERS)
    }

    /**
     * Primary compiler status per package from `dumpsys package dexopt`: base APK, `arm64`
     * preferred over other ABIs; secondary dex files are ignored.
     */
    fun parseDexoptDump(output: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var pkg: String? = null
        var inSecondary = false
        var hasArm64 = false
        output.lineSequence().forEach { line ->
            val header = DUMP_PACKAGE_REGEX.find(line)
            if (header != null) {
                pkg = header.groupValues[1]
                inSecondary = false
                hasArm64 = false
                return@forEach
            }
            val current = pkg ?: return@forEach
            if (line.contains("secondary dex", ignoreCase = true)) {
                inSecondary = true
                return@forEach
            }
            if (inSecondary || hasArm64) return@forEach
            val status = DUMP_STATUS_REGEX.find(line) ?: return@forEach
            val isa = status.groupValues[1]
            if (isa == "arm64") {
                result[current] = status.groupValues[2]
                hasArm64 = true
            } else if (current !in result) {
                result[current] = status.groupValues[2]
            }
        }
        return result
    }
}
