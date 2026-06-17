package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCacheKeysTest {

    @Test
    fun sanitizeCacheKey_stripsUnsafeCharacters() {
        assertEquals("my_theme", ThemeCacheKeys.sanitizeCacheKey("  my theme!!  "))
    }

    @Test
    fun sanitizeCacheKey_keepsAllowedPunctuation() {
        assertEquals("theme-v2.1", ThemeCacheKeys.sanitizeCacheKey("theme-v2.1"))
    }

    @Test
    fun driveModeCacheKey_includesRawValueAndSlug() {
        val key = ThemeCacheKeys.driveModeCacheKey(2)
        assertEquals("drive_mode_2_eco", key)
    }

    @Test
    fun isLikelyCacheKey_rejectsUriSchemes() {
        assertFalse(ThemeCacheKeys.isLikelyCacheKey("content://doc/theme/file.tboxtheme"))
        assertTrue(ThemeCacheKeys.isLikelyCacheKey("my_theme"))
        assertTrue(ThemeCacheKeys.isLikelyCacheKey("drive_mode_2_eco"))
    }
}
