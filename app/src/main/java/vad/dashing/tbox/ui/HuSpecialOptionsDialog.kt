package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import vad.dashing.tbox.R

@Composable
fun HuSpecialOptionsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    huCaCertsAutoInstall: Boolean,
    onHuCaCertsAutoInstallChange: (Boolean) -> Unit,
    huAppNetworkAutoOpen: Boolean,
    onHuAppNetworkAutoOpenChange: (Boolean) -> Unit,
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.tboxDialogSurfaceFill(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                AppAlertDialogTitle(stringResource(R.string.hu_special_options_title))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HuCaCertsSettingsCard(
                        autoInstall = huCaCertsAutoInstall,
                        onAutoInstallChange = onHuCaCertsAutoInstallChange,
                    )
                    HuAppNetworkSettingsCard(
                        autoOpen = huAppNetworkAutoOpen,
                        onAutoOpenChange = onHuAppNetworkAutoOpenChange,
                    )
                    HuTimeZoneSettingsCard()
                }
                OutlinedButton(
                    onClick = rememberWrappedOnClick(onDismiss),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_close))
                }
            }
        }
    }
}
