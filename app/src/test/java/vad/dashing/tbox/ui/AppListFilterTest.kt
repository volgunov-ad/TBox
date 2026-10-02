package vad.dashing.tbox.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListFilterTest {

    @Test
    fun blankQuery_matchesEverything() {
        assertTrue(appListMatchesFilter("Maps", "com.google.android.apps.maps", ""))
        assertTrue(appListMatchesFilter("Maps", "com.google.android.apps.maps", "   "))
    }

    @Test
    fun matchesLabel_caseInsensitiveSubstring() {
        assertTrue(appListMatchesFilter("Яндекс.Навигатор", "ru.yandex.yandexnavi", "навиг"))
        assertTrue(appListMatchesFilter("Maps", "com.google.android.apps.maps", "MAP"))
        assertFalse(appListMatchesFilter("Maps", "com.google.android.apps.maps", "nav"))
    }

    @Test
    fun matchesPackage_caseInsensitiveSubstring() {
        assertTrue(appListMatchesFilter("Maps", "com.google.android.apps.maps", "GOOGLE"))
        assertTrue(appListMatchesFilter("Maps", "com.google.android.apps.maps", ".maps"))
        assertFalse(appListMatchesFilter("Maps", "com.google.android.apps.maps", "yandex"))
    }
}
