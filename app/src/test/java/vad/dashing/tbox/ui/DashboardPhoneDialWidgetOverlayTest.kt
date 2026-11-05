package vad.dashing.tbox.ui

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import vad.dashing.tbox.BLUETOOTH_PROFILE_HEADSET_CLIENT
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.PHONE_DIAL_WIDGET_DATA_KEY
import vad.dashing.tbox.R
import vad.dashing.tbox.isHfpClientConnected
import vad.dashing.tbox.phone.PhoneCallSnapshot
import vad.dashing.tbox.phone.PhoneCallState
import vad.dashing.tbox.phone.PhoneCallStateRepository
import vad.dashing.tbox.phone.PhoneDialCallActivity

/**
 * Floating panels and the window-mode main screen compose inside `ComposeView(service)`:
 * no Activity in the context chain, so no ActivityResultRegistryOwner and no dialog token.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DashboardPhoneDialWidgetOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun resetPhoneState() {
        PhoneCallStateRepository.replaceSnapshotForTest(PhoneCallSnapshot())
        PhoneCallStateRepository.setHeadsetClientBroadcastConnectedForTest(null)
        ShadowToast.reset()
    }

    @After
    fun clearPhoneState() {
        PhoneCallStateRepository.replaceSnapshotForTest(PhoneCallSnapshot())
        PhoneCallStateRepository.setHeadsetClientBroadcastConnectedForTest(null)
    }

    @Test
    fun composesWithoutActivityInContext() {
        setTile(confirmBeforeCall = true)
        composeRule.onNodeWithText(NUMBER).assertExists()
    }

    @Test
    fun tapWithoutPermission_opensCallActivity() {
        setTile(confirmBeforeCall = false)
        drainStartedActivities()

        composeRule.onNodeWithText(NUMBER).performClick()

        val started = drainStartedActivities().single()
        assertEquals(PhoneDialCallActivity::class.java.name, started.component?.className)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun tapWithConfirm_opensCallActivityInsteadOfDialing() {
        grantCallPhone()
        connectHfp()
        registerCallHandler()
        setTile(confirmBeforeCall = true)
        drainStartedActivities()

        composeRule.onNodeWithText(NUMBER).performClick()

        val started = drainStartedActivities().single()
        assertEquals(PhoneDialCallActivity::class.java.name, started.component?.className)
    }

    @Test
    fun tapWithoutConfirm_dialsDirectly() {
        grantCallPhone()
        connectHfp()
        registerCallHandler()
        setTile(confirmBeforeCall = false)
        drainStartedActivities()

        composeRule.onNodeWithText(NUMBER).performClick()

        val started = drainStartedActivities().single()
        assertEquals(Intent.ACTION_CALL, started.action)
        assertEquals(callUri(), started.data)
    }

    @Test
    fun tapWithoutHfp_showsErrorAndDoesNotDial() {
        grantCallPhone()
        setTile(confirmBeforeCall = false)
        drainStartedActivities()

        composeRule.onNodeWithText(NUMBER).performClick()

        assertTrue(drainStartedActivities().isEmpty())
        assertEquals(app.getString(R.string.widget_phone_dial_no_hfp), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun hfpGate_acceptsHeadsetClientBroadcastWhenAdapterProfileStale() {
        // Simulates OEM: stock dialer / Telecom HFP Connected, getProfileConnectionState(16) false.
        PhoneCallStateRepository.setHeadsetClientBroadcastConnectedForTest(true)
        assertTrue(isHfpClientConnected())
    }

    @Test
    fun tapWhileActive_hangsUpInsteadOfDialing() {
        grantCallPhone()
        connectHfp()
        registerCallHandler()
        PhoneCallStateRepository.replaceSnapshotForTest(
            PhoneCallSnapshot(
                hfpConnected = true,
                callState = PhoneCallState.ACTIVE,
                hangupSupported = false, // force Failure toast; still must not dial
            ),
        )
        setTile(confirmBeforeCall = false)
        drainStartedActivities()

        composeRule.onNodeWithText(NUMBER).performClick()

        assertTrue(drainStartedActivities().isEmpty())
        assertNotEquals(Intent.ACTION_CALL, ShadowToast.getTextOfLatestToast())
        // Hangup capability missing → Russian failure from repository, not NO_HFP / dial.
        assertEquals(
            "Сброс звонка недоступен на этой прошивке / без привилегий",
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun tapWhileIncoming_hangsUpInsteadOfDialing() {
        grantCallPhone()
        connectHfp()
        registerCallHandler()
        PhoneCallStateRepository.replaceSnapshotForTest(
            PhoneCallSnapshot(
                hfpConnected = true,
                callState = PhoneCallState.INCOMING,
                hangupSupported = false,
            ),
        )
        setTile(confirmBeforeCall = false)
        drainStartedActivities()

        composeRule.onNodeWithText(NUMBER).performClick()

        assertTrue(drainStartedActivities().isEmpty())
        assertEquals(
            "Сброс звонка недоступен на этой прошивке / без привилегий",
            ShadowToast.getTextOfLatestToast(),
        )
    }

    private fun setTile(confirmBeforeCall: Boolean) {
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides app) {
                DashboardPhoneDialWidgetItem(
                    widget = DashboardWidget(id = 0, title = "", dataKey = PHONE_DIAL_WIDGET_DATA_KEY),
                    phoneNumber = NUMBER,
                    showNumber = true,
                    confirmBeforeCall = confirmBeforeCall,
                    showTitle = true,
                    defaultTitle = "Набрать номер",
                    isEditMode = false,
                    onEditClick = {},
                    onLongClick = {},
                    elevation = 0.dp,
                    shape = 0.dp,
                    textColor = Color.White,
                    backgroundColor = Color.Black,
                )
            }
        }
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

    private companion object {
        const val NUMBER = "+79200287693"
    }
}
