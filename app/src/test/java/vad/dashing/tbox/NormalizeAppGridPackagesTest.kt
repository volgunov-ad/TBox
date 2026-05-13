package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeAppGridPackagesTest {

    @Test
    fun trims_dedupes_and_caps() {
        assertEquals(
            listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"),
            normalizeAppGridPackages(
                listOf(
                    " a ", "b", "a", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n"
                )
            )
        )
    }
}
