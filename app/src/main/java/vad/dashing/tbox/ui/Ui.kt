package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.AppDataViewModelFactory
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.CycleDataViewModel
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModelFactory
import java.text.SimpleDateFormat
import java.util.Locale
import vad.dashing.tbox.ThemeOpenRequestBus
import vad.dashing.tbox.ui.theme.TboxAppTheme

@Composable
fun TboxApp(
    settingsManager: SettingsManager,
    appDataManager: AppDataManager,
    onTboxRestart: () -> Unit,
    onSaveToFile: (String, List<String>) -> Unit,
    onExportSettingsBackup: () -> Unit,
    onExportSettingsBackupWithoutTrips: () -> Unit,
    onImportSettingsBackup: () -> Unit,
    onServiceCommand: (String, String, String) -> Unit,
    onMockLocationSettingChanged: (Boolean) -> Unit,
    onTripFinishAndStart: () -> Unit,
    onRequestWallpaperStorageAccess: ((() -> Unit) -> Unit)? = null,
) {
    val viewModel: TboxViewModel = viewModel()
    val canViewModel: CanDataViewModel = viewModel()

    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(
        settingsManager
    )
    )
    val appDataViewModel: AppDataViewModel = viewModel(factory = AppDataViewModelFactory(
        appDataManager,
        settingsManager,
    )
    )

    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val selectedTab by settingsViewModel.selectedTab.collectAsStateWithLifecycle()
    val leftMenuLayout by settingsViewModel.leftMenuLayout.collectAsStateWithLifecycle()
    val uiClickSoundsEnabled by settingsViewModel.uiClickSoundsEnabled.collectAsStateWithLifecycle()
    val pendingThemeOpen by ThemeOpenRequestBus.pending.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        settingsViewModel.validateThemeSettings(context)
    }

    TboxAppTheme(theme = currentTheme) {
        CompositionLocalProvider(LocalClickSoundEnabled provides uiClickSoundsEnabled) {
        if (selectedTab == SettingsManager.MAIN_SCREEN_TAB_KEY) {
            MainScreen(
                tboxViewModel = viewModel,
                canViewModel = canViewModel,
                appDataViewModel = appDataViewModel,
                settingsViewModel = settingsViewModel,
                onOpenConsole = {
                    settingsViewModel.saveSelectedTab(
                        LeftMenuLayout.firstVisibleTabKey(leftMenuLayout),
                    )
                },
                onTboxRestart = onTboxRestart,
                onTripFinishAndStart = onTripFinishAndStart,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            TboxScreen(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                appDataViewModel = appDataViewModel,
                onTboxRestart = onTboxRestart,
                onSaveToFile = onSaveToFile,
                onExportSettingsBackup = onExportSettingsBackup,
                onExportSettingsBackupWithoutTrips = onExportSettingsBackupWithoutTrips,
                onImportSettingsBackup = onImportSettingsBackup,
                onServiceCommand = onServiceCommand,
                onMockLocationSettingChanged = onMockLocationSettingChanged,
                onTripFinishAndStart = onTripFinishAndStart,
                onRequestWallpaperStorageAccess = onRequestWallpaperStorageAccess,
            )
        }
        pendingThemeOpen?.let { request ->
            ThemeOpenConfirmDialog(
                request = request,
                settingsViewModel = settingsViewModel,
                onDismiss = { ThemeOpenRequestBus.clear() },
            )
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TboxScreen(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    appDataViewModel: AppDataViewModel,
    onTboxRestart: () -> Unit,
    onSaveToFile: (String, List<String>) -> Unit,
    onExportSettingsBackup: () -> Unit,
    onExportSettingsBackupWithoutTrips: () -> Unit,
    onImportSettingsBackup: () -> Unit,
    onServiceCommand: (String, String, String) -> Unit,
    onMockLocationSettingChanged: (Boolean) -> Unit,
    onTripFinishAndStart: () -> Unit,
    onRequestWallpaperStorageAccess: ((() -> Unit) -> Unit)? = null,
) {
    val canViewModel: CanDataViewModel = viewModel()
    val cycleViewModel: CycleDataViewModel = viewModel()

    val selectedTab by settingsViewModel.selectedTab.collectAsStateWithLifecycle()
    val leftMenuLayout by settingsViewModel.leftMenuLayout.collectAsStateWithLifecycle()

    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()
    val tboxConnectionTime by viewModel.tboxConnectionTime.collectAsStateWithLifecycle()
    val serviceStartTime by viewModel.serviceStartTime.collectAsStateWithLifecycle()
    val isMenuVisible by settingsViewModel.isLeftMenuVisible.collectAsStateWithLifecycle()

    // Используем remember для форматтеров даты
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val conTime = remember(tboxConnectionTime) { timeFormat.format(tboxConnectionTime) }
    val serviceTime = remember(serviceStartTime) { timeFormat.format(serviceStartTime) }

    val context = LocalContext.current
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionName = remember { packageInfo.versionName.orEmpty() }

    val scrollState = rememberScrollState()

    val menuIconSize = 28.dp
    val menuButtonSize = 64.dp

    LaunchedEffect(selectedTab, leftMenuLayout) {
        if (selectedTab != SettingsManager.MAIN_SCREEN_TAB_KEY &&
            !LeftMenuLayout.isSidebarTabEnabled(selectedTab, leftMenuLayout)
        ) {
            settingsViewModel.saveSelectedTab(LeftMenuLayout.firstVisibleTabKey(leftMenuLayout))
            return@LaunchedEffect
        }
        when (selectedTab) {
            LeftMenuTabField.MODEM.id -> onServiceCommand(
                BackgroundService.ACTION_MODEM_CHECK,
                "",
                "",
            )
            LeftMenuTabField.SETTINGS.id -> settingsViewModel.onSettingsTabSelected()
            LeftMenuTabField.MAIN_SCREEN_SETTINGS.id ->
                settingsViewModel.onMainScreenSettingsTabSelected()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(if (isMenuVisible) 300.dp else menuButtonSize)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = 16.dp,
                                horizontal = 8.dp
                            ),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            imageVector = if (isMenuVisible) ImageVector.vectorResource(R.drawable.menu_icon_close) else ImageVector.vectorResource(R.drawable.menu_icon_open),
                            contentDescription = if (isMenuVisible) stringResource(R.string.menu_hide) else stringResource(R.string.menu_show),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .size(menuIconSize)
                                .clickableWithSound {
                                    if (isMenuVisible) {
                                        settingsViewModel.saveLeftMenuVisibleSetting(false)
                                    } else {
                                        settingsViewModel.saveLeftMenuVisibleSetting(true)
                                    }
                                }
                        )
                    }

                    TabMenuItem(
                        title = stringResource(R.string.menu_navigate_home),
                        icon = ImageVector.vectorResource(R.drawable.ic_menu_home),
                        selected = false,
                        showText = isMenuVisible,
                        onClick = {
                            settingsViewModel.saveSelectedTab(SettingsManager.MAIN_SCREEN_TAB_KEY)
                        }
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.Center
                    ) {
                        for (row in leftMenuLayout.rows) {
                            if (!row.enabled) continue
                            val field = row.field
                            TabMenuItem(
                                title = stringResource(field.labelRes),
                                icon = field.menuIcon(),
                                selected = selectedTab == field.id,
                                showText = isMenuVisible,
                                onClick = {
                                    settingsViewModel.saveSelectedTab(field.id)
                                }
                            )
                        }
                    }

                    if (isMenuVisible) {
                        Text(
                            text = if (tboxConnected) {
                                stringResource(R.string.tbox_connected_at, conTime)
                            } else {
                                stringResource(R.string.tbox_disconnected_at, conTime)
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (tboxConnected) Color(0xFF4CAF50) else Color(0xFFFF0000),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.service_started_at, serviceTime),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = 8.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.tbox_short),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (tboxConnected) Color(0xFF4CAF50) else Color(0xFFFF0000),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                        )
                    }

                    if (isMenuVisible) {
                        Text(
                            text = stringResource(R.string.program_version, versionName),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.tbox_proxy_version,
                                BuildConfig.TBOX_PROXY_VERSION
                            ),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }


            }

            // Содержимое справа
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (selectedTab) {
                    LeftMenuTabField.MODEM.id -> ModemTab(viewModel, onServiceCommand)
                    LeftMenuTabField.AT_COMMANDS.id -> ATcmdTab(viewModel, onServiceCommand)
                    LeftMenuTabField.GEOPOSITION.id -> LocationTab(viewModel, onServiceCommand)
                    LeftMenuTabField.CAR_DATA.id -> CarDataTab(
                        canViewModel,
                        cycleViewModel,
                        appDataViewModel,
                        settingsViewModel,
                    )
                    LeftMenuTabField.TRIPS.id -> TripsTab(
                        appDataViewModel = appDataViewModel,
                        settingsViewModel = settingsViewModel,
                        onTripFinishAndStart = onTripFinishAndStart,
                        onSaveToFile = onSaveToFile,
                    )
                    LeftMenuTabField.REFUELS.id -> RefuelsTab(
                        appDataViewModel = appDataViewModel,
                        settingsViewModel = settingsViewModel,
                        onSaveToFile = onSaveToFile,
                        onServiceCommand = onServiceCommand,
                    )
                    LeftMenuTabField.SETTINGS.id -> SettingsTab(
                        viewModel,
                        settingsViewModel,
                        appDataViewModel,
                        onTboxRestart,
                        onMockLocationSettingChanged,
                        onServiceCommand,
                        onExportSettingsBackup = onExportSettingsBackup,
                        onExportSettingsBackupWithoutTrips = onExportSettingsBackupWithoutTrips,
                        onImportSettingsBackup = onImportSettingsBackup,
                    )
                    LeftMenuTabField.FLOATING_PANELS_SETTINGS.id -> FloatingPanelsSettingsTab(
                        settingsViewModel = settingsViewModel,
                    )
                    LeftMenuTabField.THEMES.id -> ThemesTab(
                        settingsViewModel = settingsViewModel,
                        onRequestStorageAccess = onRequestWallpaperStorageAccess,
                    )
                    LeftMenuTabField.LOGS.id -> LogsTab(viewModel, settingsViewModel, onSaveToFile)
                    LeftMenuTabField.INFO.id -> InfoTab(viewModel, settingsViewModel, onServiceCommand)
                    LeftMenuTabField.CAN.id -> CanTab(viewModel, canViewModel, onSaveToFile)
                    LeftMenuTabField.WIDGETS.id -> MainDashboardTab(
                        viewModel,
                        canViewModel,
                        settingsViewModel,
                        appDataViewModel,
                        onTboxRestart,
                        onTripFinishAndStart = onTripFinishAndStart,
                    )
                    LeftMenuTabField.MAIN_SCREEN_SETTINGS.id -> MainScreenSettingsTab(
                        settingsViewModel = settingsViewModel,
                        onRequestWallpaperStorageAccess = onRequestWallpaperStorageAccess,
                    )
                    LeftMenuTabField.CAR_SETTINGS.id -> CarSettingsTab()
                    else -> ModemTab(viewModel, onServiceCommand)
                }
            }
        }
    }
}

@Composable
fun ModemTab(
    viewModel: TboxViewModel,
    onServiceCommand: (String, String, String) -> Unit,
) {
    ModemTabContent(
        viewModel = viewModel,
        onServiceCommand = onServiceCommand
    )
}

@Composable
fun ModemModeSelector(
    selectedMode: Int,
    onServiceCommand: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModemModeSelectorContent(
        selectedMode = selectedMode,
        onServiceCommand = onServiceCommand,
        modifier = modifier
    )
}

@Composable
fun ThemesTab(
    settingsViewModel: SettingsViewModel,
    onRequestStorageAccess: ((() -> Unit) -> Unit)? = null,
) {
    ThemesTabContent(
        settingsViewModel = settingsViewModel,
        onRequestStorageAccess = onRequestStorageAccess,
    )
}

@Composable
fun FloatingPanelsSettingsTab(
    settingsViewModel: SettingsViewModel,
) {
    FloatingPanelsSettingsTabContent(settingsViewModel = settingsViewModel)
}

@Composable
fun SettingsTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    appDataViewModel: AppDataViewModel,
    onTboxRestartClick: () -> Unit,
    onMockLocationSettingChanged: (Boolean) -> Unit,
    onServiceCommand: (String, String, String) -> Unit,
    onExportSettingsBackup: () -> Unit,
    onExportSettingsBackupWithoutTrips: () -> Unit,
    onImportSettingsBackup: () -> Unit,
) {
    SettingsTabContent(
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        appDataViewModel = appDataViewModel,
        onTboxRestartClick = onTboxRestartClick,
        onMockLocationSettingChanged = onMockLocationSettingChanged,
        onServiceCommand = onServiceCommand,
        onExportSettingsBackup = onExportSettingsBackup,
        onExportSettingsBackupWithoutTrips = onExportSettingsBackupWithoutTrips,
        onImportSettingsBackup = onImportSettingsBackup,
    )
}

@Composable
fun LocationTab(
    viewModel: TboxViewModel,
    onServiceCommand: (String, String, String) -> Unit,
) {
    LocationTabContent(
        viewModel = viewModel,
        onServiceCommand = onServiceCommand,
    )
}

@Composable
fun InfoTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onServiceCommand: (String, String, String) -> Unit
) {
    InfoTabContent(
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        onServiceCommand = onServiceCommand
    )
}

@Composable
fun CarDataTab(
    canViewModel: CanDataViewModel,
    cycleViewModel: CycleDataViewModel,
    appDataViewModel: AppDataViewModel,
    settingsViewModel: SettingsViewModel,
) {
    CarDataTabContent(
        canViewModel = canViewModel,
        cycleViewModel = cycleViewModel,
        appDataViewModel = appDataViewModel,
        settingsViewModel = settingsViewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onSaveToFile: (String, List<String>) -> Unit
) {
    LogsTabContent(
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        onSaveToFile = onSaveToFile
    )
}

@Composable
fun CanTab(
    viewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    onSaveToFile: (String, List<String>) -> Unit
) {
    CanTabContent(
        viewModel = viewModel,
        canViewModel = canViewModel,
        onSaveToFile = onSaveToFile
    )
}

@Composable
fun ATcmdTab(
    viewModel: TboxViewModel,
    onServiceCommand: (String, String, String) -> Unit
) {
    ATcmdTabContent(
        viewModel = viewModel,
        onServiceCommand = onServiceCommand
    )
}