package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import vad.dashing.tbox.automation.AutomationPickerSearch
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalSource
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle

/** One row of the searchable picker: value + display fields + search haystack extras. */
internal data class AutomationPickerOption<T>(
    val value: T,
    val label: String,
    /** Second line under the label (units, sources, A9/A10 note). */
    val supportingText: String? = null,
    /** Group header; options are shown grouped in list order. */
    val group: String? = null,
    /** Extra search words (synonyms); label, group and key are always searched. */
    val keywords: List<String> = emptyList(),
    /** Canonical storage key, searched with underscores as spaces. */
    val key: String = "",
)

/** Signal picker options: label + units/sources hint + group + RU synonyms. */
internal fun List<AutomationSignalId>.toAutomationSignalOptions():
    List<AutomationPickerOption<AutomationSignalId>> = map { id ->
    val descriptor = AutomationSignalCatalog.get(id)
    val units = descriptor.unit.ifBlank { null }
    val sources = descriptor.sources.joinToString(" / ") { source ->
        when (source) {
            AutomationSignalSource.TBOX -> "TBox"
            AutomationSignalSource.HEAD_UNIT -> "ГУ"
            AutomationSignalSource.APP -> "Приложение"
        }
    }
    val supporting = listOfNotNull(units, sources).joinToString(" · ")
    AutomationPickerOption(
        value = id,
        label = descriptor.label,
        supportingText = supporting.ifBlank { null },
        group = descriptor.group.label,
        keywords = AutomationSignalCatalog.signalKeywords(id),
        key = id.storageKey,
    )
}

/**
 * Same look as [AutomationDropdown] (caption + outlined button), but opens a dialog
 * with a search field; options are grouped by [AutomationPickerOption.group].
 */
@Composable
internal fun <T> AutomationSearchablePickerField(
    label: String,
    value: T,
    options: List<AutomationPickerOption<T>>,
    optionLabel: (T) -> String,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dialogTitle: String = label,
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                    expanded = true
                },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        menuWidth = with(density) { size.width.toDp() }
                    },
            ) {
                Text(
                    text = optionLabel(value),
                    style = MaterialTheme.typography.tboxTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            }
        }
    }
    if (expanded) {
        val keyboardController = LocalSoftwareKeyboardController.current
        AutomationSearchablePickerDialog(
            title = dialogTitle,
            options = options,
            optionLabel = optionLabel,
            onSelect = {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                expanded = false
                onValueChange(it)
            },
            onDismiss = { expanded = false },
            widthDp = menuWidth,
        )
    }
}

@Composable
internal fun <T> AutomationSearchablePickerDialog(
    title: String,
    options: List<AutomationPickerOption<T>>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    widthDp: Dp,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val filtered = remember(query, options) {
        if (AutomationPickerSearch.normalize(query).isEmpty()) {
            options
        } else {
            options.filter { option ->
                AutomationPickerSearch.matches(
                    query = query,
                    label = option.label,
                    groupLabel = option.group.orEmpty(),
                    keywords = option.keywords,
                    storageKey = option.key,
                )
            }
        }
    }
    val grouped = remember(filtered) { groupPickerOptions(filtered) }
    Dialog(
        onDismissRequest = {
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(min = widthDp.coerceAtLeast(280.dp) + 32.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.tboxTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = "Поиск…",
                            style = MaterialTheme.typography.tboxBody,
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.tboxTitle.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = "Ничего не найдено",
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        grouped.forEach { section ->
                            section.header?.let { header ->
                                item(key = "header:$header") {
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.tboxCaption,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                    )
                                }
                            }
                            items(
                                count = section.options.size,
                                key = { index ->
                                    val option = section.options[index]
                                    "option:${option.key.ifBlank { option.label }}:$index"
                                },
                            ) { index ->
                                val option = section.options[index]
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(option.value) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = optionLabel(option.value),
                                            style = MaterialTheme.typography.tboxTitle,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        option.supportingText?.let { supporting ->
                                            Text(
                                                text = supporting,
                                                style = MaterialTheme.typography.tboxCaption,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class AutomationPickerSection<T>(
    val header: String?,
    val options: List<AutomationPickerOption<T>>,
)

/** Consecutive options with the same group are joined into one titled section. */
private fun <T> groupPickerOptions(
    options: List<AutomationPickerOption<T>>,
): List<AutomationPickerSection<T>> {
    val sections = mutableListOf<AutomationPickerSection<T>>()
    var currentHeader: String? = null
    var currentBucket = mutableListOf<AutomationPickerOption<T>>()
    fun flush() {
        if (currentBucket.isNotEmpty()) {
            sections += AutomationPickerSection(currentHeader, currentBucket.toList())
            currentBucket = mutableListOf()
        }
    }
    options.forEach { option ->
        if (option.group != currentHeader) {
            flush()
            currentHeader = option.group
        }
        currentBucket += option
    }
    flush()
    return sections
}
