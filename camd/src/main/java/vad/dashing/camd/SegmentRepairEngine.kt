package vad.dashing.camd

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Android-side half of the 0.8.7 repair: writes a playable MP4 around the samples
 * [SegmentRepair] found in a moov-less segment. Codec parameters (csd-0/csd-1,
 * resolution) come from the most recent intact segment of the same camera — the fixed
 * pipeline encodes everything with the same settings, so a sibling file always matches;
 * timestamps are synthesized at [TARGET_FPS]. The source file is deleted only after a
 * successful remux (rename `.mp4` → `.broken` keeps the evidence on failure).
 */
internal object SegmentRepairEngine {

    private const val TARGET_FPS = 20
    private const val MAX_SAMPLES = 12000 // 10 min at 20 fps — sanity cap

    /** Result row for one file. */
    data class Outcome(val file: File, val ok: Boolean, val detail: String)

    /**
     * Rebuilds [candidate] next to the source as `<name без .mp4>.fixed.mp4` and, on
     * success, replaces the source with it. Returns false when codec params are missing
     * (no intact sibling) or remux fails.
     */
    fun repair(
        candidate: SegmentRepair.Candidate,
        codecParams: MediaFormat,
        onProgress: (String) -> Unit = {},
    ): Outcome {
        val file = candidate.file
        val samples = SegmentRepair.scanSamples(file, candidate.mdat)
        if (samples.isEmpty()) return Outcome(file, false, "mdat не разобран")
        if (samples.size > MAX_SAMPLES) return Outcome(file, false, "слишком много кадров (${samples.size})")

        val width = codecParams.getInteger(MediaFormat.KEY_WIDTH)
        val height = codecParams.getInteger(MediaFormat.KEY_HEIGHT)
        val csd0 = codecParams.getByteBuffer("csd-0") ?: return Outcome(file, false, "нет csd-0 в образце")
        val csd1 = codecParams.getByteBuffer("csd-1") ?: return Outcome(file, false, "нет csd-1 в образце")

        val target = File(file.parentFile, file.name.replace(Regex("\\.mp4$"), "") + ".fixed.mp4")
        val targetTmp = File(file.parentFile, target.name + ".tmp")
        runCatching { targetTmp.delete() }
        runCatching { target.delete() }

        var muxer: MediaMuxer? = null
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_FRAME_RATE, TARGET_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                setInteger(MediaFormat.KEY_DURATION, samples.size * 1_000_000 / TARGET_FPS)
                setByteBuffer("csd-0", csd0.duplicate())
                setByteBuffer("csd-1", csd1.duplicate())
            }
            muxer = MediaMuxer(targetTmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val track = muxer.addTrack(format)
            muxer.start()

            val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var ptsUs = 0L
            var written = 0
            RandomAccessFile(file, "r").use { raf ->
                for (sample in samples) {
                    if (sample.size > buffer.capacity()) continue
                    buffer.clear()
                    raf.seek(sample.offset)
                    val channel = raf.channel
                    var read = 0L
                    while (read < sample.size) {
                        buffer.position(buffer.position() + read.toInt())
                        val chunk = channel.read(buffer)
                        if (chunk <= 0) break
                        read += chunk
                    }
                    if (read < sample.size) return Outcome(file, false, "обрыв чтения в mdat")
                    buffer.flip()
                    info.set(0, sample.size, ptsUs, if (sample.keyframe) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(track, buffer, info)
                    ptsUs += 1_000_000L / TARGET_FPS
                    written++
                }
            }
            muxer.stop()
            muxer.release()
            muxer = null
            if (written == 0) {
                runCatching { targetTmp.delete() }
                return Outcome(file, false, "ни одного кадра не записано")
            }
            if (!targetTmp.renameTo(target)) {
                return Outcome(file, false, "не удалось переименовать результат")
            }
            // Success: replace the broken file with the repaired one.
            if (file.delete() && !target.renameTo(File(file.parentFile, file.name))) {
                // Rare: keep the repaired file at least under .fixed.mp4.
                return Outcome(file, true, "восстановлен (остался как ${target.name})")
            }
            return Outcome(file, true, "${written} кадров")
        } catch (error: Throwable) {
            runCatching { targetTmp.delete() }
            runCatching { muxer?.release() }
            // Keep the evidence: rename the unplayable source out of the way.
            val broken = File(file.parentFile, file.name.replace(Regex("\\.mp4$"), "") + ".broken.mp4")
            runCatching {
                if (file.exists() && !broken.exists()) file.renameTo(broken)
            }
            return Outcome(file, false, error.message ?: error.javaClass.simpleName)
        } finally {
            if (muxer != null) runCatching { muxer.release() }
        }
    }

    /**
     * MediaFormat with the csd and size of an intact segment of the same camera:
     * MediaExtractor over the newest intact sibling of [candidate].
     */
    fun codecParamsFor(candidate: SegmentRepair.Candidate, dir: File): MediaFormat? {
        val inputId = RecorderFiles.segmentInput(candidate.file) ?: return null
        val sibling = RecorderFiles.listSegments(dir)
            .lastOrNull { file ->
                RecorderFiles.segmentInput(file) == inputId &&
                    file != candidate.file &&
                    SegmentRepair.findMdat(file) == null && // moov present → intact
                    file.length() > 0
            } ?: return null
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(sibling.absolutePath)
            try {
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    if (format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_AVC) {
                        return format
                    }
                }
                null
            } finally {
                extractor.release()
            }
        } catch (_: Throwable) {
            null
        }
    }
}
