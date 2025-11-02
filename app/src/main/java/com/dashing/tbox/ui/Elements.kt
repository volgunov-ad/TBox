package com.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dashing.tbox.CanFrame
import java.text.SimpleDateFormat
import java.util.Locale

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
    selected: Boolean,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Left,
            fontSize = 34.sp
        )
    }
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

        // Время последнего фрейма
        /*lastFrame?.let { frame ->
            Text(
                text = "  Последнее изменение: " +
                        "${SimpleDateFormat("HH:mm:ss", 
                            Locale.getDefault()).format(frame.date)}",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }*/
    }

    // Информация о фреймах
    Column(
        horizontalAlignment = Alignment.End
    ) {
        // Сырое значение (первые 8 байт для примера)
        lastFrame?.let { frame ->
            val rawValuePreview = frame.rawValue.joinToString(" ") {
                "%02X".format(it)
            }
            Text(
                text = "  " + if (frame.rawValue.size > 8) "$rawValuePreview..." else rawValuePreview,
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
