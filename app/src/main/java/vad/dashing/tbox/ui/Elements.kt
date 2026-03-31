package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.MainScreenPanelConfig
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.CanFrame
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel

@Composable
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = Int.MAX_VALUE,
            softWrap = true,
            overflow = TextOverflow.Visible
        )
        Text(
            text = value,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = Int.MAX_VALUE,
            softWrap = true,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Start
        )
    }
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
    )
}

@Composable
fun StatusHeader(value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = value,
            modifier = Modifier
                .weight(1f)
                .padding(top = 10.dp),
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            lineHeight = 24.sp * 1.3f,
            softWrap = true,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center
        )
    }
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
    )
}

@Composable
fun ColoredLogEntry(log: String) {
    val color = when {
        log.contains("ERROR", ignoreCase = false) -> Color(0xFFFF5252)
        log.contains("WARN", ignoreCase = false) -> Color(0xFFFFB74D) // Orange
        log.contains("INFO", ignoreCase = false) -> MaterialTheme.colorScheme.primary
        log.contains("DEBUG", ignoreCase = false) -> Color(0xFF66BB6A) // Green
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = log,
        fontSize = 20.sp,
        color = color,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor
        ),
        elevation = if (isSelected) {
            ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        } else {
            ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        }
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TabMenuItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    showText: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.background
    }

    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    val iconSize = 34.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(
                vertical = 16.dp,
                horizontal = 8.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (showText) Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = textColor,
                modifier = Modifier.size(iconSize)
            )
            if (showText) {
                Text(
                    text = title,
                    color = textColor,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Left,
                    fontSize = 34.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsTitle(
    text:String
) {
    Text(
        modifier = Modifier.padding(top=10.dp),
        text = text,
        fontSize = 26.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        lineHeight = 26.sp * 1.3f,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Left
    )
}

@Composable
fun SettingSwitch(
    isChecked: Boolean,
    onCheckedChange: (enabled: Boolean) -> Unit,
    text: String,
    description: String,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top // ← Выравнивание по верху
    ) {
        // Switch выровнен по центру первого текста
        Switch(
            checked = isChecked,
            enabled = enabled,
            onCheckedChange = { enabled ->
                onCheckedChange(enabled)
            },
            modifier = Modifier
                .align(if (description.isNotEmpty()) Alignment.Top else Alignment.CenterVertically)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp) // ← Отступ от Switch
                .align(if (description.isNotEmpty()) Alignment.Top else Alignment.CenterVertically)
        ) {
            // Основной текст
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = if (description.isNotEmpty()) 4.dp else 0.dp)
            )

            // Описание (только под текстом, не под Switch)
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun SettingSwitchWithAction(
    isChecked: Boolean,
    onCheckedChange: (enabled: Boolean) -> Unit,
    text: String,
    description: String,
    enabled: Boolean,
    actionText: String,
    onActionClick: () -> Unit,
    actionEnabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Switch(
            checked = isChecked,
            enabled = enabled,
            onCheckedChange = { onCheckedChange(it) },
            modifier = Modifier
                .align(if (description.isNotEmpty()) Alignment.Top else Alignment.CenterVertically)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
                .align(if (description.isNotEmpty()) Alignment.Top else Alignment.CenterVertically)
        ) {
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = if (description.isNotEmpty()) 4.dp else 0.dp)
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }

        OutlinedButton(
            onClick = onActionClick,
            enabled = actionEnabled,
            modifier = Modifier
                .padding(start = 8.dp)
                .align(if (description.isNotEmpty()) Alignment.Top else Alignment.CenterVertically)
        ) {
            Text(
                text = actionText,
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun <T> SettingDropdownGeneric(
    selectedValue: T,
    onValueChange: (T) -> Unit,
    text: String,
    description: String,
    enabled: Boolean = true,
    options: List<T>,
    popupFocusable: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .align(if (description.isNotEmpty()) Alignment.Top else Alignment.CenterVertically)
                .wrapContentWidth()
        ) {
            GenericDropdownSelector(
                selectedValue = selectedValue,
                options = options,
                onValueChange = onValueChange,
                width = 140.dp,
                enabled = enabled,
                valueFontSize = 24.sp,
                itemFontSize = 24.sp,
                popupFocusable = popupFocusable
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
                .align(if (description.isNotEmpty()) Alignment.Top else Alignment.CenterVertically)
        ) {
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = if (description.isNotEmpty()) 4.dp else 0.dp)
            )

            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun <T> GenericDropdownSelector(
    selectedValue: T,
    options: List<T>,
    onValueChange: (T) -> Unit,
    width: Dp,
    enabled: Boolean = true,
    valueFontSize: TextUnit = 24.sp,
    itemFontSize: TextUnit = 24.sp,
    popupFocusable: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.wrapContentSize()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.width(width)
        ) {
            Text(
                text = selectedValue.toString(),
                fontSize = valueFontSize,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (expanded) {
                    stringResource(R.string.dropdown_collapse)
                } else {
                    stringResource(R.string.dropdown_expand)
                }
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(width),
            properties = PopupProperties(
                focusable = popupFocusable
            )
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.toString(),
                            fontSize = itemFontSize,
                            color = if (option == selectedValue) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SettingInt(
    value: Int,
    onValueChange: (value: Int) -> Unit,
    text: String,
    description: String,
    minValue: Int,
    maxValue: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        IntInputField(
            value = value,
            onValueChange = { newValue ->
                if (newValue >= minValue && newValue <= maxValue) {
                    onValueChange(newValue)
                }
            },
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .width(140.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
                .align(Alignment.CenterVertically)
        ) {
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 0.dp)
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun CanIdEntry(
    canId: String,
    lastFrame: CanFrame?
) {
    // CAN ID
    Column() {
        Text(
            text = stringResource(R.string.can_id_entry, canId),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    // Информация о фреймах
    Column(
        horizontalAlignment = Alignment.End
    ) {
        lastFrame?.let { frame ->
            val rawValueHex =
                frame.rawValue.joinToString(" ") {
                    "%02X".format(it)
            }
            val rawValueDec =
                frame.rawValue.joinToString(" ") {
                    "%-3d".format(it.toInt() and 0xFF)
                }
            Text(
                text = stringResource(R.string.can_raw_value_entry, rawValueHex, rawValueDec),
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        } ?: Text(
            text = stringResource(R.string.can_no_data),
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun LogsCard(
    logs: List<String>,
    logLevel: String,
    searchText: String = ""
) {
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, logLevel, searchText) {
        val levelFilteredLogs = when (logLevel) {
            "DEBUG" -> {
                logs.filter { it.contains("DEBUG") ||
                        it.contains("INFO") ||
                        it.contains("WARN") ||
                        it.contains("ERROR")}
            }
            "INFO" -> {
                logs.filter { it.contains("INFO") ||
                        it.contains("WARN") ||
                        it.contains("ERROR") }
            }
            "WARN" -> {
                logs.filter { it.contains("WARN") ||
                        it.contains("ERROR") }
            }
            else -> {
                logs.filter { it.contains("ERROR") }
            }
        }

        if (searchText.length >= 3) {
            levelFilteredLogs.filter { log ->
                log.contains(searchText, ignoreCase = true)
            }
        } else {
            levelFilteredLogs
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                items(count = filteredLogs.size) { index ->
                    val logEntry = filteredLogs[index]
                    ColoredLogEntry(log = logEntry)
                }
            }
        }
    }
}

@Composable
fun ATLogsCard(
    logs: List<String>
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                items(count = logs.size) { index ->
                    val logEntry = logs[index]
                    Text(
                        text = logEntry,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private data class FloatingPanelDropdownOption(val id: String, val label: String) {
    override fun toString() = label
}

private data class MainScreenPanelDropdownOption(val id: String, val label: String) {
    override fun toString() = label
}

@Composable
fun FloatingDashboardPanelEditor(
    panels: List<FloatingDashboardConfig>,
    selectedPanelId: String,
    onSelectPanelId: (String) -> Unit,
    onRenamePanel: (panelId: String, name: String) -> Unit,
    onAddPanel: () -> Unit,
    onDeletePanel: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    deleteInProgressPanelId: String? = null
) {
    if (panels.isEmpty()) return
    val selectedConfig = panels.find { it.id == selectedPanelId } ?: panels.first()
    val effectiveId = selectedConfig.id
    var draftName by remember { mutableStateOf(selectedConfig.name) }
    LaunchedEffect(effectiveId, selectedConfig.name) {
        draftName = selectedConfig.name
    }
    val options = remember(panels) {
        panels.map { FloatingPanelDropdownOption(it.id, it.name.ifBlank { it.id }) }
    }
    val selectedOption = remember(options, effectiveId) {
        options.find { it.id == effectiveId } ?: options.first()
    }
    val trimmedDraft = draftName.trim()
    val nameDirty = trimmedDraft != selectedConfig.name
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GenericDropdownSelector(
                selectedValue = selectedOption,
                options = options,
                onValueChange = { option -> onSelectPanelId(option.id) },
                width = 300.dp,
                enabled = enabled
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    label = {
                        Text(
                            text = stringResource(R.string.floating_panel_name_label),
                            fontSize = 16.sp
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(fontSize = 22.sp)
                )
                Button(
                    onClick = { onRenamePanel(effectiveId, trimmedDraft) },
                    enabled = enabled && nameDirty
                ) {
                    Text(
                        stringResource(R.string.floating_panel_rename_button),
                        fontSize = 18.sp
                    )
                }
            }
            Button(onClick = onAddPanel, enabled = enabled) {
                Text(stringResource(R.string.action_add), fontSize = 20.sp)
            }
            Button(
                onClick = { onDeletePanel(effectiveId) },
                enabled = enabled && deleteInProgressPanelId != effectiveId
            ) {
                Text(stringResource(R.string.action_delete), fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun MainScreenPanelEditor(
    panels: List<MainScreenPanelConfig>,
    selectedPanelId: String,
    onSelectPanelId: (String) -> Unit,
    onRenamePanel: (panelId: String, name: String) -> Unit,
    onAddPanel: () -> Unit,
    onDeletePanel: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    deleteInProgressPanelId: String? = null
) {
    if (panels.isEmpty()) return
    val selectedConfig = panels.find { it.id == selectedPanelId } ?: panels.first()
    val effectiveId = selectedConfig.id
    var draftName by remember { mutableStateOf(selectedConfig.name) }
    LaunchedEffect(effectiveId, selectedConfig.name) {
        draftName = selectedConfig.name
    }
    val options = remember(panels) {
        panels.map { MainScreenPanelDropdownOption(it.id, it.name.ifBlank { it.id }) }
    }
    val selectedOption = remember(options, effectiveId) {
        options.find { it.id == effectiveId } ?: options.first()
    }
    val trimmedDraft = draftName.trim()
    val nameDirty = trimmedDraft != selectedConfig.name
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GenericDropdownSelector(
                selectedValue = selectedOption,
                options = options,
                onValueChange = { option -> onSelectPanelId(option.id) },
                width = 300.dp,
                enabled = enabled
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    label = {
                        Text(
                            text = stringResource(R.string.floating_panel_name_label),
                            fontSize = 16.sp
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(fontSize = 22.sp)
                )
                Button(
                    onClick = { onRenamePanel(effectiveId, trimmedDraft) },
                    enabled = enabled && nameDirty
                ) {
                    Text(
                        stringResource(R.string.floating_panel_rename_button),
                        fontSize = 18.sp
                    )
                }
            }
            Button(onClick = onAddPanel, enabled = enabled) {
                Text(stringResource(R.string.action_add), fontSize = 20.sp)
            }
            Button(
                onClick = { onDeletePanel(effectiveId) },
                enabled = enabled && deleteInProgressPanelId != effectiveId
            ) {
                Text(stringResource(R.string.action_delete), fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun MainScreenPanelRelativeLayoutSettings(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val relX by settingsViewModel.mainScreenPanelRelXPercent.collectAsStateWithLifecycle()
    val relY by settingsViewModel.mainScreenPanelRelYPercent.collectAsStateWithLifecycle()
    val relW by settingsViewModel.mainScreenPanelRelWidthPercent.collectAsStateWithLifecycle()
    val relH by settingsViewModel.mainScreenPanelRelHeightPercent.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.main_screen_panel_rel_width_pct),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = relW,
                    onValueChange = { newValue ->
                        if (newValue in 8..100) {
                            settingsViewModel.saveMainScreenPanelRelWidthPercent(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.main_screen_panel_rel_height_pct),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = relH,
                    onValueChange = { newValue ->
                        if (newValue in 8..100) {
                            settingsViewModel.saveMainScreenPanelRelHeightPercent(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.main_screen_panel_rel_x_pct),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = relX,
                    onValueChange = { newValue ->
                        if (newValue in 0..100) {
                            settingsViewModel.saveMainScreenPanelRelXPercent(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.main_screen_panel_rel_y_pct),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = relY,
                    onValueChange = { newValue ->
                        if (newValue in 0..100) {
                            settingsViewModel.saveMainScreenPanelRelYPercent(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
fun MainScreenMapWindowRelativeLayoutSettings(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val relX by settingsViewModel.mainScreenMapWindowRelXPercent.collectAsStateWithLifecycle()
    val relY by settingsViewModel.mainScreenMapWindowRelYPercent.collectAsStateWithLifecycle()
    val relW by settingsViewModel.mainScreenMapWindowRelWidthPercent.collectAsStateWithLifecycle()
    val relH by settingsViewModel.mainScreenMapWindowRelHeightPercent.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.main_screen_panel_rel_width_pct),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = relW,
                    onValueChange = { newValue ->
                        if (newValue in 8..100) {
                            settingsViewModel.saveMainScreenMapWindowRelWidthPercent(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.main_screen_panel_rel_height_pct),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = relH,
                    onValueChange = { newValue ->
                        if (newValue in 8..100) {
                            settingsViewModel.saveMainScreenMapWindowRelHeightPercent(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.main_screen_panel_rel_x_pct),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = relX,
                    onValueChange = { newValue ->
                        if (newValue in 0..100) {
                            settingsViewModel.saveMainScreenMapWindowRelXPercent(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.main_screen_panel_rel_y_pct),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = relY,
                    onValueChange = { newValue ->
                        if (newValue in 0..100) {
                            settingsViewModel.saveMainScreenMapWindowRelYPercent(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
fun FloatingDashboardPositionSizeSettings(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val floatingDashboardHeight by settingsViewModel.floatingDashboardHeight.collectAsStateWithLifecycle()
    val floatingDashboardWidth by settingsViewModel.floatingDashboardWidth.collectAsStateWithLifecycle()
    val floatingDashboardStartX by settingsViewModel.floatingDashboardStartX.collectAsStateWithLifecycle()
    val floatingDashboardStartY by settingsViewModel.floatingDashboardStartY.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        // Строка для ширины и высоты
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Поле для ширины
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.floating_panel_width_px),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = floatingDashboardWidth,
                    onValueChange = { newValue ->
                        if (newValue >= 50) {
                            settingsViewModel.saveFloatingDashboardWidth(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }

            // Поле для высоты
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.floating_panel_height_px),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = floatingDashboardHeight,
                    onValueChange = { newValue ->
                        if (newValue >= 50) {
                            settingsViewModel.saveFloatingDashboardHeight(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
        }

        // Строка для X и Y координат
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Поле для X координаты
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.floating_panel_pos_x_px),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = floatingDashboardStartX,
                    onValueChange = { newValue ->
                        if (newValue >= 0) {
                            settingsViewModel.saveFloatingDashboardStartX(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }

            // Поле для Y координаты
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.floating_panel_pos_y_px),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = floatingDashboardStartY,
                    onValueChange = { newValue ->
                        if (newValue >= 0) {
                            settingsViewModel.saveFloatingDashboardStartY(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
        }
    }
}

// Компонент для ввода целых чисел
@Composable
fun IntInputField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    enabled: Boolean = true
) {
    var textValue by remember { mutableStateOf(value.toString()) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        textValue = value.toString()
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newText ->
            textValue = newText
            if (newText.isEmpty()) {
                onValueChange(0)
                isError = false
            } else {
                val intValue = newText.toIntOrNull()
                if (intValue != null) {
                    onValueChange(intValue)
                    isError = false
                } else {
                    isError = true
                }
            }
        },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        isError = isError,
        textStyle = LocalTextStyle.current.copy(fontSize = fontSize),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            errorBorderColor = MaterialTheme.colorScheme.error
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun TboxApplicationControls(
    appName: String,
    tboxConnected: Boolean,
    onServiceCommand: (String, String, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var commandButtonsEnabled by remember { mutableStateOf(true) }

        LaunchedEffect(commandButtonsEnabled) {
            if (!commandButtonsEnabled) {
                delay(5000) // Блокировка на 5 секунд
                commandButtonsEnabled = true
            }
        }

        Text(
            text = stringResource(R.string.application_label, appName),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.5f)
        )

        Button(
            onClick = {
                if (commandButtonsEnabled) {
                    commandButtonsEnabled = false
                    onServiceCommand(
                        BackgroundService.ACTION_TBOX_APP_SUSPEND,
                        BackgroundService.EXTRA_APP_NAME,
                        appName
                    )
                }
            },
            enabled = commandButtonsEnabled && tboxConnected,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.button_suspend),
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = {
                if (commandButtonsEnabled) {
                    commandButtonsEnabled = false
                    onServiceCommand(
                        BackgroundService.ACTION_TBOX_APP_RESUME,
                        BackgroundService.EXTRA_APP_NAME,
                        appName
                    )
                }
            },
            enabled = commandButtonsEnabled && tboxConnected,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.button_resume),
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = {
                if (commandButtonsEnabled) {
                    commandButtonsEnabled = false
                    onServiceCommand(
                        BackgroundService.ACTION_TBOX_APP_STOP,
                        BackgroundService.EXTRA_APP_NAME,
                        appName
                    )
                }
            },
            enabled = commandButtonsEnabled && tboxConnected,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.button_stop),
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
