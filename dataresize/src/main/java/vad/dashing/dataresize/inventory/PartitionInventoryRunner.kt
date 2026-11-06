package vad.dashing.dataresize.inventory

import java.util.Date
import vad.dashing.dataresize.device.RootShell

/**
 * Orchestrates collect → verdicts → markdown → USB (or fallback) write.
 * Collection failures propagate; write failures are returned in [InventoryResult.writeError]
 * so the UI can still show verdicts and a copyable summary.
 */
object PartitionInventoryRunner {
    fun run(shell: RootShell): InventoryResult {
        val collector = PartitionInventoryCollector(shell)
        val inventory = collector.collect()
        val verdicts = PartitionInventoryVerdicts.from(inventory)
        val candidates = collector.listOutputCandidates()
        val picked = PartitionInventoryPaths.pickOutputDir(candidates)
        val fileName = PartitionInventoryPaths.reportFileName(inventory.incremental, Date())
        val output = picked?.let { (kind, dir) -> InventoryOutput(kind, dir, fileName) }
        val markdown = PartitionInventoryReport.format(inventory, verdicts, output?.absolutePath)
        val writeError = when {
            output == null -> "USB не найден; нет доступной записи в /sdcard или intsd"
            else -> try {
                collector.writeReport(output.absolutePath, markdown)
                null
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
        }
        return InventoryResult(inventory, verdicts, markdown, output, writeError)
    }
}
