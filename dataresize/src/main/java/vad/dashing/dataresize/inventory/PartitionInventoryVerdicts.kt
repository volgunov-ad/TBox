package vad.dashing.dataresize.inventory

/**
 * Auto-verdicts from the design doc (media shrink, intsd, cache/fota, dataresize tail).
 * Thresholds are intentional product rules, not guesses.
 */
object PartitionInventoryVerdicts {
    const val MEDIA_SHRINK_OK_USED_MIB = 1536L // 1.5 GiB
    const val MEDIA_SHRINK_BLOCK_USED_MIB = 2048L // 2 GiB
    const val FOTA_NOTICE_MIB = 500L
    const val MIB = 1024L
    const val KIB_PER_MIB = 1024L

    fun from(inventory: PartitionInventory): List<InventoryVerdict> {
        val byName = inventory.partitions.associateBy { it.name }
        val media = byName["media"]
        val intsd = byName["intsd"]
        val cache = byName["cache"]
        val userdata = byName["userdata"]
        return listOf(
            mediaShrink(media, inventory.userdataMediaAdjacent),
            intsdTouch(intsd),
            cacheFota(cache),
            dataresizeTail(inventory.freeTailSectors, userdata?.sectors),
        )
    }

    fun mediaShrink(media: PartitionInfo?, adjacent: Boolean?): InventoryVerdict {
        val topic = "Сжать media ×2 → отдать userdata"
        if (media == null) {
            return InventoryVerdict(topic, "НЕТ данных — раздела media в GPT нет", ok = false)
        }
        if (adjacent == false) {
            return InventoryVerdict(
                topic,
                "НЕТ — userdata и media не смежны; shrink-на-месте не предлагать",
                ok = false,
            )
        }
        val usedMiB = usedMiB(media)
        return when {
            usedMiB == null -> InventoryVerdict(
                topic,
                "НЕИЗВЕСТНО — media не смонтирован или df недоступен; соседство: ${adjText(adjacent)}",
                ok = null,
            )
            usedMiB < MEDIA_SHRINK_OK_USED_MIB -> InventoryVerdict(
                topic,
                "ДА, по объёму данных — media used ~${usedMiB} МиБ ≪ 2 ГиБ; соседство userdata|media ${adjText(adjacent)}",
                ok = true,
            )
            usedMiB >= MEDIA_SHRINK_BLOCK_USED_MIB -> InventoryVerdict(
                topic,
                "НЕТ — media заполнен ≥2 ГиБ (~${usedMiB} МиБ) — ×2 без чистки/бэкапа нельзя",
                ok = false,
            )
            else -> InventoryVerdict(
                topic,
                "ОСТОРОЖНО — media used ~${usedMiB} МиБ (между 1.5 и 2 ГиБ); нужен бэкап/ручная оценка",
                ok = null,
            )
        }
    }

    fun intsdTouch(intsd: PartitionInfo?): InventoryVerdict {
        val topic = "Трогать intsd"
        if (intsd == null) {
            return InventoryVerdict(topic, "НЕТ данных — раздела intsd в GPT нет", ok = null)
        }
        val usedMiB = usedMiB(intsd)
        val hasUserContent = intsd.notes.any {
            it.contains("Maps", ignoreCase = true) ||
                it.contains("Music", ignoreCase = true) ||
                it.contains("Movies", ignoreCase = true) ||
                it.contains("Android", ignoreCase = true) ||
                it.contains("навигац", ignoreCase = true) ||
                it.contains("пользователь", ignoreCase = true)
        } || intsd.topDirs.any { (name, sizeKiB) ->
            name.equals("Maps", true) || name.equals("Music", true) ||
                name.equals("Movies", true) || name.equals("Android", true) ||
                name.equals("Download", true) || sizeKiB >= 100 * KIB_PER_MIB
        }
        return when {
            usedMiB != null && usedMiB >= 512 && hasUserContent -> InventoryVerdict(
                topic,
                "НЕТ — занят картами/медиа/пользовательскими данными (~${usedMiB} МиБ used)",
                ok = false,
            )
            usedMiB != null && usedMiB < 64 && !hasUserContent -> InventoryVerdict(
                topic,
                "мало данных (~${usedMiB} МиБ) — как донор всё равно не трогать без отдельной задачи",
                ok = false,
            )
            usedMiB != null -> InventoryVerdict(
                topic,
                "НЕТ — не использовать как донор переразметки (used ~${usedMiB} МиБ)",
                ok = false,
            )
            else -> InventoryVerdict(
                topic,
                "НЕИЗВЕСТНО — intsd не смонтирован; по умолчанию не трогать",
                ok = false,
            )
        }
    }

    fun cacheFota(cache: PartitionInfo?): InventoryVerdict {
        val topic = "Чистить /cache/fota"
        if (cache == null) {
            return InventoryVerdict(topic, "НЕТ данных — раздела cache в GPT нет", ok = null)
        }
        val fotaMiB = cache.fotaKiB?.div(KIB_PER_MIB)
        return when {
            fotaMiB != null && fotaMiB > FOTA_NOTICE_MIB -> InventoryVerdict(
                topic,
                "можно после копии на ПК — ~${fotaMiB} МиБ старого OTA; на /data не влияет",
                ok = true,
            )
            fotaMiB != null && fotaMiB > 0 -> InventoryVerdict(
                topic,
                "fota есть (~${fotaMiB} МиБ) — при желании скопировать на ПК, потом удалить",
                ok = true,
            )
            fotaMiB == 0L -> InventoryVerdict(topic, "каталог fota пуст или отсутствует", ok = null)
            else -> InventoryVerdict(topic, "размер fota неизвестен", ok = null)
        }
    }

    fun dataresizeTail(freeTailSectors: Long, userdataSectors: Long?): InventoryVerdict {
        val topic = "Текущий :dataresize (хвост)"
        val tailMiB = freeTailSectors / 2048L
        val userMiB = userdataSectors?.div(2048L)
        return when {
            userdataSectors == null -> InventoryVerdict(
                topic,
                "НЕИЗВЕСТНО — нет userdata; свободный хвост ~${tailMiB} МиБ",
                ok = null,
            )
            freeTailSectors <= userdataSectors -> InventoryVerdict(
                topic,
                "недоступен — свободный хвост ~${tailMiB} МиБ < размера userdata (~${userMiB} МиБ)",
                ok = false,
            )
            else -> InventoryVerdict(
                topic,
                "возможен — хвост ~${tailMiB} МиБ > userdata (~${userMiB} МиБ)",
                ok = true,
            )
        }
    }

    private fun usedMiB(part: PartitionInfo): Long? = part.usedKiB?.div(KIB_PER_MIB)

    private fun adjText(adjacent: Boolean?): String = when (adjacent) {
        true -> "OK"
        false -> "нет"
        null -> "не проверено"
    }
}
