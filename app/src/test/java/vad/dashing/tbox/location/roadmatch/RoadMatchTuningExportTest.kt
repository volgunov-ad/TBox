package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMatchTuningExportTest {
    @Test
    fun wrappedExportRoundTrip() {
        val tuning = RoadMatchTuning.DEFAULT
            .with(RoadMatchTuningKey.CROSS_BLEND, 0.55)
            .with(RoadMatchTuningKey.FREE_UNBIND_BEFORE_M, 42.0)

        val json = RoadMatchTuningExport.exportJson("vad.dashing.tbox", tuning)
        val restored = RoadMatchTuningExport.importJson(json).getOrThrow()

        assertEquals(0.55, restored[RoadMatchTuningKey.CROSS_BLEND], 1e-6)
        assertEquals(42.0, restored[RoadMatchTuningKey.FREE_UNBIND_BEFORE_M], 1e-6)
        assertTrue(json.contains("road_match_tuning"))
    }

    @Test
    fun rawTuningJsonIsAccepted() {
        val tuning = RoadMatchTuning.DEFAULT.with(RoadMatchTuningKey.RAILS_SOFT_BLEND, 0.60)
        val raw = tuning.toJson()

        val restored = RoadMatchTuningExport.importJson(raw).getOrThrow()

        assertEquals(0.60, restored[RoadMatchTuningKey.RAILS_SOFT_BLEND], 1e-6)
    }

    @Test
    fun rejectsUnsupportedWrappedFormat() {
        val bad = """{"formatVersion":99,"kind":"road_match_tuning","tuning":{"version":1}}"""
        assertTrue(RoadMatchTuningExport.importJson(bad).isFailure)
    }

    @Test
    fun rejectsWrongKind() {
        val bad = """{"formatVersion":1,"kind":"other","tuning":{"version":1}}"""
        assertTrue(RoadMatchTuningExport.importJson(bad).isFailure)
    }

    @Test
    fun emptyOverridesExportAndImport() {
        val json = RoadMatchTuningExport.exportJson("test.pkg", RoadMatchTuning.DEFAULT)
        val restored = RoadMatchTuningExport.importJson(json).getOrThrow()
        assertTrue(restored.isDefault())
        assertFalse(json.contains("crossBlend"))
    }
}
