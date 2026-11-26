package vad.dashing.camd

/**
 * Diagnostic run of `libcamd_gpu_probe`: CPU per frame for the current glTexSubImage2D
 * upload against an AHardwareBuffer the GPU samples in place, plus EGL/GL extensions.
 * Blocking (a few seconds); call from a worker thread.
 */
object GpuUploadProbe {
    private const val WIDTH = 1280
    private const val HEIGHT = 800
    private const val FRAMES = 100

    fun run(): String = try {
        System.loadLibrary("camd_gpu_probe")
        nativeRun(WIDTH, HEIGHT, FRAMES)
    } catch (error: Throwable) {
        "Проверка не запустилась: ${error.message ?: error.javaClass.simpleName}"
    }

    @JvmStatic
    private external fun nativeRun(width: Int, height: Int, frames: Int): String
}
