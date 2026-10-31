package vad.dashing.tbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import vad.dashing.tbox.SettingsViewModel

/**
 * Horizontal section index persisted per left-menu [tabKey].
 *
 * Restores the saved section key among [sectionKeys] (first tab if unset/unknown),
 * and writes back when the user changes the selection.
 */
@Composable
fun rememberPersistedHorizontalSectionIndex(
    tabKey: String,
    sectionKeys: List<String>,
    settingsViewModel: SettingsViewModel,
): MutableIntState {
    val selectedSectionIndex = rememberSaveable(tabKey) { mutableIntStateOf(0) }
    var restored by rememberSaveable(tabKey) { mutableStateOf(false) }

    LaunchedEffect(tabKey, sectionKeys) {
        val saved = settingsViewModel.peekLastMenuSection(tabKey)
        selectedSectionIndex.intValue = LastMenuSections.resolveIndex(saved, sectionKeys)
        restored = true
    }

    LaunchedEffect(restored, tabKey, selectedSectionIndex.intValue, sectionKeys) {
        if (!restored || sectionKeys.isEmpty()) return@LaunchedEffect
        val index = selectedSectionIndex.intValue.coerceIn(0, sectionKeys.lastIndex)
        if (index != selectedSectionIndex.intValue) {
            selectedSectionIndex.intValue = index
        }
        settingsViewModel.saveLastMenuSection(tabKey, sectionKeys[index])
    }

    return selectedSectionIndex
}
