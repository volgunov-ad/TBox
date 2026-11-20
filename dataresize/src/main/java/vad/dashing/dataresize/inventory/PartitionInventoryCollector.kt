package vad.dashing.dataresize.inventory

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import vad.dashing.dataresize.device.RootShell
import vad.dashing.dataresize.gpt.Gpt
import vad.dashing.dataresize.gpt.GptEntry
import vad.dashing.dataresize.gpt.GptImages
import vad.dashing.dataresize.gpt.GptPlanner
import vad.dashing.dataresize.gpt.SECTOR

/**
 * Read-only collector: GPT via the same sector reads as `:dataresize`, then mount/df/du probes.
 * Never touches eMMC write-protect or GPT tables.
 */
class PartitionInventoryCollector(
    private val shell: RootShell,
    private val device: String = DEVICE,
) {
    fun collect(): PartitionInventory {
        val props = readProps()
        val images = readImages()
        val layout = GptPlanner.parse(images)
        val entries = layout.entries.sortedBy { it.first }
        val lastPartEnd = entries.maxOfOrNull { it.last } ?: layout.header.lastUsable
        val freeTail = (layout.header.lastUsable - lastPartEnd).coerceAtLeast(0L)
        val mounts = parseMounts(shell.rootText("cat /proc/mounts"))
        val userdata = entries.firstOrNull { it.name == "userdata" }
        val media = entries.firstOrNull { it.name == "media" }
        val adjacent = if (userdata != null && media != null) userdata.last + 1 == media.first else null

        val partitions = entries.map { entry ->
            scanPartition(entry, entries, mounts)
        }

        val takenAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.forLanguageTag("ru")).format(Date())
        return PartitionInventory(
            takenAt = takenAt,
            model = props["model"].orEmpty(),
            displayId = props["display"].orEmpty(),
            incremental = props["incremental"].orEmpty(),
            hardware = props["hw"].orEmpty(),
            diskSectors = images.diskSectors,
            lastUsable = layout.header.lastUsable,
            freeTailSectors = freeTail,
            partitions = partitions,
            userdataMediaAdjacent = adjacent,
        )
    }

    /** Writable destinations for the report; same USB search roots as hu_rescue. */
    fun listOutputCandidates(): List<String> {
        // No single quotes — RootShell.root wraps the command in sh -c '...'.
        val udisk = "/udisk" + "*"
        val mediaRw = "/mnt/media_rw/" + "*"
        val storage = "/storage/" + "*"
        val cmd = "for d in $udisk $mediaRw $storage; do " +
            "[ -d \"\$d\" ] || continue; " +
            "base=\${d##*/}; " +
            "case \"\$base\" in emulated|self) continue ;; esac; " +
            "[ -w \"\$d\" ] && echo \"\$d\"; " +
            "done; " +
            "[ -d /sdcard ] && [ -w /sdcard ] && echo /sdcard; " +
            "[ -d /storage/emulated/0 ] && [ -w /storage/emulated/0 ] && echo /storage/emulated/0; " +
            "[ -d /mnt/media_rw/internal_sdcard ] && [ -w /mnt/media_rw/internal_sdcard ] && echo /mnt/media_rw/internal_sdcard; " +
            "true"
        return shell.rootText(cmd).lines().map { it.trim() }.filter { it.startsWith("/") }
    }

    fun writeReport(path: String, markdown: String) {
        require('\'' !in path) { "path must not contain single quotes" }
        shell.writeFile(path, markdown.toByteArray(Charsets.UTF_8))
        val check = shell.rootText("wc -c < \"$path\" 2>/dev/null || echo 0").trim()
        val size = check.toLongOrNull() ?: 0L
        if (size <= 0L) throw IOException("отчёт не записался: $path")
    }

    private fun readProps(): Map<String, String> {
        val out = shell.rootText(
            "echo MODEL=`getprop ro.product.model`; " +
                "echo DISPLAY=`getprop ro.build.display.id`; " +
                "echo INC=`getprop ro.build.version.incremental`; " +
                "echo HW=`getprop ro.hardware`",
        )
        val map = mutableMapOf<String, String>()
        for (line in out.lines()) {
            val t = line.trim()
            when {
                t.startsWith("MODEL=") -> map["model"] = t.removePrefix("MODEL=")
                t.startsWith("DISPLAY=") -> map["display"] = t.removePrefix("DISPLAY=")
                t.startsWith("INC=") -> map["incremental"] = t.removePrefix("INC=")
                t.startsWith("HW=") -> map["hw"] = t.removePrefix("HW=")
            }
        }
        return map
    }

    private fun readImages(): GptImages {
        val disk = shell.rootText("cat /sys/block/mmcblk0/size").trim().toLong()
        val head = shell.readSectors(device, 0, 2)
        val header = Gpt.parseHeader(head, SECTOR)
        val tailSectors = disk - header.lastUsable - 1
        if (header.firstUsable !in 34..2048 || tailSectors !in 33..2048) {
            throw IOException("необычная разметка: first ${header.firstUsable}, last ${header.lastUsable}")
        }
        return GptImages(
            primary = shell.readSectors(device, 0, header.firstUsable),
            secondary = shell.readSectors(device, header.lastUsable + 1, tailSectors),
            diskSectors = disk,
        )
    }

    private fun scanPartition(
        entry: GptEntry,
        all: List<GptEntry>,
        mounts: List<MountRow>,
    ): PartitionInfo {
        val neighbor = all.firstOrNull { it.first == entry.last + 1 }?.name
            ?: if (entry.last >= all.maxOf { it.last }) "хвост" else null
        val mount = findMount(entry.name, mounts)
        val detailed = entry.name in DETAIL_PARTITIONS
        var usedKiB: Long? = null
        var freeKiB: Long? = null
        var sizeKiB: Long? = null
        var topDirs = emptyList<Pair<String, Long>>()
        val notes = mutableListOf<String>()
        var samples = emptyList<String>()
        var fotaKiB: Long? = null
        var fstype = mount?.fstype
        var mountPoint = mount?.point
        var mountOpts = mount?.opts
        var mounted = mount != null
        var tempMount: String? = null

        if (detailed) {
            if (mount == null) {
                // Optional read-only mount of the block device for shallow scan (media etc.).
                val tmp = "/data/local/tmp/tbox_part_inv_${entry.name}"
                val blk = "/dev/block/${entry.name}"
                if (tryRoMount(blk, tmp)) {
                    mounted = true
                    mountPoint = tmp
                    tempMount = tmp
                    fstype = fstype ?: probeFstype(tmp)
                    mountOpts = "ro"
                    notes += "временный mount -o ro для сканирования"
                }
            }
            val scanRoot = mountPoint ?: guessMountPoint(entry.name)
            if (scanRoot != null && (mounted || mountPoint != null)) {
                val df = readDf(scanRoot)
                usedKiB = df?.used
                freeKiB = df?.free
                sizeKiB = df?.size
                topDirs = readTopDirs(scanRoot, entry.name)
                notes += heuristicNotes(entry.name, scanRoot, topDirs)
                samples = readSamples(scanRoot, entry.name)
                if (entry.name == "cache") {
                    fotaKiB = readDuKiB("$scanRoot/fota")
                    val fota = fotaKiB
                    if (fota != null && fota > 0) {
                        notes += "есть /cache/fota (~${fota / 1024} МиБ)"
                        samples = samples + listFotaSamples(scanRoot)
                    }
                }
            } else {
                notes += "не смонтирован"
                val exists = shell.root("[ -b /dev/block/${entry.name} ]").exitCode == 0
                if (!exists) notes += "нет /dev/block/${entry.name}"
            }
            tempMount?.let { tmp ->
                runCatching { shell.root("umount \"$tmp\" 2>/dev/null; rmdir \"$tmp\" 2>/dev/null; true") }
            }
        }

        return PartitionInfo(
            name = entry.name,
            firstLba = entry.first,
            lastLba = entry.last,
            sectors = entry.sectors,
            neighborRight = neighbor,
            fstype = fstype,
            mountPoint = mountPoint,
            mountOptions = mountOpts,
            mounted = mounted,
            usedKiB = usedKiB,
            freeKiB = freeKiB,
            sizeKiB = sizeKiB,
            topDirs = topDirs,
            notes = notes.distinct(),
            sampleFiles = samples.distinct().take(12),
            fotaKiB = fotaKiB,
        )
    }

    private fun tryRoMount(block: String, tmp: String): Boolean {
        if ('\'' in block || '\'' in tmp) return false
        val r = shell.root(
            "mkdir -p \"$tmp\" && " +
                "mount -o ro,noload \"$block\" \"$tmp\" 2>/dev/null || " +
                "mount -o ro \"$block\" \"$tmp\" 2>/dev/null",
        )
        return r.exitCode == 0
    }

    private fun probeFstype(mp: String): String? {
        val line = shell.rootText("grep \" $mp \" /proc/mounts 2>/dev/null | head -1; true").trim()
        if (line.isEmpty()) return null
        return line.split(Regex("\\s+")).getOrNull(2)
    }

    private fun readDf(mp: String): Df? {
        if ('\'' in mp) return null
        val line = shell.rootText("df -k \"$mp\" 2>/dev/null | tail -1; true").trim()
        val cols = line.split(Regex("\\s+"))
        if (cols.size < 4) return null
        val size = cols[1].toLongOrNull() ?: return null
        val used = cols[2].toLongOrNull() ?: return null
        val free = cols[3].toLongOrNull() ?: return null
        return Df(size, used, free)
    }

    private fun readDuKiB(path: String): Long? {
        if ('\'' in path) return null
        val out = shell.rootText("du -sk \"$path\" 2>/dev/null | awk \"{print \$1}\"; true").trim()
        return out.lines().firstOrNull { it.isNotBlank() }?.toLongOrNull()
    }

    private fun readTopDirs(mp: String, partName: String): List<Pair<String, Long>> {
        if ('\'' in mp) return emptyList()
        // Shallow: only immediate children. Cap lines. Avoid deep /data/data walk.
        val cmd = if (partName == "userdata") {
            // Named top-level dirs only — do not recurse into /data/data packages.
            "cd \"$mp\" && for d in app data dalvik-cache media system user misc local vendor property; " +
                "do [ -e \"\$d\" ] && du -sk \"\$d\" 2>/dev/null; done; true"
        } else {
            "cd \"$mp\" && du -sk * 2>/dev/null | sort -nr | head -n 20; true"
        }
        val out = shell.rootText(cmd)
        return parseDuLines(out).take(20)
    }

    private fun readSamples(mp: String, partName: String): List<String> {
        if ('\'' in mp) return emptyList()
        val findCmd = when (partName) {
            "media", "intsd" ->
                "find \"$mp\" -maxdepth 2 -type f \\( -name \"*.mp3\" -o -name \"*.mp4\" -o -name \"*.mkv\" " +
                    "-o -name \"*.jpg\" -o -name \"*.png\" \\) 2>/dev/null | head -n 8; " +
                    "ls -1 \"$mp\" 2>/dev/null | head -n 15; true"
            "userdata" ->
                "ls -1 \"$mp\" 2>/dev/null | head -n 20; true"
            else ->
                "ls -1 \"$mp\" 2>/dev/null | head -n 20; true"
        }
        return shell.rootText(findCmd).lines().map { it.trim() }.filter { it.isNotEmpty() }.take(20)
    }

    private fun listFotaSamples(cacheMp: String): List<String> {
        if ('\'' in cacheMp) return emptyList()
        return shell.rootText(
            "ls -1 \"$cacheMp/fota\" 2>/dev/null | head -n 8; true",
        ).lines().map { it.trim() }.filter { it.isNotEmpty() }.map { "fota/$it" }
    }

    private fun heuristicNotes(partName: String, mp: String, top: List<Pair<String, Long>>): List<String> {
        val notes = mutableListOf<String>()
        val names = top.map { it.first }.toSet()
        val listing = runCatching {
            shell.rootText("ls -1 \"$mp\" 2>/dev/null | head -n 40; true")
        }.getOrDefault("").lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val all = names + listing
        when (partName) {
            "media" -> {
                if (top.isEmpty() || top.sumOf { it.second } < 64 * 1024) notes += "почти пуст"
                if ("LOST.DIR" in all) notes += "есть LOST.DIR"
                if (all.none { it.contains("Maps", true) || it.contains("navi", true) }) {
                    notes += "нет Maps/navi на верхнем уровне"
                }
                val mediaFiles = runCatching {
                    shell.rootText(
                        "find \"$mp\" -maxdepth 2 -type f \\( -name \"*.mp3\" -o -name \"*.mp4\" \\) 2>/dev/null | head -n 3; true",
                    )
                }.getOrDefault("")
                if (mediaFiles.lines().none { it.isNotBlank() }) {
                    notes += "аудио/видео не найдены (maxdepth 2)"
                }
            }
            "intsd" -> {
                if (all.any { it.equals("Maps", true) }) notes += "есть Maps (навигация/карты)"
                if (all.any { it.equals("Music", true) }) notes += "есть Music"
                if (all.any { it.equals("Movies", true) }) notes += "есть Movies"
                if (all.any { it.equals("Android", true) }) notes += "есть Android (пользовательское)"
                if (all.any { it.equals("Download", true) }) notes += "есть Download"
            }
            "userdata" -> notes += "обычный Android /data; APK в app/"
            "cache" -> { /* fota handled separately */ }
            "map" -> notes += "служебный раздел map (не путать с картами на intsd)"
            "miscbk" -> notes += "OEM backup/калибровки — не трогать без отдельной задачи"
        }
        return notes
    }

    private fun findMount(partName: String, mounts: List<MountRow>): MountRow? {
        val guess = guessMountPoint(partName)
        mounts.firstOrNull { it.point == guess }?.let { return it }
        mounts.firstOrNull { it.device.endsWith("/$partName") || it.device == partName }?.let { return it }
        mounts.firstOrNull { it.device.contains(partName) && it.point != "/" }?.let { return it }
        return null
    }

    private fun guessMountPoint(name: String): String? = when (name) {
        "userdata" -> "/data"
        "cache" -> "/cache"
        "intsd" -> "/mnt/media_rw/internal_sdcard"
        "media" -> null // discovered from mounts / optional ro mount
        else -> null
    }

    companion object {
        const val DEVICE = "/dev/block/mmcblk0"
        val DETAIL_PARTITIONS = setOf("userdata", "media", "intsd", "cache", "map", "miscbk")

        data class MountRow(val device: String, val point: String, val fstype: String, val opts: String)
        data class Df(val size: Long, val used: Long, val free: Long)

        fun parseMounts(text: String): List<MountRow> = text.lines().mapNotNull { line ->
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size < 4) return@mapNotNull null
            MountRow(cols[0], cols[1], cols[2], cols[3])
        }

        fun parseDuLines(text: String): List<Pair<String, Long>> = text.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty()) return@mapNotNull null
            val cols = t.split(Regex("\\s+"), limit = 2)
            if (cols.size < 2) return@mapNotNull null
            val kib = cols[0].toLongOrNull() ?: return@mapNotNull null
            val name = cols[1].trim().removePrefix("./").substringAfterLast('/')
            if (name.isEmpty() || name == ".") return@mapNotNull null
            name to kib
        }.sortedByDescending { it.second }
    }
}
