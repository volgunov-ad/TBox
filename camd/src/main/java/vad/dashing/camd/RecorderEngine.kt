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
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Headless dashcam pipeline for one camera: reads the grabber's shared UYVY file,
 * renders each frame through the same correction shader as the preview into an
 * hardware AVC encoder input surface, and writes short crash-safe MP4 segments
 * ([RecorderFiles.SEGMENT_MS]): MediaMuxer places moov at each segment close, so a
 * power cut leaves finished files playable (P1-3).
 * When the media folder is unavailable [nextSegment] returns null and the engine
 * parks itself, retrying every few seconds until stopped.
 */
internal class RecorderEngine(
    private val inputId: Int,
    private val frame: File,
    private val prefs: SharedPreferences,
    private val nextSegment: (Int) -> File?,
    private val onSegmentClosed: (File) -> Unit,
    private val onWaiting: (Boolean, String) -> Unit,
    private val onError: (String) -> Unit,
    /** Segment wall-clock length; re-read per segment, so a settings change applies to the next segment. */
    private val segmentMs: () -> Long = { RecorderFiles.SEGMENT_MS },
) : Thread("camd-rec-$inputId") {

    @Volatile
    private var running = true

    /**
     * Set by the emergency save ([RecorderController.requestEmergencySave]): the current
     * in-flight segment closes immediately through the normal finally path —
     * signalEndOfInputStream → muxer.stop() writes the moov → onSegmentClosed — and the
     * next segment starts fresh. Delay is one render frame (~50 ms).
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
                recordSegment(segment, gl)
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
        var uploadedWidth = 0
        var uploadedHeight = 0
        var correction = CamdLensCorrectionStore.load(prefs, inputId)
        var lastLensMs = 0L
        var lastRenderMs = 0L
        val segmentLengthMs = segmentMs()
        val segmentStart = System.currentTimeMillis()
        closeNow = false

        try {
            while (running && !closeNow && System.currentTimeMillis() - segmentStart < segmentLengthMs) {
                val read = readFrame(source, mapped, mappedIno, mappedLength, pixels, lastSeq)
                source = read.source
                mapped = read.buffer
                mappedIno = read.inode
                mappedLength = read.length
                pixels = read.pixels
                if (read.slot != null) {
                    lastSeq = read.slot.seq
                }

                if (codec == null) {
                    if (read.slot == null) {
                        Thread.sleep(30)
                        continue
                    }
                    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, read.slot.width, read.slot.height).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                        setInteger(MediaFormat.KEY_FRAME_RATE, TARGET_FPS)
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
                if (read.slot != null && read.seqChanged && now - lastRenderMs >= FRAME_INTERVAL_MS) {
                    if (now - lastLensMs >= 500) {
                        correction = CamdLensCorrectionStore.load(prefs, inputId)
                        lastLensMs = now
                    }
                    lastRenderMs = now
                    uploadAndDraw(gl, read.slot, correction, pixels!!)
                    val format = drain(eos = false)
                    if (format != null && track == -1 && muxer != null) {
                        track = muxer.addTrack(format)
                        muxer.start()
                        muxerStarted = true
                    }
                } else {
                    Thread.sleep(8)
                }
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
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) return null
        val config = configs[0] ?: return null
        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        if (context == null || context == EGL14.EGL_NO_CONTEXT) return null
        val pbuffer = EGL14.eglCreatePbufferSurface(
            display, config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        val program = buildProgram()
        if (program == 0) return null
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
    )

    private fun readFrame(
        source: RandomAccessFile?,
        mapped: ByteBuffer?,
        mappedIno: Long,
        mappedLength: Long,
        pixels: ByteBuffer?,
        lastSeq: Int,
    ): FrameRead {
        var src = source
        var buf = mapped
        var ino = mappedIno
        var len = mappedLength
        if (src != null && buf != null) {
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
            if (!frame.isFile) return FrameRead(null, null, 0, 0, pixels, null, false)
            val probe = runCatching { RandomAccessFile(frame, "r") }.getOrNull()
                ?: return FrameRead(null, null, 0, 0, pixels, null, false)
            val stat = runCatching { android.system.Os.fstat(probe.fd) }.getOrNull()
            val length = runCatching { probe.length() }.getOrDefault(0L)
            if (stat == null || length < UYVY_HEADER) {
                runCatching { probe.close() }
                return FrameRead(null, null, 0, 0, pixels, null, false)
            }
            val mappedNew = runCatching {
                probe.channel.map(FileChannel.MapMode.READ_ONLY, 0, length).order(ByteOrder.LITTLE_ENDIAN)
            }.getOrNull()
            if (mappedNew == null) {
                runCatching { probe.close() }
                return FrameRead(null, null, 0, 0, pixels, null, false)
            }
            src = probe
            buf = mappedNew
            ino = stat.st_ino
            len = length
        }
        val mappedBuf = buf ?: return FrameRead(src, buf, ino, len, pixels, null, false)
        val slot = readUyvySlot(mappedBuf)
        if (slot == null || slot.seq == lastSeq) {
            return FrameRead(src, buf, ino, len, pixels, null, false)
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
            return FrameRead(src, buf, ino, len, copy, null, false)
        }
        copy.position(0)
        return FrameRead(src, buf, ino, len, copy, slot, true)
    }

    companion object {
        private const val BIT_RATE = 6_000_000
        private const val TARGET_FPS = 20
        private const val FRAME_INTERVAL_MS = 50L
    }
}
