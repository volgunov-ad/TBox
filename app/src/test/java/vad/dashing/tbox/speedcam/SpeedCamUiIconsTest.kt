package vad.dashing.tbox.speedcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.ui.UiIconCatalog

class SpeedCamUiIconsTest {
    @Test
    fun allKeysRegisteredInUiIconCatalog() {
        for (key in SpeedCamUiIcons.allKeys) {
            val entry = UiIconCatalog.entry(key)
            assertNotNull("missing catalog entry for $key", entry)
            assertNotNull("missing drawable for $key", entry!!.drawableRes)
            assertEquals(key, UiIconCatalog.keyForDrawable(entry.drawableRes!!))
        }
    }

    @Test
    fun everyCategoryMapsToCatalogKey() {
        for (category in SpeedCamCategory.entries) {
            val ref = SpeedCamUiIcons.forCategory(category)
            assertTrue(ref.key in SpeedCamUiIcons.allCategoryKeys)
            assertNotNull(UiIconCatalog.entry(ref.key))
        }
    }

    @Test
    fun relativeArrowsMapToCatalog() {
        assertEquals(
            SpeedCamUiIcons.KEY_ARROW_SAME,
            SpeedCamUiIcons.forRelative(SpeedCamRelativeDirection.SAME).key,
        )
        assertEquals(
            SpeedCamUiIcons.KEY_ARROW_ONCOMING,
            SpeedCamUiIcons.forRelative(SpeedCamRelativeDirection.ONCOMING).key,
        )
    }
}
