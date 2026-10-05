package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.ui.theme.TboxAppTheme
import vad.dashing.tbox.ui.theme.tboxTabLabel

/**
 * Horizontal section tabs styled like [TabMenuItem] in the left sidebar:
 * solid primary fill for the selected tab, background color for others.
 */
@Composable
fun HorizontalSectionTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showBottomDivider: Boolean = true,
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            tabs.forEachIndexed { index, title ->
                HorizontalSectionTabItem(
                    title = title,
                    selected = selectedIndex == index,
                    onClick = { onTabSelected(index) },
                )
            }
        }
        if (showBottomDivider) {
            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun HorizontalSectionTabItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
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
            .clickableWithSound(onClick = onClick)
            .background(backgroundColor)
            .padding(vertical = 16.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = textColor,
            style = MaterialTheme.typography.tboxTabLabel.copy(
                lineHeight = MaterialTheme.typography.tboxTabLabel.fontSize * 1.1f,
            ),
        )
    }
}

@Composable
internal fun HorizontalSectionTabRowGeopositionPreviewContent(
    selectedIndex: Int = 0,
) {
    HorizontalSectionTabRow(
        tabs = listOf("Общие", "Подмена", "Данные и отладка"),
        selectedIndex = selectedIndex,
        onTabSelected = {},
    )
}

@Composable
internal fun HorizontalSectionTabRowCarSettingsPreviewContent(
    selectedIndex: Int = 2,
) {
    HorizontalSectionTabRow(
        tabs = listOf(
            "Аудио",
            "Шасси",
            "Помощь водителю",
            "Замки",
            "Свет",
            "Дворники и зеркала",
            "Окна",
            "Климат доп.",
            "Экраны",
        ),
        selectedIndex = selectedIndex,
        onTabSelected = {},
    )
}

@Preview(name = "Geoposition — light", widthDp = 1280, heightDp = 120, showBackground = true)
@Composable
private fun HorizontalSectionTabRowGeopositionLightPreview() {
    TboxAppTheme(theme = 1) {
        HorizontalSectionTabRowGeopositionPreviewContent(selectedIndex = 0)
    }
}

@Preview(name = "Geoposition — dark", widthDp = 1280, heightDp = 120, showBackground = true)
@Composable
private fun HorizontalSectionTabRowGeopositionDarkPreview() {
    TboxAppTheme(theme = 2) {
        HorizontalSectionTabRowGeopositionPreviewContent(selectedIndex = 1)
    }
}

@Preview(name = "Car settings — light", widthDp = 1280, heightDp = 120, showBackground = true)
@Composable
private fun HorizontalSectionTabRowCarSettingsLightPreview() {
    TboxAppTheme(theme = 1) {
        HorizontalSectionTabRowCarSettingsPreviewContent(selectedIndex = 2)
    }
}

@Preview(name = "Car settings — dark", widthDp = 1280, heightDp = 120, showBackground = true)
@Composable
private fun HorizontalSectionTabRowCarSettingsDarkPreview() {
    TboxAppTheme(theme = 2) {
        HorizontalSectionTabRowCarSettingsPreviewContent(selectedIndex = 2)
    }
}
