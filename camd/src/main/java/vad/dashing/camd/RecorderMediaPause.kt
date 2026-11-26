package vad.dashing.camd

/** When the grabber may stop copying frames for the recorder: every recording camera waits for the media. */
internal object RecorderMediaPause {
    fun paused(inputs: Set<Int>, waiting: Set<Int>): Boolean =
        inputs.isNotEmpty() && waiting.containsAll(inputs)
}
