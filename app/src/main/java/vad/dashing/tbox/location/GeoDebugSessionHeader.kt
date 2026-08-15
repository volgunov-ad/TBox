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

    fun commentLines(appVer: String, mapsLabel: String, matchPeriodMs: Long): String =
        "# appVer=$appVer\n" +
            "# maps=$mapsLabel\n" +
            "# matchPeriodMs=$matchPeriodMs\n"
}
