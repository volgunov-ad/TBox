package vad.dashing.winpatch

/**
 * Permanent install of the patched launcher-window Android Auto (p5) and CarPlayView (p3)
 * APKs into the A10 head unit /system (companion of the Data Resize / USB Audio / Firewall
 * one-shot apps: dangerous /system writes live outside TBox Monitor).
 *
 * The patched APKs are bundled in assets; originals are backed up once into
 * /data/local/tmp/winpatch (adopting the pre-existing manual backups in /data/local/tmp).
 * CarPlayManagerConfig.json is never touched — wired and wireless AA/CarPlay pairing keep
 * the stock behavior. See docs/LAUNCHER_WINDOW_AA_CARPLAY_RU.md.
 */
object WinPatchCodec {
    const val WORK_DIR = "/data/local/tmp/winpatch"
    const val APPLY_SCRIPT = "$WORK_DIR/apply.sh"
    const val RESTORE_SCRIPT = "$WORK_DIR/restore.sh"
    const val CARPLAY_CONFIG = "/system/etc/adayo/carplay/CarPlayManagerConfig.json"

    const val APPLIED_MARKER = "TBOX_WINPATCH_APPLIED"
    const val RESTORED_MARKER = "TBOX_WINPATCH_RESTORED"
    const val ERR_MARKER = "TBOX_WINPATCH_ERR"

    private const val WP_PROC = "/proc/mmc_debug"
    private const val DISK = "/dev/block/mmcblk0"

    data class Target(
        val id: String,
        val title: String,
        val patchLabel: String,
        val systemPath: String,
        val backupPath: String,
        val legacyBackupPath: String,
        val assetName: String,
        val stockMd5: String,
        val patchedMd5: String,
        val processName: String,
    ) {
        val rootStagePath: String get() = "$WORK_DIR/$assetName"
    }

    val ANDROID_AUTO = Target(
        id = "aa",
        title = "Android Auto",
        patchLabel = "p5",
        systemPath = "/system/app/AndroidAuto/AndroidAuto.apk",
        backupPath = "$WORK_DIR/AndroidAuto.orig.apk",
        legacyBackupPath = "/data/local/tmp/AndroidAuto_orig_backup.apk",
        assetName = "AndroidAuto_p5.apk",
        stockMd5 = "821cf2db51435747bd974a81fd52d1bb",
        patchedMd5 = "f91c1def77c9105db35eed2dc0732c27",
        processName = "com.adayo.androidauto",
    )

    val CAR_PLAY = Target(
        id = "cp",
        title = "CarPlay",
        patchLabel = "p3",
        systemPath = "/system/app/CarPlayView/CarPlayView.apk",
        backupPath = "$WORK_DIR/CarPlayView.orig.apk",
        legacyBackupPath = "/data/local/tmp/CarPlayView_orig_backup.apk",
        assetName = "CarPlayView_p3.apk",
        stockMd5 = "2718803663e016a969e6d2fa0d2b4c16",
        patchedMd5 = "e9c8d958178875c0712f6bd0d39ae8a2",
        processName = "com.adayo.carplay.view",
    )

    val TARGETS = listOf(ANDROID_AUTO, CAR_PLAY)

    enum class FileState { Stock, Patched, Unknown, Missing }

    private val md5LineRegex = Regex("^([0-9a-fA-F]{32})\\s+(\\S+)\\s*$")

    /** `md5sum` output lines ("hash  path") to path → lowercase md5; stderr noise is skipped. */
    fun parseMd5Map(output: String): Map<String, String> =
        output.lineSequence()
            .mapNotNull { line -> md5LineRegex.find(line)?.let { it.groupValues[2] to it.groupValues[1].lowercase() } }
            .toMap()

    fun fileState(md5: String?, target: Target): FileState = when {
        md5 == null -> FileState.Missing
        md5.equals(target.stockMd5, ignoreCase = true) -> FileState.Stock
        md5.equals(target.patchedMd5, ignoreCase = true) -> FileState.Patched
        else -> FileState.Unknown
    }

    fun targetById(id: String): Target? = TARGETS.firstOrNull { it.id == id }

    private val Target.v: String get() = id.uppercase()

    /**
     * APKs are written next to the target as `.new`, verified on disk and renamed over it: the
     * running AA/CarPlay keep their mmapped old inode and a short write never truncates /system.
     * After the remount, a failure (or a signal) restores any selected target that is neither
     * stock nor patched from its backup. wp_on is idempotent and also remounts / ro.
     */
    private fun scriptPrologue(targets: List<Target>): String = """
        |W=$WORK_DIR
        |P=$WP_PROC
        |D=$DISK
        |WP=0
        |ROLLBACK=0
        |wp_on() {
        |  [ "${'$'}WP" = 1 ] || return 0
        |  WP=0
        |  sync
        |  mount -o remount,ro / 2>/dev/null
        |  echo emmc_enable_all_wp > "${'$'}P" 2>/dev/null
        |}
        |md5() { md5sum "${'$'}1" 2>/dev/null | cut -d' ' -f1; }
        |free_kb() { set -- ${'$'}(df -k "${'$'}1" 2>/dev/null | tail -n 1); echo "${'$'}4"; }
        |has_room() {
        |  F=${'$'}(free_kb "${'$'}1"); Z=${'$'}(stat -c %s "${'$'}2" 2>/dev/null)
        |  case "${'$'}F" in ''|*[!0-9]*) return 0 ;; esac
        |  case "${'$'}Z" in ''|*[!0-9]*) return 0 ;; esac
        |  [ "${'$'}F" -ge ${'$'}((Z / 1024 + 1024)) ]
        |}
        |put() {
        |  N="${'$'}1.new"
        |  rm -f "${'$'}N"
        |  has_room "${'$'}{1%/*}" "${'$'}2" || { R=nospace; return 1; }
        |  cat "${'$'}2" > "${'$'}N" || { rm -f "${'$'}N"; R=write; return 1; }
        |  chown root:root "${'$'}N" && chmod 644 "${'$'}N" || { rm -f "${'$'}N"; R=perms; return 1; }
        |  chcon u:object_r:system_file:s0 "${'$'}N" 2>/dev/null
        |  sync
        |  blockdev --flushbufs "${'$'}D" 2>/dev/null
        |  echo 3 > /proc/sys/vm/drop_caches
        |  [ "${'$'}(md5 "${'$'}N")" = "${'$'}3" ] || { rm -f "${'$'}N"; R=verify; return 1; }
        |  mv -f "${'$'}N" "${'$'}1" || { rm -f "${'$'}N"; R=rename; return 1; }
        |  sync
        |  [ "${'$'}(md5 "${'$'}1")" = "${'$'}3" ] || { R=verify; return 1; }
        |}
        |restore_one() {
        |  [ -f "${'$'}1" ] || return 0
        |  M=${'$'}(md5 "${'$'}1")
        |  [ "${'$'}M" = "${'$'}3" ] && return 0
        |  [ "${'$'}M" = "${'$'}4" ] && return 0
        |  [ "${'$'}(md5 "${'$'}2")" = "${'$'}3" ] || return 1
        |  put "${'$'}1" "${'$'}2" "${'$'}3" || return 1
        |  RBD=1
        |}
        |rollback() {
        |  RB=""
        |  [ "${'$'}ROLLBACK" = 1 ] || return 0
        |  ROLLBACK=0
        |  RBD=0
        |  RC=0
        |${targets.joinToString("\n") { "  restore_one ${it.systemPath} ${it.backupPath} ${it.stockMd5} ${it.patchedMd5} || RC=1" }}
        |  if [ "${'$'}RC" = 1 ]; then RB=" norollback"; elif [ "${'$'}RBD" = 1 ]; then RB=" rolledback"; fi
        |}
        |fail() { rollback; echo "$ERR_MARKER ${'$'}1${'$'}RB"; exit 1; }
        |write_one() {
        |  S="${'$'}1"; SRC="${'$'}2"; EXPECT="${'$'}3"; NAME="${'$'}4"
        |  [ "${'$'}(md5 "${'$'}SRC")" = "${'$'}EXPECT" ] || fail "src_${'$'}NAME"
        |  [ -f "${'$'}S" ] || fail "nosys_${'$'}NAME"
        |  put "${'$'}S" "${'$'}SRC" "${'$'}EXPECT" || fail "${'$'}{R}_${'$'}NAME"
        |}
        |save_copy() {
        |  rm -f "${'$'}2.tmp"
        |  cp "${'$'}1" "${'$'}2.tmp" && [ "${'$'}(md5 "${'$'}2.tmp")" = "${'$'}3" ] && mv -f "${'$'}2.tmp" "${'$'}2" && return 0
        |  rm -f "${'$'}2.tmp"
        |  return 1
        |}
        |ensure_backup() {
        |  S="${'$'}1"; B="${'$'}2"; L="${'$'}3"; STOCK="${'$'}4"; PATCHED="${'$'}5"; NAME="${'$'}6"
        |  if [ ! -f "${'$'}B" ] && [ -f "${'$'}L" ] && [ "${'$'}(md5 "${'$'}L")" = "${'$'}STOCK" ]; then
        |    save_copy "${'$'}L" "${'$'}B" "${'$'}STOCK" || fail "backup_${'$'}NAME"
        |  fi
        |  if [ ! -f "${'$'}B" ] && [ "${'$'}(md5 "${'$'}S")" = "${'$'}STOCK" ]; then
        |    save_copy "${'$'}S" "${'$'}B" "${'$'}STOCK" || fail "backup_${'$'}NAME"
        |  fi
        |  if [ -f "${'$'}B" ]; then
        |    [ "${'$'}(md5 "${'$'}B")" = "${'$'}STOCK" ] || { rm -f "${'$'}B"; fail "badbackup_${'$'}NAME"; }
        |  else
        |    [ "${'$'}(md5 "${'$'}S")" = "${'$'}PATCHED" ] || fail "nobackup_${'$'}NAME"
        |  fi
        |}
        |stage_in() {
        |  [ -f "${'$'}1" ] || fail "nostage_${'$'}4"
        |  save_copy "${'$'}1" "${'$'}2" "${'$'}3" || fail "stage_${'$'}4"
        |  chmod 600 "${'$'}2"
        |}
        |
    """.trimMargin()

    private fun wpOff(): String = """
        |trap 'wp_on' EXIT
        |trap 'rollback; wp_on; exit 1' HUP INT TERM PIPE
        |WP=1
        |echo emmc_disable_all_wp > "${'$'}P" 2>/dev/null || fail wpoff
        |mount -o remount,rw / || fail remountsys
        |
    """.trimMargin()

    private fun lines(targets: List<Target>, line: (Target) -> String): String =
        targets.joinToString("") { line(it) + "\n" }

    /** Stage paths are app-writable; the script copies them into root-only [WORK_DIR] first. */
    private fun applyBody(stages: Map<Target, String>): String {
        val targets = stages.keys.toList()
        return lines(targets) { "${it.v}_SRC='${stages.getValue(it)}'" } +
            lines(targets) { "${it.v}_STAGE=${it.rootStagePath}" } +
            lines(targets) { "stage_in \"\$${it.v}_SRC\" \"\$${it.v}_STAGE\" ${it.patchedMd5} ${it.id}" } +
            lines(targets) {
                "ensure_backup ${it.systemPath} ${it.backupPath} ${it.legacyBackupPath} ${it.stockMd5} ${it.patchedMd5} ${it.id}"
            } +
            "\n" + wpOff() +
            "ROLLBACK=1\n" +
            lines(targets) { "write_one ${it.systemPath} \"\$${it.v}_STAGE\" ${it.patchedMd5} ${it.id}" } +
            "ROLLBACK=0\n" +
            "rm -f ${targets.joinToString(" ") { "\"\$${it.v}_STAGE\"" }}\n"
    }

    private fun restoreBody(targets: List<Target>): String =
        lines(targets) { "${it.v}_BAK=${it.backupPath}" } +
            lines(targets) { "[ -f \"\$${it.v}_BAK\" ] || fail nobackup_${it.id}" } +
            "\n" + wpOff() +
            lines(targets) { "write_one ${it.systemPath} \"\$${it.v}_BAK\" ${it.stockMd5} ${it.id}" }

    private fun scriptTail(targets: List<Target>): String =
        "\nwp_on\ntrap - EXIT HUP INT TERM PIPE\n" +
            lines(targets) { "kill \$(pidof ${it.processName}) 2>/dev/null" }

    /** Patches only the targets in [stages] (target → app-side staged APK path), in [TARGETS] order. */
    fun applyScript(stages: Map<Target, String>): String {
        require(stages.isNotEmpty()) { "no targets" }
        val ordered = TARGETS.filter { it in stages }.associateWith { stages.getValue(it) }
        val targets = ordered.keys.toList()
        return scriptPrologue(targets) + applyBody(ordered) + scriptTail(targets) + "echo \"$APPLIED_MARKER\"\n"
    }

    /** Restores only [targets] from their backups. */
    fun restoreScript(targets: Collection<Target>): String {
        val ordered = TARGETS.filter { it in targets }
        require(ordered.isNotEmpty()) { "no targets" }
        return scriptPrologue(ordered) + restoreBody(ordered) + scriptTail(ordered) + "echo \"$RESTORED_MARKER\"\n"
    }

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
