package vad.dashing.camd

import android.content.SharedPreferences
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale

/**
 * Headless dashcam pipeline for one camera: reads the grabber's shared UYVY file,
 * renders each frame through the same correction shader as the preview into an
 * hardware AVC encoder input surface, and writes short crash-safe MP4 segments
 * ([RecorderFiles.SEGMENT_MS]): MediaMuxer places moov at each segment close, so a
 * power cut loses at most one in-flight segment (~[SEGMENT_MS], user-selectable, 0.8.6),
 * and every finished file remains playable.
 * A failure of one segment (muxer/IO error on a corrupted file) skips that segment and
 * starts the next one — the recording thread survives ([onSegmentError]). The thread
 * parks itself only when the media folder is unavailable ([nextSegment] returns null),
 * retrying every few seconds until stopped.
 */
internal class RecorderEngine(
    private val inputId: Int,
    private val frame: File,
    private val prefs: SharedPreferences,
    private val nextSegment: (Int) -> File?,
    private val onSegmentClosed: (File) -> Unit,
    private val onWaiting: (Boolean, String) -> Unit,
    private val onError: (String) -> Unit,
    private val onSegmentError: (String) -> Unit = onError,
    /** Segment wall-clock length; re-read per segment, so a settings change applies to the next segment. */
    private val segmentMs: () -> Long = { RecorderFiles.SEGMENT_MS },
    /** Records every [fpsDivisor]-th sensor frame ([RecorderFps]). */
    private val fpsDivisor: Int = 1,
) : Thread("camd-rec-$inputId") {

    private val frameMs = RecorderFps.frameMs(fpsDivisor)

    @Volatile
    private var running = true

    /**
     * Set by the emergency save ([RecorderController.requestEmergencySave]): the current
     * in-flight segment closes immediately through the normal finally path —
     * signalEndOfInputStream → muxer.stop() writes the moov → onSegmentClosed — and the
     * next segment starts fresh. Delay is one render frame (~40 ms).
     */
    @Volatile
    private var closeNow = false

    /** Segment currently being written (file exists on disk but has no moov yet). */
    @Volatile
    private var currentSegment: File? = null

    /** Number of segments fully closed with a moov — the SOS completion signal. */
    private val closedCount = java.util.concurrent.atomic.AtomicInteger(0)

    /** Last segment that got its moov (the SOS "current" file to copy). */
    @Volatile
    private var lastClosedSegment: File? = null

    fun requestStop() {
        running = false
    }

    fun requestCloseNow() {
        closeNow = true
    }

    /** The in-flight segment file, or null while parked between segments. */
    fun currentSegmentFile(): File? = currentSegment

    /** Monotonic count of successfully closed segments. */
    fun closedSegmentCount(): Int = closedCount.get()

    /** The most recently closed segment, or null if none has been closed yet. */
    fun lastClosedSegmentFile(): File? = lastClosedSegment

    override fun run() {
        var gl: Gl? = null
        try {
            while (running) {
                val segment = nextSegment(inputId)
                if (segment == null) {
                    onWaiting(true, "камера $inputId: жду носитель")
                    sleep(5_000)
                    continue
                }
                onWaiting(false, "")
                if (gl == null) {
                    gl = createGl()
                    if (gl == null) {
                        onError("камера $inputId: не удалось создать GL для записи")
                        return
                    }
                }
                try {
                    recordSegment(segment, gl)
                } catch (error: InterruptedException) {
                    // Stop request: recordSegment's finally already closed (or removed) the
                    // segment; deleting here would drop the last finished video.
                    throw error
                } catch (error: Throwable) {
                    // A corrupted segment (muxer failure, IO error) must not kill the
                    // recording thread: recordSegment's finally either closes the file
                    // with a moov or removes it, the loop starts the next segment after
                    // a short backoff so the skip reason stays visible in the UI.
                    if (!running) break
                    onSegmentError("камера $inputId: сегмент пропущен — ${error.message ?: error.javaClass.simpleName}")
                    sleep(3_000)
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            if (running) onError("камера $inputId: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            val finished = gl
            if (finished != null) release(finished)
        }
    }

    private fun recordSegment(segment: File, gl: Gl) {
        var codec: MediaCodec? = null
        var surface: EGLSurface? = null
        var muxer: MediaMuxer? = null
        var track = -1
        var muxerStarted = false
        var ptsBase = System.nanoTime() / 1000

        fun drain(eos: Boolean): MediaFormat? {
            val c = codec ?: return null
            val info = MediaCodec.BufferInfo()
            var attempts = 0
            while (attempts++ < 1000) {
                val index = c.dequeueOutputBuffer(info, if (eos) 10_000L else 0L)
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!eos) return null
                    continue
                }
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) return c.outputFormat
                if (index < 0) {
                    if (eos) continue
                    return null
                }
                val m = muxer
                if (m != null && muxerStarted && track >= 0) {
                    val buffer = c.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        if (info.presentationTimeUs <= 0) {
                            info.presentationTimeUs = System.nanoTime() / 1000 - ptsBase
                        }
                        m.writeSampleData(track, buffer, info)
                    }
                }
                c.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    muxerStarted = false
                    return null
                }
            }
            return null
        }

        var source: RandomAccessFile? = null
        var mapped: ByteBuffer? = null
        var mappedIno = 0L
        var mappedLength = 0L
        var pixels: ByteBuffer? = null
        var lastSeq = 0
        var lastFrameNo = 0
        var uploadedWidth = 0
        var uploadedHeight = 0
        var correction = CamdLensCorrectionStore.load(prefs, inputId)
        var lastLensMs = 0L
        var lastFrameMs = System.currentTimeMillis()
        var lastStatMs = 0L
        var frames = 0
        var skipped = 0
        val segmentLengthMs = segmentMs()
        val segmentStart = System.currentTimeMillis()
        closeNow = false

        try {
            while (running && !closeNow && System.currentTimeMillis() - segmentStart < segmentLengthMs) {
                val statNow = System.currentTimeMillis()
                val recheck = statNow - lastStatMs >= RecorderPacing.STAT_INTERVAL_MS
                if (recheck) lastStatMs = statNow
                val read = readFrame(source, mapped, mappedIno, mappedLength, pixels, lastSeq, lastFrameNo, recheck)
                source = read.source
                mapped = read.buffer
                mappedIno = read.inode
                mappedLength = read.length
                pixels = read.pixels
                lastSeq = read.seenSeq

                if (codec == null) {
                    if (read.slot == null) {
                        Thread.sleep(30)
                        continue
                    }
                    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, read.slot.width, read.slot.height).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        setInteger(MediaFormat.KEY_BIT_RATE, RecorderFps.bitRate(fpsDivisor))
                        setInteger(MediaFormat.KEY_FRAME_RATE, RecorderFps.encoderFps(fpsDivisor))
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                    }
                    val created = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    created.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    val inputSurface = created.createInputSurface()
                    created.start()
                    codec = created
                    surface = EGL14.eglCreateWindowSurface(gl.display, gl.config, inputSurface, intArrayOf(EGL14.EGL_NONE), 0)
                    if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
                        throw IllegalStateException("encoder surface")
                    }
                    EGL14.eglMakeCurrent(gl.display, surface, surface, gl.context)
                    gl.currentSurface = surface
                    uploadedWidth = 0
                    uploadedHeight = 0
                    ptsBase = System.nanoTime() / 1000
                    // MPEG-4 moov is finalized in muxer.stop() at segment end — keep segments short.
                    currentSegment = segment
                    muxer = MediaMuxer(segment.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                }

                val now = System.currentTimeMillis()
                if (read.slot != null && read.seqChanged) {
                    if (now - lastLensMs >= 500) {
                        correction = CamdLensCorrectionStore.load(prefs, inputId)
                        lastLensMs = now
                    }
                    lastFrameMs = now
                    if (frames > 0) skipped += RecorderPacing.skippedFrames(read.slot.frameNo, lastFrameNo, fpsDivisor)
                    lastFrameNo = read.slot.frameNo
                    frames += 1
                    uploadAndDraw(gl, read.slot, correction, pixels!!)
                    val format = drain(eos = false)
                    if (format != null && track == -1 && muxer != null) {
                        track = muxer.addTrack(format)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                Thread.sleep(RecorderPacing.sleepMs(System.currentTimeMillis() - lastFrameMs, frameMs))
            }
        } finally {
            try {
                codec?.signalEndOfInputStream()
                drain(eos = true)
            } catch (_: Throwable) {
            }
            try {
                val m = muxer
                if (track >= 0 && m != null) {
                    m.stop()
                    onSegmentClosed(segment)
                    closedCount.incrementAndGet()
                    lastClosedSegment = segment
                } else {
                    segment.delete()
                }
            } catch (_: Throwable) {
                segment.delete()
            }
            val seconds = (System.currentTimeMillis() - segmentStart) / 1000.0
            Log.i(
                LOG_TAG,
                "cam$inputId ${segment.name}: кадров $frames, пропущено $skipped, " +
                    "%.1f с".format(Locale.US, seconds),
            )
            currentSegment = null
            runCatching { muxer?.release() }
            runCatching { codec?.release() }
            if (surface != null) {
                EGL14.eglMakeCurrent(gl.display, gl.pbuffer, gl.pbuffer, gl.context)
                gl.currentSurface = gl.pbuffer
                EGL14.eglDestroySurface(gl.display, surface)
            }
            runCatching { source?.close() }
        }
    }

    private fun uploadAndDraw(gl: Gl, slot: UyvySlot, correction: CamdLensCorrection, pixels: ByteBuffer) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, gl.texture)
        val buffer = pixels.also { it.position(0) }
        if (gl.uploadedWidth != slot.width || gl.uploadedHeight != slot.height) {
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                slot.width / 2, slot.height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer,
            )
            gl.uploadedWidth = slot.width
            gl.uploadedHeight = slot.height
        } else {
            GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D, 0, 0, 0,
                slot.width / 2, slot.height,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer,
            )
        }
        GLES20.glViewport(0, 0, slot.width, slot.height)
        GLES20.glUseProgram(gl.program)
        GLES20.glUniform1i(gl.uTex, 0)
        GLES20.glUniform1f(gl.uWidth, slot.width.toFloat())
        GLES20.glUniform1f(gl.uRot, 0f)
        GLES20.glUniform1f(gl.uFlipH, 0f)
        GLES20.glUniform1f(gl.uFlipV, 0f)
        GLES20.glUniform1f(gl.uK1, correction.k1)
        GLES20.glUniform1f(gl.uK2, correction.k2)
        GLES20.glUniform1f(gl.uCx, correction.cx)
        GLES20.glUniform1f(gl.uCy, correction.cy)
        GLES20.glUniform1f(gl.uCropOn, 0f)
        GLES20.glUniform4f(gl.uCrop, 0f, 0f, 1f, 1f)
        GLES20.glUniform1f(gl.uTiltV, correction.tiltV)
        GLES20.glUniform1f(gl.uTiltH, correction.tiltH)
        GLES20.glUniform1f(gl.uShape, 0f)
        GLES20.glUniform1f(gl.uAspect, slot.width.toFloat() / slot.height.toFloat())
        GLES20.glUniform1f(gl.uAA, 1.5f / minOf(slot.width, slot.height))
        gl.quad.position(0)
        GLES20.glVertexAttribPointer(gl.aPos, 2, GLES20.GL_FLOAT, false, 0, gl.quad)
        GLES20.glEnableVertexAttribArray(gl.aPos)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGL14.eglSwapBuffers(gl.display, gl.currentSurface)
    }

    // GL ----------------------------------------------------------------------

    private class Gl(
        val display: EGLDisplay,
        val context: EGLContext,
        val pbuffer: EGLSurface,
        val config: EGLConfig,
        val program: Int,
        val texture: Int,
        val aPos: Int,
        val quad: java.nio.FloatBuffer,
        val uTex: Int,
        val uWidth: Int,
        val uRot: Int,
        val uFlipH: Int,
        val uFlipV: Int,
        val uK1: Int,
        val uK2: Int,
        val uCx: Int,
        val uCy: Int,
        val uCropOn: Int,
        val uCrop: Int,
        val uTiltV: Int,
        val uTiltH: Int,
        val uShape: Int,
        val uAspect: Int,
        val uAA: Int,
    ) {
        var uploadedWidth = 0
        var uploadedHeight = 0
        var currentSurface: EGLSurface? = null
    }

    private fun createGl(): Gl? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val config = configs.takeIf {
            EGL14.eglChooseConfig(display, attribs, 0, it, 0, 1, count, 0) && count[0] > 0
        }?.get(0)
        if (config == null) {
            EGL14.eglTerminate(display)
            return null
        }
        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        if (context == null || context == EGL14.EGL_NO_CONTEXT) {
            EGL14.eglTerminate(display)
            return null
        }
        val pbuffer = EGL14.eglCreatePbufferSurface(
            display, config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        val program = if (pbuffer != null && pbuffer != EGL14.EGL_NO_SURFACE &&
            EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        ) {
            buildProgram()
        } else {
            0
        }
        if (program == 0) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (pbuffer != null && pbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, pbuffer)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            return null
        }
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        quad.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        quad.position(0)
        val gl = Gl(
            display, context, pbuffer, config, program, texture[0],
            GLES20.glGetAttribLocation(program, "aPos"),
            quad,
            GLES20.glGetUniformLocation(program, "uTex"),
            GLES20.glGetUniformLocation(program, "uWidth"),
            GLES20.glGetUniformLocation(program, "uRot"),
            GLES20.glGetUniformLocation(program, "uFlipH"),
            GLES20.glGetUniformLocation(program, "uFlipV"),
            GLES20.glGetUniformLocation(program, "uK1"),
            GLES20.glGetUniformLocation(program, "uK2"),
            GLES20.glGetUniformLocation(program, "uCx"),
            GLES20.glGetUniformLocation(program, "uCy"),
            GLES20.glGetUniformLocation(program, "uCropOn"),
            GLES20.glGetUniformLocation(program, "uCrop"),
            GLES20.glGetUniformLocation(program, "uTiltV"),
            GLES20.glGetUniformLocation(program, "uTiltH"),
            GLES20.glGetUniformLocation(program, "uShape"),
            GLES20.glGetUniformLocation(program, "uAspect"),
            GLES20.glGetUniformLocation(program, "uAA"),
        )
        gl.currentSurface = pbuffer
        return gl
    }

    private fun buildProgram(): Int {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, UyvyRenderer.VERTEX)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, UyvyRenderer.FRAGMENT)
        if (vertex == 0 || fragment == 0) return 0
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        if (linked[0] == 0) {
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun release(gl: Gl) {
        runCatching {
            GLES20.glDeleteTextures(1, intArrayOf(gl.texture), 0)
            if (gl.program != 0) GLES20.glDeleteProgram(gl.program)
            EGL14.eglMakeCurrent(gl.display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(gl.display, gl.pbuffer)
            EGL14.eglDestroyContext(gl.display, gl.context)
            EGL14.eglTerminate(gl.display)
        }
    }

    // Shared UYVY frame source (same protocol as UyvyRenderer). ----------------

    private class FrameRead(
        val source: RandomAccessFile?,
        val buffer: ByteBuffer?,
        val inode: Long,
        val length: Long,
        val pixels: ByteBuffer?,
        val slot: UyvySlot?,
        val seqChanged: Boolean,
        /** Newest grabber seq looked at, including a frame passed over for the rate. */
        val seenSeq: Int,
    )

    private fun readFrame(
        source: RandomAccessFile?,
        mapped: ByteBuffer?,
        mappedIno: Long,
        mappedLength: Long,
        pixels: ByteBuffer?,
        lastSeq: Int,
        lastFrameNo: Int,
        recheck: Boolean,
    ): FrameRead {
        var src = source
        var buf = mapped
        var ino = mappedIno
        var len = mappedLength
        if (src != null && buf != null && recheck) {
            val stat = runCatching { android.system.Os.stat(frame.absolutePath) }.getOrNull()
            if (stat == null || stat.st_ino != ino || stat.st_size != len) {
                runCatching { src.close() }
                src = null
                buf = null
                ino = 0
                len = 0
            }
        }
        if (src == null) {
            if (!frame.isFile) return FrameRead(null, null, 0, 0, pixels, null, false, lastSeq)
            val probe = runCatching { RandomAccessFile(frame, "r") }.getOrNull()
                ?: return FrameRead(null, null, 0, 0, pixels, null, false, lastSeq)
            val stat = runCatching { android.system.Os.fstat(probe.fd) }.getOrNull()
            val length = runCatching { probe.length() }.getOrDefault(0L)
            if (stat == null || length < UYVY_HEADER) {
                runCatching { probe.close() }
                return FrameRead(null, null, 0, 0, pixels, null, false, lastSeq)
            }
            val mappedNew = runCatching {
                probe.channel.map(FileChannel.MapMode.READ_ONLY, 0, length).order(ByteOrder.LITTLE_ENDIAN)
            }.getOrNull()
            if (mappedNew == null) {
                runCatching { probe.close() }
                return FrameRead(null, null, 0, 0, pixels, null, false, lastSeq)
            }
            src = probe
            buf = mappedNew
            ino = stat.st_ino
            len = length
        }
        val mappedBuf = buf ?: return FrameRead(src, buf, ino, len, pixels, null, false, lastSeq)
        val slot = readUyvySlot(mappedBuf)
        if (slot == null || slot.seq == lastSeq) {
            return FrameRead(src, buf, ino, len, pixels, null, false, lastSeq)
        }
        if (!RecorderPacing.accept(slot.frameNo, lastFrameNo, fpsDivisor)) {
            return FrameRead(src, buf, ino, len, pixels, null, false, slot.seq)
        }
        val bytes = slot.slotBytes
        val copy = pixels?.takeIf { it.capacity() == bytes }
            ?: ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        copy.clear()
        val view = mappedBuf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        view.position(UYVY_HEADER + slot.slot * bytes)
        view.limit(UYVY_HEADER + slot.slot * bytes + bytes)
        copy.put(view)
        if (mappedBuf.getInt(16) != slot.seq) {
            return FrameRead(src, buf, ino, len, copy, null, false, lastSeq)
        }
        copy.position(0)
        return FrameRead(src, buf, ino, len, copy, slot, true, slot.seq)
    }

    companion object {
        private const val LOG_TAG = "CamdRecorder"
    }
}

/**
 * Poll schedule and frame choice of the recorder thread. After an encoded frame the
 * thread sleeps until shortly before the next one is due and then polls finely, so a
 * frame is neither missed nor waited on with ~100 wakeups per second.
 */
internal object RecorderPacing {
    /** First poll comes this long before the next frame is due; covers grabber loop jitter with several cameras. */
    const val EXPECT_MARGIN_MS = 15L
    const val POLL_MS = 5L
    /** No frame for this long — the input is silent, poll lazily. */
    const val SILENT_MS = 500L
    const val SILENT_POLL_MS = 30L
    /** How often the frame file is re-stat'ed for a grabber reopen (new inode/size). */
    const val STAT_INTERVAL_MS = 1000L

    fun sleepMs(sinceFrameMs: Long, frameMs: Long): Long {
        val expect = frameMs - EXPECT_MARGIN_MS
        return when {
            sinceFrameMs < 0 -> POLL_MS
            sinceFrameMs < expect -> expect - sinceFrameMs
            sinceFrameMs < SILENT_MS.coerceAtLeast(frameMs * 3) -> POLL_MS
            else -> SILENT_POLL_MS
        }
    }

    /**
     * Whether the grabber frame with sensor number [frameNo] is the next one to record.
     * The grabber already thins frames for a camera without a window; with a window open
     * it publishes every frame and the choice is made here. Numbers are 0 from an old
     * grabber and restart after a reopen — such a frame is taken.
     */
    fun accept(frameNo: Int, lastFrameNo: Int, divisor: Int): Boolean =
        divisor <= 1 || frameNo <= 0 || lastFrameNo <= 0 || frameNo <= lastFrameNo ||
            frameNo - lastFrameNo >= divisor

    /** Recording-rate frames lost between two encoded ones. */
    fun skippedFrames(frameNo: Int, lastFrameNo: Int, divisor: Int): Int {
        if (frameNo <= 0 || lastFrameNo <= 0 || frameNo <= lastFrameNo) return 0
        return ((frameNo - lastFrameNo) / divisor.coerceAtLeast(1) - 1).coerceAtLeast(0)
    }
}
