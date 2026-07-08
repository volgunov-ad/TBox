package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.resolveDriveModeWidgetOption
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.rememberLaunchableAppEntries
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.valueToString

private sealed class HomeDockEntry(val key: String) {
    data class App(val entry: LaunchableAppEntry, val index: Int) : HomeDockEntry("app_${entry.packageName}_$index")
    data class Split(val preset: LauncherSplitPreset, val index: Int) : HomeDockEntry("split_${preset.id}_$index")
    data object Add : HomeDockEntry("add")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherRightPanel(
    canViewModel: CanDataViewModel,
    settingsViewModel: SettingsViewModel,
    tboxViewModel: TboxViewModel,
    onOpenConsole: () -> Unit,
    onOpenApps: () -> Unit,
    configRevision: Int = 0,
    onConfigChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val odometer by canViewModel.odometer.collectAsStateWithLifecycle()
    val outsideTemp by canViewModel.outsideTemperature.collectAsStateWithLifecycle()
    val insideTemp by canViewModel.insideTemperature.collectAsStateWithLifecycle()
    val distanceToFuelEmpty by canViewModel.distanceToFuelEmpty.collectAsStateWithLifecycle()
    val driveModeRaw by UniversalCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val rawApps = rememberLaunchableAppEntries(settingsViewModel, iconRevision)
    val priority = remember(context) { LauncherOemAppSort.loadPriorityPackages(context) }
    val hidden = remember(context, configRevision) { LauncherAppConfigStore.hiddenPackages(context) }
    val homeItems = remember(context, configRevision) { LauncherHomeStore.loadItems(context) }
    val splitPresets = remember(context, configRevision) { LauncherSplitPresetStore.loadPresets(context) }
    val visibleApps = remember(rawApps, hidden) { LauncherAppConfigStore.filterVisible(rawApps, hidden) }
    val pickerApps = remember(visibleApps, priority) { LauncherOemAppSort.sortEntries(visibleApps, priority) }
    val appsByPackage = remember(visibleApps) { visibleApps.associateBy { it.packageName } }

    var addMenuVisible by remember { mutableStateOf(false) }
    var appPickerVisible by remember { mutableStateOf(false) }
    var splitCreateVisible by remember { mutableStateOf(false) }
    var splitEditPreset by remember { mutableStateOf<LauncherSplitPreset?>(null) }
    var contextMenuIndex by remember { mutableIntStateOf(-1) }
    var replaceIndex by remember { mutableIntStateOf(-1) }

    val anyLocalDialog = addMenuVisible || appPickerVisible || splitCreateVisible || contextMenuIndex >= 0 || replaceIndex >= 0
    LaunchedEffect(anyLocalDialog) {
        LauncherOverlayElevator.setHoldSource("right_panel_dialog", anyLocalDialog)
    }

    val timeText = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
    val dateText = remember {
        SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
    }
    val driveLabel = driveModeRaw?.let { resolveDriveModeWidgetOption(it).label }.orEmpty()
    val cabinTemp = insideTemp ?: outsideTemp

    val dockEntries = remember(homeItems, splitPresets, appsByPackage) {
        buildList {
            homeItems.forEachIndexed { index, item ->
                when (item) {
                    is LauncherHomeItem.App -> appsByPackage[item.packageName]?.let { add(HomeDockEntry.App(it, index)) }
                    is LauncherHomeItem.Split -> splitPresets.firstOrNull { it.id == item.presetId }
                        ?.let { add(HomeDockEntry.Split(it, index)) }
                }
            }
            add(HomeDockEntry.Add)
        }
    }

    LauncherAppPickerDialog(
        visible = appPickerVisible || replaceIndex >= 0,
        title = stringResource(R.string.launcher_grid_pick_app),
        apps = pickerApps,
        onDismiss = {
            appPickerVisible = false
            replaceIndex = -1
        },
        onPick = { app ->
            if (replaceIndex >= 0) {
                LauncherHomeStore.replaceAt(context, replaceIndex, LauncherHomeItem.App(app.packageName))
            } else {
                LauncherHomeStore.addApp(context, app.packageName)
            }
            onConfigChanged()
            appPickerVisible = false
            replaceIndex = -1
        },
    )

    LauncherSplitPresetCreateDialog(
        visible = splitCreateVisible,
        apps = pickerApps,
        initialPreset = splitEditPreset,
        pinToHomeOnSave = splitEditPreset == null,
        onDismiss = {
            splitCreateVisible = false
            splitEditPreset = null
        },
        onSaved = onConfigChanged,
    )

    if (addMenuVisible) {
        AlertDialog(
            onDismissRequest = { addMenuVisible = false },
            title = { Text(stringResource(R.string.launcher_home_add_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.launcher_home_add_app),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                addMenuVisible = false
                                appPickerVisible = true
                            }
                            .padding(12.dp),
                        color = LauncherColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.launcher_home_add_split),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                addMenuVisible = false
                                splitEditPreset = null
                                splitCreateVisible = true
                            }
                            .padding(12.dp),
                        color = LauncherColors.TextPrimary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { addMenuVisible = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (contextMenuIndex >= 0) {
        val item = homeItems.getOrNull(contextMenuIndex)
        AlertDialog(
            onDismissRequest = { contextMenuIndex = -1 },
            title = { Text(stringResource(R.string.launcher_icon_menu_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item is LauncherHomeItem.App) {
                        Text(
                            text = stringResource(R.string.launcher_icon_menu_configure),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    replaceIndex = contextMenuIndex
                                    contextMenuIndex = -1
                                }
                                .padding(12.dp),
                            color = LauncherColors.AccentCyan,
                        )
                        Text(
                            text = stringResource(R.string.launcher_icon_menu_hide),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LauncherAppConfigStore.hidePackage(context, item.packageName)
                                    LauncherHomeStore.removeAt(context, contextMenuIndex)
                                    onConfigChanged()
                                    contextMenuIndex = -1
                                }
                                .padding(12.dp),
                            color = LauncherColors.TextPrimary,
                        )
                    }
                    if (item is LauncherHomeItem.Split) {
                        Text(
                            text = stringResource(R.string.launcher_icon_menu_configure),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    contextMenuIndex = -1
                                    splitEditPreset = splitPresets.firstOrNull { it.id == item.presetId }
                                    splitCreateVisible = true
                                }
                                .padding(12.dp),
                            color = LauncherColors.AccentCyan,
                        )
                    }
                    Text(
                        text = stringResource(R.string.launcher_icon_menu_remove),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LauncherHomeStore.removeAt(context, contextMenuIndex)
                                onConfigChanged()
                                contextMenuIndex = -1
                            }
                            .padding(12.dp),
                        color = LauncherColors.TextSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { contextMenuIndex = -1 }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LauncherColors.CanvasDark)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInWindow()
                    LauncherEmbeddedBoundsState.topHeaderBottomPx = rect.bottom.toInt()
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_vehicle_name),
                color = LauncherColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LauncherHeaderStatusIcons(tboxViewModel = tboxViewModel)
                Text(
                    text = "$timeText  ·  $dateText",
                    color = LauncherColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInWindow()
                    LauncherEmbeddedBoundsState.embeddedZoneBounds = android.graphics.Rect(
                        rect.left.toInt(),
                        rect.top.toInt(),
                        rect.right.toInt(),
                        rect.bottom.toInt(),
                    )
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LauncherMetricCard(
                        label = stringResource(R.string.data_title_odometer),
                        value = odometer?.let { "${valueToString(it, 0)} ${stringResource(R.string.unit_km)}" } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    LauncherMetricCard(
                        label = stringResource(R.string.launcher_metric_range),
                        value = distanceToFuelEmpty?.let { "${valueToString(it, 0)} ${stringResource(R.string.unit_km)}" } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    LauncherMetricCard(
                        label = stringResource(R.string.launcher_metric_cabin),
                        value = cabinTemp?.let { "${valueToString(it, 1)}°" } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    LauncherMetricCard(
                        label = stringResource(R.string.launcher_drive_mode_label),
                        value = driveLabel.ifBlank { "—" },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(60.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { coordinates ->
                            val rect = coordinates.boundsInWindow()
                            LauncherEmbeddedBoundsState.dockGridTopPx = rect.top.toInt()
                        },
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(dockEntries, key = { _, entry -> entry.key }) { _, entry ->
                        when (entry) {
                            is HomeDockEntry.Add -> LauncherAddIcon(onClick = {
                                LauncherOverlayElevator.bringLauncherToFront(context)
                                addMenuVisible = true
                            })
                            is HomeDockEntry.App -> LauncherAppDockIcon(
                                app = entry.entry,
                                onClick = {
                                    launchLauncherAppEmbedded(context, entry.entry.packageName, entry.entry.activityName)
                                },
                                onLongClick = { contextMenuIndex = entry.index },
                            )
                            is HomeDockEntry.Split -> LauncherSplitDockIcon(
                                preset = entry.preset,
                                leftApp = appsByPackage[entry.preset.leftPackage],
                                rightApp = appsByPackage[entry.preset.rightPackage],
                                onClick = { launchSplitPreset(context, entry.preset, visibleApps) },
                                onLongClick = { contextMenuIndex = entry.index },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInWindow()
                    LauncherEmbeddedBoundsState.rightFooterBottomPx = rect.bottom.toInt()
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "v${vad.dashing.tbox.BuildConfig.VERSION_NAME}",
                color = LauncherColors.TextMuted,
                fontSize = 11.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LauncherFooterPill(
                    label = stringResource(R.string.launcher_footer_apps),
                    onClick = onOpenApps,
                )
                LauncherFooterPill(
                    label = stringResource(R.string.action_configure),
                    onClick = onOpenConsole,
                )
            }
        }
    }
}

@Composable
private fun LauncherAddIcon(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.CardDark.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            Icons.Filled.Add,
            contentDescription = stringResource(R.string.launcher_home_add_title),
            tint = LauncherColors.AccentCyan,
            modifier = Modifier.size(25.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherAppDockIcon(
    app: LaunchableAppEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LauncherColors.CardDarkElevated)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size(39.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = app.label.take(1).uppercase(),
                color = LauncherColors.AccentCyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherSplitDockIcon(
    preset: LauncherSplitPreset,
    leftApp: LaunchableAppEntry?,
    rightApp: LaunchableAppEntry?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LauncherColors.CardDarkElevated)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .align(Alignment.CenterStart)
                .offset(x = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            LauncherMiniIcon(app = leftApp, fallback = preset.leftPackage.substringAfterLast('.'))
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .align(Alignment.CenterEnd)
                .offset(x = (-7).dp),
            contentAlignment = Alignment.Center,
        ) {
            LauncherMiniIcon(app = rightApp, fallback = preset.rightPackage.substringAfterLast('.'))
        }
        Text(
            text = "‖",
            modifier = Modifier.align(Alignment.Center),
            color = LauncherColors.AccentCyan.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun LauncherMiniIcon(app: LaunchableAppEntry?, fallback: String) {
    if (app?.icon != null) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(28.dp),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(
            text = (app?.label ?: fallback).take(1).uppercase(),
            color = LauncherColors.AccentCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LauncherMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(LauncherColors.CardDark)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.tboxCaption,
            color = LauncherColors.TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LauncherFooterPill(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(LauncherColors.CardDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        color = LauncherColors.TextPrimary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
    )
}
