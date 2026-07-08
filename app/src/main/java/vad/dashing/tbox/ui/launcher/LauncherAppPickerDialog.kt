package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalContext
import java.util.UUID
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.theme.tboxCaption

@Composable
internal fun LauncherAppPickerDialog(
    visible: Boolean,
    title: String,
    apps: List<LaunchableAppEntry>,
    onDismiss: () -> Unit,
    onPick: (LaunchableAppEntry) -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                apps.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onPick(app)
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = app.label,
                                modifier = Modifier.size(32.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Text(
                            text = app.label,
                            color = LauncherColors.TextPrimary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun LauncherSplitPresetCreateDialog(
    visible: Boolean,
    apps: List<LaunchableAppEntry>,
    initialPreset: LauncherSplitPreset? = null,
    pinToHomeOnSave: Boolean = true,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
) {
    val context = LocalContext.current
    val newPresetName = stringResource(R.string.launcher_split_new_preset)
    if (!visible) return

    var editing by remember(visible, initialPreset?.id) {
        mutableStateOf(
            initialPreset ?: LauncherSplitPreset(
                id = UUID.randomUUID().toString(),
                name = newPresetName,
                leftPackage = "",
                rightPackage = "",
            ),
        )
    }
    var pickSide by remember { mutableStateOf<String?>(null) }

    if (pickSide != null) {
        LauncherAppPickerDialog(
            visible = true,
            title = if (pickSide == "left") stringResource(R.string.launcher_split_pick_left)
            else stringResource(R.string.launcher_split_pick_right),
            apps = apps,
            onDismiss = { pickSide = null },
            onPick = { entry ->
                editing = if (pickSide == "left") {
                    editing.copy(leftPackage = entry.packageName)
                } else {
                    editing.copy(rightPackage = entry.packageName)
                }
                pickSide = null
            },
        )
    }

    val draft = editing
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var ratio by remember(draft.id) { mutableFloatStateOf(draft.leftRatio) }
    val leftLabel = apps.firstOrNull { it.packageName == draft.leftPackage }?.label ?: draft.leftPackage
    val rightLabel = apps.firstOrNull { it.packageName == draft.rightPackage }?.label ?: draft.rightPackage

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialPreset != null) stringResource(R.string.launcher_split_edit_preset)
                else stringResource(R.string.launcher_home_add_split),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.launcher_split_preset_name)) },
                )
                LauncherSplitPickRow(
                    stringResource(R.string.launcher_split_left),
                    leftLabel.takeIf { draft.leftPackage.isNotBlank() }
                        ?.let { LaunchableAppEntry(draft.leftPackage, it, null, null) },
                    { pickSide = "left" },
                )
                LauncherSplitPickRow(
                    stringResource(R.string.launcher_split_right),
                    rightLabel.takeIf { draft.rightPackage.isNotBlank() }
                        ?.let { LaunchableAppEntry(draft.rightPackage, it, null, null) },
                    { pickSide = "right" },
                )
                Text(
                    "${stringResource(R.string.launcher_split_ratio)}: ${(ratio * 100).toInt()}% / ${((1f - ratio) * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = LauncherColors.TextSecondary,
                )
                Slider(value = ratio, onValueChange = { ratio = it }, valueRange = 0.2f..0.8f)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank() || draft.leftPackage.isBlank() || draft.rightPackage.isBlank()) return@TextButton
                    val saved = draft.copy(name = name, leftRatio = ratio)
                    LauncherSplitPresetStore.upsertPreset(context, saved)
                    if (pinToHomeOnSave) {
                        LauncherHomeStore.addSplit(context, saved.id)
                    }
                    onSaved()
                    onDismiss()
                },
                enabled = name.isNotBlank() && draft.leftPackage.isNotBlank() && draft.rightPackage.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun LauncherSplitPresetsDialog(
    visible: Boolean,
    apps: List<LaunchableAppEntry>,
    configRevision: Int,
    onDismiss: () -> Unit,
    onPresetsChanged: () -> Unit,
    onPinToHome: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val newPresetName = stringResource(R.string.launcher_split_new_preset)
    if (!visible) return
    var presets by remember(context, configRevision) { mutableStateOf(LauncherSplitPresetStore.loadPresets(context)) }
    var editing by remember { mutableStateOf<LauncherSplitPreset?>(null) }
    var pickSide by remember { mutableStateOf<String?>(null) }

    if (pickSide != null && editing != null) {
        LauncherAppPickerDialog(
            visible = true,
            title = if (pickSide == "left") stringResource(R.string.launcher_split_pick_left)
            else stringResource(R.string.launcher_split_pick_right),
            apps = apps,
            onDismiss = { pickSide = null },
            onPick = { entry ->
                editing = if (pickSide == "left") {
                    editing!!.copy(leftPackage = entry.packageName)
                } else {
                    editing!!.copy(rightPackage = entry.packageName)
                }
                pickSide = null
            },
        )
    }

    if (editing != null) {
        val draft = editing!!
        var name by remember(draft.id) { mutableStateOf(draft.name) }
        var ratio by remember(draft.id) { mutableFloatStateOf(draft.leftRatio) }
        val leftLabel = apps.firstOrNull { it.packageName == draft.leftPackage }?.label ?: draft.leftPackage
        val rightLabel = apps.firstOrNull { it.packageName == draft.rightPackage }?.label ?: draft.rightPackage
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.launcher_split_edit_preset)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.launcher_split_preset_name)) })
                    LauncherSplitPickRow(stringResource(R.string.launcher_split_left), leftLabel.takeIf { draft.leftPackage.isNotBlank() }?.let { LaunchableAppEntry(draft.leftPackage, it, null, null) }, { pickSide = "left" })
                    LauncherSplitPickRow(stringResource(R.string.launcher_split_right), rightLabel.takeIf { draft.rightPackage.isNotBlank() }?.let { LaunchableAppEntry(draft.rightPackage, it, null, null) }, { pickSide = "right" })
                    Text("${stringResource(R.string.launcher_split_ratio)}: ${(ratio * 100).toInt()}% / ${((1f - ratio) * 100).toInt()}%", fontSize = 12.sp, color = LauncherColors.TextSecondary)
                    Slider(value = ratio, onValueChange = { ratio = it }, valueRange = 0.2f..0.8f)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank() && draft.leftPackage.isNotBlank() && draft.rightPackage.isNotBlank()) {
                        val saved = draft.copy(name = name, leftRatio = ratio)
                        LauncherSplitPresetStore.upsertPreset(context, saved)
                        presets = LauncherSplitPresetStore.loadPresets(context)
                        onPresetsChanged()
                        editing = null
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.launcher_split_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (presets.isEmpty()) {
                    Text(stringResource(R.string.launcher_split_no_presets), fontSize = 13.sp, color = LauncherColors.TextSecondary)
                }
                presets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LauncherColors.CardDark)
                            .clickable { launchSplitPreset(context, preset, apps) }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, color = LauncherColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${(preset.leftRatio * 100).toInt()}/${((1f - preset.leftRatio) * 100).toInt()} · ${preset.leftPackage.substringAfterLast('.')} | ${preset.rightPackage.substringAfterLast('.')}",
                                color = LauncherColors.TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { editing = preset }) { Text("✎") }
                        TextButton(onClick = { onPinToHome(preset.id) }) { Text("+") }
                        TextButton(onClick = {
                            LauncherSplitPresetStore.deletePreset(context, preset.id)
                            presets = LauncherSplitPresetStore.loadPresets(context)
                            onPresetsChanged()
                        }) { Text("✕") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                editing = LauncherSplitPreset(
                    id = UUID.randomUUID().toString(),
                    name = newPresetName,
                    leftPackage = "",
                    rightPackage = "",
                )
            }) { Text(stringResource(R.string.launcher_split_add_preset)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun LauncherSplitModeDialog(
    visible: Boolean,
    apps: List<LaunchableAppEntry>,
    onDismiss: () -> Unit,
    onLaunch: (LaunchableAppEntry, LaunchableAppEntry) -> Unit,
) {
    if (!visible || apps.size < 2) return
    var left by remember(visible) { mutableStateOf<LaunchableAppEntry?>(apps.firstOrNull()) }
    var right by remember(visible) { mutableStateOf<LaunchableAppEntry?>(apps.getOrNull(1)) }
    var pickingSide by remember { mutableStateOf<String?>(null) }

    if (pickingSide != null) {
        LauncherAppPickerDialog(
            visible = true,
            title = if (pickingSide == "left") {
                stringResource(R.string.launcher_split_pick_left)
            } else {
                stringResource(R.string.launcher_split_pick_right)
            },
            apps = apps,
            onDismiss = { pickingSide = null },
            onPick = { entry ->
                if (pickingSide == "left") left = entry else right = entry
                pickingSide = null
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.launcher_split_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.launcher_split_hint),
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextSecondary,
                    fontSize = 13.sp,
                )
                LauncherSplitPickRow(
                    label = stringResource(R.string.launcher_split_left),
                    app = left,
                    onClick = { pickingSide = "left" },
                )
                LauncherSplitPickRow(
                    label = stringResource(R.string.launcher_split_right),
                    app = right,
                    onClick = { pickingSide = "right" },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val l = left
                    val r = right
                    if (l != null && r != null && l.packageName != r.packageName) {
                        onLaunch(l, r)
                        onDismiss()
                    }
                },
                enabled = left != null && right != null && left?.packageName != right?.packageName,
            ) {
                Text(stringResource(R.string.launcher_split_launch))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun LauncherSplitPickRow(
    label: String,
    app: LaunchableAppEntry?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LauncherColors.CardDark)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = LauncherColors.TextSecondary, fontSize = 13.sp)
        Text(
            text = app?.label ?: "—",
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
