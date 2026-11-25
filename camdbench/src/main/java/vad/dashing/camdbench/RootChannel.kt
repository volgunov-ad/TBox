package vad.dashing.camdbench

import java.io.File
import java.util.concurrent.TimeUnit

/** Root through mb_daemon's `/sdcard/autorun.sh`, the same primitive camd uses. */
object RootChannel {

    private const val FOREIGN_WAIT_MS = 10_000L
    private const val ANSWER_WAIT_MS = 8_000L

    fun arm(script: String, nonce: String) {
        val autorun = File(BenchScripts.AUTORUN)
        waitForeign(autorun)
        autorun.writeText(script)
        exec("chmod", "777", BenchScripts.AUTORUN)
        File(BenchScripts.AUTORUN_FLAG).createNewFile()
        exec("setprop", BenchScripts.AUTORUN_PROP, "")
        Thread.sleep(300)
        exec("setprop", BenchScripts.AUTORUN_PROP, BenchScripts.AUTORUN_FLAG)
        val marker = File(BenchScripts.ARM)
        val deadline = System.currentTimeMillis() + ANSWER_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val text = runCatching { if (marker.canRead()) marker.readText() else "" }.getOrDefault("")
            if (text.contains(nonce)) return
            Thread.sleep(200)
        }
        throw IllegalStateException("mb_daemon не ответил — root недоступен (это ГУ A9?)")
    }

    private fun waitForeign(autorun: File) {
        val deadline = System.currentTimeMillis() + FOREIGN_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val text = if (autorun.exists()) runCatching { autorun.readText() }.getOrDefault("") else null
            if (!BenchScripts.isForeignAutorun(text)) return
            Thread.sleep(200)
        }
    }

    private fun exec(vararg command: String) {
        runCatching {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            process.inputStream.use { it.readBytes() }
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroy()
        }
    }
}
