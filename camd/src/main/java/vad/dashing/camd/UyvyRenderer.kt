package vad.dashing.camd

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.system.Os
import android.util.Log
import android.view.Surface
import android.view.TextureView
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel

/**
 * Paints one camera from the grabber's shared UYVY buffer.
 * The shader does the color conversion. The UI thread does not touch pixels.
 *
 * Header, little-endian uint32: magic, width, height, stride, seq, slot, slotBytes.
 * Two slots follow the 64-byte header. `seq` grows only after `slot` is published.
 */
internal class UyvyRenderer(
    private val view: TextureView,
    private val frame: File,
    private val main: Handler,
    initialRotation: Int = 0,
    initialFlipH: Boolean = false,
    initialFlipV: Boolean = false,
    initialLens: CamdLensCorrection = CamdLensCorrection.IDENTITY,
    initialCrop: CamdCrop = CamdCrop.IDENTITY,
    initialShape: CamdShape = CamdShape.RECT,
    private val stockView: CamdStockView? = null,
    /** API / window source id for WARN logs on GL/render failure (P2-2). */
    private val inputId: Int = -1,
    /** true when frames stopped arriving after the first one, false when they resume. */
    private val onStall: (Boolean) -> Unit = {},
    private val onFirstFrame: () -> Unit,
) : TextureView.SurfaceTextureListener {

    private val mirror = stockView?.mirror ?: false

    @Volatile
    private var started = false
    @Volatile
    private var running = false
    @Volatile
    private var rotation = when (initialRotation) {
        in 0..359 -> initialRotation
        else -> 0
    }
    @Volatile
    private var flipH = initialFlipH
    @Volatile
    private var flipV = initialFlipV
    @Volatile
    private var lens = CamdLensCorrection.clamp(initialLens)
    @Volatile
    private var crop = CamdCrop.clamp(initialCrop)
    @Volatile
    private var shape = initialShape
    @Volatile
    private var surfaceWidth = 1
    @Volatile
    private var surfaceHeight = 1
    @Volatile
    private var token = 0L
    @Volatile
    private var windowAttached = true
    @Volatile
    private var settingsVersion = 0L
    private var thread: Thread? = null

    /** Set when the view gave up its SurfaceTexture while the GL thread was still alive. */
    @Volatile
    private var releaseOnExit: SurfaceTexture? = null

    fun setRotation(degrees: Int) {
        rotation = ((degrees % 360) + 360) % 360
        settingsVersion += 1
    }

    fun setFlip(horizontal: Boolean, vertical: Boolean) {
        flipH = horizontal
        flipV = vertical
        settingsVersion += 1
    }

    fun setLensCorrection(value: CamdLensCorrection) {
        lens = CamdLensCorrection.clamp(value)
        settingsVersion += 1
    }

    fun setCrop(value: CamdCrop) {
        crop = CamdCrop.clamp(value)
        settingsVersion += 1
    }

    fun setShape(value: CamdShape) {
        shape = value
        settingsVersion += 1
    }

    fun setWindowAttached(value: Boolean) {
        windowAttached = value
    }

    fun start() {
        started = true
        view.surfaceTextureListener = this
        if (view.isAvailable) {
            val surface = view.surfaceTexture ?: return
            onSurfaceTextureAvailable(surface, view.width, view.height)
        }
    }

    fun stop() {
        started = false
        token += 1
        running = false
        joinThread()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (!started) return
        val previous = thread
        if (previous != null && previous.isAlive) return
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        surface.setDefaultBufferSize(surfaceWidth, surfaceHeight)
        val myToken = token
        running = true
        thread = Thread({ render(surface, myToken) }, "camd-gl").apply {
            isDaemon = true
            start()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        surface.setDefaultBufferSize(surfaceWidth, surfaceHeight)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        token += 1
        running = false
        joinThread()
        if (thread?.isAlive != true) return true
        // Returning false hands ownership to us: the GL thread releases it on exit.
        releaseOnExit = surface
        if (thread?.isAlive != true && releaseOnExit === surface) {
            releaseOnExit = null
            return true
        }
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun joinThread() {
        val worker = thread ?: return
        runCatching { worker.join(1_000) }
        if (!worker.isAlive) thread = null
    }

    private fun render(surfaceTexture: SurfaceTexture, myToken: Long) {
        val window = Surface(surfaceTexture)
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            warnGl("eglGetDisplay failed")
            window.release()
            return
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            warnGl("eglInitialize failed")
            window.release()
            return
        }
        val config = chooseConfig(display)
        if (config == null) {
            warnGl("eglChooseConfig failed")
            EGL14.eglTerminate(display)
            window.release()
            return
        }
        val context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        val surface = EGL14.eglCreateWindowSurface(display, config, window, intArrayOf(EGL14.EGL_NONE), 0)
        if (context == null || context == EGL14.EGL_NO_CONTEXT || surface == null || surface == EGL14.EGL_NO_SURFACE) {
            warnGl("eglCreateContext/Surface failed")
            if (surface != null && surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != null && context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            window.release()
            return
        }
        EGL14.eglMakeCurrent(display, surface, surface, context)
        val program = buildProgram()
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
        val meshPos = stockView?.let { directFloatBuffer(it.positions) }
        val meshUv = stockView?.let { directFloatBuffer(it.uvs) }
        val aPos = GLES20.glGetAttribLocation(program, "aPos")
        val aMesh = GLES20.glGetAttribLocation(program, "aMesh")
        val uStock = GLES20.glGetUniformLocation(program, "uStock")
        val uTex = GLES20.glGetUniformLocation(program, "uTex")
        val uWidth = GLES20.glGetUniformLocation(program, "uWidth")
        val uRot = GLES20.glGetUniformLocation(program, "uRot")
        val uFlipH = GLES20.glGetUniformLocation(program, "uFlipH")
        val uFlipV = GLES20.glGetUniformLocation(program, "uFlipV")
        val uK1 = GLES20.glGetUniformLocation(program, "uK1")
        val uK2 = GLES20.glGetUniformLocation(program, "uK2")
        val uCx = GLES20.glGetUniformLocation(program, "uCx")
        val uCy = GLES20.glGetUniformLocation(program, "uCy")
        val uCropOn = GLES20.glGetUniformLocation(program, "uCropOn")
        val uCrop = GLES20.glGetUniformLocation(program, "uCrop")
        val uTiltV = GLES20.glGetUniformLocation(program, "uTiltV")
        val uTiltH = GLES20.glGetUniformLocation(program, "uTiltH")
        val uShape = GLES20.glGetUniformLocation(program, "uShape")
        val uAspect = GLES20.glGetUniformLocation(program, "uAspect")
        val uAA = GLES20.glGetUniformLocation(program, "uAA")
        val drawCurrent: (UyvySlot) -> Unit = { published ->
            GLES20.glViewport(0, 0, surfaceWidth.coerceAtLeast(1), surfaceHeight.coerceAtLeast(1))
            GLES20.glUseProgram(program)
            // Transparent clear so discarded circle corners stay transparent in the buffer.
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUniform1i(uTex, 0)
            GLES20.glUniform1f(uStock, if (stockView != null) 1f else 0f)
            GLES20.glUniform1f(uWidth, published.width.toFloat())
            GLES20.glUniform1f(uRot, rotation.toFloat())
            GLES20.glUniform1f(uFlipH, if (flipH != mirror) 1f else 0f)
            GLES20.glUniform1f(uFlipV, if (flipV) 1f else 0f)
            val correction = lens
            GLES20.glUniform1f(uK1, correction.k1)
            GLES20.glUniform1f(uK2, correction.k2)
            GLES20.glUniform1f(uCx, correction.cx)
            GLES20.glUniform1f(uCy, correction.cy)
            val activeCrop = crop
            GLES20.glUniform1f(uCropOn, if (activeCrop.isIdentity()) 0f else 1f)
            GLES20.glUniform4f(uCrop, activeCrop.x, activeCrop.y, activeCrop.w, activeCrop.h)
            GLES20.glUniform1f(uTiltV, correction.tiltV)
            GLES20.glUniform1f(uTiltH, correction.tiltH)
            val activeShape = shape
            GLES20.glUniform1f(uShape, if (activeShape == CamdShape.CIRCLE) 1f else 0f)
            val surfaceW = surfaceWidth.coerceAtLeast(1)
            val surfaceH = surfaceHeight.coerceAtLeast(1)
            GLES20.glUniform1f(uAspect, surfaceW.toFloat() / surfaceH.toFloat())
            GLES20.glUniform1f(uAA, 1.5f / minOf(surfaceW, surfaceH))
            if (stockView == null || meshPos == null || meshUv == null) {
                quad.position(0)
                GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
                GLES20.glEnableVertexAttribArray(aPos)
                if (aMesh >= 0) GLES20.glDisableVertexAttribArray(aMesh)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } else {
                meshPos.position(0)
                GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 12, meshPos)
                GLES20.glEnableVertexAttribArray(aPos)
                if (aMesh >= 0) {
                    meshUv.position(0)
                    GLES20.glVertexAttribPointer(aMesh, 2, GLES20.GL_FLOAT, false, 0, meshUv)
                    GLES20.glEnableVertexAttribArray(aMesh)
                }
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, stockView.triangleCount * 3)
            }
        }
        var mapped: ByteBuffer? = null
        var mappedIno = 0L
        var mappedLength = 0L
        var source: RandomAccessFile? = null
        var pixels: ByteBuffer? = null
        var directUpload = true
        var lastSeq = 0
        var uploadedWidth = 0
        var uploadedHeight = 0
        var sawFrame = false
        var stalled = false
        var lastFrameMs = 0L
        var idle = 0
        var lastSettingsVersion = settingsVersion
        if (program == 0) warnGl("shader build failed")
        try {
            while (running && myToken == token && program != 0) {
                if (!windowAttached) {
                    lastFrameMs = System.currentTimeMillis()
                    idle = 0
                    Thread.sleep(16)
                    continue
                }
                val shared = mapFrame(source, mapped, mappedIno, mappedLength)
                source = shared.source
                mapped = shared.buffer
                mappedIno = shared.inode
                mappedLength = shared.length
                val buffer = mapped
                if (buffer == null) {
                    Thread.sleep(RenderPacing.sleepMs(idle))
                    idle += 1
                    continue
                }
                val published = readUyvySlot(buffer)
                if (published == null || published.seq == lastSeq) {
                    if (sawFrame && !stalled && System.currentTimeMillis() - lastFrameMs > STALL_MS) {
                        stalled = true
                        runCatching { Log.i(TAG, "нет кадров input=$inputId seq=$lastSeq") }
                        main.post { onStall(true) }
                    }
                    if (lastSettingsVersion != settingsVersion) {
                        lastSettingsVersion = settingsVersion
                        if (!redrawCurrentFrame(display, surface, published, drawCurrent)) {
                            break
                        }
                    }
                    Thread.sleep(RenderPacing.sleepMs(idle))
                    idle += 1
                    continue
                }
                idle = 0
                val bytes = published.slotBytes
                val slotView = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                slotView.position(UYVY_HEADER + published.slot * bytes)
                slotView.limit(UYVY_HEADER + published.slot * bytes + bytes)
                val upload = if (directUpload) {
                    slotView.slice()
                } else {
                    val copy = pixels?.takeIf { it.capacity() == bytes } ?: ByteBuffer.allocateDirect(bytes).also {
                        pixels = it
                    }
                    copy.clear()
                    copy.put(slotView)
                    copy.position(0)
                    copy
                }
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
                val uploaded = runCatching {
                    if (uploadedWidth != published.width || uploadedHeight != published.height) {
                        GLES20.glTexImage2D(
                            GLES20.GL_TEXTURE_2D,
                            0,
                            GLES20.GL_RGBA,
                            published.width / 2,
                            published.height,
                            0,
                            GLES20.GL_RGBA,
                            GLES20.GL_UNSIGNED_BYTE,
                            upload,
                        )
                        uploadedWidth = published.width
                        uploadedHeight = published.height
                    } else {
                        GLES20.glTexSubImage2D(
                            GLES20.GL_TEXTURE_2D,
                            0,
                            0,
                            0,
                            published.width / 2,
                            published.height,
                            GLES20.GL_RGBA,
                            GLES20.GL_UNSIGNED_BYTE,
                            upload,
                        )
                    }
                }
                if (uploaded.isFailure) {
                    if (!directUpload) throw uploaded.exceptionOrNull()!!
                    // A read-only mapping the GL binding cannot take: fall back to a private copy.
                    warnGl("direct texture upload failed, copying frames", uploaded.exceptionOrNull())
                    directUpload = false
                    uploadedWidth = 0
                    continue
                }
                // GL has taken the pixels once the call returns. If the grabber moved on meanwhile it
                // may have been writing into this slot, so the frame is dropped and the newer one drawn.
                if (buffer.getInt(16) != published.seq) continue
                drawCurrent(published)
                if (!EGL14.eglSwapBuffers(display, surface)) break
                lastSeq = published.seq
                val pauseMs = System.currentTimeMillis() - lastFrameMs
                lastFrameMs += pauseMs
                if (stalled) {
                    stalled = false
                    runCatching { Log.i(TAG, "кадры вернулись input=$inputId seq=${published.seq} пауза $pauseMs мс") }
                    main.post { onStall(false) }
                }
                if (!sawFrame) {
                    sawFrame = true
                    main.post { onFirstFrame() }
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            // A bad frame must not leave the EGL surface attached.
            warnGl("GL/render failure", error)
        } finally {
            runCatching { source?.close() }
            GLES20.glDeleteTextures(1, texture, 0)
            if (program != 0) GLES20.glDeleteProgram(program)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            window.release()
            releaseOnExit?.takeIf { it === surfaceTexture }?.let { pending ->
                releaseOnExit = null
                runCatching { pending.release() }
            }
        }
    }

    private fun warnGl(message: String, error: Throwable? = null) {
        runCatching {
            if (error != null) {
                Log.w(TAG, "$message input=$inputId frame=${frame.name}", error)
            } else {
                Log.w(TAG, "$message input=$inputId frame=${frame.name}")
            }
        }
    }

    /** Re-draws the last uploaded frame after a settings change without a new frame. */
    private fun redrawCurrentFrame(
        display: EGLDisplay,
        surface: EGLSurface,
        published: UyvySlot?,
        draw: (UyvySlot) -> Unit,
    ): Boolean {
        if (published == null) return true
        draw(published)
        return EGL14.eglSwapBuffers(display, surface)
    }

    private fun directFloatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values)
            position(0)
        }

    private fun mapFrame(
        current: RandomAccessFile?,
        mapped: ByteBuffer?,
        mappedIno: Long,
        mappedLength: Long,
    ): SharedMap {
        if (current != null && mapped != null) {
            val stat = runCatching { Os.stat(frame.absolutePath) }.getOrNull()
            if (stat != null && stat.st_ino == mappedIno && stat.st_size == mappedLength) {
                return SharedMap(current, mapped, mappedIno, mappedLength)
            }
            runCatching { current.close() }
            return SharedMap(null, null, 0L, 0L)
        }
        if (!frame.isFile) {
            return SharedMap(null, null, 0L, 0L)
        }
        val probe = runCatching { RandomAccessFile(frame, "r") }.getOrNull()
            ?: return SharedMap(null, null, 0L, 0L)
        val stat = runCatching { Os.fstat(probe.fd) }.getOrNull()
        val length = runCatching { probe.length() }.getOrDefault(0L)
        if (stat == null || length < UYVY_HEADER) {
            runCatching { probe.close() }
            return SharedMap(null, null, 0L, 0L)
        }
        val buffer = runCatching {
            probe.channel.map(FileChannel.MapMode.READ_ONLY, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        }.getOrNull()
        if (buffer == null) {
            runCatching { probe.close() }
            return SharedMap(null, null, 0L, 0L)
        }
        return SharedMap(probe, buffer, stat.st_ino, length)
    }

    private fun chooseConfig(display: EGLDisplay): EGLConfig? {
        // Alpha size 8 is required for shape=circle: without it the SurfaceTexture
        // buffer has no alpha channel and the outside-circle area renders black.
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
        val ok = EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0)
        if (!ok || count[0] == 0) return null
        return configs[0]
    }

    private fun buildProgram(): Int {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT)
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

    private class SharedMap(
        val source: RandomAccessFile?,
        val buffer: ByteBuffer?,
        val inode: Long,
        val length: Long,
    )

    internal object RenderPacing {
        const val ACTIVE_MS = 8L
        const val BASE_MS = 12L
        const val STEP_FACTOR = 1.6
        const val BACKOFF_CAP_MS = 40L

        /** Idle sleep for the Nth consecutive empty iteration (0 = frame just arrived). */
        fun sleepMs(idleIterations: Int): Long {
            if (idleIterations <= 0) return ACTIVE_MS
            val ms = BASE_MS * Math.pow(STEP_FACTOR, (idleIterations - 1).toDouble())
            return ms.toLong().coerceIn(BASE_MS, BACKOFF_CAP_MS)
        }
    }

    internal companion object {
        private const val TAG = "CamdGl"
        private const val STALL_MS = 3_000L

        const val VERTEX = """
            attribute vec2 aPos;
            attribute vec2 aMesh;
            uniform float uStock;
            uniform float uRot;
            uniform float uFlipH;
            uniform float uFlipV;
            uniform vec4 uCrop;
            varying vec2 vTex;
            void main() {
                if (uStock > 0.5) {
                    vec2 cu = (aMesh - uCrop.xy) / uCrop.zw;
                    if (uFlipH > 0.5) cu.x = 1.0 - cu.x;
                    if (uFlipV > 0.5) cu.y = 1.0 - cu.y;
                    vec2 p = cu - 0.5;
                    float a = radians(-uRot);
                    float ca = cos(a);
                    float sa = sin(a);
                    vec2 r = vec2(ca * p.x - sa * p.y, sa * p.x + ca * p.y);
                    gl_Position = vec4(2.0 * (r + 0.5) - 1.0, 0.0, 1.0);
                    vTex = aMesh;
                } else {
                    vTex = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);
                    gl_Position = vec4(aPos, 0.0, 1.0);
                }
            }
        """
        // Order: rotate → optional H/V flip of the displayed image → radial visual tweak
        // → keystone tilt (tiltV narrows/widens top vs bottom, tiltH left vs right)
        // → crop rect in the corrected-frame axes (stretched to fill the window) → UYVY sample.
        // Lens k1/k2/cx/cy are a preview aid only, not a real camera calibration.
        // Circle shape clips the window to an inscribed circle with a smooth edge.
        const val FRAGMENT = """
            precision highp float;
            uniform sampler2D uTex;
            uniform float uWidth;
            uniform float uStock;
            uniform float uRot;
            uniform float uFlipH;
            uniform float uFlipV;
            uniform float uK1;
            uniform float uK2;
            uniform float uCx;
            uniform float uCy;
            uniform float uCropOn;
            uniform vec4 uCrop;
            uniform float uTiltV;
            uniform float uTiltH;
            uniform float uShape;
            uniform float uAspect;
            uniform float uAA;
            varying vec2 vTex;
            vec4 sampleUyvy(vec2 uv) {
                float x = uv.x * uWidth;
                float texel = (floor(x * 0.5) + 0.5) / (uWidth * 0.5);
                vec4 uyvy = texture2D(uTex, vec2(texel, uv.y));
                float y = mod(floor(x), 2.0) < 0.5 ? uyvy.g : uyvy.a;
                float u = uyvy.r - 0.5;
                float v = uyvy.b - 0.5;
                y = 1.164 * (y - 0.0625);
                return vec4(y + 1.596 * v, y - 0.391 * u - 0.813 * v, y + 2.018 * u, 1.0);
            }
            void main() {
                float alpha = 1.0;
                if (uShape > 0.5) {
                    vec2 pc = vTex - 0.5;
                    vec2 qc = uAspect >= 1.0 ? vec2(pc.x * uAspect, pc.y) : vec2(pc.x, pc.y / uAspect);
                    float dist = length(qc);
                    alpha = 1.0 - smoothstep(0.5 - uAA, 0.5, dist);
                    if (alpha <= 0.004) discard;
                }
                if (uStock > 0.5) {
                    if (vTex.x < 0.0 || vTex.x > 1.0 || vTex.y < 0.0 || vTex.y > 1.0) {
                        gl_FragColor = vec4(0.0, 0.0, 0.0, alpha);
                        return;
                    }
                    gl_FragColor = vec4(sampleUyvy(vTex).rgb, alpha);
                    return;
                }
                vec2 p = vTex - 0.5;
                float a = radians(uRot);
                float ca = cos(a);
                float sa = sin(a);
                vec2 r = vec2(ca * p.x - sa * p.y, sa * p.x + ca * p.y);
                vec2 uv = r + 0.5;
                if (uFlipH > 0.5) uv.x = 1.0 - uv.x;
                if (uFlipV > 0.5) uv.y = 1.0 - uv.y;
                vec2 c = vec2(0.5 + uCx, 0.5 + uCy);
                vec2 d = uv - c;
                float r2 = dot(d, d);
                float scale = 1.0 + uK1 * r2 + uK2 * r2 * r2;
                uv = c + d * scale;
                float wv = 1.0 + uTiltV * (uv.y - 0.5) * 2.0;
                uv.x = 0.5 + (uv.x - 0.5) / wv;
                float wh = 1.0 + uTiltH * (uv.x - 0.5) * 2.0;
                uv.y = 0.5 + (uv.y - 0.5) / wh;
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, alpha);
                    return;
                }
                if (uCropOn > 0.5) uv = uCrop.xy + uCrop.zw * uv;
                gl_FragColor = vec4(sampleUyvy(uv).rgb, alpha);
            }
        """
    }
}
