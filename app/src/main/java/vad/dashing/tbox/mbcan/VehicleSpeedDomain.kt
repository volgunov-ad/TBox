package vad.dashing.tbox.mbcan

/**
 * A10 VHAL vehicle speed for [FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_PROPERTY_ID]
 * (`MCU_REPLY_SPEED`): stock SystemSettings path — **км/ч = raw as-is** (INT32 ≥ 0).
 */
object VehicleSpeedDomain {
    fun decodeMcuReplyKmh(raw: Number): Float? {
        val numeric = raw.toFloat()
        if (!numeric.isFinite() || numeric < 0f) return null
        return numeric
    }
}
