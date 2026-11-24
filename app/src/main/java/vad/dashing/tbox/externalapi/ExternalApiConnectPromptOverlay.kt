package vad.dashing.tbox.externalapi

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.AppAlertDialogButtonLabel
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.theme.TboxAppTheme
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxTitle

/**
 * SYSTEM_ALERT_WINDOW Allow/Deny prompt for External API connect / pairing.
 * Same non-cancelable lifecycle as [ExternalApiLocalConsentActivity]: only Allow / Deny
 * (or Settings → API) clear pending; outside tap does nothing.
 */
internal object ExternalApiConnectPromptOverlay {
    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var shownKey: String? = null

    /** True when the overlay was shown or already matches [prompt]. False if permission missing. */
    fun show(
        context: Context,
        prompt: ExternalApiConnectPrompt,
        onAllow: () -> Unit,
        onDeny: () -> Unit,
    ): Boolean {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) {
            hide()
            return false
        }
        val key = promptKey(prompt)
        if (composeView != null && shownKey == key) {
            return true
        }
        hide()
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val owner = MyLifecycleOwner().also {
            it.setCurrentState(Lifecycle.State.CREATED)
            it.setCurrentState(Lifecycle.State.STARTED)
            it.setCurrentState(Lifecycle.State.RESUMED)
        }
        val view = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                val night = (
                    appContext.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                    ) == Configuration.UI_MODE_NIGHT_YES
                TboxAppTheme(theme = if (night) 2 else 1) {
                    ExternalApiConnectPromptCard(
                        prompt = prompt,
                        onAllow = onAllow,
                        onDeny = onDeny,
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            title = "TBox External API connect"
        }
        wm.addView(view, params)
        windowManager = wm
        composeView = view
        lifecycleOwner = owner
        shownKey = key
        return true
    }

    fun hide() {
        val view = composeView
        if (view != null) {
            runCatching {
                if (view.isAttachedToWindow) windowManager?.removeView(view)
            }
            runCatching { view.disposeComposition() }
        }
        lifecycleOwner?.setCurrentState(Lifecycle.State.DESTROYED)
        lifecycleOwner?.clear()
        composeView = null
        windowManager = null
        lifecycleOwner = null
        shownKey = null
    }

    private fun promptKey(prompt: ExternalApiConnectPrompt): String = when (prompt) {
        is ExternalApiConnectPrompt.Local ->
            "local:${prompt.consent.packageName}:${prompt.consent.certSha256}"
        is ExternalApiConnectPrompt.HttpPair ->
            "pair:${prompt.request.requestId}"
    }
}

@Composable
internal fun ExternalApiConnectPromptCard(
    prompt: ExternalApiConnectPrompt,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    val message = when (prompt) {
        is ExternalApiConnectPrompt.Local -> stringResource(
            R.string.settings_api_local_dialog_message,
            prompt.consent.clientName,
            prompt.consent.packageName,
        )
        is ExternalApiConnectPrompt.HttpPair -> stringResource(
            R.string.settings_api_pairing_dialog_message,
            prompt.request.clientName,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(32.dp)
                .widthIn(min = 360.dp, max = 720.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_api_pairing_dialog_title),
                    style = MaterialTheme.typography.tboxTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onDeny) {
                        AppAlertDialogButtonLabel(stringResource(R.string.settings_api_pairing_deny))
                    }
                    Button(onClick = onAllow) {
                        AppAlertDialogButtonLabel(stringResource(R.string.settings_api_pairing_approve))
                    }
                }
            }
        }
    }
}
