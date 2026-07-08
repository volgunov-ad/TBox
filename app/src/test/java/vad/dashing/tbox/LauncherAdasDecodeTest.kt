package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.ui.launcher.LauncherAdasAccMode
import vad.dashing.tbox.ui.launcher.LauncherAdasFrontObjectType
import vad.dashing.tbox.ui.launcher.LauncherAdasLaneVisualization
import vad.dashing.tbox.ui.launcher.buildLauncherAdasState
import vad.dashing.tbox.ui.launcher.decodeAccMode
import vad.dashing.tbox.ui.launcher.distanceToRoadDepth

class LauncherAdasDecodeTest {

    @Test
    fun decodeAccMode_matchesStockThresholds() {
        assertEquals(LauncherAdasAccMode.Standby, decodeAccMode(9))
        assertEquals(LauncherAdasAccMode.ActiveBlue, decodeAccMode(4))
        assertEquals(LauncherAdasAccMode.ActiveDark, decodeAccMode(7))
    }

    @Test
    fun buildState_objectValidAndType() {
        val state = buildLauncherAdasState(
            accModeRaw = 4,
            vSetDisRaw = 80,
            objValidRaw = 2,
            frontObjectTypeRaw = 1,
            dxTarObjRaw = 42,
            objectDxRaw = 40,
            takeOverRaw = 0,
            textInfoRaw = 0,
            fcwPreWarningRaw = 0,
            distanceWarningRaw = 0,
            timeGapRaw = 1,
            leftLaneRaw = 1,
            rightLaneRaw = 0,
            adasTakeOverRaw = 0,
        )
        assertTrue(state.frontObject.valid)
        assertEquals(LauncherAdasFrontObjectType.Car, state.frontObject.type)
        assertEquals(40, state.frontObject.objectDxM)
        assertEquals(42, state.frontObject.targetDxM)
        assertEquals(40, state.frontObject.displayDistanceM)
        assertEquals(80, state.accSetSpeedKmh)
        assertEquals(1, state.timeGapLevel)
        assertEquals(LauncherAdasLaneVisualization.Tracking, state.leftLane)
    }

    @Test
    fun buildState_fcwAndTakeoverFlags() {
        val state = buildLauncherAdasState(
            accModeRaw = 7,
            vSetDisRaw = 60,
            objValidRaw = 2,
            frontObjectTypeRaw = 3,
            dxTarObjRaw = 15,
            objectDxRaw = 0,
            takeOverRaw = 2,
            textInfoRaw = 18,
            fcwPreWarningRaw = 2,
            distanceWarningRaw = 0,
            timeGapRaw = 0,
            leftLaneRaw = 3,
            rightLaneRaw = 0,
            adasTakeOverRaw = 0,
        )
        assertTrue(state.fcwActive)
        assertTrue(state.accTakeOver)
        assertTrue(state.accOverride)
        assertTrue(state.laneDepartureLeft)
        assertEquals(15, state.frontObject.displayDistanceM)
        assertEquals(LauncherAdasFrontObjectType.Motorcycle, state.frontObject.type)
    }

    @Test
    fun distanceToRoadDepth_monotonic() {
        assertTrue(distanceToRoadDepth(10) > distanceToRoadDepth(80))
        assertFalse(distanceToRoadDepth(5) < 0.1f)
    }
}
