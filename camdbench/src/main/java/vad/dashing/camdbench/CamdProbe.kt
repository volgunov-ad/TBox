package vad.dashing.camdbench

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Socket

/** Read-only look at the installed camd: version, STATUS, grabber log, finished segments. */
object CamdProbe {

    private const val CAMD_PACKAGE = "vad.dashing.camd"
    private const val API_PORT = 8770

    fun camdVersion(context: Context): String = try {
        val info = context.packageManager.getPackageInfo(CAMD_PACKAGE, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else 0L
        "${info.versionName} ($code)"
    } catch (_: PackageManager.NameNotFoundException) {
        "не установлен"
    }

    fun camdVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(CAMD_PACKAGE, 0).versionName ?: "unknown"
    } catch (_: PackageManager.NameNotFoundException) {
        "none"
    }

    fun grabVersion(): String =
        runCatching { File(BenchScripts.CAMD_GRAB_VERSION).readText().trim() }.getOrNull()?.ifBlank { null } ?: "?"

    fun status(): String = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", API_PORT), 2_000)
            socket.soTimeout = 3_000
            socket.getOutputStream().write("STATUS\n".toByteArray())
            socket.getOutputStream().flush()
            socket.getInputStream().bufferedReader().readLine()?.trim().orEmpty().ifBlank { "нет ответа" }
        }
    } catch (error: Exception) {
        "camd не отвечает на 127.0.0.1:$API_PORT (${error.javaClass.simpleName})"
    }

    fun grabLogLength(): Long = runCatching { File(BenchScripts.CAMD_GRAB_LOG).length() }.getOrDefault(0L)

    /** Grabber log written since [offset]; a restarted grabber truncates the log, then read it all. */
    fun grabLogSince(offset: Long): String = runCatching {
        RandomAccessFile(BenchScripts.CAMD_GRAB_LOG, "r").use { file ->
            val start = if (file.length() < offset) 0L else offset
            val bytes = ByteArray((file.length() - start).coerceIn(0L, 4_000_000L).toInt())
            file.seek(start)
            file.readFully(bytes)
            String(bytes)
        }
    }.getOrDefault("")

    /**
     * Frame count and timestamp span of every finished segment that was closed during the
     * run. A segment still being written has no moov: the extractor rejects it.
     */
    fun segmentFps(folder: String?, fromWallMs: Long, toWallMs: Long): List<BenchLogs.SegmentFps> {
        if (folder == null) return emptyList()
        val files = File(folder).listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && BenchLogs.segmentInput(it.name) != null }
            .filter { it.lastModified() in fromWallMs..toWallMs }
            .sortedBy { it.name }
            .mapNotNull { file -> measure(file) }
    }

    private fun measure(file: File): BenchLogs.SegmentFps? {
        val input = BenchLogs.segmentInput(file.name) ?: return null
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return null
            extractor.selectTrack(track)
            var frames = 0
            var first = -1L
            var last = -1L
            while (true) {
                val time = extractor.sampleTime
                if (time < 0) break
                if (first < 0 || time < first) first = time
                if (time > last) last = time
                frames += 1
                if (!extractor.advance()) break
            }
            if (frames < 2) null else BenchLogs.SegmentFps(input, file.name, frames, (last - first) / 1_000_000.0)
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }
}
