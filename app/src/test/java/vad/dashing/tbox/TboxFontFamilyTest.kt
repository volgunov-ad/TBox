package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import vad.dashing.tbox.ui.theme.TboxFontFamily
import vad.dashing.tbox.ui.theme.resolveFontFamily
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxMaterialTypography
import vad.dashing.tbox.ui.theme.tboxTextStyles
import vad.dashing.tbox.ui.theme.tboxTitle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

class TboxFontFamilyTest {

    @Test
    fun fromId_returnsMatchingPresetOrDefault() {
        assertEquals(TboxFontFamily.Serif, TboxFontFamily.fromId(2))
        assertEquals(TboxFontFamily.Cabin, TboxFontFamily.fromId(5))
        assertEquals(TboxFontFamily.Nunito, TboxFontFamily.fromId(6))
        assertEquals(TboxFontFamily.Roboto, TboxFontFamily.fromId(7))
        assertEquals(TboxFontFamily.Default, TboxFontFamily.fromId(4))
        assertEquals(TboxFontFamily.Default, TboxFontFamily.fromId(99))
    }

    @Test
    fun fromSlug_roundTripsSlug() {
        assertEquals(TboxFontFamily.Monospace, TboxFontFamily.fromSlug("monospace"))
        assertEquals(TboxFontFamily.Cabin, TboxFontFamily.fromSlug("cabin"))
        assertEquals(TboxFontFamily.Roboto, TboxFontFamily.fromSlug("roboto"))
        assertNull(TboxFontFamily.fromSlug("crimson_text"))
        assertNull(TboxFontFamily.fromSlug("unknown"))
        assertNull(TboxFontFamily.fromSlug(""))
    }

    @Test
    fun typographyUsesSelectedFontFamilyAndMediumTitleHeadline() {
        val typography = tboxMaterialTypography(FontFamily.Serif)
        assertEquals(FontFamily.Serif, typography.tboxTitle.fontFamily)
        assertEquals(FontWeight.Medium, typography.tboxTitle.fontWeight)
        assertEquals(FontFamily.Serif, typography.tboxHeadline.fontFamily)
        assertEquals(FontWeight.Medium, typography.tboxHeadline.fontWeight)
        assertEquals(FontFamily.Serif, typography.tboxCaption.fontFamily)
        assertEquals(FontWeight.Normal, typography.tboxCaption.fontWeight)
    }

    @Test
    fun widgetTextStyles_useMediumWeightAndStaySeparateFromAppText() {
        val styles = tboxTextStyles(FontFamily.Serif)
        assertEquals(FontFamily.Serif, styles.WidgetTitle.fontFamily)
        assertEquals(FontWeight.Medium, styles.WidgetTitle.fontWeight)
        assertEquals(FontWeight.Medium, styles.WidgetValue.fontWeight)
        assertEquals(FontWeight.Medium, styles.WidgetUnit.fontWeight)
        assertEquals(FontWeight.Medium, styles.Title.fontWeight)
        assertEquals(FontWeight.Medium, styles.Headline.fontWeight)
        assertEquals(FontWeight.Normal, styles.Body.fontWeight)
        assertEquals(FontWeight.Normal, styles.Caption.fontWeight)
    }

    @Test
    fun resolveFontFamily_mapsStoredId() {
        assertEquals(FontFamily.Monospace, resolveFontFamily(TboxFontFamily.Monospace.id))
    }

    @Test
    fun bundledNunito_differsFromDefault() {
        val nunito = TboxFontFamily.Nunito.toComposeFontFamily()
        assertNotEquals(FontFamily.Default, nunito)
        assertEquals(nunito, resolveFontFamily(TboxFontFamily.Nunito.id))
    }

    @Test
    fun bundledRoboto_differsFromDefault() {
        val roboto = TboxFontFamily.Roboto.toComposeFontFamily()
        assertNotEquals(FontFamily.Default, roboto)
        assertEquals(roboto, resolveFontFamily(TboxFontFamily.Roboto.id))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BundledFontWeightResourcesTest {

    @Test
    fun bundledWeightFontResources_exist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fontIds = listOf(
            R.font.nunito_medium,
            R.font.nunito_semibold,
            R.font.nunito_bold,
            R.font.cabin_medium,
            R.font.cabin_semibold,
            R.font.cabin_bold,
            R.font.roboto_medium,
            R.font.roboto_semibold,
            R.font.roboto_bold,
        )
        fontIds.forEach { fontId ->
            context.resources.openRawResource(fontId).use { input ->
                assertTrue(input.available() > 0)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainScreenWallpaperSelectionsTest {

    @Test
    fun withFileName_storesPerPageAndTheme() {
        val selections = MainScreenWallpaperSelectionsByPage.empty()
            .withFileName(page = 1, forLightTheme = true, fileName = "a.jpg")
            .withFileName(page = 2, forLightTheme = true, fileName = "b.jpg")
            .withFileName(page = 1, forLightTheme = false, fileName = "night.jpg")

        assertEquals("a.jpg", selections.fileNameFor(1, forLightTheme = true))
        assertEquals("b.jpg", selections.fileNameFor(2, forLightTheme = true))
        assertEquals("night.jpg", selections.fileNameFor(1, forLightTheme = false))
        assertNull(selections.fileNameFor(2, forLightTheme = false))
    }

    @Test
    fun json_roundTrip() {
        val original = MainScreenWallpaperSelectionsByPage.empty()
            .withFileName(1, forLightTheme = true, fileName = "light.jpg")
            .withFileName(2, forLightTheme = false, fileName = "dark.png")

        val restored = MainScreenWallpaperSelectionsByPage.fromJson(original.toJson())
        assertEquals(original, restored)
    }

    @Test
    fun fromDataStoreJson_handlesBlank() {
        assertEquals(MainScreenWallpaperSelectionsByPage.empty(), MainScreenWallpaperSelectionsByPage.fromDataStoreJson(""))
        assertEquals(MainScreenWallpaperSelectionsByPage.empty(), MainScreenWallpaperSelectionsByPage.fromDataStoreJson(null))
    }

    @Test
    fun clearedForTheme_removesOnlyMatchingSide() {
        val selections = MainScreenWallpaperSelectionsByPage(
            lightByPage = mapOf(1 to "a.jpg"),
            darkByPage = mapOf(1 to "b.jpg"),
        )
        val clearedLight = selections.clearedForTheme(forLightTheme = true)
        assertTrue(clearedLight.lightByPage.isEmpty())
        assertEquals("b.jpg", clearedLight.fileNameFor(1, forLightTheme = false))
    }

    @Test
    fun driveModeThemes_samePage_differentWallpapers_remainIndependent() {
        val eco = MainScreenWallpaperSelectionsByPage.empty()
            .withFileName(page = 1, forLightTheme = true, fileName = "eco.jpg")
        val nor = MainScreenWallpaperSelectionsByPage.empty()
            .withFileName(page = 1, forLightTheme = true, fileName = "nor.jpg")
        val spt = MainScreenWallpaperSelectionsByPage.empty()
            .withFileName(page = 2, forLightTheme = true, fileName = "spt.jpg")

        assertEquals("eco.jpg", eco.fileNameFor(1, forLightTheme = true))
        assertEquals("nor.jpg", nor.fileNameFor(1, forLightTheme = true))
        assertEquals("spt.jpg", spt.fileNameFor(2, forLightTheme = true))
        assertNotEquals(eco.fileNameFor(1, true), nor.fileNameFor(1, true))
    }

    @Test
    fun huColorTheme_lightAndDarkSelections_areIndependentOnSamePage() {
        val selections = MainScreenWallpaperSelectionsByPage.empty()
            .withFileName(page = 1, forLightTheme = true, fileName = "day.jpg")
            .withFileName(page = 1, forLightTheme = false, fileName = "night.jpg")

        assertEquals("day.jpg", selections.fileNameFor(1, forLightTheme = true))
        assertEquals("night.jpg", selections.fileNameFor(1, forLightTheme = false))
    }
}
