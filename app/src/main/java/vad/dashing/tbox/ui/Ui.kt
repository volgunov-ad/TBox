package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModelFactory
import java.text.SimpleDateFormat
import java.util.Locale
import vad.dashing.tbox.ui.theme.TboxAppTheme

data class TabItem(
    val title: String,
    val icon: ImageVector
)

@Composable
fun TboxApp(
    settingsManager: SettingsManager,
    appDataManager: AppDataManager,
    onTboxRestart: () -> Unit,
    onSaveToFile: (String, List<String>) -> Unit,
    onServiceCommand: (String, String, String) -> Unit,
    onMockLocationSettingChanged: (Boolean) -> Unit
) {
    val viewModel: TboxViewModel = viewModel()

    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(
        settingsManager
    )
    )
    val appDataViewModel: AppDataViewModel = viewModel(factory = AppDataViewModelFactory(
        appDataManager
    )
    )

    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

    TboxAppTheme(theme = currentTheme) {
        TboxScreen(
            viewModel = viewModel,
            settingsViewModel = settingsViewModel,
            appDataViewModel = appDataViewModel,
            onTboxRestart = onTboxRestart,
            onSaveToFile = onSaveToFile,
            onServiceCommand = onServiceCommand,
            onMockLocationSettingChanged = onMockLocationSettingChanged
        )
    }
}

object TabItems {
    @Composable
    fun getItems(): List<TabItem> {
        return listOf(
            TabItem(stringResource(R.string.tab_modem), ImageVector.vectorResource(R.drawable.menu_icon_modem)),
            TabItem(stringResource(R.string.tab_at_commands), ImageVector.vectorResource(R.drawable.menu_icon_at)),
            TabItem(stringResource(R.string.tab_geoposition), Icons.Filled.Place),
            TabItem(stringResource(R.string.tab_car_data), Icons.Filled.Build),
            TabItem(stringResource(R.string.tab_settings), Icons.Filled.Settings),
            TabItem(stringResource(R.string.tab_logs), ImageVector.vectorResource(R.drawable.menu_icon_log)),
            TabItem(stringResource(R.string.tab_info), Icons.Filled.Info),
            TabItem(stringResource(R.string.tab_can), ImageVector.vectorResource(R.drawable.menu_icon_data)),
            TabItem(stringResource(R.string.tab_widgets), ImageVector.vectorResource(R.drawable.menu_icon_widgets))
        )
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
    onServiceCommand: (String, String, String) -> Unit,
    onMockLocationSettingChanged: (Boolean) -> Unit,
) {
    val canViewModel: CanDataViewModel = viewModel()
    val cycleViewModel: CycleDataViewModel = viewModel()

    val selectedTab by settingsViewModel.selectedTab.collectAsStateWithLifecycle()
    val isExpertModeEnabled by settingsViewModel.isExpertModeEnabled.collectAsStateWithLifecycle()

    val tabs = TabItems.getItems()

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

    // Показываем loading пока загружается сохраненная вкладка
    if (selectedTab == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.loading), fontSize = 18.sp)
        }
        return
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            onServiceCommand(
                BackgroundService.ACTION_MODEM_CHECK,
                "",
                ""
            )
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
                                .clickable(onClick = {
                                    if (isMenuVisible) {
                                        settingsViewModel.saveLeftMenuVisibleSetting(false)
                                    } else {
                                        settingsViewModel.saveLeftMenuVisibleSetting(true)
                                    }
                                })
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.Center
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            if (isExpertModeEnabled || index !in setOf(1, 5, 7)) {
                                TabMenuItem(
                                    title = tab.title,
                                    icon = tab.icon,
                                    selected = selectedTab == index,
                                    showText = isMenuVisible,
                                    onClick = {
                                        // Сохраняем выбор вкладки через ViewModel
                                        settingsViewModel.saveSelectedTab(index)
                                    }
                                )
                            }
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
                    0 -> ModemTab(viewModel, onServiceCommand)
                    1 -> if (isExpertModeEnabled) {
                        ATcmdTab (viewModel, onServiceCommand)
                    } else {
                        ModemTab(viewModel, onServiceCommand)
                    }
                    2 -> LocationTab(viewModel)
                    3 -> CarDataTab(
                        canViewModel,
                        cycleViewModel,
                        appDataViewModel,
                        settingsViewModel)
                    4 -> SettingsTab(
                        viewModel,
                        settingsViewModel,
                        onTboxRestart,
                        onMockLocationSettingChanged,
                        onServiceCommand)
                    5 -> if (isExpertModeEnabled) {
                        LogsTab(viewModel, settingsViewModel, onSaveToFile)
                    } else {
                        ModemTab(viewModel, onServiceCommand)
                    }
                    6 -> InfoTab(viewModel, settingsViewModel, onServiceCommand)
                    7 -> if (isExpertModeEnabled) {
                        CanTab(viewModel, canViewModel, onSaveToFile)
                    } else {
                        ModemTab(viewModel, onServiceCommand)
                    }
                    8 -> MainDashboardTab(
                        viewModel,
                        canViewModel,
                        settingsViewModel,
                        appDataViewModel,
                        onTboxRestart)
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
fun SettingsTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onTboxRestartClick: () -> Unit,
    onMockLocationSettingChanged: (Boolean) -> Unit,
    onServiceCommand: (String, String, String) -> Unit,
) {
    SettingsTabContent(
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        onTboxRestartClick = onTboxRestartClick,
        onMockLocationSettingChanged = onMockLocationSettingChanged,
        onServiceCommand = onServiceCommand
    )
}

@Composable
fun LocationTab(
    viewModel: TboxViewModel
) {
    LocationTabContent(viewModel = viewModel)
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