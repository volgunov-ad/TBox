package vad.dashing.tbox.location

import vad.dashing.tbox.location.roadmatch.RoadMapBundle
import java.io.File

/**
 * Session-level geo-debug header: app version, installed road packs, match period.
 */
object GeoDebugSessionHeader {
    fun installedMapsLabel(mapsDir: File?): String {
        if (mapsDir == null || !mapsDir.isDirectory) return "-"
        val dirs = mapsDir.listFiles { file ->
            file.isDirectory && file.name.endsWith(RoadMapBundle.INSTALL_SUFFIX)
        }?.sortedBy { it.name }.orEmpty()
        if (dirs.isEmpty()) return "-"
        return dirs.joinToString(",") { dir ->
            val id = dir.name.removeSuffix(RoadMapBundle.INSTALL_SUFFIX)
            val ver = runCatching { RoadMapBundle.loadIndex(dir).graphVersion }.getOrNull()
            if (ver != null) "$id@$ver" else id
        }
    }

    fun commentLines(
        appVer: String,
        mapsLabel: String,
        matchPeriodMs: Long,
        logPeriodMs: Long = matchPeriodMs,
        maxFileBytes: Long,
        part: Int = 1,
        continuedFrom: String? = null,
    ): String {
        val cont = if (continuedFrom.isNullOrBlank()) {
            ""
        } else {
            "# continuedFrom=$continuedFrom\n"
        }
        return "# appVer=$appVer\n" +
            "# maps=$mapsLabel\n" +
            "# matchPeriodMs=$matchPeriodMs\n" +
            "# logPeriodMs=$logPeriodMs\n" +
            "# maxFileBytes=$maxFileBytes\n" +
            "# part=$part\n" +
            cont
    }
}
