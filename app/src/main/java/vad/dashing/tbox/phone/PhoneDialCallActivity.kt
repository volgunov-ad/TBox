package vad.dashing.tbox.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.PlaceCallResult
import vad.dashing.tbox.R
import vad.dashing.tbox.hasAnswerPhoneCallsPermission
import vad.dashing.tbox.hasCallPhonePermission
import vad.dashing.tbox.isHfpClientConnected
import vad.dashing.tbox.normalizeDialPhoneNumber
import vad.dashing.tbox.phoneDialFailureMessage
import vad.dashing.tbox.placeOutgoingCall
import vad.dashing.tbox.ui.AppAlertDialogButtonLabel
import vad.dashing.tbox.ui.AppAlertDialogText
import vad.dashing.tbox.ui.AppAlertDialogTitle
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * Runtime phone permission request and the optional «Позвонить …?» dialog for the dial tile.
 * Floating panels and the window-mode main screen compose in `ComposeView(service)`, which has
 * neither an ActivityResultRegistry nor a window token for a Dialog.
 *
 * Confirm dialog: temporarily [BackgroundService.ACTION_SUSPEND_OVERLAYS] so TYPE_APPLICATION_OVERLAY
 * panels do not cover the Activity dialog (S11). Residual: other apps' overlays are untouched.
 */
class PhoneDialCallActivity : ComponentActivity() {
    private var number = ""
    private var label = ""
    private var confirm = false
    private var permissionOnly = false
    private var confirmVisible by mutableStateOf(false)
    private var overlaysSuspendedForConfirm = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (permissionOnly) {
            finishPermissionOnly()
            return@registerForActivityResult
        }
        if (hasCallPhonePermission(this)) {
            continueWithPermission()
        } else {
            finishWithToast(getString(R.string.widget_phone_dial_no_permission))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionOnly = intent.getBooleanExtra(EXTRA_PERMISSION_ONLY, false)
        if (permissionOnly) {
            if (hasPhonePermissions()) {
                finishWithToast(getString(R.string.permissions_call_phone_granted_toast))
                return
            }
            permissionLauncher.launch(PHONE_CALL_PERMISSIONS)
            return
        }
        number = normalizeDialPhoneNumber(intent.getStringExtra(EXTRA_NUMBER).orEmpty())
        label = intent.getStringExtra(EXTRA_LABEL).orEmpty().trim().ifBlank { number }
        confirm = intent.getBooleanExtra(EXTRA_CONFIRM, false)
        if (number.isEmpty()) {
            finishWithToast(getString(R.string.widget_phone_dial_empty_number))
            return
        }
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        setContent {
            if (!confirmVisible) return@setContent
            TboxAppTheme(theme = if (night) 2 else 1) {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = {
                        AppAlertDialogTitle(stringResource(R.string.widget_phone_dial_confirm_title))
                    },
                    text = {
                        AppAlertDialogText(
                            stringResource(R.string.widget_phone_dial_confirm_message, label),
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                setConfirmDialogVisible(false)
                                placeCallAndFinish()
                            },
                        ) {
                            AppAlertDialogButtonLabel(
                                stringResource(R.string.widget_phone_dial_confirm_call),
                            )
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { finish() }) {
                            AppAlertDialogButtonLabel(stringResource(android.R.string.cancel))
                        }
                    },
                )
            }
        }
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(STATE_CONFIRM_VISIBLE, false)) {
                setConfirmDialogVisible(true)
            }
            return
        }
        if (hasCallPhonePermission(this)) {
            continueWithPermission()
        } else {
            permissionLauncher.launch(PHONE_CALL_PERMISSIONS)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_CONFIRM_VISIBLE, confirmVisible)
    }

    override fun onDestroy() {
        resumeFloatingOverlaysIfNeeded()
        super.onDestroy()
    }

    private fun continueWithPermission() {
        if (!isHfpClientConnected()) {
            finishWithToast(getString(R.string.widget_phone_dial_no_hfp))
            return
        }
        if (confirm) {
            setConfirmDialogVisible(true)
        } else {
            placeCallAndFinish()
        }
    }

    private fun setConfirmDialogVisible(visible: Boolean) {
        if (visible && !confirmVisible) {
            suspendFloatingOverlays()
        }
        if (!visible && confirmVisible) {
            resumeFloatingOverlaysIfNeeded()
        }
        confirmVisible = visible
    }

    private fun suspendFloatingOverlays() {
        if (overlaysSuspendedForConfirm) return
        runCatching {
            startService(
                Intent(this, BackgroundService::class.java).apply {
                    action = BackgroundService.ACTION_SUSPEND_OVERLAYS
                },
            )
            overlaysSuspendedForConfirm = true
        }
    }

    private fun resumeFloatingOverlaysIfNeeded() {
        if (!overlaysSuspendedForConfirm) return
        overlaysSuspendedForConfirm = false
        runCatching {
            startService(
                Intent(this, BackgroundService::class.java).apply {
                    action = BackgroundService.ACTION_RESUME_OVERLAYS
                },
            )
        }
    }

    private fun placeCallAndFinish() {
        when (val result = placeOutgoingCall(this, number)) {
            PlaceCallResult.Success -> finish()
            is PlaceCallResult.Failure -> finishWithToast(phoneDialFailureMessage(resources, result))
        }
    }

    private fun finishPermissionOnly() {
        if (hasPhonePermissions()) {
            finishWithToast(getString(R.string.permissions_call_phone_granted_toast))
        } else {
            finishWithToast(getString(R.string.widget_phone_dial_no_permission))
        }
    }

    private fun hasPhonePermissions(): Boolean =
        hasCallPhonePermission(this) && hasAnswerPhoneCallsPermission(this)

    private fun finishWithToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        private const val EXTRA_NUMBER = "vad.dashing.tbox.phone.extra.NUMBER"
        private const val EXTRA_LABEL = "vad.dashing.tbox.phone.extra.LABEL"
        private const val EXTRA_CONFIRM = "vad.dashing.tbox.phone.extra.CONFIRM"
        private const val EXTRA_PERMISSION_ONLY = "vad.dashing.tbox.phone.extra.PERMISSION_ONLY"
        private const val STATE_CONFIRM_VISIBLE = "confirm_visible"

        /** One PHONE-group dialog on API 28; ANSWER_PHONE_CALLS backs Telecom answer/hangup. */
        private val PHONE_CALL_PERMISSIONS = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
        )

        fun start(context: Context, number: String, label: String, confirm: Boolean): Boolean =
            runCatching {
                context.startActivity(
                    Intent(context, PhoneDialCallActivity::class.java)
                        .putExtra(EXTRA_NUMBER, number)
                        .putExtra(EXTRA_LABEL, label)
                        .putExtra(EXTRA_CONFIRM, confirm)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)

        /**
         * Opens a translucent activity that only requests CALL_PHONE + ANSWER_PHONE_CALLS.
         * For Settings / automation editor / first failure of place_call without a dial tile.
         */
        fun startPermissionRequest(context: Context): Boolean =
            runCatching {
                context.startActivity(
                    Intent(context, PhoneDialCallActivity::class.java)
                        .putExtra(EXTRA_PERMISSION_ONLY, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
    }
}
