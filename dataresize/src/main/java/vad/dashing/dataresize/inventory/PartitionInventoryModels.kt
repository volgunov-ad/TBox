package vad.dashing.dataresize.inventory

/**
 * Read-only snapshot of GPT partitions and mounted filesystems for the on-HU report.
 * No write-protect dance and no GPT rewrite — metadata / df / shallow du only.
 */
data class PartitionInventory(
    val takenAt: String,
    val model: String,
    val displayId: String,
    val incremental: String,
    val hardware: String,
    val diskSectors: Long,
    val lastUsable: Long,
    val freeTailSectors: Long,
    val partitions: List<PartitionInfo>,
    val userdataMediaAdjacent: Boolean?,
    val outputHint: String? = null,
)

data class PartitionInfo(
    val name: String,
    val firstLba: Long,
    val lastLba: Long,
    val sectors: Long,
    val neighborRight: String?,
    val fstype: String?,
    val mountPoint: String?,
    val mountOptions: String?,
    val mounted: Boolean,
    val usedKiB: Long?,
    val freeKiB: Long?,
    val sizeKiB: Long?,
    val topDirs: List<Pair<String, Long>>,
    val notes: List<String>,
    val sampleFiles: List<String>,
    val fotaKiB: Long? = null,
)

data class InventoryVerdict(
    val topic: String,
    val text: String,
    val ok: Boolean?,
)

enum class OutputKind { Usb, Sdcard, Intsd }

data class InventoryOutput(
    val kind: OutputKind,
    val directory: String,
    val fileName: String,
) {
    val absolutePath: String get() = "$directory/$fileName"
}

data class InventoryResult(
    val inventory: PartitionInventory,
    val verdicts: List<InventoryVerdict>,
    val markdown: String,
    val output: InventoryOutput?,
    val writeError: String? = null,
)
