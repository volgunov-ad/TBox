package vad.dashing.tbox.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TboxTextSizeScalesTest {

    @Test
    fun normalize_clampsAndSnapsToStep() {
        assertEquals(0.70f, TboxTextSizeScales.normalize(0.5f), 0.001f)
        assertEquals(1.40f, TboxTextSizeScales.normalize(2f), 0.001f)
        assertEquals(1.05f, TboxTextSizeScales.normalize(1.04f), 0.001f)
        assertEquals(1.00f, TboxTextSizeScales.normalize(1.02f), 0.001f)
    }

    @Test
    fun json_roundTrip() {
        val original = TboxTextSizeScales(
            caption = 0.85f,
            body = 1.10f,
            button = 1.15f,
            title = 0.95f,
            headline = 1.20f,
            tabLabel = 1.25f,
            widgetTitle = 0.90f,
            widgetValue = 1.30f,
            widgetUnit = 1.05f,
        )
        val restored = TboxTextSizeScales.fromJson(original.toJsonString())
        assertEquals(original, restored)
    }

    @Test
    fun fromJson_blankOrInvalid_returnsDefault() {
        assertEquals(TboxTextSizeScales.Default, TboxTextSizeScales.fromJson(null))
        assertEquals(TboxTextSizeScales.Default, TboxTextSizeScales.fromJson(""))
        assertEquals(TboxTextSizeScales.Default, TboxTextSizeScales.fromJson("{broken"))
    }

    @Test
    fun withRole_updatesOnlySelectedScale() {
        val updated = TboxTextSizeScales.Default.withRole(TextSizeRole.WidgetValue, 1.2f)
        assertEquals(1.2f, updated.widgetValue, 0.001f)
        assertEquals(1f, updated.widgetTitle, 0.001f)
        assertNotEquals(TboxTextSizeScales.Default, updated)
    }

    @Test
    fun widgetTypography_appliesGlobalRoleScale() {
        val base = tboxTextStyles().WidgetValue
        val plain = TboxWidgetTypography.fontSizeSpForHeight(90f, TboxWidgetTextRole.VALUE)
        val scaledStyle = TboxWidgetTypography.textStyleForHeight(
            containerHeightDp = 90f,
            role = TboxWidgetTextRole.VALUE,
            baseStyle = base,
            textScale = 1.1f,
            globalRoleScale = 1.2f,
        )
        assertEquals(plain * 1.1f * 1.2f, scaledStyle.fontSize.value, 0.01f)
    }
}
