package vad.dashing.tbox.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.location.roadmatch.RoadMatchAnchorState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OsmSpeedLimitDisplayTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun currentOnlyWhenNextMissing() {
        val d = OsmSpeedLimitDisplay.from(
            RoadMatchAnchorState(currentLimitKmh = 60),
        )
        assertEquals("60", d.currentLabel)
        assertFalse(d.showNext)
        assertNull(d.nextLabel)
    }

    @Test
    fun showsNextWithDistance() {
        val d = OsmSpeedLimitDisplay.from(
            RoadMatchAnchorState(
                currentLimitKmh = 60,
                nextLimitKmh = 40,
                nextLimitDistanceM = 180.0,
                nextLimitHidden = false,
            ),
        )
        assertEquals("60", d.currentLabel)
        assertTrue(d.showNext)
        assertEquals("40", d.nextLabel)
        assertEquals(180.0, d.nextDistanceM!!, 0.0)
        assertEquals(
            context.getString(vad.dashing.tbox.R.string.osm_speed_limit_distance_m, 180),
            d.nextDistanceLabel(context),
        )
    }

    @Test
    fun hidesNextWhenAmbiguousFork() {
        val d = OsmSpeedLimitDisplay.from(
            RoadMatchAnchorState(
                currentLimitKmh = 90,
                nextLimitKmh = 60,
                nextLimitDistanceM = 50.0,
                nextLimitHidden = true,
            ),
        )
        assertEquals("90", d.currentLabel)
        assertFalse(d.showNext)
    }

    @Test
    fun unknownCurrentIsNullLabel() {
        val d = OsmSpeedLimitDisplay.from(RoadMatchAnchorState.EMPTY)
        assertNull(d.currentLabel)
        assertFalse(d.showNext)
    }

    @Test
    fun formatDistanceUsesKmAbove1000() {
        assertEquals(
            context.getString(vad.dashing.tbox.R.string.osm_speed_limit_distance_km, "1.2"),
            OsmSpeedLimitDisplay.formatDistanceAhead(context, 1200.0),
        )
        assertEquals(
            context.getString(vad.dashing.tbox.R.string.osm_speed_limit_distance_km, "12"),
            OsmSpeedLimitDisplay.formatDistanceAhead(context, 12_500.0),
        )
    }
}
