package vad.dashing.tbox.internet

/**
 * Pure status transitions for the HU internet probe.
 *
 * Online flips on the first successful probe. Offline requires
 * [FAILURES_BEFORE_OFFLINE] consecutive failures so a single blip does not clear the status.
 */
object HuInternetStatusLogic {
    const val FAILURES_BEFORE_OFFLINE = 2

    data class Snapshot(
        val status: HuInternetStatus = HuInternetStatus.UNKNOWN,
        val consecutiveFailures: Int = 0,
    )

    fun onSuccess(previous: Snapshot): Snapshot =
        Snapshot(status = HuInternetStatus.ONLINE, consecutiveFailures = 0)

    fun onFailure(previous: Snapshot): Snapshot {
        val failures = previous.consecutiveFailures + 1
        val status =
            if (failures >= FAILURES_BEFORE_OFFLINE) {
                HuInternetStatus.OFFLINE
            } else {
                previous.status
            }
        return Snapshot(status = status, consecutiveFailures = failures)
    }

    fun automationStateKey(status: HuInternetStatus): String =
        when (status) {
            HuInternetStatus.UNKNOWN -> "unknown"
            HuInternetStatus.CHECKING -> "checking"
            HuInternetStatus.ONLINE -> "online"
            HuInternetStatus.OFFLINE -> "offline"
        }
}
