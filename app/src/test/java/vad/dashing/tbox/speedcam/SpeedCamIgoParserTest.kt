package vad.dashing.tbox.speedcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedCamIgoParserTest {
    @Test
    fun parsesIgoextHeaderAndRows() {
        val text = """
            IDX,X,Y,TYPE,SPEED,DIRTYPE,DIRECTION
            1,37.6173,55.7558,1,60,1,90
            2,37.6200,55.7600,4,0,0,0
        """.trimIndent()
        assertTrue(SpeedCamIgoParser.looksLikeIgoCsv(text))
        val points = SpeedCamIgoParser.parse(text)
        assertEquals(2, points.size)
        assertEquals(1, points[0].id)
        assertEquals(37.6173, points[0].lon, 1e-6)
        assertEquals(55.7558, points[0].lat, 1e-6)
        assertEquals(60, points[0].speedKmh)
        assertEquals(1, points[0].dirType)
        assertEquals(90, points[0].directionDeg)
        assertEquals(SpeedCamCategory.FIXED, points[0].category)
        assertEquals(SpeedCamCategory.SECTION, points[1].category)
    }

    @Test
    fun rejectsContactStub() {
        val stub = "Contact with me via Telegram @speedcamonline"
        assertFalse(SpeedCamIgoParser.looksLikeIgoCsv(stub))
    }

    @Test
    fun parseDataLineNormalizesDirection() {
        val p = SpeedCamIgoParser.parseDataLine("9,30.0,50.0,5,40,2,-10")
        assertNotNull(p)
        assertEquals(350, p!!.directionDeg)
        assertEquals(SpeedCamCategory.MOBILE, p.category)
    }
}
