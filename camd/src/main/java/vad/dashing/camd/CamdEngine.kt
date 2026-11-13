package vad.dashing.camd

import java.io.File
import java.util.Locale

/**
 * Writes the window XML and a one-line command for the root daemon, and starts that
 * daemon through mb_daemon when it is not already running.
 */
class CamdEngine(private val filesDir: File) {

    private val lock = Any()
    private var seq = 0

    fun open(command: CamdCommand.Open): String = synchronized(lock) {
        runCatching {
            val input = KnownInputs.byId(command.inputId)
                ?: return "state=error error=unknown_input"
            val ticket = nextSeq()
            installDaemonScript()
            ensureDaemon()
            writeText(File(filesDir, "camd-window.xml"), WindowXml.build(input, command.x, command.y, command.w, command.h))
            writeCommand(openLine(command, ticket))
            await(ticket)
        }.getOrElse { "state=error error=${it.message}" }
    }

    fun close(): String = synchronized(lock) {
        runCatching {
            val ticket = nextSeq()
            installDaemonScript()
            ensureDaemon()
            writeCommand("close $ticket")
            await(ticket)
        }.getOrElse { "state=error error=${it.message}" }
    }

    fun status(): String = readIfAllowed(File(DaemonScripts.STATUS_FILE))

    fun logTail(): String {
        val text = readIfAllowed(File(DaemonScripts.QCAR_LOG))
        if (text.length <= 500) return text
        return text.takeLast(500)
    }

    /** Runs one 20 second on-screen probe and returns the Download log path. */
    fun runProbe(stopAvm: Boolean, input: CameraInput): String = synchronized(lock) {
        runCatching {
            val nonce = "p${System.currentTimeMillis()}"
            val result = File(ProbeScripts.RESULT_FILE)
            if (result.exists()) result.delete()
            val body = File(filesDir, "camd-probe-$nonce.sh")
            writeText(body, ProbeScripts.body(stopAvm, input, nonce))
            writeText(File(DaemonScripts.AUTORUN), ProbeScripts.launcher(ProbeScripts.bodyPath(nonce)))
            exec(arrayOf("chmod", "777", DaemonScripts.AUTORUN))
            File(DaemonScripts.AUTORUN_FLAG).createNewFile()
            exec(arrayOf("setprop", DaemonScripts.AUTORUN_PROP, ""))
            Thread.sleep(300)
            exec(arrayOf("setprop", DaemonScripts.AUTORUN_PROP, DaemonScripts.AUTORUN_FLAG))
            val deadline = System.currentTimeMillis() + PROBE_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val text = readIfAllowed(result)
                if (text.contains(nonce)) {
                    val path = text.lineSequence().firstOrNull { it.startsWith("/") } ?: text
                    return path
                }
                Thread.sleep(500)
            }
            "state=error error=probe_timeout смотрите Загрузки camd-probe-*.txt"
        }.getOrElse { "state=error error=${it.message}" }
    }

    private fun ensureDaemon() {
        val beat = File(DaemonScripts.PID_FILE)
        val fresh = beat.canRead() && System.currentTimeMillis() - beat.lastModified() < 5_000
        if (!fresh) arm()
    }

    private fun arm() {
        val nonce = "n${System.currentTimeMillis()}"
        writeText(File(DaemonScripts.AUTORUN), DaemonScripts.arm(nonce))
        exec(arrayOf("chmod", "777", DaemonScripts.AUTORUN))
        File(DaemonScripts.AUTORUN_FLAG).createNewFile()
        exec(arrayOf("setprop", DaemonScripts.AUTORUN_PROP, ""))
        Thread.sleep(300)
        exec(arrayOf("setprop", DaemonScripts.AUTORUN_PROP, DaemonScripts.AUTORUN_FLAG))
        val deadline = System.currentTimeMillis() + ARM_TIMEOUT_MS
        val marker = File(DaemonScripts.ARM_FILE)
        while (System.currentTimeMillis() < deadline) {
            val text = readIfAllowed(marker)
            if (text.contains(nonce)) {
                if (text.contains("missing")) {
                    throw IllegalStateException("демон не найден")
                }
                return
            }
            Thread.sleep(200)
        }
        throw IllegalStateException("mb_daemon не ответил")
    }

    private fun await(ticket: Int): String {
        val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val last = status()
            if (hasSeqTicket(last, ticket)) return last
            Thread.sleep(300)
        }
        return "state=error error=timeout seq=$ticket"
    }

    private fun installDaemonScript() {
        filesDir.mkdirs()
        writeText(File(filesDir, "camd-daemon.sh"), DaemonScripts.daemon())
    }

    private fun writeCommand(line: String) {
        val tmp = File(filesDir, "camd.cmd.tmp")
        val dest = File(filesDir, "camd.cmd")
        writeText(tmp, line + "\n")
        if (!tmp.renameTo(dest)) {
            writeText(dest, line + "\n")
            tmp.delete()
        }
    }

    private fun nextSeq(): Int {
        seq += 1
        return seq
    }

    private fun openLine(command: CamdCommand.Open, ticket: Int): String {
        return "open ${command.inputId} ${fmt(command.x)} ${fmt(command.y)} ${fmt(command.w)} ${fmt(command.h)} $ticket"
    }

    private fun fmt(value: Float): String = String.format(Locale.US, "%.4f", value)

    private fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    private fun readIfAllowed(file: File): String {
        return runCatching { if (file.canRead()) file.readText().trim() else "" }.getOrDefault("")
    }

    private fun exec(cmd: Array<String>) {
        CamdShell.exec(cmd)
    }

    private companion object {
        const val ARM_TIMEOUT_MS = 8_000L
        const val WAIT_TIMEOUT_MS = 12_000L
        const val PROBE_TIMEOUT_MS = 45_000L
    }
}

internal fun hasSeqTicket(status: String, ticket: Int): Boolean {
    val token = "seq=$ticket"
    return status.split(Regex("\\s+")).any { it == token }
}
