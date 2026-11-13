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
import android.view.Surface
import android.view.TextureView
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    private val onFirstFrame: () -> Unit,
) : TextureView.SurfaceTextureListener {

    @Volatile
    private var started = false
    @Volatile
    private var running = false
    @Volatile
    private var rotation = when (initialRotation) {
        90, 180, 270 -> initialRotation
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
    private var surfaceWidth = 1
    @Volatile
    private var surfaceHeight = 1
    @Volatile
    private var token = 0L
    @Volatile
    private var windowAttached = true
    private var thread: Thread? = null

    fun setRotation(degrees: Int) {
        rotation = when (degrees) {
            90, 180, 270 -> degrees
            else -> 0
        }
    }

    fun setFlip(horizontal: Boolean, vertical: Boolean) {
        flipH = horizontal
        flipV = vertical
    }

    fun setLensCorrection(value: CamdLensCorrection) {
        lens = CamdLensCorrection.clamp(value)
    }

    fun setCrop(value: CamdCrop) {
        crop = CamdCrop.clamp(value)
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
        return thread?.isAlive != true
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
            window.release()
            return
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            window.release()
            return
        }
        val config = chooseConfig(display)
        if (config == null) {
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
        val aPos = GLES20.glGetAttribLocation(program, "aPos")
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
        var mapped: ByteBuffer? = null
        var mappedIno = 0L
        var mappedLength = 0L
        var source: RandomAccessFile? = null
        var pixels: ByteBuffer? = null
        var lastSeq = 0
        var uploadedWidth = 0
        var uploadedHeight = 0
        var sawFrame = false
        try {
            while (running && myToken == token && program != 0) {
                if (!windowAttached) {
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
                    Thread.sleep(8)
                    continue
                }
                val published = readUyvySlot(buffer)
                if (published == null || published.seq == lastSeq) {
                    Thread.sleep(8)
                    continue
                }
                val bytes = published.slotBytes
                val copy = pixels?.takeIf { it.capacity() == bytes } ?: ByteBuffer.allocateDirect(bytes).also {
                    pixels = it
                }
                copy.clear()
                val viewBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                viewBuffer.position(UYVY_HEADER + published.slot * bytes)
                viewBuffer.limit(UYVY_HEADER + published.slot * bytes + bytes)
                copy.put(viewBuffer)
                if (buffer.getInt(16) != published.seq) continue
                copy.position(0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
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
                        copy,
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
                        copy,
                    )
                }
                GLES20.glViewport(0, 0, surfaceWidth.coerceAtLeast(1), surfaceHeight.coerceAtLeast(1))
                GLES20.glUseProgram(program)
                GLES20.glUniform1i(uTex, 0)
                GLES20.glUniform1f(uWidth, published.width.toFloat())
                GLES20.glUniform1f(uRot, rotation.toFloat())
                GLES20.glUniform1f(uFlipH, if (flipH) 1f else 0f)
                GLES20.glUniform1f(uFlipV, if (flipV) 1f else 0f)
                val correction = lens
                GLES20.glUniform1f(uK1, correction.k1)
                GLES20.glUniform1f(uK2, correction.k2)
                GLES20.glUniform1f(uCx, correction.cx)
                GLES20.glUniform1f(uCy, correction.cy)
                val activeCrop = crop
                GLES20.glUniform1f(uCropOn, if (activeCrop.isIdentity()) 0f else 1f)
                GLES20.glUniform4f(uCrop, activeCrop.x, activeCrop.y, activeCrop.w, activeCrop.h)
                quad.position(0)
                GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
                GLES20.glEnableVertexAttribArray(aPos)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                if (!EGL14.eglSwapBuffers(display, surface)) break
                lastSeq = published.seq
                if (!sawFrame) {
                    sawFrame = true
                    main.post { onFirstFrame() }
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Throwable) {
            // A bad frame must not leave the EGL surface attached.
        } finally {
            runCatching { source?.close() }
            GLES20.glDeleteTextures(1, texture, 0)
            if (program != 0) GLES20.glDeleteProgram(program)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            window.release()
        }
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
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
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

    private companion object {
        const val VERTEX = """
            attribute vec2 aPos;
            varying vec2 vTex;
            void main() {
                vTex = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """
        // Order: rotate → optional H/V flip of the displayed image → radial visual tweak
        // → crop rect in the corrected-frame axes (stretched to fill the window) → UYVY sample.
        // Lens k1/k2/cx/cy are a preview aid only, not a real camera calibration.
        const val FRAGMENT = """
            precision highp float;
            uniform sampler2D uTex;
            uniform float uWidth;
            uniform float uRot;
            uniform float uFlipH;
            uniform float uFlipV;
            uniform float uK1;
            uniform float uK2;
            uniform float uCx;
            uniform float uCy;
            uniform float uCropOn;
            uniform vec4 uCrop;
            varying vec2 vTex;
            void main() {
                vec2 p = vTex - 0.5;
                vec2 r = p;
                if (uRot > 45.0 && uRot < 135.0) r = vec2(-p.y, p.x);
                else if (uRot > 135.0 && uRot < 225.0) r = -p;
                else if (uRot > 225.0) r = vec2(p.y, -p.x);
                vec2 uv = r + 0.5;
                if (uFlipH > 0.5) uv.x = 1.0 - uv.x;
                if (uFlipV > 0.5) uv.y = 1.0 - uv.y;
                vec2 c = vec2(0.5 + uCx, 0.5 + uCy);
                vec2 d = uv - c;
                float r2 = dot(d, d);
                float scale = 1.0 + uK1 * r2 + uK2 * r2 * r2;
                uv = c + d * scale;
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    return;
                }
                if (uCropOn > 0.5) uv = uCrop.xy + uCrop.zw * uv;
                float x = uv.x * uWidth;
                float texel = (floor(x * 0.5) + 0.5) / (uWidth * 0.5);
                vec4 uyvy = texture2D(uTex, vec2(texel, uv.y));
                float y = mod(floor(x), 2.0) < 0.5 ? uyvy.g : uyvy.a;
                float u = uyvy.r - 0.5;
                float v = uyvy.b - 0.5;
                y = 1.164 * (y - 0.0625);
                gl_FragColor = vec4(
                    y + 1.596 * v,
                    y - 0.391 * u - 0.813 * v,
                    y + 2.018 * u,
                    1.0
                );
            }
        """
    }
}
