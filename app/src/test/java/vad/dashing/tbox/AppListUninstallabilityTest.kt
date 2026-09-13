package vad.dashing.tbox

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
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
}
