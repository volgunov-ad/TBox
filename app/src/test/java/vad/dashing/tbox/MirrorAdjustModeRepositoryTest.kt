package vad.dashing.tbox

import android.content.ContentResolver
import android.provider.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MirrorAdjustModeRepositoryTest {
    @Test
    fun toggleMirrorAdjustMode_writesGlobalKeyOnApi28() {
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver
        Settings.Global.putInt(resolver, "ro.mb.mirror.adjust.mode", 0)

        assertTrue(MirrorAdjustModeRepository.toggleMirrorAdjustMode(context))
        assertTrue(MirrorAdjustModeRepository.readMirrorAdjustModeEnabled(context))

        assertTrue(MirrorAdjustModeRepository.toggleMirrorAdjustMode(context))
        assertFalse(MirrorAdjustModeRepository.readMirrorAdjustModeEnabled(context))
    }
}
