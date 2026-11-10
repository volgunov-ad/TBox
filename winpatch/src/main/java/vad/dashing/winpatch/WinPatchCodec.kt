package vad.dashing.winpatch

/**
 * Permanent install of the patched launcher-window Android Auto (p6) and CarPlayView (p6)
 * APKs into the A10 head unit /system (companion of the Data Resize / USB Audio / Firewall
 * one-shot apps: dangerous /system writes live outside TBox Monitor).
 *
 * One universal patched APK per target is bundled in assets (AndroidAuto_p6 is built from the
 * newest V00.07.12 stock APK and works across all A10 firmwares). Stock backup APKs for every
 * known A10 firmware (V00.03.07, V00.03.16, V00.05.09, V00.07.12) are also bundled in assets
 * under `stock/`, so backup/restore works even if `/data/local/tmp` was wiped or a previous
 * patch revision (p1..p5) is currently installed on `/system`.
 */
object WinPatchCodec {
    const val WORK_DIR = "/data/local/tmp/winpatch"
    const val APPLY_SCRIPT = "$WORK_DIR/apply.sh"
    const val RESTORE_SCRIPT = "$WORK_DIR/restore.sh"
    const val CARPLAY_CONFIG = "/system/etc/adayo/carplay/CarPlayManagerConfig.json"
    const val CARPLAY_CONFIG_BACKUP = "$WORK_DIR/CarPlayManagerConfig.stock.json"
    const val CARPLAY_CONFIG_LEGACY_BACKUP = "/data/local/tmp/CarPlayManagerConfig.json.orig"
    const val CARPLAY_SERVER = "com.adayo.carplay"
    const val LAYOUT_SCRIPT = "$WORK_DIR/layout.sh"
    const val LAYOUT_MARKER = "TBOX_WINPATCH_LAYOUT"

    /** `settings get/put global`. Read by the patched AA and CarPlay on each launch. */
    const val LAYOUT_SETTING = "tbox_winpatch_layout"
    const val LAYOUT_WINDOW = "window"
    const val LAYOUT_BARS = "bars"

    enum class Layout { Window, Bars }

    /** Unset, `null`, and anything other than [LAYOUT_BARS] stay on the launcher window. */
    fun parseLayout(raw: String?): Layout {
        val value = raw?.lineSequence()?.map { it.trim() }?.lastOrNull { it.isNotEmpty() }?.lowercase()
        return if (value == LAYOUT_BARS) Layout.Bars else Layout.Window
    }

    const val APPLIED_MARKER = "TBOX_WINPATCH_APPLIED"
    const val RESTORED_MARKER = "TBOX_WINPATCH_RESTORED"
    const val ERR_MARKER = "TBOX_WINPATCH_ERR"

    private const val WP_PROC = "/proc/mmc_debug"
    private const val DISK = "/dev/block/mmcblk0"

    data class StockVariant(
        val firmwareLabel: String,
        val buildIncrementals: Set<String>,
        val assetPath: String,
        val md5: String,
    )

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
        val stockVariants: List<StockVariant>,
        val legacyPatchedMd5s: Map<String, String> = emptyMap(),
    ) {
        val rootStagePath: String get() = "$WORK_DIR/$assetName"
        val dalvikPattern: String
            get() {
                val encoded = systemPath.trimStart('/').replace('/', '@')
                return "/data/dalvik-cache/*/$encoded@classes.* ${systemPath.substringBeforeLast('/')}/oat"
            }

        fun stockVariantForIncremental(incremental: String?): StockVariant {
            if (!incremental.isNullOrBlank()) {
                stockVariants.firstOrNull { incremental.trim() in it.buildIncrementals }?.let { return it }
            }
            return stockVariants.firstOrNull { it.md5.equals(stockMd5, ignoreCase = true) } ?: stockVariants.last()
        }

        fun stockVariantByMd5(md5: String?): StockVariant? {
            if (md5.isNullOrBlank()) return null
            return stockVariants.firstOrNull { it.md5.equals(md5.trim(), ignoreCase = true) }
        }
    }

    val ANDROID_AUTO_STOCK_VARIANTS = listOf(
        StockVariant(
            firmwareLabel = "V00.03.07 / V00.03.16",
            buildIncrementals = setOf("1719404309", "1725521697"),
            assetPath = "stock/AndroidAuto_v0307.apk",
            md5 = "ed05e3db4ac59fbf82bf9d7018c0b01a",
        ),
        StockVariant(
            firmwareLabel = "V00.05.09",
            buildIncrementals = setOf("1742980640"),
            assetPath = "stock/AndroidAuto_v0509.apk",
            md5 = "821cf2db51435747bd974a81fd52d1bb",
        ),
        StockVariant(
            firmwareLabel = "V00.07.12",
            buildIncrementals = setOf("1763366978"),
            assetPath = "stock/AndroidAuto_v0712.apk",
            md5 = "5bbdbcb35d8c895912534ef3f9e46dee",
        ),
    )

    val CARPLAY_STOCK_VARIANTS = listOf(
        StockVariant(
            firmwareLabel = "V00.03.07 – V00.07.12",
            buildIncrementals = setOf("1719404309", "1725521697", "1742980640", "1763366978"),
            assetPath = "stock/CarPlayView_stock.apk",
            md5 = "2718803663e016a969e6d2fa0d2b4c16",
        ),
    )

    val ANDROID_AUTO = Target(
        id = "aa",
        title = "Android Auto",
        patchLabel = "p6",
        systemPath = "/system/app/AndroidAuto/AndroidAuto.apk",
        backupPath = "$WORK_DIR/AndroidAuto.orig.apk",
        legacyBackupPath = "/data/local/tmp/AndroidAuto_orig_backup.apk",
        assetName = "AndroidAuto_p6.apk",
        stockMd5 = "821cf2db51435747bd974a81fd52d1bb",
        patchedMd5 = "14325504ae3961e08138ab39c9151366",
        processName = "com.adayo.androidauto",
        stockVariants = ANDROID_AUTO_STOCK_VARIANTS,
        legacyPatchedMd5s = mapOf(
            "b47ebdd0a56953d215217fbac7cb7485" to "p1",
            "380839b160c0a48743c08ae6e0e61c0b" to "p2",
            "e6c527effb3e4cfc3ce9dbd5ff9b93e3" to "p3",
            "0ca6fc37a46629ea08095165d0acca05" to "p4",
            "f91c1def77c9105db35eed2dc0732c27" to "p5",
            "cb434d97d52d3cdd18c401e04454e181" to "p6-test1",
            "faa1584b4c5d215a018ceca2752b501a" to "p6-test2",
            "9310bb89c5a854db72521856b602b980" to "p6-test3",
            "2b38bc58847269d66a56d269137e38a1" to "p6-bars",
            "83063975c2463d3a6a1c5a517381a939" to "p6-test4",
            "eec23bafc74cfa1542a5e890c4b993ef" to "p6-test5",
            "7de94e8e0a80bd17af4ce097913fc9eb" to "p6",
            "22619227b2df11c30c76a49dee268c29" to "p6-mode",
            "8aceab907534f273e93a37d6b851dc02" to "p6-crash",
            "4627daaf718dc7565d5bb2e2392aba73" to "p6-stretch",
            "db8efcab587b992ef96f720f84399144" to "p6-crop",
            "39f5e576f5464e70667b5ab7bb865324" to "p6-rebuild",
            "5db12f93b9cb64d28acc1d399d12b191" to "p6-rebuild2",
            "60d81e8e5252773969aa47c9655c6cce" to "p6-prev",
            "85ba1bfecf397cd9b65dc4b13a6bb50a" to "p6-rebuild3",
        ),
    )

    val CAR_PLAY = Target(
        id = "cp",
        title = "CarPlay",
        patchLabel = "p6",
        systemPath = "/system/app/CarPlayView/CarPlayView.apk",
        backupPath = "$WORK_DIR/CarPlayView.orig.apk",
        legacyBackupPath = "/data/local/tmp/CarPlayView_orig_backup.apk",
        assetName = "CarPlayView_p6.apk",
        stockMd5 = "2718803663e016a969e6d2fa0d2b4c16",
        patchedMd5 = "44b12e46506df98b3b6ebe101deac39c",
        processName = "com.adayo.carplay.view",
        stockVariants = CARPLAY_STOCK_VARIANTS,
        legacyPatchedMd5s = mapOf(
            "ab1ebdfe8b572d799965c84f1d8f1ad7" to "p1",
            "035e74c3792a77d5cee55a13e1db9a39" to "p1",
            "b652a30c36d11fea4343c0e3123ab942" to "p2",
            "e9c8d958178875c0712f6bd0d39ae8a2" to "p3",
            "719289e15bc858241e805060cfe845cd" to "p4",
            "bc5ce29096eef28d8a68709bf90f9dfc" to "p5",
            "da365136a431d56e179d24135cac9ee1" to "p6",
            "0841e6252eb54c88c98ef561dafd2857" to "p6-window",
            "af0888794b02440a499ee22f83f12842" to "p6-mode",
            "a03971924667e2d8972b3acd63d634e4" to "p6-crash",
            "a00fc1a55bbbbe52977833b217663f09" to "p6-prev",
            "8acc7bddaf06221892d27ae76796a5c2" to "p6-fit",
            "9b2ecdbddc3aa3a0a16578c148b91685" to "p6-fill",
            "f96afe29961c113566b9c58a4ac071f7" to "p6-bars",
            "a70979af92b8642cc668b2f802350df3" to "p6-1327",
            "31e3f40b8e1cc04952a6df487b0c0770" to "p6-fill",
        ),
    )

    val TARGETS = listOf(ANDROID_AUTO, CAR_PLAY)

    enum class FileState { Stock, Patched, LegacyPatched, Unknown, Missing }

    private val md5LineRegex = Regex("^([0-9a-fA-F]{32})\\s+(\\S+)\\s*$")

    /** `md5sum` output lines ("hash  path") to path → lowercase md5; stderr noise is skipped. */
    fun parseMd5Map(output: String): Map<String, String> =
        output.lineSequence()
            .mapNotNull { line -> md5LineRegex.find(line)?.let { it.groupValues[2] to it.groupValues[1].lowercase() } }
            .toMap()

    fun fileState(md5: String?, target: Target): FileState {
        if (md5 == null) return FileState.Missing
        val lower = md5.lowercase()
        return when {
            lower == target.patchedMd5.lowercase() -> FileState.Patched
            target.stockVariantByMd5(lower) != null -> FileState.Stock
            lower in target.legacyPatchedMd5s -> FileState.LegacyPatched
            else -> FileState.Unknown
        }
    }

    fun firmwareNameForIncremental(incremental: String?): String = when (incremental?.trim()) {
        "1719404309" -> "V00.03.07 (2024-06-26)"
        "1725521697" -> "V00.03.16 (2024-09-05)"
        "1742980640" -> "V00.05.09 (2025-03-26)"
        "1763366978" -> "V00.07.12 (2025-11-17)"
        null, "" -> "не определена"
        else -> "incremental ${incremental.trim()}"
    }

    fun targetById(id: String): Target? = TARGETS.firstOrNull { it.id == id }

    private val Target.v: String get() = id.uppercase()

    /**
     * APKs are written next to the target as `.new`, verified on disk and renamed over it: the
     * running AA/CarPlay keep their mmapped old inode and a short write never truncates /system.
     * After the remount, a failure (or a signal) restores any selected target that is neither
     * stock nor patched from its backup. wp_on is idempotent and also remounts / ro.
     */
    private fun scriptPrologue(targets: List<Target>, stockMd5ByTarget: Map<Target, String>): String = """
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
        |${targets.joinToString("\n") {
            val stockMd5 = stockMd5ByTarget[it] ?: it.stockMd5
            "  restore_one ${it.systemPath} ${it.backupPath} $stockMd5 ${it.patchedMd5} || RC=1"
        }}
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
        |  S="${'$'}1"; B="${'$'}2"; L="${'$'}3"; STOCK="${'$'}4"; PATCHED="${'$'}5"; NAME="${'$'}6"; BUNDLED="${'$'}7"
        |  if [ ! -f "${'$'}B" ] && [ -n "${'$'}BUNDLED" ] && [ -f "${'$'}BUNDLED" ] && [ "${'$'}(md5 "${'$'}BUNDLED")" = "${'$'}STOCK" ]; then
        |    save_copy "${'$'}BUNDLED" "${'$'}B" "${'$'}STOCK" || fail "backup_${'$'}NAME"
        |  fi
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
    private fun applyBody(
        stages: Map<Target, String>,
        stockStages: Map<Target, String>,
        stockMd5ByTarget: Map<Target, String>,
    ): String {
        val targets = stages.keys.toList()
        return lines(targets) { "${it.v}_SRC='${stages.getValue(it)}'" } +
            lines(targets) { "${it.v}_STAGE=${it.rootStagePath}" } +
            lines(targets) {
                val stockSrc = stockStages[it] ?: ""
                "${it.v}_BUNDLED='$stockSrc'"
            } +
            lines(targets) { "stage_in \"\$${it.v}_SRC\" \"\$${it.v}_STAGE\" ${it.patchedMd5} ${it.id}" } +
            lines(targets) {
                val stockMd5 = stockMd5ByTarget[it] ?: it.stockMd5
                "ensure_backup ${it.systemPath} ${it.backupPath} ${it.legacyBackupPath} $stockMd5 ${it.patchedMd5} ${it.id} \"\$${it.v}_BUNDLED\""
            } +
            "\n" + wpOff() +
            "ROLLBACK=1\n" +
            lines(targets) { "write_one ${it.systemPath} \"\$${it.v}_STAGE\" ${it.patchedMd5} ${it.id}" } +
            lines(targets) { "rm -rf ${it.dalvikPattern} 2>/dev/null" } +
            "ROLLBACK=0\n" +
            "rm -f ${targets.joinToString(" ") { "\"\$${it.v}_STAGE\"" }}\n"
    }

    private fun restoreBody(
        targets: List<Target>,
        stockStages: Map<Target, String>,
        stockMd5ByTarget: Map<Target, String>,
    ): String =
        lines(targets) { "${it.v}_BAK=${it.backupPath}" } +
            lines(targets) {
                val stockSrc = stockStages[it] ?: ""
                val stockMd5 = stockMd5ByTarget[it] ?: it.stockMd5
                "if [ ! -f \"\$${it.v}_BAK\" ] && [ -n '$stockSrc' ] && [ -f '$stockSrc' ]; then save_copy '$stockSrc' \"\$${it.v}_BAK\" $stockMd5 || fail backup_${it.id}; fi"
            } +
            lines(targets) { "[ -f \"\$${it.v}_BAK\" ] || fail nobackup_${it.id}" } +
            "\n" + wpOff() +
            lines(targets) {
                val stockMd5 = stockMd5ByTarget[it] ?: it.stockMd5
                "write_one ${it.systemPath} \"\$${it.v}_BAK\" $stockMd5 ${it.id}"
            } +
            lines(targets) { "rm -rf ${it.dalvikPattern} 2>/dev/null" }

    private fun scriptTail(targets: List<Target>): String =
        "\nwp_on\ntrap - EXIT HUP INT TERM PIPE\n" +
            lines(targets) { "am force-stop ${it.processName} 2>/dev/null; kill \$(pidof ${it.processName}) 2>/dev/null" }

    /** Patches only the targets in [stages] (target → app-side staged APK path), in [TARGETS] order. */
    fun applyScript(
        stages: Map<Target, String>,
        stockStages: Map<Target, String> = emptyMap(),
        stockMd5ByTarget: Map<Target, String> = emptyMap(),
    ): String {
        require(stages.isNotEmpty()) { "no targets" }
        val ordered = TARGETS.filter { it in stages }.associateWith { stages.getValue(it) }
        val targets = ordered.keys.toList()
        return scriptPrologue(targets, stockMd5ByTarget) +
            applyBody(ordered, stockStages, stockMd5ByTarget) +
            scriptTail(targets) +
            "echo \"$APPLIED_MARKER\"\n"
    }

    /** Restores only [targets] from their backups (or bundled stock APKs). */
    fun restoreScript(
        targets: Collection<Target>,
        stockStages: Map<Target, String> = emptyMap(),
        stockMd5ByTarget: Map<Target, String> = emptyMap(),
    ): String {
        val ordered = TARGETS.filter { it in targets }
        require(ordered.isNotEmpty()) { "no targets" }
        return scriptPrologue(ordered, stockMd5ByTarget) +
            restoreBody(ordered, stockStages, stockMd5ByTarget) +
            scriptTail(ordered) +
            "echo \"$RESTORED_MARKER\"\n"
    }

    /**
     * Rewrites the CarPlay main display from the stock backup and stores the launch mode.
     * Bars is the checked 1920×864 stream (heightPhysical 156). Window restores 1920×1080.
     * The iPhone reads the size only when it connects, so the caller must ask for a replug.
     */
    fun carPlayLayoutScript(layout: Layout): String {
        val mode = if (layout == Layout.Bars) LAYOUT_BARS else LAYOUT_WINDOW
        val prepare = if (layout == Layout.Bars) {
            """
            |sed 's/"heightPixels":1080/"heightPixels":864/; s/"heightPhysical":195/"heightPhysical":156/' "${'$'}BAK" > "${'$'}N" || fail sed
            |grep -q '"heightPixels":864' "${'$'}N" || fail verify
            |grep -q '"heightPhysical":156' "${'$'}N" || fail verify
            |grep -q '"widthPixels":1920' "${'$'}N" || fail verify
            |grep -q '"heightPixels":1080' "${'$'}N" && fail verify
            """.trimMargin()
        } else {
            """
            |cp "${'$'}BAK" "${'$'}N" || fail cp
            |grep -q '"heightPixels":1080' "${'$'}N" || fail verify
            |grep -q '"heightPhysical":195' "${'$'}N" || fail verify
            |grep -q '"widthPixels":1920' "${'$'}N" || fail verify
            """.trimMargin()
        }
        val checkLive = if (layout == Layout.Bars) {
            """
            |grep -q '"heightPixels":864' "${'$'}S" || fail verify
            |grep -q '"heightPhysical":156' "${'$'}S" || fail verify
            """.trimMargin()
        } else {
            """
            |grep -q '"heightPixels":1080' "${'$'}S" || fail verify
            |grep -q '"heightPhysical":195' "${'$'}S" || fail verify
            """.trimMargin()
        }
        return """
            |S=$CARPLAY_CONFIG
            |BAK=$CARPLAY_CONFIG_BACKUP
            |LEG=$CARPLAY_CONFIG_LEGACY_BACKUP
            |N=$WORK_DIR/CarPlayManagerConfig.next
            |P=$WP_PROC
            |D=$DISK
            |fail() { echo $ERR_MARKER "${'$'}1"; exit 1; }
            |mkdir -p $WORK_DIR
            |if [ ! -s "${'$'}BAK" ]; then
            |  if [ -s "${'$'}LEG" ] && grep -q '"heightPixels":1080' "${'$'}LEG" && grep -q '"heightPhysical":195' "${'$'}LEG"; then
            |    cp "${'$'}LEG" "${'$'}BAK" || fail cpbackup
            |  elif grep -q '"heightPixels":1080' "${'$'}S" && grep -q '"heightPhysical":195' "${'$'}S"; then
            |    cp "${'$'}S" "${'$'}BAK" || fail cpbackup
            |  else
            |    fail nocpbackup
            |  fi
            |fi
            |grep -q '"heightPixels":1080' "${'$'}BAK" || fail badcpbackup
            |grep -q '"heightPhysical":195' "${'$'}BAK" || fail badcpbackup
            |grep -q '"widthPixels":1920' "${'$'}BAK" || fail badcpbackup
            |$prepare
            |echo emmc_disable_all_wp > "${'$'}P" || fail wpoff
            |mount -o remount,rw / || { echo emmc_enable_all_wp > "${'$'}P"; fail remount; }
            |cat "${'$'}N" > "${'$'}S" || { echo emmc_enable_all_wp > "${'$'}P"; fail write; }
            |chown root:root "${'$'}S"
            |chmod 644 "${'$'}S"
            |chcon u:object_r:system_file:s0 "${'$'}S" 2>/dev/null
            |sync
            |blockdev --flushbufs "${'$'}D" 2>/dev/null
            |echo 3 > /proc/sys/vm/drop_caches
            |$checkLive
            |sync
            |mount -o remount,ro / 2>/dev/null
            |echo emmc_enable_all_wp > "${'$'}P"
            |settings put global $LAYOUT_SETTING $mode
            |/system/bin/am force-stop ${ANDROID_AUTO.processName} 2>/dev/null
            |/system/bin/am force-stop ${CAR_PLAY.processName} 2>/dev/null
            |/system/bin/am force-stop $CARPLAY_SERVER 2>/dev/null
            |kill ${'$'}(pidof ${ANDROID_AUTO.processName}) 2>/dev/null
            |kill ${'$'}(pidof ${CAR_PLAY.processName}) 2>/dev/null
            |kill ${'$'}(pidof $CARPLAY_SERVER) 2>/dev/null
            |echo $LAYOUT_MARKER
            |settings get global $LAYOUT_SETTING
            |
        """.trimMargin()
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
