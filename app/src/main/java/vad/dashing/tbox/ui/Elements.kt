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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import vad.dashing.tbox.trip.TripWidgetTileDisplay
import vad.dashing.tbox.ui.theme.TboxFontFamily
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxTabLabel
import vad.dashing.tbox.ui.theme.tboxTitle

@Composable
fun StatusRow(
    label: String,
    value: String,
    unit: String = "",
    style: TextStyle? = null,
    fontSize: TextUnit? = null,
    color: Color? = null,
    showDivider: Boolean = true,
    labelColumnWidthPercent: Int = TripWidgetTileDisplay.DEFAULT_LABEL_COLUMN_WIDTH_PERCENT,
) {
    val textColor = color ?: MaterialTheme.colorScheme.onSurface
    val valueWithUnit = if (unit.isNotEmpty()) "$value\u2009$unit" else value
    val baseStyle = style ?: MaterialTheme.typography.tboxTitle
    val resolvedStyle = if (fontSize != null) baseStyle.copy(fontSize = fontSize) else baseStyle
    val lineHeight = resolvedStyle.lineHeight
    val labelPercent = TripWidgetTileDisplay.normalizeLabelColumnWidthPercent(
        labelColumnWidthPercent,
    )
    val labelWeight = labelPercent / 100f
    val valueWeight = (100 - labelPercent) / 100f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier
                .weight(labelWeight)
                .padding(end = 8.dp),
            style = resolvedStyle,
            lineHeight = lineHeight,
            color = textColor,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = valueWithUnit,
            modifier = Modifier
                .weight(valueWeight)
                .padding(start = 8.dp),
            style = resolvedStyle,
            lineHeight = lineHeight,
            color = textColor,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start
        )
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
    }
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
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
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
        style = MaterialTheme.typography.tboxBody,
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
        onClick = rememberWrappedOnClick(onClick),
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
            style = MaterialTheme.typography.tboxTitle,
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
    onClick: () -> Unit,
    subtitle: String? = null,
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
            .clickableWithSound(onClick = onClick)
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
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = title,
                        color = textColor,
                        textAlign = TextAlign.Left,
                        style = MaterialTheme.typography.tboxTabLabel.copy(
                            lineHeight = MaterialTheme.typography.tboxTabLabel.fontSize * 1.1f,
                        ),
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = textColor,
                            textAlign = TextAlign.Left,
                            style = MaterialTheme.typography.tboxCaption,
                        )
                    }
                }
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
        style = MaterialTheme.typography.tboxHeadline,
        maxLines = 2,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Left
    )
}

/** Заголовок Material3-диалогов: как крупные подписи вкладок настроек. */
@Composable
fun AppAlertDialogTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.tboxHeadline.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/** Основной текст диалога: как поля поездок/заправок (24 sp). */
@Composable
fun AppAlertDialogText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.tboxTitle.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/** Подписи кнопок в диалогах; цвет берётся из кнопки (Filled / Outlined). */
@Composable
fun AppAlertDialogButtonLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.tboxButton,
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
            onCheckedChange = rememberWrappedOnCheckedChange(onCheckedChange),
            modifier = Modifier
                .align(if (description.isNotEmpty()) Alignment.Top else Alignment.CenterVertically)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp) // ← Отступ от Switch
                .align(if (description.isNotEmpty()) Alignment.Top else Alignment.CenterVertically)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = if (description.isNotEmpty()) 4.dp else 0.dp)
            )

            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            onCheckedChange = rememberWrappedOnCheckedChange(onCheckedChange),
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
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = if (description.isNotEmpty()) 4.dp else 0.dp)
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedButton(
            onClick = rememberWrappedOnClick(onActionClick),
            enabled = actionEnabled,
            modifier = Modifier
                .padding(start = 8.dp)
                .align(if (description.isNotEmpty()) Alignment.Top else Alignment.CenterVertically)
        ) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.tboxBody,
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
    selectorWidth: Dp = 140.dp
) {
    val dropdownValueStyle = MaterialTheme.typography.tboxTitle
    val dropdownItemStyle = MaterialTheme.typography.tboxTitle
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
                width = selectorWidth,
                enabled = enabled,
                valueStyle = dropdownValueStyle,
                itemStyle = dropdownItemStyle,
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
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = if (description.isNotEmpty()) 4.dp else 0.dp)
            )

            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun appFontFamilyLabel(fontFamily: TboxFontFamily): String = stringResource(
    when (fontFamily) {
        TboxFontFamily.Default -> R.string.settings_app_font_family_default
        TboxFontFamily.SansSerif -> R.string.settings_app_font_family_sans_serif
        TboxFontFamily.Serif -> R.string.settings_app_font_family_serif
        TboxFontFamily.Monospace -> R.string.settings_app_font_family_monospace
        TboxFontFamily.CrimsonText -> R.string.settings_app_font_family_crimson_text
        TboxFontFamily.Cabin -> R.string.settings_app_font_family_cabin
        TboxFontFamily.Nunito -> R.string.settings_app_font_family_nunito
    }
)

@Composable
fun SettingAppFontFamily(
    selectedFontFamilyId: Int,
    onFontFamilyIdChange: (Int) -> Unit,
    text: String,
    description: String = "",
    enabled: Boolean = true,
    selectorWidth: Dp = 200.dp,
) {
    val selected = TboxFontFamily.fromId(selectedFontFamilyId)
    var expanded by remember { mutableStateOf(false) }
    val previewStyle = MaterialTheme.typography.tboxTitle.copy(fontFamily = selected.toComposeFontFamily())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .align(if (description.isNotEmpty()) Alignment.Top else Alignment.CenterVertically)
                .wrapContentWidth(),
        ) {
            Box(modifier = Modifier.wrapContentSize()) {
                OutlinedButton(
                    onClick = rememberWrappedOnClick { expanded = true },
                    enabled = enabled,
                    modifier = Modifier.width(selectorWidth),
                ) {
                    Text(
                        text = appFontFamilyLabel(selected),
                        style = previewStyle,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (expanded) {
                            stringResource(R.string.dropdown_collapse)
                        } else {
                            stringResource(R.string.dropdown_expand)
                        },
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.width(selectorWidth),
                ) {
                    TboxFontFamily.all.forEach { option ->
                        key(option) {
                            val menuItemClick = rememberWrappedOnClick {
                                onFontFamilyIdChange(option.id)
                                expanded = false
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = appFontFamilyLabel(option),
                                        style = MaterialTheme.typography.tboxTitle.copy(
                                            fontFamily = option.toComposeFontFamily(),
                                        ),
                                        color = if (option == selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                onClick = menuItemClick,
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
                .align(if (description.isNotEmpty()) Alignment.Top else Alignment.CenterVertically),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = if (description.isNotEmpty()) 4.dp else 0.dp),
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    valueStyle: TextStyle? = null,
    itemStyle: TextStyle? = null,
    popupFocusable: Boolean = true,
) {
    val resolvedValueStyle = valueStyle ?: MaterialTheme.typography.tboxTitle
    val resolvedItemStyle = itemStyle ?: MaterialTheme.typography.tboxTitle
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.wrapContentSize()) {
        OutlinedButton(
            onClick = rememberWrappedOnClick { expanded = true },
            enabled = enabled,
            modifier = Modifier.width(width)
        ) {
            Text(
                text = selectedValue.toString(),
                style = resolvedValueStyle,
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
                key(option) {
                    val menuItemClick = rememberWrappedOnClick {
                        onValueChange(option)
                        expanded = false
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.toString(),
                                style = resolvedItemStyle,
                                color = if (option == selectedValue) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        },
                        onClick = menuItemClick
                    )
                }
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
                .width(150.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
                .align(Alignment.CenterVertically)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 0.dp)
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            style = MaterialTheme.typography.tboxTitle,
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
                style = MaterialTheme.typography.tboxButton,
                color = MaterialTheme.colorScheme.onSurface
            )
        } ?: Text(
            text = stringResource(R.string.can_no_data),
            style = MaterialTheme.typography.tboxButton,
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
                        style = MaterialTheme.typography.tboxBody,
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
private fun PanelNameSaveTrailingIcon(
    visible: Boolean,
    onSave: () -> Unit,
) {
    if (!visible) return
    SettingsCommitIconButton(
        onClick = onSave,
        contentDescription = stringResource(R.string.action_save),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_refuel_save),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsCommitIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = rememberWrappedOnClick(onClick),
        modifier = Modifier
            .size(40.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                shape = CircleShape,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
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
                            style = MaterialTheme.typography.tboxCaption,
                        )
                    },
                    textStyle = MaterialTheme.typography.tboxButton,
                    trailingIcon = {
                        PanelNameSaveTrailingIcon(
                            visible = enabled && nameDirty && trimmedDraft.isNotEmpty(),
                            onSave = { onRenamePanel(effectiveId, trimmedDraft) },
                        )
                    },
                )
            }
            Button(onClick = rememberWrappedOnClick(onAddPanel), enabled = enabled) {
                Text(stringResource(R.string.action_add), style = MaterialTheme.typography.tboxBody)
            }
            Button(
                onClick = rememberWrappedOnClick { onDeletePanel(effectiveId) },
                enabled = enabled && deleteInProgressPanelId != effectiveId
            ) {
                Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.tboxBody)
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
                            style = MaterialTheme.typography.tboxCaption,
                        )
                    },
                    textStyle = MaterialTheme.typography.tboxButton,
                    trailingIcon = {
                        PanelNameSaveTrailingIcon(
                            visible = enabled && nameDirty && trimmedDraft.isNotEmpty(),
                            onSave = { onRenamePanel(effectiveId, trimmedDraft) },
                        )
                    },
                )
            }
            Button(onClick = rememberWrappedOnClick(onAddPanel), enabled = enabled) {
                Text(stringResource(R.string.action_add), style = MaterialTheme.typography.tboxBody)
            }
            Button(
                onClick = rememberWrappedOnClick { onDeletePanel(effectiveId) },
                enabled = enabled && deleteInProgressPanelId != effectiveId
            ) {
                Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.tboxBody)
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
                    style = MaterialTheme.typography.tboxTitle,
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
                    style = MaterialTheme.typography.tboxTitle,
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
                    style = MaterialTheme.typography.tboxTitle,
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
                    style = MaterialTheme.typography.tboxTitle,
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
                    style = MaterialTheme.typography.tboxTitle,
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
                    style = MaterialTheme.typography.tboxTitle,
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
                    style = MaterialTheme.typography.tboxTitle,
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
                    style = MaterialTheme.typography.tboxTitle,
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
    textStyle: TextStyle? = null,
    enabled: Boolean = true
) {
    val resolvedTextStyle = textStyle ?: MaterialTheme.typography.tboxTitle
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
        textStyle = LocalTextStyle.current.merge(resolvedTextStyle),
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
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.5f)
        )

        Button(
            onClick = rememberWrappedOnClick {
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
                style = MaterialTheme.typography.tboxButton,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = rememberWrappedOnClick {
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
                style = MaterialTheme.typography.tboxButton,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = rememberWrappedOnClick {
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
                style = MaterialTheme.typography.tboxButton,
                textAlign = TextAlign.Center
            )
        }
    }
}
