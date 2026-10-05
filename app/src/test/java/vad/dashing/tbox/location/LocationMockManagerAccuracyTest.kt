package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.LocValues

class LocationMockManagerAccuracyTest {

    @Test
    fun dopToMetersUsesGpsConnectorScale() {
        assertEquals(4.7f, LocationMockManager.dopToMeters(1.0f)!!, 1e-3f)
        assertEquals(9.4f, LocationMockManager.dopToMeters(2.0f)!!, 1e-3f)
        // Floor DOP at 1.0
        assertEquals(4.7f, LocationMockManager.dopToMeters(0.5f)!!, 1e-3f)
        assertNull(LocationMockManager.dopToMeters(null))
        assertNull(LocationMockManager.dopToMeters(0f))
    }

    @Test
    fun horizontalAccuracyPrefersGstOverHdop() {
        assertEquals(
            0.114f,
            LocationMockManager.horizontalAccuracyMeters(
                hdop = 1.0f,
                retainingFix = false,
                hrms = 0.114f,
            ),
            1e-3f,
        )
    }

    @Test
    fun horizontalAccuracyLivePrefersHdopOrDefault() {
        assertEquals(
            4.7f,
            LocationMockManager.horizontalAccuracyMeters(1.0f, retainingFix = false),
            1e-3f,
        )
        assertEquals(
            LocationMockManager.FIX_ACCURACY_M,
            LocationMockManager.horizontalAccuracyMeters(null, retainingFix = false),
            0f,
        )
    }

    @Test
    fun horizontalAccuracyRetentionGrowsFromBaseToCeiling() {
        val atStart = LocationMockManager.horizontalAccuracyMeters(
            hdop = null,
            retainingFix = true,
            retentionAgeMs = 0L,
            retentionBaseAccuracyM = 5f,
        )
        assertEquals(5f, atStart, 1e-3f)

        val mid = LocationMockManager.horizontalAccuracyMeters(
            hdop = null,
            retainingFix = true,
            retentionAgeMs = 60_000L,
            retentionBaseAccuracyM = 5f,
        )
        assertTrue(mid > 5f)
        assertTrue(mid < MockRetentionAccuracy.DEFAULT_CEILING_M)

        val capped = LocationMockManager.horizontalAccuracyMeters(
            hdop = null,
            retainingFix = true,
            retentionAgeMs = 600_000L,
            retentionBaseAccuracyM = 5f,
        )
        assertEquals(MockRetentionAccuracy.DEFAULT_CEILING_M, capped, 0f)
    }

    @Test
    fun horizontalAccuracyRetentionUsesCustomCeiling() {
        val capped = LocationMockManager.horizontalAccuracyMeters(
            hdop = null,
            retainingFix = true,
            retentionAgeMs = 600_000L,
            retentionBaseAccuracyM = 5f,
            retentionCeilingM = 30f,
        )
        assertEquals(30f, capped, 0f)
    }

    @Test
    fun horizontalAccuracyRetentionUsesLiveEstimateWhenBaseMissing() {
        // hrms 0.114 would be live; retaining without base uses that as start, then grows.
        val aged = LocationMockManager.horizontalAccuracyMeters(
            hdop = 1.0f,
            retainingFix = true,
            hrms = 0.114f,
            retentionAgeMs = 30_000L,
        )
        assertTrue(aged > 0.114f)
        assertTrue(aged < MockRetentionAccuracy.DEFAULT_CEILING_M)
    }

    @Test
    fun buildMockExtrasIncludesDopAndSats() {
        val extras = LocationMockManager.mockExtraEntries(
            LocValues(
                hdop = 1.2f,
                pdop = 1.5f,
                vdop = 2.0f,
                hrms = 0.2f,
                vrms = 0.3f,
                usingSatellites = 8,
                visibleSatellites = 12,
            ),
        )
        assertEquals(1.2f, extras["hdop"] as Float, 1e-3f)
        assertEquals(1.5f, extras["pdop"] as Float, 1e-3f)
        assertEquals(2.0f, extras["vdop"] as Float, 1e-3f)
        assertEquals(0.2f, extras["hrms"] as Float, 1e-3f)
        assertEquals(0.3f, extras["vrms"] as Float, 1e-3f)
        assertEquals(8, extras["satellites"])
        assertEquals(12, extras["satellitesView"])
        assertEquals(12, extras["totalSatInView"])
    }

    @Test
    fun buildMockExtrasIncludesDiffStatusAndAge() {
        val extras = LocationMockManager.mockExtraEntries(
            LocValues(fixQuality = 4, diffAgeSec = 0.5f),
        )
        assertEquals(4, extras["diffStatus"])
        assertEquals(0.5f, extras["diffAge"] as Float, 1e-3f)
        assertEquals(4, LocationMockManager.trimbleDiffStatus(4))
        assertNull(LocationMockManager.trimbleDiffStatus(null))
    }

    @Test
    fun buildMockExtrasNullWhenEmpty() {
        assertTrue(LocationMockManager.mockExtraEntries(LocValues()).isEmpty())
    }
}
