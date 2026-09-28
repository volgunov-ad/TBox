package vad.dashing.tbox.mbcan

/**
 * BCM / `MBCanVehicleDoor` open-closed snapshot (A9).
 *
 * Live door ajar is **not** cfg 1/2/13 (auto-lock settings). Full object arrives via
 * BCM `getDoorStatus()` and optionally `IMbCanVehicleDoorCallback.onVehicleDoorChange`.
 *
 * Raw scale (same family as trunk): typically **1** closed / **2** open — confirm on car.
 */
data class BcmDoorSnapshot(
    val driver: Int? = null,
    val passenger: Int? = null,
    val rearLeft: Int? = null,
    val rearRight: Int? = null,
    val trunk: Int? = null,
    val hood: Int? = null,
    val driverLock: Int? = null,
    val srfOperate: Int? = null,
) {
    fun journalSample(): String = buildString {
        append("FL=").append(driver ?: "?")
        append(" FR=").append(passenger ?: "?")
        append(" RL=").append(rearLeft ?: "?")
        append(" RR=").append(rearRight ?: "?")
        append(" trunk=").append(trunk ?: "?")
        append(" hood=").append(hood ?: "?")
        append(" lock=").append(driverLock ?: "?")
        if (srfOperate != null) append(" srf=").append(srfOperate)
    }

    fun isEmpty(): Boolean =
        driver == null && passenger == null && rearLeft == null && rearRight == null &&
            trunk == null && hood == null && driverLock == null && srfOperate == null
}

object BcmDoorDomain {
    /**
     * Reflective extract from OEM `MBCanVehicleDoor` (or any object with the same getters).
     */
    fun fromDoorObject(door: Any?): BcmDoorSnapshot? {
        if (door == null) return null
        fun byte(name: String): Int? = runCatching {
            (door.javaClass.getMethod(name).invoke(door) as? Number)?.toInt()
        }.getOrNull()
        val snapshot = BcmDoorSnapshot(
            driver = byte("getDriverDoorSts"),
            passenger = byte("getPsngrDoorSts"),
            rearLeft = byte("getLHRdoorSts"),
            rearRight = byte("getRHRDoorSts"),
            trunk = byte("getTrunkSts"),
            hood = byte("getHoodSts"),
            driverLock = byte("getDriverDoorLockSts"),
            srfOperate = byte("getSRF_OpreateSts"),
        )
        return snapshot.takeUnless { it.isEmpty() }
    }

    /** Seat-belt warning raw pair from `MBCanSeatBeltWarning`. */
    fun seatBeltJournalSample(driverWarning: Int?, passengerWarning: Int?): String =
        "driverWarn=$driverWarning passengerWarn=$passengerWarning"
}
