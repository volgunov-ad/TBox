package vad.dashing.tbox.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HuDisplayDumpParserTest {

    @Test
    fun parse_mDisplayIdThenSize_collectsAllDisplays() {
        val dump = """
            Display Devices: size=2
              mDisplayId=0
              mBaseDisplayInfo=DisplayInfo{"Built-in", 1920 x 981, 160.0 dpi}
              mDisplayId=5
              mBaseDisplayInfo=DisplayInfo{"Virtual", 1320 x 856, 160.0 dpi}
        """.trimIndent()

        val displays = HuDisplayDumpParser.parse(dump)
        assertEquals(
            listOf(
                HuDisplayInfo(0, 1920, 981),
                HuDisplayInfo(5, 1320, 856),
            ),
            displays,
        )
    }

    @Test
    fun parse_displayHashSections() {
        val dump = """
            Display #0 (name=Built-in Screen):
              init=1920x1080 160dpi
            Display #5 (name=ActivityDisplay):
              init=1320x856 160dpi
        """.trimIndent()

        val displays = HuDisplayDumpParser.parse(dump)
        assertEquals(2, displays.size)
        assertEquals(0, displays[0].displayId)
        assertEquals(1920, displays[0].widthPx)
        assertEquals(5, displays[1].displayId)
        assertEquals(1320, displays[1].widthPx)
    }

    @Test
    fun parse_empty_returnsEmpty() {
        assertTrue(HuDisplayDumpParser.parse("").isEmpty())
        assertTrue(HuDisplayDumpParser.parse("no displays here").isEmpty())
    }

    @Test
    fun json_roundTrip() {
        val list = listOf(HuDisplayInfo(0, 1920, 981), HuDisplayInfo(5, 1320, 856))
        val json = HuDisplayInfo.listToJson(list)
        assertEquals(list, HuDisplayInfo.listFromJson(json))
        assertTrue(HuDisplayInfo.listFromJson(null).isEmpty())
        assertTrue(HuDisplayInfo.listFromJson("not-json").isEmpty())
    }

    @Test
    fun label_usesIdAndSize() {
        assertEquals("5: 1320×856", HuDisplayInfo(5, 1320, 856).label())
    }
}
