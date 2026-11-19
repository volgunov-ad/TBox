package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationPickerSearchTest {
    @Test
    fun emptyQuery_matchesEverything() {
        assertTrue(
            AutomationPickerSearch.matches(
                query = "",
                label = "Обогрев руля",
            ),
        )
        assertTrue(
            AutomationPickerSearch.matches(
                query = "   ",
                label = "Обогрев руля",
            ),
        )
    }

    @Test
    fun labelMatching_isCaseInsensitiveAndYoTolerant() {
        assertTrue(
            AutomationPickerSearch.matches(
                query = "обогрев",
                label = "Обогрев руля",
            ),
        )
        // Ё in the query vs Е in the label.
        assertTrue(
            AutomationPickerSearch.matches(
                query = "все стёкла",
                label = "Все стёкла",
            ),
        )
        assertTrue(
            AutomationPickerSearch.matches(
                query = "стекла",
                label = "Все стёкла",
            ),
        )
        assertFalse(
            AutomationPickerSearch.matches(
                query = "климат",
                label = "Обогрев руля",
            ),
        )
    }

    @Test
    fun multiWordQuery_requiresAllWordsSomewhere() {
        assertTrue(
            AutomationPickerSearch.matches(
                query = "обогрев руля",
                label = "Обогрев руля",
            ),
        )
        // Inflection-tolerant: «руль» matches «руля».
        assertTrue(
            AutomationPickerSearch.matches(
                query = "обогрев руль",
                label = "Обогрев руля",
            ),
        )
        // Words may match different haystack parts: label + synonym.
        assertTrue(
            AutomationPickerSearch.matches(
                query = "печка руль",
                label = "Обогрев руля",
                keywords = listOf("печка"),
            ),
        )
        assertFalse(
            AutomationPickerSearch.matches(
                query = "печка руль",
                label = "Обогрев руля",
            ),
        )
    }

    @Test
    fun storageKeyWords_areSearchedWithSpaces() {
        assertTrue(
            AutomationPickerSearch.matches(
                query = "hvac ac max",
                label = "Максимальное охлаждение (AC MAX)",
                storageKey = "hvac_ac_max",
            ),
        )
        assertTrue(
            AutomationPickerSearch.matches(
                query = "fuel level",
                label = "Уровень топлива",
                storageKey = "fuel_level_percent",
            ),
        )
        // Prefix ≥3 chars: "setmedia" matches the "set" token of the key (typo-tolerant).
        assertTrue(
            AutomationPickerSearch.matches(
                query = "setmedia",
                label = "Громкость медиа",
                storageKey = "set_media_volume",
            ),
        )
        assertFalse(
            AutomationPickerSearch.matches(
                query = "zzz",
                label = "Громкость медиа",
                storageKey = "set_media_volume",
            ),
        )
    }

    @Test
    fun groupLabel_participatesInMatching() {
        assertTrue(
            AutomationPickerSearch.matches(
                query = "климат",
                label = "Обогрев руля",
                groupLabel = "Климат и воздух",
            ),
        )
    }

    @Test
    fun normalize_collapsesWhitespaceAndLowercases() {
        assertEquals("печка тест", AutomationPickerSearch.normalize("  ПЕЧКА   ТеСт  "))
    }
}
