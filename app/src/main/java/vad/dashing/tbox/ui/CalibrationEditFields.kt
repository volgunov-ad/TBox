package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxTitle
import java.util.Locale
import kotlin.math.abs

/** Locale-tolerant float parse (comma or dot). */
fun parseCalibFloat(raw: String): Float? =
    raw.trim().replace(',', '.').takeIf { it.isNotBlank() }?.toFloatOrNull()

fun formatCalibFloat(value: Float, decimals: Int = 3): String =
    String.format(Locale.US, "%.${decimals}f", value)

fun calibFloatDraftMatchesSaved(draft: String, saved: Float, decimals: Int = 3): Boolean {
    val parsed = parseCalibFloat(draft) ?: return false
    return formatCalibFloat(parsed, decimals) == formatCalibFloat(saved, decimals)
}

/**
 * Draft float field with trailing save icon — same UX as fuel calibration ints
 * ([CalibrationIntCommitField]).
 */
@Composable
fun CalibrationFloatCommitField(
    title: String,
    description: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    savedValue: Float,
    minValue: Float,
    maxValue: Float,
    decimals: Int = 3,
    onCommit: (Float) -> Unit,
) {
    val parsed = parseCalibFloat(draft)
    val inRange = parsed != null && parsed in minValue..maxValue
    val canCommit = inRange && !calibFloatDraftMatchesSaved(draft, savedValue, decimals)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .width(150.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.tboxTitle.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            trailingIcon = if (canCommit) {
                {
                    CalibSaveIconButton(
                        onClick = {
                            parseCalibFloat(draft)?.let { v ->
                                if (v in minValue..maxValue) onCommit(v)
                            }
                        },
                    )
                }
            } else {
                null
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
                .align(Alignment.CenterVertically),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.onSurface,
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

/**
 * Sign field: accepts −1 or +1 (any negative → −1, otherwise +1 on commit if |v|≥0.5).
 */
@Composable
fun CalibrationSignCommitField(
    title: String,
    description: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    savedSign: Int,
    onCommit: (Int) -> Unit,
) {
    val saved = if (savedSign < 0) -1 else 1
    val parsed = parseCalibFloat(draft)
    val nextSign = when {
        parsed == null || abs(parsed) < 0.5f -> null
        parsed < 0f -> -1
        else -> 1
    }
    val canCommit = nextSign != null && nextSign != saved
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .width(150.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.tboxTitle.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            trailingIcon = if (canCommit) {
                {
                    CalibSaveIconButton(
                        onClick = { nextSign?.let(onCommit) },
                    )
                }
            } else {
                null
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
                .align(Alignment.CenterVertically),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.onSurface,
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
private fun CalibSaveIconButton(onClick: () -> Unit) {
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
            Icon(
                painter = painterResource(R.drawable.ic_refuel_save),
                contentDescription = stringResource(R.string.action_save),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
