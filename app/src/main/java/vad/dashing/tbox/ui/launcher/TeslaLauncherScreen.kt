package vad.dashing.tbox.ui.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SettingsViewModelFactory
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.ui.theme.TboxAppTheme

@Composable
fun TeslaLauncherScreen(
    settingsManager: SettingsManager,
    appDataManager: AppDataManager,
    onTboxRestart: () -> Unit,
    onTripFinishAndStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tboxViewModel: TboxViewModel = viewModel()
    val canViewModel: CanDataViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(settingsManager),
    )

    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
    var appDrawerVisible by remember { mutableStateOf(false) }
    var vehicleSettingsVisible by remember { mutableStateOf(false) }
    var drawerEditMode by remember { mutableStateOf(false) }
    var configRevision by remember { mutableIntStateOf(0) }
    var paintRevision by remember { mutableIntStateOf(0) }
    var carRig by remember { mutableStateOf(LauncherCarRigState()) }
    var carPaintId by remember(context) { mutableStateOf(LauncherAppConfigStore.carPaintId(context)) }
    var colorPickerVisible by remember { mutableStateOf(false) }
    var settingsUserYaw by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        LauncherVehicleBodyRepository.ensurePolling()
        LauncherAdasRepository.ensureActive()
        onDispose {
            LauncherVehicleBodyRepository.stopPolling()
            LauncherAdasRepository.stop()
        }
    }

    val sidebarWidth = rememberLauncherSidebarWidth()
    val isDraggingApp = LauncherDropTargetState.draggingPackage != null
    val settingsTransitionProgress by animateFloatAsState(
        targetValue = if (vehicleSettingsVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 430f),
        label = "settingsCarTransition",
    )

    val openConsole: () -> Unit = {
        launchTBoxSettings(context)
    }

    val onConfigChanged: () -> Unit = { configRevision++ }
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()

    val openVehicleSettings: () -> Unit = {
        LauncherOverlayElevator.bringLauncherToFront(context)
        LauncherOverlayElevator.setHoldSource("main_overlay", true)
        vehicleSettingsVisible = true
    }
    val openAppDrawer: () -> Unit = {
        LauncherOverlayElevator.bringLauncherToFront(context)
        LauncherOverlayElevator.setHoldSource("main_overlay", true)
        appDrawerVisible = true
    }

    LaunchedEffect(appDrawerVisible, vehicleSettingsVisible) {
        LauncherOverlayElevator.setHoldSource(
            "main_overlay",
            appDrawerVisible || vehicleSettingsVisible,
        )
    }

    TboxAppTheme(theme = currentTheme) {
        BackHandler(vehicleSettingsVisible) { vehicleSettingsVisible = false }
        BackHandler(appDrawerVisible) {
            if (drawerEditMode) {
                drawerEditMode = false
            } else {
                appDrawerVisible = false
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LauncherDevScaleProvider {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .background(LauncherColors.CanvasDark)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                LauncherEmbeddedBoundsState.contentRowBounds = android.graphics.Rect(
                    rect.left.toInt(),
                    rect.top.toInt(),
                    rect.right.toInt(),
                    rect.bottom.toInt(),
                )
            },
                    ) {
                        LauncherLeftPanel(
                            tboxViewModel = tboxViewModel,
                            canViewModel = canViewModel,
                            onOpenVehicleSettings = openVehicleSettings,
                            modelRevision = 0,
                            paintId = carPaintId,
                            paintRevision = paintRevision,
                            onCarBoundsChanged = {},
                            colorPickerVisible = colorPickerVisible,
                            roadVisible = !vehicleSettingsVisible && settingsTransitionProgress < 0.22f,
                            carHidden = vehicleSettingsVisible,
                            onColorPickerOpen = { colorPickerVisible = true },
                            onColorPickerDismiss = { colorPickerVisible = false },
                            onPaintChanged = { id ->
                                carPaintId = id
                                paintRevision++
                            },
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight(),
                        )
                        LauncherRightPanel(
                            canViewModel = canViewModel,
                            settingsViewModel = settingsViewModel,
                            tboxViewModel = tboxViewModel,
                            onOpenConsole = openConsole,
                            onOpenApps = openAppDrawer,
                            configRevision = configRevision,
                            onConfigChanged = onConfigChanged,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    LauncherBottomBar(
                        canViewModel = canViewModel,
                        onOpenApps = openAppDrawer,
                        vehicleSettingsOpen = vehicleSettingsVisible,
                        appDrawerOpen = appDrawerVisible,
                        onCloseVehicleSettings = { vehicleSettingsVisible = false },
                        onCloseAppDrawer = { appDrawerVisible = false },
                        configRevision = configRevision,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(80f),
            ) {
            LauncherVehicleSettingsOverlay(
                visible = vehicleSettingsVisible,
                canViewModel = canViewModel,
                tboxViewModel = tboxViewModel,
                onDismiss = { vehicleSettingsVisible = false },
                bodyState = carRig,
                onBodyStateChanged = { carRig = it },
                modelRevision = 0,
                paintId = carPaintId,
                paintRevision = paintRevision,
                onPaintChanged = { id ->
                    carPaintId = id
                    paintRevision++
                },
                onCarPaneBoundsChanged = {},
                transitionProgress = settingsTransitionProgress,
                settingsUserYawDeg = settingsUserYaw,
                onCarRotate = { delta ->
                    settingsUserYaw = (settingsUserYaw + delta).coerceIn(-135f, 135f)
                },
            )
            LauncherAppDrawer(
                visible = appDrawerVisible,
                settingsViewModel = settingsViewModel,
                onDismiss = { appDrawerVisible = false },
                editMode = drawerEditMode,
                onEditModeChange = { drawerEditMode = it },
                configRevision = configRevision,
                onConfigChanged = onConfigChanged,
            )
            LauncherAppDragOverlay(
                dragging = isDraggingApp,
                sidebarWidth = sidebarWidth,
            )
            LauncherDevScaleControls(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 88.dp),
            )
            }
        }
    }
}
