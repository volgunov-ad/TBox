package vad.dashing.tbox

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * en flavor overrides main (Russian) string resources. A missing name falls back to Russian.
 */
class EnStringsCoverageTest {
    @Test
    fun enStringsOverrideEveryMainName() {
        val main = readResource("src/main/res/values/strings.xml")
        val en = readResource("src/en/res/values/strings.xml")
        val missing = resourceNames(main) - resourceNames(en)
        assertTrue("en/strings.xml missing: $missing", missing.isEmpty())
    }

    @Test
    fun enStringsHaveNoCyrillic() {
        val en = readResource("src/en/res/values/strings.xml")
        val cyrillic = Regex("[А-Яа-яЁё]")
        val hits = en.lineSequence()
            .filter { cyrillic.containsMatchIn(it) && !it.trim().startsWith("<!--") }
            .toList()
        assertTrue("Cyrillic in en/strings.xml: $hits", hits.isEmpty())
    }

    private fun readResource(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("strings.xml not found from ${File(".").absolutePath}")
        return file.readText()
    }

    private fun resourceNames(xml: String): Set<String> {
        val names = Regex("""<(?:string|plurals|string-array)\s([^>]*?)/?>""")
        return names.findAll(xml).mapNotNull { match ->
            val attrs = match.groupValues[1]
            if (attrs.contains("""translatable="false"""")) return@mapNotNull null
            Regex("""name="([^"]+)"""").find(attrs)?.groupValues?.get(1)
        }.toSet()
    }
}
