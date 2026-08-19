package vad.dashing.tbox.ui

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.location.roadmatch.RoadMatchTuningKey

class RoadMatchTuningTextTest {
    @Test
    fun everySliderHasClearRussianAndEnglishText() {
        RoadMatchTuningKey.entries.forEach { key ->
            val ruTitle = roadMatchTuningTitle(key, ru = true)
            val enTitle = roadMatchTuningTitle(key, ru = false)
            val ruDescription = roadMatchTuningDescription(key, ru = true)
            val enDescription = roadMatchTuningDescription(key, ru = false)

            assertTrue("${key.name}: missing RU title", ruTitle.length >= 4)
            assertTrue("${key.name}: missing EN title", enTitle.length >= 4)
            assertTrue("${key.name}: RU description is too short", ruDescription.length >= 35)
            assertTrue("${key.name}: EN description is too short", enDescription.length >= 35)
            assertNotEquals("${key.name}: descriptions were not localized", ruDescription, enDescription)
        }
    }
}
