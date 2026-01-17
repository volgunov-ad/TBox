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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanFrame
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
            maxLines = 2,
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
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
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
                .align(Alignment.Top)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp) // ← Отступ от Switch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingDropdownGeneric(
    selectedValue: T,
    onValueChange: (T) -> Unit,
    text: String,
    description: String,
    enabled: Boolean = true,
    options: List<T>
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Top)
                .wrapContentWidth()
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.width(140.dp)
            ) {
                TextField(
                    value = selectedValue.toString(),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier.menuAnchor(),
                    enabled = enabled,
                    textStyle = TextStyle(fontSize = 24.sp)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(
                                text = option.toString(),
                                style = TextStyle(fontSize = 24.sp
                                )) },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
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
fun CanIdEntry(
    canId: String,
    lastFrame: CanFrame?
) {
    // CAN ID
    Column() {
        Text(
            text = "CAN ID: $canId",
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
                text = "  $rawValueHex -> $rawValueDec",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        } ?: Text(
            text = "  Нет данных",
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

@Composable
fun FloatingDashboardProfileSelector(
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        "floating-1" to "1",
        "floating-2" to "2",
        "floating-3" to "3"
    )
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (id, label) ->
                val isSelected = id == selectedId
                if (isSelected) {
                    Button(
                        onClick = { onSelect(id) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = label, fontSize = 22.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(id) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = label, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingDashboardPositionSizeSettings(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
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
                    text = "Ширина плавающей панели (px)",
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
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Поле для высоты
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Высота плавающей панели (px)",
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
                    modifier = Modifier.fillMaxWidth()
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
                    text = "Позиция X плавающей панели (px)",
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
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Поле для Y координаты
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Позиция Y плавающей панели (px)",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                IntInputField(
                    value = floatingDashboardStartY,
                    onValueChange = { newValue ->
                        if (newValue >= -100) {
                            settingsViewModel.saveFloatingDashboardStartY(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
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
    fontSize: TextUnit = 24.sp
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
