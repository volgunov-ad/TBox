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
    const val ORIGINAL_BACKUP = "$WORK_DIR/AdayoFirewallAppDef.orig.json"
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

    /** True for a file in this app's canonical layout, i.e. not an original worth backing up. */
    fun isGenerated(content: String): Boolean = buildApplyConfig(content, emptyList()) == content

    /**
     * The new file is written next to the target as `.new`, verified on disk and renamed over it,
     * so a short write never leaves a truncated config. wp_on is idempotent and also remounts ro.
     */
    private fun scriptPrologue(): String = """
        |C='$STAGED_CONFIG'
        |S=$CONFIG_PATH
        |B='$ORIGINAL_BACKUP'
        |P=$WP_PROC
        |D=$DISK
        |WP=0
        |wp_on() {
        |    [ "${'$'}WP" = 1 ] || return 0
        |    WP=0
        |    sync
        |    mount -o remount,ro / 2>/dev/null
        |    echo emmc_enable_all_wp > "${'$'}P" 2>/dev/null
        |}
        |fail() { echo "$ERR_MARKER ${'$'}1"; exit 1; }
        |md5() { md5sum "${'$'}1" 2>/dev/null | cut -d' ' -f1; }
        |free_kb() { set -- ${'$'}(df -k "${'$'}1" 2>/dev/null | tail -n 1); echo "${'$'}4"; }
        |has_room() {
        |    F=${'$'}(free_kb "${'$'}1"); Z=${'$'}(stat -c %s "${'$'}2" 2>/dev/null)
        |    case "${'$'}F" in ''|*[!0-9]*) return 0 ;; esac
        |    case "${'$'}Z" in ''|*[!0-9]*) return 0 ;; esac
        |    [ "${'$'}F" -ge ${'$'}((Z / 1024 + 256)) ]
        |}
        |put() {
        |    N="${'$'}1.new"
        |    rm -f "${'$'}N"
        |    has_room "${'$'}{1%/*}" "${'$'}2" || { R=nospace; return 1; }
        |    cat "${'$'}2" > "${'$'}N" || { rm -f "${'$'}N"; R=write; return 1; }
        |    chown root:root "${'$'}N" && chmod 644 "${'$'}N" || { rm -f "${'$'}N"; R=perms; return 1; }
        |    chcon u:object_r:system_file:s0 "${'$'}N" 2>/dev/null
        |    sync
        |    blockdev --flushbufs "${'$'}D" 2>/dev/null
        |    echo 3 > /proc/sys/vm/drop_caches
        |    [ "${'$'}(md5 "${'$'}N")" = "${'$'}3" ] || { rm -f "${'$'}N"; R=verify; return 1; }
        |    mv -f "${'$'}N" "${'$'}1" || { rm -f "${'$'}N"; R=rename; return 1; }
        |    sync
        |    [ "${'$'}(md5 "${'$'}1")" = "${'$'}3" ] || { R=verify; return 1; }
        |}
        |
    """.trimMargin()

    private fun writeAndRestart(marker: String): String = """
        |trap 'wp_on' EXIT
        |trap 'wp_on; exit 1' HUP INT TERM PIPE
        |WP=1
        |echo emmc_disable_all_wp > "${'$'}P" 2>/dev/null || fail wpoff
        |mount -o remount,rw / || fail remountsys
        |put "${'$'}S" "${'$'}SRC" "${'$'}H" || fail "${'$'}R"
        |wp_on
        |trap - EXIT HUP INT TERM PIPE
        |kill $(pidof com.android.car) || fail carpid
        |sleep 12
        |[ -n "$(pidof com.android.car)" ] || fail carback
        |echo "$marker"
        |
    """.trimMargin()

    /** [backupOriginal]: the current file is not ours, keep it once as the restore source. */
    fun applyScript(backupOriginal: Boolean): String = scriptPrologue() + """
        |SRC="${'$'}C"
        |[ -f "${'$'}SRC" ] || fail nostage
        |grep -q '"packageName"' "${'$'}SRC" || fail stage
        |H=${'$'}(md5 "${'$'}SRC")
        |[ -n "${'$'}H" ] || fail stage
        |
    """.trimMargin() + (if (backupOriginal) """
        |if [ ! -f "${'$'}B" ] && [ -f "${'$'}S" ]; then
        |    rm -f "${'$'}B.tmp"
        |    cp "${'$'}S" "${'$'}B.tmp" && [ "${'$'}(md5 "${'$'}B.tmp")" = "${'$'}(md5 "${'$'}S")" ] && mv -f "${'$'}B.tmp" "${'$'}B" || { rm -f "${'$'}B.tmp"; fail backup; }
        |fi
        |
    """.trimMargin() else "") + writeAndRestart(APPLIED_MARKER)

    /** Writes back the original saved on the first apply, the staged stock file otherwise. */
    fun restoreScript(): String = scriptPrologue() + """
        |SRC="${'$'}C"
        |if [ -f "${'$'}B" ] && grep -q '"APPs"' "${'$'}B"; then SRC="${'$'}B"; fi
        |[ -f "${'$'}SRC" ] || fail nostage
        |grep -q '"APPs"' "${'$'}SRC" || fail stage
        |H=${'$'}(md5 "${'$'}SRC")
        |[ -n "${'$'}H" ] || fail stage
        |
    """.trimMargin() + writeAndRestart(RESTORED_MARKER)

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
