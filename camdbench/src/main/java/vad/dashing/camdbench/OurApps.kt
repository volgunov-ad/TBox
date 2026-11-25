package vad.dashing.camdbench

/**
 * Processes whose threads the sampler records: every `vad.dashing.*` app and service
 * process, the DCL launcher (`ras.dashing.*`), the camd grabber and the DCL window helper.
 * Must match `watched()` in camdbench_sampler.cpp.
 */
object OurApps {

    const val DCL_LAUNCHER = "ras.dashing.tbox.launcher"
    const val DCL_WM = "dclwm"

    private val labels = mapOf(
        "vad.dashing.tbox" to "TBox Monitor",
        DCL_LAUNCHER to "DCL лаунчер",
        DCL_WM to "DCL окна, root",
        BenchAccumulator.CAMD_APP to "Камеры ГУ",
        BenchAccumulator.CAMD_GRAB to "граббер camd, root",
        "vad.dashing.voice" to "VAD Voice",
        "vad.dashing.mqtt" to "TBox MQTT",
        "vad.dashing.camdbench" to "этот тест",
    )

    fun isOurs(process: String): Boolean =
        process == BenchAccumulator.CAMD_GRAB ||
            process == DCL_WM ||
            process.startsWith("vad.dashing.") ||
            process.startsWith("ras.dashing.")

    /** `vad.dashing.tbox:api` keeps the label of its app with the process suffix. */
    fun label(process: String): String {
        val app = process.substringBefore(':')
        val base = labels[app] ?: return process
        return if (app == process) "$process ($base)" else "$process ($base, ${process.substringAfter(':')})"
    }

    /** Thread keys are `<process>/<thread>`; process names have no slash. */
    fun ownerOf(threadKey: String): String = threadKey.substringBefore('/')
}
