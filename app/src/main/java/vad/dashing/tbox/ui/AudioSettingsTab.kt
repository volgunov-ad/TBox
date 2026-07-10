package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mengbo.mbCan.defines.MBAudioProperty
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanAudioPropertyHelp
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxTitle

private const val AUDIO_SETTINGS_POLL_INTERVAL_MS = 2_000L

private data class MbCanAudioPropertyUiEntry(
    val propertyId: Int,
    val propertyName: String,
)

private val mbCanAudioPropertyUiEntries: List<MbCanAudioPropertyUiEntry> =
    MBAudioProperty.values()
        .filter { it != MBAudioProperty.eAUDIO_PROPERTY_COUNT }
        .map { property ->
            MbCanAudioPropertyUiEntry(
                propertyId = property.value,
                propertyName = property.name,
            )
        }

@Composable
fun AudioSettingsTab(
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val availability by UniversalCanRepository.availability.collectAsStateWithLifecycle()
    val headUnitCanMode by UniversalCanRepository.mode.collectAsStateWithLifecycle()

    val isAndroid9MbCan = headUnitCanMode == HeadUnitCanMode.Android9MbCan
    val mbCanOk = availability is MbCanAvailability.Available
    val controlsEnabled = isAndroid9MbCan && mbCanOk

    val currentValues = remember { mutableStateMapOf<Int, Int?>() }
    val draftValues = remember { mutableStateMapOf<Int, String>() }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isAndroid9MbCan) {
        if (isAndroid9MbCan) {
            UniversalCanRepository.warmUpAvailabilityForUi()
        }
    }

    LaunchedEffect(controlsEnabled) {
        if (!controlsEnabled) return@LaunchedEffect
        while (isActive) {
            for (entry in mbCanAudioPropertyUiEntries) {
                currentValues[entry.propertyId] = UniversalCanRepository.getAudioParam(entry.propertyId)
            }
            delay(AUDIO_SETTINGS_POLL_INTERVAL_MS)
        }
    }

    val noDataLabel = stringResource(R.string.value_no_data)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        Text(
            text = stringResource(R.string.audio_settings_screen_title),
            style = MaterialTheme.typography.tboxHeadline,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        when {
            !isAndroid9MbCan -> {
                Text(
                    text = stringResource(R.string.audio_settings_android9_only),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            !mbCanOk -> {
                Text(
                    text = stringResource(R.string.audio_settings_mbcan_unavailable),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.audio_settings_screen_desc),
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = stringResource(R.string.audio_help_legend),
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = mbCanAudioPropertyUiEntries,
                key = { it.propertyId },
            ) { entry ->
                AudioPropertyRow(
                    entry = entry,
                    currentValue = currentValues[entry.propertyId],
                    draftValue = draftValues[entry.propertyId].orEmpty(),
                    noDataLabel = noDataLabel,
                    enabled = controlsEnabled,
                    onDraftChange = { draftValues[entry.propertyId] = it },
                    onApply = { rawValue ->
                        val parsedValue = rawValue.toIntOrNull()
                        if (parsedValue == null) {
                            statusMessage = context.getString(
                                R.string.audio_settings_invalid_value,
                                entry.propertyName,
                            )
                            return@AudioPropertyRow
                        }
                        coroutineScope.launch {
                            val result = UniversalCanRepository.execute(
                                MbCanCommand.SetAudioPropertyRaw(entry.propertyId, parsedValue),
                            )
                            statusMessage = "${entry.propertyName}: ${result.message}"
                            if (result.success) {
                                currentValues[entry.propertyId] =
                                    UniversalCanRepository.getAudioParam(entry.propertyId)
                            }
                        }
                        focusManager.clearFocus()
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun AudioPropertyRow(
    entry: MbCanAudioPropertyUiEntry,
    currentValue: Int?,
    draftValue: String,
    noDataLabel: String,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onApply: (String) -> Unit,
) {
    val context = LocalContext.current
    val help = MbCanAudioPropertyHelp.get(entry.propertyId)
    val title = help?.let { stringResource(it.titleRes) } ?: entry.propertyName
    val description = help?.let { stringResource(it.descriptionRes) }
    val confidenceLabel = help?.let {
        stringResource(MbCanAudioPropertyHelp.confidenceLabelRes(it.confidence))
    }
    val currentValueLabel = MbCanAudioPropertyHelp.formatCurrentValue(
        context = context,
        propertyId = entry.propertyId,
        raw = currentValue,
        noDataLabel = noDataLabel,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.audio_settings_property_id, entry.propertyId) +
                " · " + entry.propertyName,
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        confidenceLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        }
        description?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.audio_settings_current_value, currentValueLabel),
                modifier = Modifier.weight(0.35f),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = draftValue,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(0.4f),
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.tboxBody,
                label = {
                    Text(
                        text = stringResource(R.string.audio_settings_new_value_label),
                        style = MaterialTheme.typography.tboxCaption,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onApply(draftValue) },
                ),
            )
            Button(
                onClick = rememberWrappedOnClick { onApply(draftValue) },
                enabled = enabled,
                modifier = Modifier.weight(0.25f),
            ) {
                Text(
                    text = stringResource(R.string.action_set),
                    style = MaterialTheme.typography.tboxButton,
                    maxLines = 1,
                )
            }
        }
    }
}
