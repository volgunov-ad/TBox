package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveModeCycleWidgetTest {

    @Test
    fun normalize_defaultsOnEmpty() {
        assertEquals(
            DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES,
            normalizeDriveModeCycleSelection(emptyList()),
        )
    }

    @Test
    fun normalize_keepsOrderFromOptionsAndDropsInvalid() {
        assertEquals(
            listOf(2, 0, 1, 5),
            normalizeDriveModeCycleSelection(listOf(5, 99, 2, 0, 1)),
        )
    }

    @Test
    fun normalize_keepsFamilyOfFirstValidValue() {
        assertEquals(
            listOf(2, 0),
            normalizeDriveModeCycleSelection(listOf(2, 101, 0, 100)),
        )
        assertEquals(
            listOf(101, 102, 100),
            normalizeDriveModeCycleSelection(listOf(101, 2, 102, 100)),
        )
    }

    @Test
    fun next_cyclesWithinSelected() {
        val selected = listOf(2, 0, 1) // ECO, NOR, SPT
        assertEquals(0, nextDriveModeCycleTarget(2, selected).rawValue) // ECO ? NOR
        assertEquals(1, nextDriveModeCycleTarget(0, selected).rawValue) // NOR ? SPT
        assertEquals(2, nextDriveModeCycleTarget(1, selected).rawValue) // SPT ? ECO
    }

    @Test
    fun next_fromOutsideSelected_takesNextSelectedInFullOrder() {
        // Selected ECO+SPT; current SAND (index after SPT) ? next selected is ECO (wrap)
        assertEquals(
            2,
            nextDriveModeCycleTarget(5, listOf(2, 1)).rawValue,
        )
        // Current NOR (between ECO and SPT) ? next selected SPT
        assertEquals(
            1,
            nextDriveModeCycleTarget(0, listOf(2, 1)).rawValue,
        )
    }

    @Test
    fun next_nullCurrent_returnsFirstSelected() {
        assertEquals(
            2,
            nextDriveModeCycleTarget(null, listOf(2, 0, 1)).rawValue,
        )
    }

    @Test
    fun toggle_cannotClearLast() {
        val onlyEco = listOf(2)
        assertEquals(onlyEco, toggleDriveModeCycleSelection(onlyEco, 2))
    }

    @Test
    fun toggle_switchesFamily() {
        val standard = listOf(2, 0, 1)
        val switched = toggleDriveModeCycleSelection(standard, 101)
        assertEquals(listOf(101), switched)
        assertTrue(switched.all { isDriveMode6dct(it) })
    }

    @Test
    fun toggle_addsSameFamily() {
        val ecoNor = listOf(2, 0)
        assertEquals(listOf(2, 0, 1), toggleDriveModeCycleSelection(ecoNor, 1))
    }

    @Test
    fun isDriveMode6dct_detectsFamilies() {
        assertFalse(isDriveMode6dct(2))
        assertTrue(isDriveMode6dct(101))
    }
}
