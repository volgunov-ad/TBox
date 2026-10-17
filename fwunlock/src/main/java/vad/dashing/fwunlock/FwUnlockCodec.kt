package vad.dashing.fwunlock

/**
 * Permanent Adayo firewall unlock for the A10 head unit (companion of the Data Resize and
 * USB Audio one-shot apps: dangerous /system writes live outside TBox Monitor).
 *
 * com.android.car's CarNetworkService reads /system/etc/adayo/firewall/AdayoFirewallAppDef.json
 * (Gson, lenient — tolerates the OEM /** */ comment), maps APPs entries to
 * PackageInfo{packageName}, resolves each uid via PackageManager and pushes
 * setFirewallAllRules(66/67) — netd builds `--uid-owner <uid> -j RETURN` into the
 * adayo_output_wifi / adayo_output_mobile whitelist chains (unknown packages are skipped
 * silently, there is no "all" wildcard; Hosts is dead code in this firmware). The full
 * parse+apply cycle runs at every com.android.car process start; ADAYO_BOOT_COMPLETED only
 * re-enables the chains. Verified on a real HU with a bind-mounted file: entries
 * {"packageName": "..."} produce live iptables rules. See docs/HEAD_UNITS_DEVICE_NOTES_RU.md.
 */
object FwUnlockCodec {
    const val CONFIG_PATH = "/system/etc/adayo/firewall/AdayoFirewallAppDef.json"
    const val WORK_DIR = "/data/local/tmp/fwunlock"
    const val STAGED_CONFIG = "$WORK_DIR/AdayoFirewallAppDef.tbox.json"
    const val APPLY_SCRIPT = "$WORK_DIR/apply.sh"
    const val RESTORE_SCRIPT = "$WORK_DIR/restore.sh"

    private const val WP_PROC = "/proc/mmc_debug"
    private const val DISK = "/dev/block/mmcblk0"
    const val APPLIED_MARKER = "TBOX_FW_APPLIED"
    const val RESTORED_MARKER = "TBOX_FW_RESTORED"
    const val ERR_MARKER = "TBOX_FW_ERR"

    /** Byte-exact stock file pulled from the Jetour A10 HU (206 bytes, CRLF, tab indent). */
    val STOCK_CONFIG: String = listOf(
        "{",
        "\t\"APPs\":",
        "\t[",
        "\t],",
        "\t",
        "\t/**",
        "\thost: host name",
        "\tpackage: app package name",
        "\ttype: network server type",
        "\tport: network port",
        "\tiptype: ip type ,0:ipv4, 1:ipv6, 2:ipv4&ipv6",
        "\t*/",
        "    \"Hosts\":",
        "    [",
        "\t]",
        "}",
    ).joinToString("\r\n") + "\r\n"

    private val blockComment = Regex("/\\*\\*?.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val entryRegex = Regex("\"packageName\"\\s*:\\s*\"([^\"]+)\"")
    private val packageNameRegex = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")

    /** Whitelisted packages from a config file body (OEM entries included, order preserved). */
    fun parsePackages(content: String): List<String> =
        entryRegex.findAll(content.replace(blockComment, "")).map { it.groupValues[1] }.distinct().toList()

    /** True when no package is whitelisted (the Jetour stock state — all apps blocked). */
    fun isStock(content: String): Boolean = parsePackages(content).isEmpty()

    /**
     * Config with existing + [packages] entries; null when a name is not a valid package
     * (the firmware has no wildcard — "all" would just be skipped) or the merge is empty.
     */
    fun buildApplyConfig(current: String, packages: List<String>): String? {
        val distinct = packages.distinct()
        val valid = distinct.filter { packageNameRegex.matches(it) }
        if (valid.size != distinct.size) return null
        val merged = (parsePackages(current) + valid).distinct()
        if (merged.isEmpty()) return null
        val body = merged.joinToString(",\r\n") { "\t\t{\"packageName\": \"$it\"}" }
        return "{\r\n\t\"APPs\":\r\n\t[\r\n$body\r\n\t],\r\n\t\"Hosts\":\r\n\t[\r\n\t]\r\n}\r\n"
    }

    fun applyScript(): String = """
        |C='$STAGED_CONFIG'
        |S=$CONFIG_PATH
        |P=$WP_PROC
        |D=$DISK
        |wp_on() { echo emmc_enable_all_wp > "${'$'}P" 2>/dev/null; }
        |fail() { echo "$ERR_MARKER ${'$'}1"; exit 1; }
        |[ -f "${'$'}C" ] || fail nostage
        |grep -q '"packageName"' "${'$'}C" || fail stage
        |echo emmc_disable_all_wp > "${'$'}P" 2>/dev/null || fail wpoff
        |trap 'wp_on' EXIT
        |mount -o remount,rw / || fail remountsys
        |cp "${'$'}C" "${'$'}S" || fail copysys
        |chown root:root "${'$'}S" && chmod 644 "${'$'}S"
        |chcon u:object_r:system_file:s0 "${'$'}S" 2>/dev/null
        |sync
        |blockdev --flushbufs "${'$'}D" 2>/dev/null
        |echo 3 > /proc/sys/vm/drop_caches
        |H1=$(md5sum "${'$'}C" | cut -d' ' -f1)
        |H2=$(md5sum "${'$'}S" | cut -d' ' -f1)
        |[ "${'$'}H1" = "${'$'}H2" ] || fail verify
        |wp_on
        |trap - EXIT
        |kill $(pidof com.android.car) || fail carpid
        |sleep 12
        |[ -n "$(pidof com.android.car)" ] || fail carback
        |echo "$APPLIED_MARKER"
        |
    """.trimMargin()

    fun restoreScript(): String = """
        |C='$STAGED_CONFIG'
        |S=$CONFIG_PATH
        |P=$WP_PROC
        |D=$DISK
        |wp_on() { echo emmc_enable_all_wp > "${'$'}P" 2>/dev/null; }
        |fail() { echo "$ERR_MARKER ${'$'}1"; exit 1; }
        |[ -f "${'$'}C" ] || fail nostage
        |grep -q '"APPs"' "${'$'}C" || fail stage
        |echo emmc_disable_all_wp > "${'$'}P" 2>/dev/null || fail wpoff
        |trap 'wp_on' EXIT
        |mount -o remount,rw / || fail remountsys
        |cp "${'$'}C" "${'$'}S" || fail copysys
        |chown root:root "${'$'}S" && chmod 644 "${'$'}S"
        |chcon u:object_r:system_file:s0 "${'$'}S" 2>/dev/null
        |sync
        |blockdev --flushbufs "${'$'}D" 2>/dev/null
        |echo 3 > /proc/sys/vm/drop_caches
        |H1=$(md5sum "${'$'}C" | cut -d' ' -f1)
        |H2=$(md5sum "${'$'}S" | cut -d' ' -f1)
        |[ "${'$'}H1" = "${'$'}H2" ] || fail verify
        |wp_on
        |trap - EXIT
        |kill $(pidof com.android.car) || fail carpid
        |sleep 12
        |[ -n "$(pidof com.android.car)" ] || fail carback
        |echo "$RESTORED_MARKER"
        |
    """.trimMargin()

    sealed interface Outcome {
        data object Applied : Outcome
        data object Restored : Outcome
        data class Failed(val code: String) : Outcome
    }

    fun parseOutcome(output: String): Outcome {
        val lines = output.lineSequence().map { it.trim() }.toList()
        if (APPLIED_MARKER in lines) return Outcome.Applied
        if (RESTORED_MARKER in lines) return Outcome.Restored
        val code = lines.firstOrNull { it.startsWith(ERR_MARKER) }
            ?.removePrefix(ERR_MARKER)?.trim()?.ifEmpty { null }
        return Outcome.Failed(code ?: "failed")
    }
}
