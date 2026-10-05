package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.location.ConstantDrMath

class AutomationGeofenceEvaluatorTest {
    @Test
    fun enter_firesAfterCrossingInnerRadiusAndHolds() {
        val evaluator = evaluator(
            geofence(direction = AutomationGeofenceDirection.ENTER, holdMillis = 2_000L),
        )
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 80.0, elapsedMillis = 0L)))
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 20.0, elapsedMillis = 100L)))
        assertNull(evaluator.onTick(2_099L))
        assertEquals("home", evaluator.onTick(2_100L)?.triggerId)
    }

    @Test
    fun enter_annulusDoesNotRearm() {
        val evaluator = evaluator(geofence(direction = AutomationGeofenceDirection.ENTER))
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 80.0, elapsedMillis = 0L)))
        assertEquals("home", evaluator.onSignalSample(geoSample(northMeters = 20.0, elapsedMillis = 100L))?.triggerId)
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 55.0, elapsedMillis = 200L)))
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 20.0, elapsedMillis = 300L)))
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 80.0, elapsedMillis = 400L)))
        assertEquals("home", evaluator.onSignalSample(geoSample(northMeters = 20.0, elapsedMillis = 500L))?.triggerId)
    }

    @Test
    fun enter_dropBelowZoneKeepsArmedAndRestartsHold() {
        val evaluator = evaluator(
            geofence(direction = AutomationGeofenceDirection.ENTER, holdMillis = 2_000L),
        )
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 80.0, elapsedMillis = 0L)))
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 20.0, elapsedMillis = 100L)))
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 80.0, elapsedMillis = 500L)))
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 20.0, elapsedMillis = 600L)))
        assertNull(evaluator.onTick(2_599L))
        assertEquals("home", evaluator.onTick(2_600L)?.triggerId)
    }

    @Test
    fun exit_firesAfterLeavingZoneAndRearmsInsideSmallerRadius() {
        val evaluator = evaluator(
            geofence(
                direction = AutomationGeofenceDirection.EXIT,
                zoneRadiusMeters = 50.0,
                rearmRadiusMeters = 40.0,
            ),
        )
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 0.0, elapsedMillis = 0L)))
        assertEquals("home", evaluator.onSignalSample(geoSample(northMeters = 80.0, elapsedMillis = 100L))?.triggerId)
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 45.0, elapsedMillis = 200L)))
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 80.0, elapsedMillis = 300L)))
        assertNull(evaluator.onSignalSample(geoSample(northMeters = 0.0, elapsedMillis = 400L)))
        assertEquals("home", evaluator.onSignalSample(geoSample(northMeters = 80.0, elapsedMillis = 500L))?.triggerId)
    }

    @Test
    fun rearmRadiusUnchangedWhenAlreadyOnCorrectSide() {
        assertEquals(
            80.0,
            automationGeofenceRearmRadius(AutomationGeofenceDirection.ENTER, 50.0, 80.0),
            0.0,
        )
        assertEquals(
            60.0,
            automationGeofenceRearmRadius(AutomationGeofenceDirection.ENTER, 50.0, 40.0),
            0.0,
        )
        assertEquals(
            20.0,
            automationGeofenceRearmRadius(AutomationGeofenceDirection.EXIT, 50.0, 20.0),
            0.0,
        )
        assertEquals(
            40.0,
            automationGeofenceRearmRadius(AutomationGeofenceDirection.EXIT, 50.0, 70.0),
            0.0,
        )
    }

    private fun evaluator(trigger: AutomationTrigger): AutomationEvaluator =
        AutomationEvaluator(
            AutomationDefinition(
                id = "automation",
                name = "Geo",
                enabled = true,
                triggers = listOf(trigger),
                actions = listOf(AutomationAction.Delay(0L)),
            ),
            allowStartupFire = false,
        )

    private fun geofence(
        direction: AutomationGeofenceDirection,
        zoneRadiusMeters: Double = 50.0,
        rearmRadiusMeters: Double = 60.0,
        holdMillis: Long = 0L,
    ): AutomationTrigger.Geofence =
        AutomationTrigger.Geofence(
            id = "home",
            queryText = "$POINT_LAT, $POINT_LON",
            latitude = POINT_LAT,
            longitude = POINT_LON,
            direction = direction,
            zoneRadiusMeters = zoneRadiusMeters,
            rearmRadiusMeters = rearmRadiusMeters,
            holdMillis = holdMillis,
        )

    private fun geoSample(northMeters: Double, elapsedMillis: Long): AutomationSignalSample {
        val latitude = POINT_LAT + northMeters / 111_320.0
        val distance = ConstantDrMath.distanceMeters(POINT_LAT, POINT_LON, latitude, POINT_LON)
        check(kotlin.math.abs(distance - kotlin.math.abs(northMeters)) < 0.01)
        return AutomationSignalSample(
            key = AUTOMATION_GEO_DISPLAY_KEY,
            value = AutomationSignalValue.Position(latitude, POINT_LON),
            observedAtElapsedMillis = elapsedMillis,
        )
    }

    companion object {
        private const val POINT_LAT = 55.75
        private const val POINT_LON = 37.62
    }
}
