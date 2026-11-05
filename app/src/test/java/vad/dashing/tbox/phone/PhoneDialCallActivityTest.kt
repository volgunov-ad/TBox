package vad.dashing.tbox.phone

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import vad.dashing.tbox.BLUETOOTH_PROFILE_HEADSET_CLIENT
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhoneDialCallActivityTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun invalidNumber_finishesWithToast() {
        val activity = Robolectric.buildActivity(
            PhoneDialCallActivity::class.java,
            dialIntent(number = "8 800 abc", confirm = false),
        ).setup().get()

        assertTrue(activity.isFinishing)
        assertEquals(app.getString(R.string.widget_phone_dial_empty_number), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun noPermission_requestsPhonePermissionsTogether() {
        val activity = Robolectric.buildActivity(
            PhoneDialCallActivity::class.java,
            dialIntent(confirm = false),
        ).setup().get()

        val requested = shadowOf(activity).lastRequestedPermission.requestedPermissions.toSet()
        assertEquals(setOf(Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS), requested)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun permissionDenied_finishesWithoutCall() {
        val controller = Robolectric.buildActivity(PhoneDialCallActivity::class.java, dialIntent(confirm = false))
        val activity = controller.setup().get()
        val request = shadowOf(activity).lastRequestedPermission
        drainStartedActivities()

        @Suppress("DEPRECATION")
        activity.onRequestPermissionsResult(
            request.requestCode,
            request.requestedPermissions,
            IntArray(request.requestedPermissions.size) { PackageManager.PERMISSION_DENIED },
        )

        assertTrue(activity.isFinishing)
        assertTrue(drainStartedActivities().isEmpty())
        assertEquals(app.getString(R.string.widget_phone_dial_no_permission), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun noHfp_finishesWithoutCall() {
        grantCallPhone()
        drainStartedActivities()

        val activity = Robolectric.buildActivity(
            PhoneDialCallActivity::class.java,
            dialIntent(confirm = true),
        ).setup().get()

        assertTrue(activity.isFinishing)
        assertTrue(drainStartedActivities().none { it.action == Intent.ACTION_CALL })
        assertEquals(app.getString(R.string.widget_phone_dial_no_hfp), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun withoutConfirm_placesCallAndFinishes() {
        grantCallPhone()
        connectHfp()
        registerCallHandler()
        drainStartedActivities()

        val activity = Robolectric.buildActivity(
            PhoneDialCallActivity::class.java,
            dialIntent(confirm = false),
        ).setup().get()

        assertTrue(activity.isFinishing)
        assertEquals(listOf(callUri()), startedCalls())
    }

    @Test
    fun withConfirm_placesCallOnlyAfterConfirmButton() {
        grantCallPhone()
        connectHfp()
        registerCallHandler()
        val intent = dialIntent(confirm = true)
        drainStartedActivities()

        ActivityScenario.launch<PhoneDialCallActivity>(intent).use {
            composeRule.waitForIdle()
            assertTrue(startedCalls().isEmpty())

            composeRule.onNodeWithText(app.getString(R.string.widget_phone_dial_confirm_call)).performClick()
            composeRule.waitForIdle()

            assertEquals(listOf(callUri()), startedCalls())
        }
    }

    @Test
    fun withConfirm_cancelDoesNotCall() {
        grantCallPhone()
        connectHfp()
        registerCallHandler()
        val intent = dialIntent(confirm = true)
        drainStartedActivities()

        ActivityScenario.launch<PhoneDialCallActivity>(intent).use {
            composeRule.onNodeWithText(app.getString(android.R.string.cancel)).performClick()
            composeRule.waitForIdle()

            assertTrue(startedCalls().isEmpty())
        }
    }

    @Test
    fun withConfirm_suspendsFloatingOverlaysWhileDialogVisible() {
        grantCallPhone()
        connectHfp()
        registerCallHandler()
        val intent = dialIntent(confirm = true)
        drainStartedActivities()
        drainStartedServices()

        ActivityScenario.launch<PhoneDialCallActivity>(intent).use {
            composeRule.waitForIdle()
            val services = drainStartedServices()
            assertTrue(
                services.any {
                    it.component?.className?.endsWith("BackgroundService") == true &&
                        it.action == BackgroundService.ACTION_SUSPEND_OVERLAYS
                },
            )
            assertTrue(startedCalls().isEmpty())
        }
    }

    @Test
    fun permissionOnly_requestsPhonePermissions() {
        assertTrue(PhoneDialCallActivity.startPermissionRequest(app))
        val intent = shadowOf(app).nextStartedActivity
        val activity = Robolectric.buildActivity(PhoneDialCallActivity::class.java, intent).setup().get()

        val requested = shadowOf(activity).lastRequestedPermission.requestedPermissions.toSet()
        assertEquals(setOf(Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS), requested)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun permissionOnly_alreadyGranted_finishesWithToast() {
        grantCallPhone()
        shadowOf(app).grantPermissions(Manifest.permission.ANSWER_PHONE_CALLS)
        assertTrue(PhoneDialCallActivity.startPermissionRequest(app))
        val intent = shadowOf(app).nextStartedActivity
        val activity = Robolectric.buildActivity(PhoneDialCallActivity::class.java, intent).setup().get()

        assertTrue(activity.isFinishing)
        assertEquals(app.getString(R.string.permissions_call_phone_granted_toast), ShadowToast.getTextOfLatestToast())
    }

    private fun dialIntent(number: String = NUMBER, confirm: Boolean): Intent {
        assertTrue(PhoneDialCallActivity.start(app, number, number, confirm))
        return shadowOf(app).nextStartedActivity
    }

    private fun grantCallPhone() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
    }

    private fun connectHfp() {
        val adapter = shadowOf(BluetoothAdapter.getDefaultAdapter())
        adapter.setEnabled(true)
        adapter.setProfileConnectionState(BLUETOOTH_PROFILE_HEADSET_CLIENT, BluetoothProfile.STATE_CONNECTED)
    }

    private fun registerCallHandler() {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.server.telecom"
                name = "com.android.server.telecom.components.UserCallActivity"
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_CALL, callUri()), info)
    }

    private fun callUri(): Uri = Uri.fromParts("tel", NUMBER, null)

    private fun drainStartedActivities(): List<Intent> =
        generateSequence { shadowOf(app).nextStartedActivity }.toList()

    private fun drainStartedServices(): List<Intent> =
        generateSequence { shadowOf(app).nextStartedService }.toList()

    private fun startedCalls(): List<Uri?> =
        drainStartedActivities().filter { it.action == Intent.ACTION_CALL }.map { it.data }

    private companion object {
        const val NUMBER = "+79200287693"
    }
}
