package vad.dashing.dataresize

import vad.dashing.dataresize.gpt.GptPlanner
import vad.dashing.dataresize.gpt.MovePlan

/** Files the app installs into [DIR] before a move; the script sources [PARAMS]. */
object MoveFiles {
    const val DIR = "/cache/dataresize"
    const val SCRIPT = "$DIR/dataresize.sh"
    const val PARAMS = "$DIR/params"
    const val ASSET_SCRIPT = "dataresize.sh"

    fun params(plan: MovePlan): String = listOf(
        "DISK_SECT" to plan.diskSectors,
        "OLD_FIRST" to plan.entry.first,
        "OLD_SECT" to plan.entry.sectors,
        "NEW_FIRST" to plan.newFirst,
        "NEW_SECT" to plan.newSectors,
        "COPY_SEEK_MIB" to plan.copySeekMiB,
        "COPY_MIB" to plan.copyMiB,
        "PGPT_SECT" to plan.primarySectors,
        "SGPT_LBA" to plan.secondaryLba,
        "SGPT_SECT" to plan.secondarySectors,
        "MD5_OLD_P" to GptPlanner.md5(plan.oldPrimary),
        "MD5_NEW_P" to GptPlanner.md5(plan.newPrimary),
        "MD5_OLD_S" to GptPlanner.md5(plan.oldSecondary),
        "MD5_NEW_S" to GptPlanner.md5(plan.newSecondary),
    ).joinToString("\n", postfix = "\n") { (k, v) -> "$k=$v" }

    fun binaries(plan: MovePlan): Map<String, ByteArray> = mapOf(
        "old_pgpt.bin" to plan.oldPrimary,
        "new_pgpt.bin" to plan.newPrimary,
        "old_sgpt.bin" to plan.oldSecondary,
        "new_sgpt.bin" to plan.newSecondary,
    )

    /**
     * A rollback is a move back ([plan] from [GptPlanner.relocate] of the tables on disk): the current tables
     * become «new», the rolled-back ones «old», so the script, mixed-copy detection and fixsgpt stay as they are.
     * The layout keys of the original move are kept.
     */
    fun rollbackParams(moveParams: Map<String, String>, plan: MovePlan): String = (
        moveParams + listOf(
            "DISK_SECT" to plan.diskSectors.toString(),
            "PGPT_SECT" to plan.primarySectors.toString(),
            "SGPT_LBA" to plan.secondaryLba.toString(),
            "SGPT_SECT" to plan.secondarySectors.toString(),
            "MD5_OLD_P" to GptPlanner.md5(plan.newPrimary),
            "MD5_NEW_P" to GptPlanner.md5(plan.oldPrimary),
            "MD5_OLD_S" to GptPlanner.md5(plan.newSecondary),
            "MD5_NEW_S" to GptPlanner.md5(plan.oldSecondary),
        )
        ).entries.joinToString("\n", postfix = "\n") { (k, v) -> "$k=$v" }

    fun rollbackBinaries(plan: MovePlan): Map<String, ByteArray> = mapOf(
        "old_pgpt.bin" to plan.newPrimary,
        "new_pgpt.bin" to plan.oldPrimary,
        "old_sgpt.bin" to plan.newSecondary,
        "new_sgpt.bin" to plan.oldSecondary,
    )

    /** `KEY=value` pairs separated by spaces or newlines (params file, `status`, `running`). */
    fun fields(text: String): Map<String, String> = text.trim().split(Regex("\\s+"))
        .mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
        .toMap()

    /**
     * Each GPT copy is exactly one of the tables in [params] but from different ones: true when the
     * primary is the new table. Null in any other case, including both copies from the same table.
     */
    fun mixedPrimaryNew(params: Map<String, String>, primaryMd5: String, secondaryMd5: String): Boolean? {
        val oldP = params["MD5_OLD_P"] ?: return null
        val newP = params["MD5_NEW_P"] ?: return null
        val oldS = params["MD5_OLD_S"] ?: return null
        val newS = params["MD5_NEW_S"] ?: return null
        return when {
            primaryMd5 == oldP && secondaryMd5 == newS -> false
            primaryMd5 == newP && secondaryMd5 == oldS -> true
            else -> null
        }
    }
}
