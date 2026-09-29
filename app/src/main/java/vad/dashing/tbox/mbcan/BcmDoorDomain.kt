package vad.dashing.tbox.mbcan

/**
 * BCM / `MBCanVehicleDoor` open-closed snapshot (A9).
 *
 * Live door ajar is **not** cfg 1/2/13 (auto-lock settings). Full object arrives via
 * BCM `getDoorStatus()` and optionally `IMbCanVehicleDoorCallback.onVehicleDoorChange`.
 *
 * Raw scale (same family as trunk): **1** closed / **2** open (confirmed on A9 logs for FL/FR).
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
     * A9 BCM ajar (FL/FR/RL/RR/hood/trunk family): **1** closed / **2** open
     * (same scale as [TrunkDoorDomain.decodeBinaryOpenMbCan]; confirmed on A9 logs).
     */
    fun decodeAjarOpenMbCan(raw: Int?): Boolean? = TrunkDoorDomain.decodeBinaryOpenMbCan(raw)

    /**
     * A10 CEM2 door ajar (`R_0402_CEM_2_*DoorSts`): standard CEM 1-bit —
     * **1** active/open, **0** closed ([TurnSignalsDomain.decodeCemBinaryActive]).
     *
     * Code-assumed: live A10 ajar values were not present in available journals (subscribe only).
     */
    fun decodeAjarOpenVhalCem(raw: Int?): Boolean? =
        raw?.let(TurnSignalsDomain::decodeCemBinaryActive)

    /**
     * A10 CEM2 ajar property ids keyed like [Android10VhalRepository] `publishVhalDoorAjar`
     * (FL/FR/RL/RR + hood). Used on TrunkDoor pull refresh to seed before first onChange.
     */
    fun vhalCem2AjarSeedPropertyIds(): Map<String, Int> = mapOf(
        "FL" to FirmwareVehicleJsonMapper.VHAL_CEM2_DRIVER_DOOR_STS,
        "FR" to FirmwareVehicleJsonMapper.VHAL_CEM2_PSNGR_DOOR_STS,
        "RL" to FirmwareVehicleJsonMapper.VHAL_CEM2_LHR_DOOR_STS,
        "RR" to FirmwareVehicleJsonMapper.VHAL_CEM2_RHR_DOOR_STS,
        "hood" to FirmwareVehicleJsonMapper.VHAL_CEM2_HOOD_STS,
    )

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
