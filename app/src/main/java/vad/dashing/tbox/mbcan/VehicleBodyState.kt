package vad.dashing.tbox.mbcan

/** Door / tailgate state for launcher and vehicle UI. */
data class VehicleBodyState(
    val doorFlOpen: Boolean = false,
    val doorFrOpen: Boolean = false,
    val doorRlOpen: Boolean = false,
    val doorRrOpen: Boolean = false,
    val tailgateOpen: Boolean = false,
    val hoodOpen: Boolean = false,
) {
    fun anyDoorOpen(): Boolean =
        doorFlOpen || doorFrOpen || doorRlOpen || doorRrOpen || tailgateOpen
}

internal fun decodeMbCanDoorByte(status: Byte): Boolean {
    val v = status.toInt() and 0xFF
    return v == 1 || v == 2
}

internal fun decodeMbCanTrunkByte(status: Byte): Boolean {
    val v = status.toInt() and 0xFF
    return v > 0
}

internal fun decodeVhalDoorOpen(raw: Int?): Boolean = when (raw) {
    1, 2 -> true
    else -> false
}
