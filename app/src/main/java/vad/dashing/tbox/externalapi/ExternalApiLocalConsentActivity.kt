package vad.dashing.tbox.externalapi

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.AppAlertDialogButtonLabel
import vad.dashing.tbox.ui.AppAlertDialogText
import vad.dashing.tbox.ui.AppAlertDialogTitle
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * Started by [ExternalApiController.localAccess] over whatever app is on screen.
 *
 * Non-cancelable: Back / outside tap do not clear pending consent. Only Allow / Deny
 * (here or in Settings → API) resolve it; [LaunchedEffect] finishes when pending is cleared.
 */
class ExternalApiLocalConsentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Non-cancelable consent: ignore Back; pending stays until Allow / Deny.
                }
            },
        )
        val controller = ExternalApiControllerHolder.get()
        if (controller == null) {
            finish()
            return
        }
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        setContent {
            val consent by controller.localConsent.collectAsStateWithLifecycle()
            LaunchedEffect(consent) {
                if (consent == null) finish()
            }
            val request = consent ?: return@setContent
            TboxAppTheme(theme = if (night) 2 else 1) {
                AlertDialog(
                    onDismissRequest = {
                        // Non-cancelable: Material still invokes this for some paths; do not finish.
                    },
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                    ),
                    title = { AppAlertDialogTitle(stringResource(R.string.settings_api_pairing_dialog_title)) },
                    text = {
                        AppAlertDialogText(
                            stringResource(
                                R.string.settings_api_local_dialog_message,
                                request.clientName,
                                request.packageName,
                            ),
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                controller.approveLocalConsent()
                                finish()
                            },
                        ) {
                            AppAlertDialogButtonLabel(stringResource(R.string.settings_api_pairing_approve))
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = {
                                controller.denyLocalConsent()
                                finish()
                            },
                        ) {
                            AppAlertDialogButtonLabel(stringResource(R.string.settings_api_pairing_deny))
                        }
                    },
                )
            }
        }
    }
}
