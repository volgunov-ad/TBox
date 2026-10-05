package vad.dashing.tbox.internet

/** Result of the head-unit internet probe shown on the Modem tab. */
enum class HuInternetStatus {
    /** Monitor not started yet. */
    UNKNOWN,

    /** Probe in flight. */
    CHECKING,

    /** HTTP probe succeeded (2xx/3xx). */
    ONLINE,

    /** No network or probe failed. */
    OFFLINE,
}
