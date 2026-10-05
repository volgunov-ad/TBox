package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.MainScreenPageSelectorLogic
import vad.dashing.tbox.PagingStateNormalizer
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.freeform.FreeformCompanionSession

/**
 * Multi-button page picker for the main screen: one control per page (1..pageCount),
 * horizontal or vertical, with active/inactive control colors.
 */
@Composable
fun DashboardMainScreenPageSelectorWidgetItem(
    isVertical: Boolean,
    settingsViewModel: SettingsViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enableInnerInteractions: Boolean,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = true,
    titleOverride: String = "",
) {
    val pageCountRaw by settingsViewModel.mainScreenPageCount.collectAsStateWithLifecycle()
    val normalCurrentPage by settingsViewModel.mainScreenCurrentPage.collectAsStateWithLifecycle()
    val windowModeCurrentPage by
        settingsViewModel.mainScreenWindowModeCurrentPage.collectAsStateWithLifecycle()
    val freeformSession by FreeformCompanionSession.state.collectAsStateWithLifecycle()

    val pageCount = PagingStateNormalizer.normalizePageCount(pageCountRaw)
    val windowModeActive = freeformSession != null
    val pinnedOverlayPage = freeformSession?.pinnedOverlayPage
    val currentPage = MainScreenPageSelectorLogic.resolveDisplayedPage(
        windowModeActive = windowModeActive,
        pinnedOverlayPage = pinnedOverlayPage,
        windowModeCurrentPage = windowModeCurrentPage,
        normalCurrentPage = normalCurrentPage,
        pageCount = pageCount,
    )

    val defaultTitle = stringResource(R.string.data_title_main_screen_page_selector_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val pages = (1..pageCount).toList()
    val controls = LocalWidgetControlAppearance.current

    fun selectPage(page: Int) {
        val plan = MainScreenPageSelectorLogic.planSelect(
            targetPage = page,
            windowModeActive = windowModeActive,
            pinnedOverlayPage = pinnedOverlayPage,
            windowModeCurrentPage = windowModeCurrentPage,
            normalCurrentPage = normalCurrentPage,
            pageCount = pageCount,
        )
        if (!plan.shouldApply) return
        if (plan.clearPinnedOverlayPage) {
            FreeformCompanionSession.clearPinnedOverlayPage()
        }
        settingsViewModel.scheduleSaveMainScreenCurrentPage(plan.page, plan.windowMode)
    }

    DashboardWidgetScaffold(
        onClick = if (enableInnerInteractions) {
            {}
        } else {
            onClick
        },
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
            modifier = Modifier
                .fillMaxSize()
                .widgetControlOuterPadding(controls),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { contentModifier ->
            if (isVertical) {
                Column(
                    modifier = contentModifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    pages.forEach { page ->
                        MainScreenPageSelectorButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            page = page,
                            selected = page == currentPage,
                            availableHeight = availableHeight,
                            enabled = enableInnerInteractions,
                            onClick = { selectPage(page) },
                            onLongClick = onLongClick,
                        )
                    }
                }
            } else {
                Row(
                    modifier = contentModifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    pages.forEach { page ->
                        MainScreenPageSelectorButton(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f),
                            page = page,
                            selected = page == currentPage,
                            availableHeight = availableHeight,
                            enabled = enableInnerInteractions,
                            onClick = { selectPage(page) },
                            onLongClick = onLongClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScreenPageSelectorButton(
    modifier: Modifier,
    page: Int,
    selected: Boolean,
    availableHeight: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val controls = LocalWidgetControlAppearance.current
    val labelColor = if (selected) controls.activeContent else controls.inactiveContent
    WidgetControlChrome(
        background = if (selected) controls.activeBackground else controls.inactiveBackground,
        shapeDp = controls.shapeDp,
        modifier = modifier.combinedClickableWithSound(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = page.toString(),
                color = labelColor,
                style = calculateResponsiveTextStyle(
                    containerHeight = availableHeight,
                    textType = TextType.VALUE,
                ),
                textAlign = LocalWidgetTextAlign.current,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
