package vad.dashing.camd

/** Retry schedule of the recorder start when the grabber cannot be launched. */
internal object RecorderStartRetry {
    /** Pauses before attempts 2…N; about four minutes in total, enough for the boot-time mb_daemon rush. */
    val PAUSES_MS = longArrayOf(5_000, 10_000, 20_000, 30_000, 60_000, 60_000, 60_000)

    fun message(reason: String, retryInMs: Long?): String =
        if (retryInMs != null) {
            "Не удалось запустить граббер: $reason — повтор через ${retryInMs / 1000} с"
        } else {
            "Не удалось запустить граббер: $reason — попытки исчерпаны, включите запись снова"
        }
}
