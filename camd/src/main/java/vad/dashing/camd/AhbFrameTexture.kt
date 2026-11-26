package vad.dashing.camd

import java.nio.ByteBuffer

/**
 * UYVY frame texture backed by an AHardwareBuffer (`libcamd_ahb`): [write] copies the
 * grabber slot into the buffer and the GPU samples it in place, about 1.2 ms of CPU less
 * per 1280×800 frame than glTexSubImage2D on the A9 Adreno 612. Every call must come
 * from the GL thread with the owning EGL context current.
 */
internal class AhbFrameTexture private constructor(
    private var handle: Long,
    val width: Int,
    val height: Int,
) {
    /** Waits until the GPU is done with the previous frame, then copies [bytes] at [offset] of [source]. */
    fun write(source: ByteBuffer, offset: Int, bytes: Int): Boolean =
        handle != 0L && nativeWrite(handle, source, offset, bytes)

    /** Call after the draw that samples this texture, before the swap. */
    fun fenceAfterDraw() {
        if (handle != 0L) nativeFence(handle)
    }

    fun release() {
        if (handle != 0L) nativeDestroy(handle)
        handle = 0L
    }

    companion object {
        private val loaded = runCatching { System.loadLibrary("camd_ahb") }.isSuccess

        /** Binds a new buffer to [texture]; null when the firmware cannot (then use glTexSubImage2D). */
        fun create(width: Int, height: Int, texture: Int): AhbFrameTexture? {
            if (!loaded) return null
            val handle = nativeCreate(width, height, texture)
            return if (handle == 0L) null else AhbFrameTexture(handle, width, height)
        }

        @JvmStatic
        private external fun nativeCreate(width: Int, height: Int, texture: Int): Long

        @JvmStatic
        private external fun nativeWrite(handle: Long, source: ByteBuffer, offset: Int, bytes: Int): Boolean

        @JvmStatic
        private external fun nativeFence(handle: Long)

        @JvmStatic
        private external fun nativeDestroy(handle: Long)
    }
}
