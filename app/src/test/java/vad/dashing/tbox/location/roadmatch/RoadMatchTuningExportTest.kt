package vad.dashing.tbox.location.roadmatch

import org.json.JSONObject
import org.junit.Assert.assertEquals
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

        RoadMatchTuningKey.entries.forEach { key ->
            assertEquals(key.storageName, tuning[key], restored[key], 1e-6)
        }
        assertTrue(json.contains("road_match_tuning"))
        assertEquals(RoadMatchTuningExport.FORMAT_VERSION, JSONObject(json).getInt("formatVersion"))
    }

    @Test
    fun fullExportIncludesDefaultValues() {
        val json = RoadMatchTuningExport.exportJson("test.pkg", RoadMatchTuning.DEFAULT)
        val tuningObj = JSONObject(json).getJSONObject("tuning")

        assertTrue(json.contains("crossBlend"))
        assertEquals(RoadMatchTuning.EXPORT_MODE_FULL, tuningObj.getString("exportMode"))
        assertEquals(
            RoadMatchTuningKey.entries.size,
            tuningObj.length() - 2,
        )
    }

    @Test
    fun fullImportRestoresExplicitDefaults() {
        val exported = RoadMatchTuningExport.exportJson("pkg", RoadMatchTuning.DEFAULT)
        val restored = RoadMatchTuningExport.importJson(exported).getOrThrow()

        RoadMatchTuningKey.entries.forEach { key ->
            assertEquals(key.defaultValue, restored[key], 1e-6)
        }
        assertTrue(restored.isDefault())
    }

    @Test
    fun rawSparseTuningJsonIsAccepted() {
        val tuning = RoadMatchTuning.DEFAULT.with(RoadMatchTuningKey.RAILS_SOFT_BLEND, 0.60)
        val raw = tuning.toJson()

        val restored = RoadMatchTuningExport.importJson(raw).getOrThrow()

        assertEquals(0.60, restored[RoadMatchTuningKey.RAILS_SOFT_BLEND], 1e-6)
    }

    @Test
    fun rawFullTuningJsonIsAccepted() {
        val tuning = RoadMatchTuning.DEFAULT.with(RoadMatchTuningKey.RAILS_SOFT_BLEND, 0.60)
        val raw = tuning.toFullJson()

        val restored = RoadMatchTuningExport.importJson(raw).getOrThrow()

        RoadMatchTuningKey.entries.forEach { key ->
            assertEquals(tuning[key], restored[key], 1e-6)
        }
    }

    @Test
    fun legacySparseWrappedFormatStillImports() {
        val sparse = RoadMatchTuning.DEFAULT.with(RoadMatchTuningKey.CROSS_BLEND, 0.55).toJson()
        val wrapped =
            """{"formatVersion":1,"kind":"road_match_tuning","tuning":$sparse}"""

        val restored = RoadMatchTuningExport.importJson(wrapped).getOrThrow()

        assertEquals(0.55, restored[RoadMatchTuningKey.CROSS_BLEND], 1e-6)
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
}
