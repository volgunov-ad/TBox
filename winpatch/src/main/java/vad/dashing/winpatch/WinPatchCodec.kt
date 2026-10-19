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

    /**
     * APKs are written next to the target as `.new`, verified on disk and renamed over it: the
     * running AA/CarPlay keep their mmapped old inode and a short write never truncates /system.
     * After the remount, a failure (or a signal) restores any target that is neither stock nor
     * patched from its backup. wp_on is idempotent and also remounts / ro.
     */
    private fun scriptPrologue(): String = """
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
        |  restore_one ${ANDROID_AUTO.systemPath} ${ANDROID_AUTO.backupPath} ${ANDROID_AUTO.stockMd5} ${ANDROID_AUTO.patchedMd5} || RC=1
        |  restore_one ${CAR_PLAY.systemPath} ${CAR_PLAY.backupPath} ${CAR_PLAY.stockMd5} ${CAR_PLAY.patchedMd5} || RC=1
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

    /** [aaStage]/[cpStage] are app-writable; the script copies them into root-only [WORK_DIR] first. */
    private fun applyBody(aaStage: String, cpStage: String): String = """
        |AA_SRC='$aaStage'
        |CP_SRC='$cpStage'
        |AA_STAGE=${ANDROID_AUTO.rootStagePath}
        |CP_STAGE=${CAR_PLAY.rootStagePath}
        |stage_in "${'$'}AA_SRC" "${'$'}AA_STAGE" ${ANDROID_AUTO.patchedMd5} ${ANDROID_AUTO.id}
        |stage_in "${'$'}CP_SRC" "${'$'}CP_STAGE" ${CAR_PLAY.patchedMd5} ${CAR_PLAY.id}
        |ensure_backup ${ANDROID_AUTO.systemPath} ${ANDROID_AUTO.backupPath} ${ANDROID_AUTO.legacyBackupPath} ${ANDROID_AUTO.stockMd5} ${ANDROID_AUTO.patchedMd5} ${ANDROID_AUTO.id}
        |ensure_backup ${CAR_PLAY.systemPath} ${CAR_PLAY.backupPath} ${CAR_PLAY.legacyBackupPath} ${CAR_PLAY.stockMd5} ${CAR_PLAY.patchedMd5} ${CAR_PLAY.id}
        |
    """.trimMargin() + wpOff() + """
        |ROLLBACK=1
        |write_one ${ANDROID_AUTO.systemPath} "${'$'}AA_STAGE" ${ANDROID_AUTO.patchedMd5} ${ANDROID_AUTO.id}
        |write_one ${CAR_PLAY.systemPath} "${'$'}CP_STAGE" ${CAR_PLAY.patchedMd5} ${CAR_PLAY.id}
        |ROLLBACK=0
        |rm -f "${'$'}AA_STAGE" "${'$'}CP_STAGE"
        |
    """.trimMargin()

    private fun restoreBody(): String = """
        |AA_BAK=${ANDROID_AUTO.backupPath}
        |CP_BAK=${CAR_PLAY.backupPath}
        |[ -f "${'$'}AA_BAK" ] || fail nobackup_aa
        |[ -f "${'$'}CP_BAK" ] || fail nobackup_cp
        |
    """.trimMargin() + wpOff() + """
        |write_one ${ANDROID_AUTO.systemPath} "${'$'}AA_BAK" ${ANDROID_AUTO.stockMd5} ${ANDROID_AUTO.id}
        |write_one ${CAR_PLAY.systemPath} "${'$'}CP_BAK" ${CAR_PLAY.stockMd5} ${CAR_PLAY.id}
        |
    """.trimMargin()

    private fun scriptTail(): String = """
        |wp_on
        |trap - EXIT HUP INT TERM PIPE
        |kill ${'$'}(pidof ${ANDROID_AUTO.processName}) 2>/dev/null
        |kill ${'$'}(pidof ${CAR_PLAY.processName}) 2>/dev/null
        |
    """.trimMargin()

    fun applyScript(aaStage: String, cpStage: String): String =
        scriptPrologue() + applyBody(aaStage, cpStage) + scriptTail() + "echo \"$APPLIED_MARKER\"\n"

    fun restoreScript(): String =
        scriptPrologue() + restoreBody() + scriptTail() + "echo \"$RESTORED_MARKER\"\n"

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
