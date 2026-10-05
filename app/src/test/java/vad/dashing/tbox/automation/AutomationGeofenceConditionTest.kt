package vad.dashing.tbox.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.location.ConstantDrMath

class AutomationGeofenceConditionTest {
    @Test
    fun inside_matchesWhenWithinRadius() {
        val condition = AutomationCondition.Geofence(
            queryText = "$POINT_LAT, $POINT_LON",
            latitude = POINT_LAT,
            longitude = POINT_LON,
            presence = AutomationGeofencePresence.INSIDE,
            zoneRadiusMeters = 50.0,
        )
        val snapshot = mapOf(
            AUTOMATION_GEO_DISPLAY_KEY to AutomationSignalValue.Position(POINT_LAT + 0.0002, POINT_LON),
        )
        assertTrue(evaluate(condition, snapshot))
    }

    @Test
    fun outside_matchesWhenBeyondRadius() {
        val condition = AutomationCondition.Geofence(
            queryText = "$POINT_LAT, $POINT_LON",
            latitude = POINT_LAT,
            longitude = POINT_LON,
            presence = AutomationGeofencePresence.OUTSIDE,
            zoneRadiusMeters = 50.0,
        )
        val farLat = POINT_LAT + 80.0 / 111_320.0
        val snapshot = mapOf(
            AUTOMATION_GEO_DISPLAY_KEY to AutomationSignalValue.Position(farLat, POINT_LON),
        )
        val distance = ConstantDrMath.distanceMeters(POINT_LAT, POINT_LON, farLat, POINT_LON)
        assertTrue(distance > 50.0)
        assertTrue(evaluate(condition, snapshot))
    }

    @Test
    fun missingGeo_isFalse() {
        val condition = AutomationCondition.Geofence(
            latitude = POINT_LAT,
            longitude = POINT_LON,
            presence = AutomationGeofencePresence.INSIDE,
            zoneRadiusMeters = 50.0,
        )
        assertFalse(evaluate(condition, emptyMap()))
    }

    private fun evaluate(
        condition: AutomationCondition.Geofence,
        snapshot: Map<AutomationSignalKey, AutomationSignalValue>,
    ): Boolean = AutomationEvaluator.evaluateCondition(
        condition = condition,
        context = AutomationTriggerContext("a", "1", 0L),
        snapshot = snapshot,
    )

    companion object {
        private const val POINT_LAT = 55.75
        private const val POINT_LON = 37.62
    }
}
