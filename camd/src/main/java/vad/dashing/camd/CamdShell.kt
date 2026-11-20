package vad.dashing.camd

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Short helper commands (chmod, setprop) with a hard timeout. The output is drained
 * on a side thread, so a child that never closes stdout cannot block the caller
 * before the waitFor timeout runs.
 */
object CamdShell {

    class CommandException(message: String) : IllegalStateException(message)

    fun interface ProcessFactory {
        fun start(cmd: Array<String>): Process
    }

    private val drainer: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "camd-shell-drain").apply { isDaemon = true }
    }

    fun exec(
        cmd: Array<String>,
        timeoutMs: Long = 8_000L,
        factory: ProcessFactory = ProcessFactory { command ->
            ProcessBuilder(*command).redirectErrorStream(true).start()
        },
    ): String {
        val process = try {
            factory.start(cmd)
        } catch (error: Exception) {
            throw CommandException("start ${cmd[0]}: ${error.message}")
        }
        val output: Future<String> = drainer.submit<String> {
            process.inputStream.bufferedReader().readText()
        }
        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroy()
                throw CommandException("timeout ${cmd[0]}")
            }
            val text = runCatching { output.get(2, TimeUnit.SECONDS) }.getOrDefault("")
            val code = process.exitValue()
            if (code != 0) {
                throw CommandException("${cmd[0]} завершился $code $text".trim())
            }
            return text.trim()
        } catch (error: CommandException) {
            output.cancel(true)
            throw error
        }
    }
}
