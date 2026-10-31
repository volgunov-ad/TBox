package vad.dashing.tbox.mbcan

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MbCanHardKeyUiLabelsTest {
    private val resources
        get() = ApplicationProvider.getApplicationContext<Application>().resources

    @Test
    fun labelResId_mapsKnownCodesIncluding216() {
        assertEquals(R.string.hard_key_label_wheel_left_joy_up, MbCanHardKeyUiLabels.labelResId(29))
        assertEquals(R.string.hard_key_label_wheel_left_joy_down, MbCanHardKeyUiLabels.labelResId(30))
        assertEquals(R.string.hard_key_label_wheel_left_btn_top, MbCanHardKeyUiLabels.labelResId(216))
        assertEquals(R.string.hard_key_label_wheel_left_btn_bottom, MbCanHardKeyUiLabels.labelResId(316))
        assertEquals(R.string.hard_key_label_wheel_right_btn_top, MbCanHardKeyUiLabels.labelResId(158))
        assertEquals(R.string.hard_key_label_wheel_right_btn_bottom, MbCanHardKeyUiLabels.labelResId(582))
        assertEquals(R.string.hard_key_label_door_front_passenger, MbCanHardKeyUiLabels.labelResId(210))
        assertEquals(R.string.hard_key_label_up, MbCanHardKeyUiLabels.labelResId(103))
        assertEquals(R.string.hard_key_label_power, MbCanHardKeyUiLabels.labelResId(116))
        assertEquals(R.string.hard_key_label_home, MbCanHardKeyUiLabels.labelResId(587))
        assertNull(MbCanHardKeyUiLabels.labelResId(999))
    }

    @Test
    fun formatLabel_usesLocalizedNameAndCode() {
        val label216 = MbCanHardKeyUiLabels.formatLabel(resources, 216)
        assertTrue(label216.contains(resources.getString(R.string.hard_key_label_wheel_left_btn_top)))
        assertTrue(label216.contains("216"))

        val label158 = MbCanHardKeyUiLabels.formatLabel(resources, 158)
        assertTrue(label158.contains(resources.getString(R.string.hard_key_label_wheel_right_btn_top)))
        assertTrue(label158.contains("158"))
    }

    @Test
    fun formatLabel_unknownFallsBackToCodeN() {
        assertEquals(
            resources.getString(R.string.hard_key_code_unknown, 999),
            MbCanHardKeyUiLabels.formatLabel(resources, 999),
        )
    }

    @Test
    fun technicalNames_unchangedForLogsAndCodecs() {
        assertEquals("WHEEL_LEFT_BTN_TOP", KeyPressDiagnosticFormat.mbCanKeyName(216))
        assertEquals("WHEEL_LEFT_BTN_BOTTOM", KeyPressDiagnosticFormat.mbCanKeyName(316))
        assertEquals("UNKNOWN", KeyPressDiagnosticFormat.mbCanKeyName(999))
    }
}
