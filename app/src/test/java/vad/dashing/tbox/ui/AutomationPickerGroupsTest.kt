package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.automation.AutomationBuiltinActionType
import vad.dashing.tbox.automation.AutomationCanCatalog
import vad.dashing.tbox.automation.builtinActionGroup

/**
 * Regression tests for the searchable picker LazyColumn keys. The dialog builds LazyColumn
 * item keys from the section index plus option key/label, so duplicate keys (and a crash)
 * happen when two options with the same group are not adjacent, or when two options share
 * the same key. `sortedGroupedConsecutive()` plus unique picker keys prevent that.
 */
class AutomationPickerGroupsTest {
    private fun option(
        value: String,
        label: String,
        group: String?,
        key: String,
    ) = AutomationPickerOption(value = value, label = label, group = group, key = key)

    @Test
    fun sortedGroupedConsecutive_sortsGroupsByFirstAppearance() {
        val sorted = listOf(
            option("a", "A", group = "G2", key = "a"),
            option("b", "B", group = "G1", key = "b"),
            option("c", "C", group = "G2", key = "c"),
            option("d", "D", group = "G1", key = "d"),
        ).sortedGroupedConsecutive()

        assertEquals(listOf("G2", "G2", "G1", "G1"), sorted.map { it.group })
    }

    @Test
    fun sortedGroupedConsecutive_keepsWithinGroupOrder() {
        val sorted = listOf(
            option("a", "A", group = "G1", key = "a"),
            option("b", "B", group = "G1", key = "b"),
            option("c", "C", group = "G1", key = "c"),
        ).sortedGroupedConsecutive()

        assertEquals(listOf("a", "b", "c"), sorted.map { it.value })
    }

    @Test
    fun sortedGroupedConsecutive_movesUngroupedToTheEnd() {
        val sorted = listOf(
            option("a", "A", group = null, key = "a"),
            option("b", "B", group = "G1", key = "b"),
            option("c", "C", group = null, key = "c"),
        ).sortedGroupedConsecutive()

        assertEquals(listOf("G1", null, null), sorted.map { it.group })
        assertEquals(listOf("b", "a", "c"), sorted.map { it.value })
    }

    @Test
    fun mergedActionOptions_keysAreUnique() {
        val canMode = HeadUnitCanMode.Android10Vhal
        val keys = buildList {
            AutomationBuiltinActionType.entries
                .filter { it != AutomationBuiltinActionType.ESP_RELAY_SET }
                .forEach { type -> add("builtin:${type.storageKey}") }
            AutomationCanCatalog.pickerEntries()
                .filter { it.supports(canMode) }
                .forEach { entry -> add("can:${entry.bus}:${entry.propertyId}") }
        }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun mergedActionOptions_sortedGroupedConsecutive_makesEveryGroupContiguous() {
        val canMode = HeadUnitCanMode.Android10Vhal
        val merged = buildList {
            AutomationBuiltinActionType.entries
                .filter { it != AutomationBuiltinActionType.ESP_RELAY_SET }
                .forEach { type ->
                    add(
                        AutomationPickerOption<Any>(
                            value = type,
                            label = builtinActionLabel(type),
                            group = builtinActionGroup(type).label,
                            key = "builtin:${type.storageKey}",
                        ),
                    )
                }
            AutomationCanCatalog.pickerEntries()
                .filter { it.supports(canMode) }
                .forEach { entry ->
                    add(
                        AutomationPickerOption<Any>(
                            value = entry,
                            label = entry.label,
                            group = entry.group.label,
                            key = "can:${entry.bus}:${entry.propertyId}",
                        ),
                    )
                }
        }.sortedGroupedConsecutive()

        val groupStarts = mutableMapOf<String, Int>()
        val groupEnds = mutableMapOf<String, Int>()
        merged.forEachIndexed { index, option ->
            val group = requireNotNull(option.group) { "Merged picker groups are non-null" }
            groupStarts.putIfAbsent(group, index)
            groupEnds[group] = index
        }
        assertTrue(
            "Groups must be contiguous blocks: starts=$groupStarts ends=$groupEnds",
            groupStarts.all { (group, start) -> groupEnds.getValue(group) - start + 1 ==
                merged.count { it.group == group } },
        )
        val keys = merged.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun defaultBuiltinAction_fillsValidDefaultsPerType() {
        assertEquals(
            10,
            defaultBuiltinAction(AutomationBuiltinActionType.SET_MEDIA_VOLUME, emptyList(), "", emptyList()).intValue,
        )
        assertEquals(
            10,
            defaultBuiltinAction(AutomationBuiltinActionType.SET_PHONE_VOLUME, emptyList(), "", emptyList()).intValue,
        )
        assertEquals(
            8,
            defaultBuiltinAction(AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS, emptyList(), "", emptyList()).intValue,
        )
        assertEquals(
            5,
            defaultBuiltinAction(AutomationBuiltinActionType.SET_NAVI_VOLUME, emptyList(), "", emptyList()).intValue,
        )
        assertEquals(
            5,
            defaultBuiltinAction(AutomationBuiltinActionType.SET_VOICE_VOLUME, emptyList(), "", emptyList()).intValue,
        )
        assertTrue(
            defaultBuiltinAction(AutomationBuiltinActionType.WIFI_SET_ENABLED, emptyList(), "", emptyList()).boolValue,
        )
        assertEquals(
            "auto",
            defaultBuiltinAction(AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME, emptyList(), "", emptyList()).stringValue,
        )
    }
}
