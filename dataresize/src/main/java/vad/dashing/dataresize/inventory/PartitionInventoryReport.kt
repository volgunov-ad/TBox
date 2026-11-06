package vad.dashing.dataresize.inventory

import java.util.Locale
import vad.dashing.dataresize.gpt.SECTOR

/**
 * Builds the UTF-8 Markdown report matching the design sample in
 * `a10-partition-inventory-app.md`. Pure string formatting — unit-tested.
 */
object PartitionInventoryReport {
    private val RU = Locale.forLanguageTag("ru")

    /** Priority sections get a full block; others stay in the GPT table only. */
    val DETAIL_NAMES = listOf("userdata", "media", "intsd", "cache", "map", "miscbk")

    fun format(
        inventory: PartitionInventory,
        verdicts: List<InventoryVerdict>,
        outputPath: String?,
    ): String = buildString {
        appendLine("# Отчёт по разделам ГУ (TBox)")
        appendLine()
        appendLine("- Снято: ${inventory.takenAt} (локальное время ГУ)")
        appendLine("- Модель: ${inventory.model.ifBlank { "—" }} (${inventory.hardware.ifBlank { "?" }})")
        appendLine(
            "- `ro.build.display.id` / incremental: ${inventory.displayId.ifBlank { "—" }} / " +
                inventory.incremental.ifBlank { "—" },
        )
        appendLine(
            "- eMMC: mmcblk0, ${inventory.diskSectors} секторов (~${gibFromSectors(inventory.diskSectors)})",
        )
        appendLine("- Режим: только чтение (WP не снималась, raw dd не делался)")
        appendLine("- Файл: ${outputPath ?: "ещё не записан"}")
        appendLine()
        appendLine("## Вердикты (сводка)")
        appendLine()
        appendLine("| Тема | Вердикт |")
        appendLine("|---|---|")
        for (v in verdicts) {
            appendLine("| ${v.topic} | ${escapeCell(v.text)} |")
        }
        appendLine()
        appendLine("## GPT (интересные записи)")
        appendLine()
        appendLine("| Раздел | Start LBA | Size | Сосед справа |")
        appendLine("|---|---:|---:|---|")
        val interesting = inventory.partitions.filter { it.name in DETAIL_NAMES || it.name == "cache" }
            .ifEmpty { inventory.partitions }
        for (p in interesting.sortedBy { it.firstLba }) {
            appendLine(
                "| ${p.name} | ${p.firstLba} | ${mibFromSectors(p.sectors)} МиБ | ${p.neighborRight ?: "—"} |",
            )
        }
        appendLine()
        val adj = when (inventory.userdataMediaAdjacent) {
            true -> "**да** (`last_userdata + 1 == first_media`)"
            false -> "**нет**"
            null -> "не определено (нет userdata или media)"
        }
        appendLine("- userdata ↔ media смежны: $adj")
        appendLine(
            "- Свободный хвост после последнего раздела (до lastUsable ${inventory.lastUsable}): " +
                "**~${mibFromSectors(inventory.freeTailSectors)} МиБ**",
        )
        appendLine()
        appendLine("## По разделам")
        appendLine()
        for (name in DETAIL_NAMES) {
            val p = inventory.partitions.firstOrNull { it.name == name } ?: continue
            appendPartition(p)
        }
        appendLine("## Рекомендуемые следующие шаги")
        appendLine()
        appendLine("1. Сохранить этот отчёт (уже на ${outputPath?.let { "носителе: `$it`" } ?: "устройстве — путь выше"}).")
        appendLine("2. По желанию: скопировать `/cache/fota/*.zip` на ПК, затем удалить с ГУ.")
        appendLine(
            "3. План +2 ГиБ к `/data`: shrink media ×2 + grow userdata **не** текущим `:dataresize` — " +
                "отдельная процедура/будущая кнопка после дампа GPT.",
        )
        appendLine("4. intsd не использовать как донор переразметки.")
        appendLine()
    }

    fun summaryText(verdicts: List<InventoryVerdict>, outputPath: String?): String = buildString {
        appendLine("Паспорт разделов ГУ (TBox)")
        for (v in verdicts) {
            appendLine("• ${v.topic}: ${v.text}")
        }
        appendLine("Файл: ${outputPath ?: "не записан"}")
    }.trimEnd()

    private fun StringBuilder.appendPartition(p: PartitionInfo) {
        val mount = when {
            p.mounted && p.mountPoint != null ->
                "${p.name} → `${p.mountPoint}` (${p.fstype ?: "?"}, ${rwHint(p.mountOptions)})"
            else -> "${p.name} (не смонтирован${p.fstype?.let { ", fstype $it" } ?: ""})"
        }
        appendLine("### $mount")
        appendLine()
        appendLine("| | |")
        appendLine("|---|---|")
        val gptDf = buildString {
            append("${mibFromSectors(p.sectors)} МиБ GPT")
            if (p.usedKiB != null && p.freeKiB != null) {
                append(" / used ${fmtKiB(p.usedKiB)}, free ${fmtKiB(p.freeKiB)}")
            } else if (p.sizeKiB != null) {
                append(" / df size ${fmtKiB(p.sizeKiB)}")
            }
        }
        appendLine("| GPT / df | ${escapeCell(gptDf)} |")
        if (p.notes.isNotEmpty()) {
            appendLine("| Заметное | ${escapeCell(p.notes.joinToString("; "))} |")
        }
        if (p.fotaKiB != null) {
            appendLine("| /cache/fota | ${fmtKiB(p.fotaKiB)} |")
        }
        appendLine()
        if (p.topDirs.isNotEmpty()) {
            appendLine("Топ 1-го уровня (du):")
            appendLine()
            appendLine("| Каталог | Размер |")
            appendLine("|---|---:|")
            for ((name, kib) in p.topDirs) {
                appendLine("| ${escapeCell(name)} | ${fmtKiB(kib)} |")
            }
            appendLine()
        }
        if (p.sampleFiles.isNotEmpty()) {
            appendLine("Примеры файлов (ограниченная выборка):")
            appendLine()
            for (f in p.sampleFiles) appendLine("- `${escapeCell(f)}`")
            appendLine()
        }
        appendLine()
    }

    private fun rwHint(opts: String?): String {
        if (opts == null) return "?"
        return when {
            opts.split(',').any { it == "ro" } -> "ro"
            opts.split(',').any { it == "rw" } -> "rw"
            else -> opts.take(24)
        }
    }

    fun gibFromSectors(sectors: Long): String =
        String.format(RU, "%.1f ГиБ", sectors * SECTOR / 1073741824.0)

    fun mibFromSectors(sectors: Long): Long = sectors / 2048L

    fun fmtKiB(kib: Long): String = when {
        kib >= 1024L * 1024L -> String.format(RU, "%.1f ГиБ", kib / (1024.0 * 1024.0))
        kib >= 1024L -> String.format(RU, "%d МиБ", kib / 1024L)
        else -> "$kib КиБ"
    }

    private fun escapeCell(text: String): String =
        text.replace("|", "\\|").replace("\n", " ")
}
