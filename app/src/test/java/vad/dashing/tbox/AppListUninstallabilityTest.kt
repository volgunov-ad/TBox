package vad.dashing.tbox

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vad.dashing.tbox.ui.buildSystemUninstallIntent
import vad.dashing.tbox.ui.canRequestUninstall

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppListUninstallabilityTest {

    @Test
    fun userApp_canRequestUninstall() {
        val context = RuntimeEnvironment.getApplication()
        val pm = context.packageManager
        val shadow = Shadows.shadowOf(pm)
        shadow.addPackage(
            android.content.pm.PackageInfo().apply {
                packageName = "com.example.userapp"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.example.userapp"
                    flags = 0
                }
            },
        )
        assertTrue(canRequestUninstall(pm, "com.example.userapp"))
    }

    @Test
    fun pureSystemApp_cannotRequestUninstall() {
        val context = RuntimeEnvironment.getApplication()
        val pm = context.packageManager
        val shadow = Shadows.shadowOf(pm)
        shadow.addPackage(
            android.content.pm.PackageInfo().apply {
                packageName = "com.android.systemui"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.android.systemui"
                    flags = ApplicationInfo.FLAG_SYSTEM
                }
            },
        )
        assertFalse(canRequestUninstall(pm, "com.android.systemui"))
    }

    @Test
    fun updatedSystemApp_canRequestUninstall() {
        val context = RuntimeEnvironment.getApplication()
        val pm = context.packageManager
        val shadow = Shadows.shadowOf(pm)
        shadow.addPackage(
            android.content.pm.PackageInfo().apply {
                packageName = "com.android.chrome"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.android.chrome"
                    flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                }
            },
        )
        assertTrue(canRequestUninstall(pm, "com.android.chrome"))
    }

    @Test
    fun buildSystemUninstallIntent_usesActionDeleteAndPackageUri() {
        val intent = buildSystemUninstallIntent("ru.vk.store")
        assertEquals(Intent.ACTION_DELETE, intent.action)
        assertEquals(Uri.parse("package:ru.vk.store"), intent.data)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
