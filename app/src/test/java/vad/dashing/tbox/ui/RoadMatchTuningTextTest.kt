package vad.dashing.tbox.ui

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.location.roadmatch.RoadMatchTuningKey
import java.io.File

/**
 * Titles and descriptions live in flavor string resources so a new language is an overlay,
 * not another Kotlin branch. Both `main` (ru) and `en` must carry every key.
 */
class RoadMatchTuningTextTest {
    @Test
    fun everySliderHasClearRussianAndEnglishText() {
        val ru = loadStrings(listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml"))
        val en = loadStrings(listOf("src/en/res/values/strings.xml", "app/src/en/res/values/strings.xml"))
        RoadMatchTuningKey.entries.forEach { key ->
            val titleName = "road_match_tune_title_${key.name.lowercase()}"
            val descName = "road_match_tune_desc_${key.name.lowercase()}"
            val ruTitle = ru[titleName]
            val enTitle = en[titleName]
            val ruDescription = ru[descName]
            val enDescription = en[descName]
            assertTrue("${key.name}: missing RU title", !ruTitle.isNullOrBlank() && ruTitle.length >= 4)
            assertTrue("${key.name}: missing EN title", !enTitle.isNullOrBlank() && enTitle.length >= 4)
            assertTrue("${key.name}: RU description is too short", !ruDescription.isNullOrBlank() && ruDescription.length >= 35)
            assertTrue("${key.name}: EN description is too short", !enDescription.isNullOrBlank() && enDescription.length >= 35)
            assertNotEquals("${key.name}: descriptions were not localized", ruDescription, enDescription)
        }
    }

    private fun loadStrings(candidates: List<String>): Map<String, String> {
        val file = candidates.map { File(it) }.firstOrNull { it.isFile }
            ?: error("strings.xml not found in $candidates")
        val names = Regex("""<string name="([^"]+)">([^<]*)</string>""")
        return names.findAll(file.readText()).associate { match ->
            match.groupValues[1] to unescape(match.groupValues[2])
        }
    }

    private fun unescape(raw: String): String =
        raw.replace("\\'", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("%%", "%")
}
