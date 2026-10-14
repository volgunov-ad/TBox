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
}
