package vad.dashing.tbox.ui

import android.graphics.Rect
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import vad.dashing.tbox.automation.AutomationPickerSearch
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
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

/** Dialog surface vertical padding (12 dp top + 12 dp bottom). */
private val dialogVerticalPadding = 24.dp

/** Title line estimate. */
private val dialogTitleEstimate = 24.dp

/** Search field estimate. */
private val searchFieldEstimate = 64.dp

/** Signal picker options: label + units hint + group + RU synonyms. */
internal fun List<AutomationSignalId>.toAutomationSignalOptions():
    List<AutomationPickerOption<AutomationSignalId>> = map { id ->
    val descriptor = AutomationSignalCatalog.get(id)
    AutomationPickerOption(
        value = id,
        label = descriptor.label,
        supportingText = descriptor.unit.ifBlank { null },
        group = descriptor.group.label,
        keywords = AutomationSignalCatalog.signalKeywords(id),
        key = id.storageKey,
    )
}
    .sortedGroupedConsecutive()

/**
 * The picker renders consecutive options with the same group under one header, so each
 * group must be one contiguous block. Catalog ordering interleaves groups; sort stably
 * by first appearance of the group, keeping the within-group order intact.
 */
internal fun <T> List<AutomationPickerOption<T>>.sortedGroupedConsecutive():
    List<AutomationPickerOption<T>> {
    val groupOrder = LinkedHashMap<String, Int>()
    forEach { option ->
        val group = option.group ?: return@forEach
        if (group !in groupOrder) groupOrder[group] = groupOrder.size
    }
    return sortedBy { option ->
        option.group?.let { groupOrder[it] } ?: Int.MAX_VALUE
    }
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
    // Taller list: ~85% of screen height, minus dialog paddings/title/search field.
    // The IME is extra: the dialog window is not resized by default (decorFitsSystemWindows
    // true lets the system pan/resize it), so cap the list to the visible window frame,
    // which shrinks when ADJUST_RESIZE reports the keyboard.
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenCapHeight = (configuration.screenHeightDp * 0.85f).dp
    var visibleFrameHeight by remember { mutableStateOf(screenCapHeight) }
    val listMaxHeight = minOf(screenCapHeight, visibleFrameHeight) -
        (dialogVerticalPadding + dialogTitleEstimate + searchFieldEstimate)
    val view = LocalView.current
    // SOFT_INPUT_ADJUST_RESIZE is the only working IME-resize mode on the API 28 HU
    // platforms (WindowInsets.ime is always 0 there); deprecated since API 30.
    @Suppress("DEPRECATION")
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val decor = window?.decorView
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (decor != null) {
                val frame = Rect()
                decor.getWindowVisibleDisplayFrame(frame)
                if (frame.bottom > 0) {
                    visibleFrameHeight = with(density) { frame.bottom.toDp() }
                }
            }
        }
        decor?.viewTreeObserver?.addOnGlobalLayoutListener(listener)
        onDispose {
            decor?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
            @Suppress("DEPRECATION")
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED)
        }
    }
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
                modifier = Modifier
                    .padding(12.dp)
                    .imePadding(),
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
                            .heightIn(max = listMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        grouped.forEachIndexed { sectionIndex, section ->
                            section.header?.let { header ->
                                item(key = "header:$sectionIndex:$header") {
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.tboxCaption,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                    )
                                }
                            }
                            itemsIndexed(
                                items = section.options,
                                key = { index, option ->
                                    "option:$sectionIndex:$index:" +
                                        (option.key.ifBlank { option.label })
                                },
                            ) { _, option ->
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
