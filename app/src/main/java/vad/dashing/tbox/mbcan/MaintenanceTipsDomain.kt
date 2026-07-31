package vad.dashing.tbox.mbcan

/**
 * Distance to next maintenance (κμ) from ICM `Maintenance_tips`.
 *
 * A9: `IcmTripInfo.getICM_6_Maintenance_tips` short as-is (`MBMaintenanceView`).
 * A10: VHAL `R_0900_ICM_6_Maintenance_tips` int km as-is (CarSettings status screen).
 * Stock shows "---" when value &lt; 0.
 */
object MaintenanceTipsDomain {
    fun decodeKm(raw: Int): UInt? {
        if (raw < 0) return null
        return raw.toUInt()
    }
}
