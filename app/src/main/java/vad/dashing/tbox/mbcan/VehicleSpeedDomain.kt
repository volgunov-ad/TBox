package vad.dashing.tbox.mbcan

/**
 * A10 VHAL vehicle speed decode for [FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_PROPERTY_ID]
 * (`R_0400_ESP_1_VehicleSpeedVSOSig`): same scale as TBox CAN — `°км/ч = raw / 16`.
 *
 * Kept as a tiny domain helper so unit tests can lock the formula without reflecting on
 * [Android10VhalRepository].
 */
object VehicleSpeedDomain {
    private const val SCALE = 1f / 16f

    fun decodeVhalRaw(raw: Number): Float? {
        val numeric = raw.toFloat()
        if (!numeric.isFinite() || numeric < 0f) return null
        return numeric * SCALE
    }
}
