package vad.dashing.tbox.automation

import vad.dashing.tbox.ruEn

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import vad.dashing.tbox.AdayoStockAppWindow
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.CAMD_ACTION_CLOSE
import vad.dashing.tbox.CAMD_ACTION_CLOSE_ALL
import vad.dashing.tbox.CAMD_ACTION_OPEN
import vad.dashing.tbox.CamdWindowClient
import vad.dashing.tbox.CarDataRepository
import vad.dashing.tbox.camdAutomationCommandLine
import vad.dashing.tbox.camdKeepAvmCommandLine
import vad.dashing.tbox.camdRecorderCommandLine
import vad.dashing.tbox.camdRecorderSosCommandLine
import vad.dashing.tbox.CamdRecorderSosResult
import vad.dashing.tbox.parseCamdRecorderSosResult
import vad.dashing.tbox.parseCamdAutomationWindowArgs
import vad.dashing.tbox.HeadUnitBrightnessRepository
import vad.dashing.tbox.HeadUnitDayNightMapping
import vad.dashing.tbox.HeadUnitDayNightRepository
import vad.dashing.tbox.MEDIA_AUTOMATION_SOURCE_HOLD_MS
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.MirrorAdjustModeRepository
import vad.dashing.tbox.PagingStateNormalizer
import vad.dashing.tbox.PlatformAudioDomain
import vad.dashing.tbox.PlatformAudioRepository
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.browserUrlFromHttpRequestYaml
import vad.dashing.tbox.dcl.DclCallResult
import vad.dashing.tbox.dcl.DclLauncherApiRepository
import vad.dashing.tbox.dcl.DclLauncherEnvironmentRouting
import vad.dashing.tbox.dcl.DclLauncherEnvironmentSetting
import vad.dashing.tbox.dcl.dclLaunchShouldUseRawFallback
import vad.dashing.tbox.dcl.dclModeCapabilityMissing
import vad.dashing.tbox.HttpRequestWidgetResult
import vad.dashing.tbox.executeHttpRequestWidget
import vad.dashing.tbox.freeform.FreeformCompanionSession
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchHelper
import vad.dashing.tbox.httpRequestWidgetErrorMessage
import vad.dashing.tbox.httpRequestWidgetIsSuccess
import vad.dashing.tbox.location.GeoDebugLogRecorder
import vad.dashing.tbox.location.MockLocationWidgetCycle
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanCommandResult
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.openHttpRequestWidgetUrlInBrowser
import vad.dashing.tbox.parseHttpRequestWidgetYaml
import vad.dashing.tbox.placeCallFailureUserMessage
import vad.dashing.tbox.placeOutgoingCall
import vad.dashing.tbox.PlaceCallFailureReason
import vad.dashing.tbox.PlaceCallResult
import vad.dashing.tbox.phone.PhoneDialCallActivity
import vad.dashing.tbox.ui.LeftMenuLayout
import vad.dashing.tbox.voice.VadVoiceLaunchContract
import android.os.SystemClock
import kotlin.math.min

data class AutomationActionResult(
    val success: Boolean,
    val message: String = "",
) {
    companion object {
        fun ok(message: String = "") = AutomationActionResult(true, message)
        fun failure(message: String) = AutomationActionResult(false, message)
    }
}

class AutomationActionExecutor(
    context: Context,
    private val settingsManager: SettingsManager,
    private val appDataManager: AppDataManager,
    private val serviceActions: AutomationServiceActions,
) {
    private val appContext = context.applicationContext
    private val canCommandMutex = Mutex()
    private val windowActionMutex = Mutex()

    suspend fun execute(
        actions: List<AutomationAction>,
        context: AutomationTriggerContext,
        signalSnapshot: () -> Map<AutomationSignalKey, AutomationSignalValue>,
    ): AutomationActionResult {
        actions.forEachIndexed { index, action ->
            val result = executeOne(action, context, signalSnapshot)
            if (!result.success) {
                return AutomationActionResult.failure(
                    ruEn("Действие ${index + 1}: ${result.message}", "Action ${index + 1}: ${result.message}"),
                )
            }
        }
        return AutomationActionResult.ok(ruEn("Выполнено действий: ${actions.size}", "Actions completed: ${actions.size}"))
    }

    private suspend fun executeOne(
        action: AutomationAction,
        context: AutomationTriggerContext,
        signalSnapshot: () -> Map<AutomationSignalKey, AutomationSignalValue>,
    ): AutomationActionResult = try {
        when (action) {
            is AutomationAction.Delay -> {
                delay(action.durationMillis)
                AutomationActionResult.ok()
            }

            is AutomationAction.IfThenElse -> {
                val branch = if (
                    AutomationEvaluator.evaluateCondition(action.condition, context, signalSnapshot())
                ) {
                    action.thenActions
                } else {
                    action.elseActions
                }
                execute(branch, context, signalSnapshot)
            }

            is AutomationAction.CanCommand -> executeCan(action)
            is AutomationAction.LaunchApplication -> launchApplication(action)
            is AutomationAction.OpenMainScreen -> openMainScreen(action)
            is AutomationAction.HttpRequest -> executeHttp(action)
            is AutomationAction.Builtin -> executeBuiltin(action)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        AutomationActionResult.failure(
            error.message ?: error.javaClass.simpleName,
        )
    }

    private suspend fun executeCan(action: AutomationAction.CanCommand): AutomationActionResult {
        // mbCAN OEM JNI aborts if get/set runs on the main thread. MbCanRepository.execute already
        // hops onto stateApplyDispatcher; Default keeps VHAL writes off the UI thread as well.
        return withContext(Dispatchers.Default) {
            canCommandMutex.withLock {
                val entry = AutomationCanCatalog.get(action.bus, action.propertyId)
                    ?: return@withLock AutomationActionResult.failure(ruEn("CAN-команда не разрешена", "CAN command is not allowed"))
                // Checks that do not depend on the backend run first: a broken action must
                // not sit through the readiness wait. Mode-dependent ones follow the wait,
                // because the mode can still change while the backend starts.
                if (!entry.isActionAllowed(action)) {
                    return@withLock AutomationActionResult.failure(
                        ruEn("Недопустимая операция или значение CAN", "Invalid CAN operation or value"),
                    )
                }
                if (action.bus == AutomationCanBus.AUDIO &&
                    action.operation == AutomationCanOperation.TRUNK_PULSE
                ) {
                    return@withLock AutomationActionResult.failure(
                        ruEn("Импульс багажника не относится к аудио", "The trunk pulse is not an audio action"),
                    )
                }
                if (!awaitCanBackendReadyForAction(
                        isAvailable = {
                            UniversalCanRepository.availability.value is MbCanAvailability.Available
                        },
                        nowElapsedMillis = { SystemClock.elapsedRealtime() },
                        delayFor = { delay(it) },
                    )
                ) {
                    return@withLock AutomationActionResult.failure(CAN_NOT_READY_MESSAGE)
                }
                val canMode = UniversalCanRepository.mode.value
                val writeValue = AutomationCanValueCodec.resolveWriteValue(action, canMode)
                    ?: return@withLock AutomationActionResult.failure(
                        ruEn("Недопустимая операция или значение CAN", "Invalid CAN operation or value"),
                    )
                if (action.operation == AutomationCanOperation.SET &&
                    writeValue !in entry.allowedValuesFor(canMode)
                ) {
                    return@withLock AutomationActionResult.failure(
                        ruEn("Недопустимая операция или значение CAN", "Invalid CAN operation or value"),
                    )
                }
                if (action.operation == AutomationCanOperation.TRUNK_PULSE &&
                    writeValue !in setOf(1, 2)
                ) {
                    return@withLock AutomationActionResult.failure(
                        ruEn("Недопустимая операция или значение CAN", "Invalid CAN operation or value"),
                    )
                }
                if (!entry.supports(canMode)) {
                    return@withLock AutomationActionResult.failure(
                        ruEn("CAN-действие не подтверждено для текущего backend ГУ", "CAN action is not confirmed for the current HU backend"),
                    )
                }
                val command = when (action.bus) {
                    AutomationCanBus.VEHICLE -> when (action.operation) {
                        AutomationCanOperation.SET ->
                            MbCanCommand.SetProperty(action.propertyId, writeValue)

                        AutomationCanOperation.TOGGLE ->
                            MbCanCommand.ToggleProperty(action.propertyId)

                        AutomationCanOperation.TRUNK_PULSE ->
                            MbCanCommand.TrunkPulse(writeValue)
                    }

                    AutomationCanBus.AUDIO -> when (action.operation) {
                        AutomationCanOperation.SET ->
                            MbCanCommand.SetAudioProperty(action.propertyId, writeValue)

                        AutomationCanOperation.TOGGLE ->
                            MbCanCommand.ToggleAudioProperty(action.propertyId)

                        AutomationCanOperation.TRUNK_PULSE ->
                            return@withLock AutomationActionResult.failure(
                                ruEn("Импульс багажника не относится к аудио", "The trunk pulse is not an audio action"),
                            )
                    }
                }
                val result = executeCanWithStartupRetry(
                    execute = { UniversalCanRepository.execute(command) },
                    nowElapsedMillis = { SystemClock.elapsedRealtime() },
                    delayFor = { delay(it) },
                )
                AutomationActionResult(result.success, result.message)
            }
        }
    }

    private suspend fun launchApplication(
        action: AutomationAction.LaunchApplication,
    ): AutomationActionResult {
        val packageName = action.packageName.trim()
        if (packageName.isEmpty()) {
            return AutomationActionResult.failure(ruEn("Не выбрано приложение", "No app selected"))
        }
        return when (action.launchMode) {
            AppLauncherLaunchMode.FREEFORM -> {
                val pageCount = settingsManager.mainScreenPageCountFlow.first()
                val pinnedPage = action.freeformOverlayPage?.let {
                    PagingStateNormalizer.normalizeCurrentPage(it, pageCount)
                }
                val launched = withContext(Dispatchers.Main) {
                    FreeformLaunchHelper.launchCompanion(
                        context = appContext,
                        packageName = packageName,
                        side = action.freeformSide,
                        percent = FreeformLaunchBounds.normalizePercent(action.freeformPercent),
                        overlayCrop = action.freeformOverlayCrop,
                        pinnedOverlayPage = pinnedPage,
                    )
                }
                val confirmed = launched && withTimeoutOrNull(WINDOW_ACTION_TIMEOUT_MS) {
                    FreeformCompanionSession.state
                        .filter { it?.packageName == packageName }
                        .first()
                } != null
                if (confirmed) {
                    AutomationActionResult.ok(ruEn("Freeform запущен", "Freeform started"))
                } else {
                    AutomationActionResult.failure(ruEn("Не удалось запустить freeform", "Could not start freeform"))
                }
            }

            AppLauncherLaunchMode.STOCK_WINDOW -> {
                val launched = awaitAfterWindowModeExit {
                    if (!AdayoStockAppWindow.launchInAppWindow(appContext, packageName)) {
                        launchFullscreen(packageName)
                    } else {
                        true
                    }
                }
                AutomationActionResult(
                    launched,
                    if (launched) ruEn("Приложение запущено", "App launched") else ruEn("Запуск приложения не удался", "Failed to launch the app"),
                )
            }

            AppLauncherLaunchMode.FULLSCREEN,
            AppLauncherLaunchMode.DCL_FULL_WIDTH,
            AppLauncherLaunchMode.DCL_FULLSCREEN,
            -> {
                if (appContext.packageManager.getLaunchIntentForPackage(packageName) == null) {
                    return AutomationActionResult.failure(ruEn("Приложение не установлено", "App is not installed"))
                }
                val dclMode = DclLauncherEnvironmentRouting.dclLaunchModeFor(
                    envEnabled = DclLauncherEnvironmentSetting.enabled,
                    launchMode = action.launchMode,
                )
                if (dclMode != null) {
                    awaitAfterWindowModeExit { true }
                    return launchViaDclOrRaw(packageName, dclMode)
                }
                val launched = awaitAfterWindowModeExit {
                    launchFullscreen(packageName)
                }
                AutomationActionResult(
                    launched,
                    if (launched) ruEn("Приложение запущено", "App launched") else ruEn("Запуск приложения не удался", "Failed to launch the app"),
                )
            }

            AppLauncherLaunchMode.VIRTUAL_DISPLAY -> {
                val displayId = action.virtualDisplayId
                if (displayId == null || displayId <= 0) {
                    return AutomationActionResult.failure(ruEn("Не выбран виртуальный дисплей", "No virtual display selected"))
                }
                if (appContext.packageManager.getLaunchIntentForPackage(packageName) == null) {
                    return AutomationActionResult.failure(ruEn("Приложение не установлено", "App is not installed"))
                }
                awaitAfterWindowModeExit { true }
                val cached = vad.dashing.tbox.adb.HuDisplayInfo.listFromJson(
                    settingsManager.huVirtualDisplaysJsonFlow.first(),
                )
                var refreshedDisplays: List<vad.dashing.tbox.adb.HuDisplayInfo>? = null
                val outcome = withContext(Dispatchers.IO) {
                    vad.dashing.tbox.adb.VirtualDisplayAdb.launchOnDisplay(
                        context = appContext,
                        packageName = packageName,
                        displayId = displayId,
                        displayWidthPx = action.virtualDisplayWidthPx,
                        displayHeightPx = action.virtualDisplayHeightPx,
                        cachedDisplays = cached,
                        onDisplaysRefreshed = { refreshedDisplays = it },
                    )
                }
                refreshedDisplays?.let { displays ->
                    settingsManager.saveHuVirtualDisplaysJson(
                        vad.dashing.tbox.adb.HuDisplayInfo.listToJson(displays),
                    )
                }
                vad.dashing.tbox.adb.HuAdbControl.refresh()
                when (outcome) {
                    is vad.dashing.tbox.adb.VirtualDisplayAdb.LaunchOutcome.Success ->
                        AutomationActionResult.ok(
                            ruEn("Приложение запущено на дисплее ${outcome.displayId}", "App launched on display ${outcome.displayId}") +
                                if (outcome.remapped) ruEn(" (было $displayId)", " (was $displayId)") else "",
                        )
                    is vad.dashing.tbox.adb.VirtualDisplayAdb.LaunchOutcome.Failed ->
                        AutomationActionResult.failure(
                            vad.dashing.tbox.adb.LocalhostAdbReasons.forVirtualDisplay(
                                appContext,
                                outcome.reason,
                                outcome.detail,
                            ),
                        )
                }
            }
        }
    }

    private fun launchFullscreen(packageName: String): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        MainActivityIntentHelper.applyExternalAppLaunchFlags(intent, appContext)
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private suspend fun launchViaDclOrRaw(
        packageName: String,
        dclMode: String,
    ): AutomationActionResult {
        if (dclModeCapabilityMissing(dclMode, DclLauncherApiRepository.capabilities.value)) {
            return rawFallbackAfterDcl(ruEn("DCL capability missing ($dclMode) — обычный запуск", "DCL capability missing ($dclMode) — normal launch"), packageName)
        }
        val result = DclLauncherApiRepository.launchApp(
            packageName = packageName,
            mode = dclMode,
            context = appContext,
        )
        if (result is DclCallResult.Ok && result.value.success) {
            return AutomationActionResult.ok(ruEn("Приложение запущено через DCL ($dclMode)", "App launched via DCL ($dclMode)"))
        }
        if (dclLaunchShouldUseRawFallback(result)) {
            val detail = when (result) {
                is DclCallResult.Ok ->
                    result.value.error?.message
                        ?: result.value.error?.errorCode
                        ?: "launch not successful"
                is DclCallResult.Error ->
                    result.error.message
                        ?: result.error.errorCode
                        ?: "DCL launch error"
                DclCallResult.Unavailable -> "API unavailable"
            }
            return rawFallbackAfterDcl(
                ruEn("DCL недоступен ($detail) — обычный запуск", "DCL unavailable ($detail) — normal launch"),
                packageName,
            )
        }
        return AutomationActionResult.failure(
            when (result) {
                is DclCallResult.Ok ->
                    result.value.error?.message
                        ?: result.value.error?.errorCode
                        ?: "DCL launch failed"
                is DclCallResult.Error ->
                    result.error.message
                        ?: result.error.errorCode
                        ?: "DCL launch error"
                DclCallResult.Unavailable -> "DCL unavailable"
            },
        )
    }

    private suspend fun rawFallbackAfterDcl(
        message: String,
        packageName: String,
    ): AutomationActionResult {
        val launched = withContext(Dispatchers.Main) {
            launchFullscreen(packageName)
        }
        return AutomationActionResult(
            launched,
            if (launched) message else ruEn("Запуск приложения не удался", "Failed to launch the app"),
        )
    }

    private suspend fun openMainScreen(
        action: AutomationAction.OpenMainScreen,
    ): AutomationActionResult {
        val pageCount = settingsManager.mainScreenPageCountFlow.first()
        val page = PagingStateNormalizer.normalizeCurrentPage(action.page, pageCount)
        return when (action.target) {
            AutomationMainScreenTarget.CURRENT_WINDOW -> {
                if (FreeformCompanionSession.pinOverlayPage(page)) {
                    AutomationActionResult.ok(ruEn("Страница оконного режима: $page", "Window-mode page: $page"))
                } else {
                    AutomationActionResult.failure(ruEn("Оконный режим не активен", "Window mode is not active"))
                }
            }

            AutomationMainScreenTarget.FULLSCREEN -> {
                settingsManager.saveMainScreenCurrentPage(page)
                settingsManager.saveSelectedTab(SettingsManager.MAIN_SCREEN_TAB_KEY)
                val opened = awaitAfterWindowModeExit(::bringMainActivityToFront)
                AutomationActionResult(
                    opened,
                    if (opened) ruEn("Главный экран, страница $page", "Home screen, page $page") else ruEn("Главный экран не открыт", "Home screen was not opened"),
                )
            }
        }
    }

    private suspend fun executeHttp(
        action: AutomationAction.HttpRequest,
    ): AutomationActionResult {
        if (action.openBrowser) {
            val url = browserUrlFromHttpRequestYaml(action.yaml).getOrElse {
                return AutomationActionResult.failure(it.message ?: ruEn("Некорректный URL", "Invalid URL"))
            }
            return if (openHttpRequestWidgetUrlInBrowser(appContext, url)) {
                AutomationActionResult.ok(ruEn("Браузер открыт", "Browser opened"))
            } else {
                AutomationActionResult.failure(ruEn("Не удалось открыть браузер", "Could not open the browser"))
            }
        }
        val config = parseHttpRequestWidgetYaml(action.yaml).getOrElse {
            return AutomationActionResult.failure(it.message ?: ruEn("Некорректный HTTP YAML", "Invalid HTTP YAML"))
        }
        val result = executeHttpRequestWidget(appContext, config)
        return AutomationActionResult(
            success = httpRequestWidgetIsSuccess(result),
            message = if (httpRequestWidgetIsSuccess(result)) {
                if (result is HttpRequestWidgetResult.Success && result.code == 0) {
                    ruEn("Запрос передан приложению", "Request sent to the app")
                } else {
                    ruEn("HTTP выполнен", "HTTP completed")
                }
            } else {
                httpRequestWidgetErrorMessage(result)
            },
        )
    }

    private suspend fun executeBuiltin(
        action: AutomationAction.Builtin,
    ): AutomationActionResult = when (action.type) {
        AutomationBuiltinActionType.OPEN_MENU -> {
            val layout = LeftMenuLayout.parse(settingsManager.leftMenuLayoutJsonFlow.first())
            settingsManager.saveSelectedTab(LeftMenuLayout.firstVisibleTabKey(layout))
            withContext(Dispatchers.Main) { bringMainActivityToFront() }
            AutomationActionResult.ok(ruEn("Меню открыто", "Menu opened"))
        }

        AutomationBuiltinActionType.FINISH_AND_START_TRIP ->
            serviceActions.finishAndStartTrip()

        AutomationBuiltinActionType.RESET_MOTOR_HOURS -> {
            CarDataRepository.setMotorHours(0f)
            appDataManager.saveMotorHours(0f)
            CarDataRepository.markPersisted(0f)
            AutomationActionResult.ok(ruEn("Моточасы сброшены", "Engine hours reset"))
        }

        AutomationBuiltinActionType.RESTART_TBOX ->
            serviceActions.restartTbox()

        AutomationBuiltinActionType.TOGGLE_APP_DAY_NIGHT_THEME -> {
            val ok = withContext(Dispatchers.Main) {
                HeadUnitDayNightRepository.toggleManualTheme(appContext)
            }
            AutomationActionResult(ok, if (ok) ruEn("Тема переключена", "Theme toggled") else ruEn("Тема не переключена", "Theme was not toggled"))
        }

        AutomationBuiltinActionType.ENABLE_HEAD_UNIT_AUTO_THEME -> {
            val ok = withContext(Dispatchers.Main) {
                HeadUnitDayNightRepository.enableAutoMode(appContext)
            }
            AutomationActionResult(ok, if (ok) ruEn("Автотема включена", "Auto theme on") else ruEn("Автотема недоступна", "Auto theme unavailable"))
        }

        AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME -> {
            val key = action.stringValue.trim().lowercase()
            val modeValue = when (key) {
                "light" -> HeadUnitDayNightRepository.NIGHT_MODE_LIGHT_MANUAL
                "dark" -> HeadUnitDayNightRepository.NIGHT_MODE_DARK_MANUAL
                "auto" -> HeadUnitDayNightRepository.NIGHT_MODE_AUTO
                else -> null
            }
            if (modeValue == null) {
                AutomationActionResult.failure(ruEn("Тема: light / dark / auto", "Theme: light / dark / auto"))
            } else {
                val ok = withContext(Dispatchers.Main) {
                    if (modeValue == HeadUnitDayNightRepository.NIGHT_MODE_AUTO) {
                        HeadUnitDayNightRepository.enableAutoMode(appContext)
                    } else {
                        HeadUnitDayNightRepository.writeAutoMode(appContext, modeValue)
                    }
                }
                val label = when (key) {
                    "light" -> ruEn("светлая", "light")
                    "dark" -> ruEn("тёмная", "dark")
                    else -> ruEn("авто", "auto")
                }
                AutomationActionResult(
                    ok,
                    if (ok) ruEn("Тема ГУ: $label", "HU theme: $label") else ruEn("Не удалось установить тему ГУ", "Could not set the HU theme"),
                )
            }
        }

        AutomationBuiltinActionType.TOGGLE_MIRROR_ADJUST_MODE -> {
            val ok = runCatching {
                MirrorAdjustModeRepository.toggleMirrorAdjustMode(appContext)
                true
            }.getOrDefault(false)
            AutomationActionResult(ok, if (ok) ruEn("Режим зеркал переключён", "Mirror mode toggled") else ruEn("Ошибка режима зеркал", "Mirror mode error"))
        }

        AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS ->
            serviceActions.applyFloatingPanelVisibility(action)

        AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED ->
            serviceActions.applyFloatingPanelEnabled(action)

        AutomationBuiltinActionType.ESP_RELAY_SET ->
            serviceActions.setEspRelayMask(action.intValue)

        AutomationBuiltinActionType.ESP_RELAY_TOGGLE ->
            serviceActions.toggleEspRelay(action.intValue)

        AutomationBuiltinActionType.ESP_RELAY_PULSE -> serviceActions.pulseEspRelay(
            channel = action.intValue,
            durationMillis = action.stringValue.toLongOrNull()?.takeIf { it > 0L },
        )

        AutomationBuiltinActionType.ESP_BLE_RELAY_SET -> serviceActions.setEspBleRelayState(
            index = action.intValue,
            on = action.boolValue,
        )

        AutomationBuiltinActionType.ESP_BLE_RELAY_TOGGLE ->
            serviceActions.toggleEspBleRelay(action.intValue)

        AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE -> serviceActions.pulseEspBleRelay(
            index = action.intValue,
            durationMillis = action.stringValue.toLongOrNull()?.takeIf { it > 0L },
        )

        AutomationBuiltinActionType.MEDIA_PREVIOUS -> mediaAction(action) {
            packages, preferred -> SharedMediaControlService.skipToPrevious(packages, preferred)
        }

        AutomationBuiltinActionType.MEDIA_PLAY_PAUSE -> mediaAction(
            action,
            holdForPlayerLaunch = true,
        ) { packages, preferred ->
            SharedMediaControlService.playPause(
                appContext,
                packages,
                preferred,
                keepPlayerForeground = true,
            )
        }

        AutomationBuiltinActionType.MEDIA_PLAY -> mediaAction(
            action,
            holdForPlayerLaunch = true,
        ) { packages, preferred ->
            SharedMediaControlService.play(
                appContext,
                packages,
                preferred,
                keepPlayerForeground = true,
            )
        }

        AutomationBuiltinActionType.MEDIA_NEXT -> mediaAction(action) { packages, preferred ->
            SharedMediaControlService.skipToNext(packages, preferred)
        }

        AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE -> mediaAction(action) { packages, preferred ->
            SharedMediaControlService.toggleHeartRating(packages, preferred)
        }

        AutomationBuiltinActionType.SET_MEDIA_VOLUME -> setPlatformVolume(
            PlatformAudioDomain.VolumeChannel.Media,
            action.intValue,
            ruEn("медиа", "media"),
        )

        AutomationBuiltinActionType.SET_PHONE_VOLUME -> setPlatformVolume(
            PlatformAudioDomain.VolumeChannel.Phone,
            action.intValue,
            ruEn("телефона", "phone"),
        )

        AutomationBuiltinActionType.SET_NAVI_VOLUME -> setPlatformVolume(
            PlatformAudioDomain.VolumeChannel.Navi,
            action.intValue,
            ruEn("навигатора", "navigation"),
        )

        AutomationBuiltinActionType.SET_VOICE_VOLUME -> setPlatformVolume(
            PlatformAudioDomain.VolumeChannel.Voice,
            action.intValue,
            ruEn("голоса", "voice"),
        )

        AutomationBuiltinActionType.SET_HEADREST_SPEAKER -> {
            val ui = when (action.stringValue.trim().lowercase()) {
                "only" -> PlatformAudioDomain.HEADREST_ONLY
                "assist" -> PlatformAudioDomain.HEADREST_ASSIST
                "off" -> PlatformAudioDomain.HEADREST_OFF
                else -> null
            }
            if (ui == null) {
                AutomationActionResult.failure(ruEn("Подголовник: only / assist / off", "Headrest: only / assist / off"))
            } else {
                // On Adayo/A10 the headrest goes through AdayoSettingsService, not VHAL.
                if (!HeadUnitDayNightMapping.usesAdayoKeys()) {
                    awaitCanBackendReadyForAction(
                        isAvailable = {
                            UniversalCanRepository.availability.value is MbCanAvailability.Available
                        },
                        nowElapsedMillis = { SystemClock.elapsedRealtime() },
                        delayFor = { delay(it) },
                    )
                }
                val ok = PlatformAudioRepository.setHeadrestMode(ui)
                AutomationActionResult(
                    ok,
                    if (ok) {
                        ruEn("Динамик подголовника установлен", "Headrest speaker set")
                    } else {
                        ruEn("Не удалось установить динамик подголовника", "Could not set the headrest speaker")
                    },
                )
            }
        }

        AutomationBuiltinActionType.CYCLE_MOCK_LOCATION_MODE -> {
            val next = MockLocationWidgetCycle.next(
                settingsManager.mockPowerStateFlow.first(),
                settingsManager.mockCanSpeedModeFlow.first(),
            )
            settingsManager.saveMockPowerAndModeSetting(next.power, next.mode)
            AutomationActionResult.ok(ruEn("Режим подмены геопозиции переключён", "Mock-location mode toggled"))
        }

        AutomationBuiltinActionType.GNSS_MODULE_REBOOT ->
            serviceActions.rebootGnssModule()

        AutomationBuiltinActionType.SET_SIMULATED_LOCATION_SOURCE_LOSS ->
            serviceActions.setSimulatedLocationSourceLoss(action.boolValue)

        AutomationBuiltinActionType.SET_GEO_DEBUG_LOG -> {
            if (action.boolValue) GeoDebugLogRecorder.start() else GeoDebugLogRecorder.stop()
            AutomationActionResult.ok(
                if (action.boolValue) ruEn("Запись гео-журнала запущена", "Geo log recording started") else ruEn("Запись остановлена", "Recording stopped"),
            )
        }

        AutomationBuiltinActionType.WIFI_SET_ENABLED ->
            WifiStaController.setRadioEnabled(appContext, action.boolValue)

        AutomationBuiltinActionType.WIFI_CONNECT ->
            WifiStaController.connectToSaved(appContext, action.stringValue)

        AutomationBuiltinActionType.WIFI_DISCONNECT ->
            WifiStaController.disconnectCurrent(appContext)

        AutomationBuiltinActionType.WIFI_MODEM_SET_DATA ->
            serviceActions.setWifiModemMobileDataEnabled(action.boolValue)

        AutomationBuiltinActionType.WIFI_MODEM_REBOOT ->
            serviceActions.rebootWifiModem()

        AutomationBuiltinActionType.ADB_SET_TCP ->
            vad.dashing.tbox.adb.AdbAutomationActions.setTcpEnabled(action.boolValue)

        AutomationBuiltinActionType.ADB_SHELL ->
            vad.dashing.tbox.adb.AdbAutomationActions.runShellCommand(
                appContext,
                action.stringValue,
            )

        AutomationBuiltinActionType.ADB_FORCE_STOP ->
            vad.dashing.tbox.adb.AdbAutomationActions.forceStopPackage(
                appContext,
                action.stringValue,
            )

        AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS -> {
            if (!HeadUnitBrightnessRepository.isAvailable(appContext)) {
                AutomationActionResult.failure(ruEn("Яркость экрана ГУ недоступна", "HU screen brightness is unavailable"))
            } else {
                val level = action.intValue.coerceIn(1, 10)
                val ok = withContext(Dispatchers.Main) {
                    HeadUnitBrightnessRepository.writeBrightnessUiLevel(appContext, level)
                }
                AutomationActionResult(
                    ok,
                    if (ok) {
                        ruEn("Яркость экрана ГУ: $level", "HU screen brightness: $level")
                    } else {
                        ruEn("Не удалось установить яркость экрана ГУ", "Could not set the HU screen brightness")
                    },
                )
            }
        }

        AutomationBuiltinActionType.SET_HU_SCREEN_AUTO_BRIGHTNESS -> {
            if (!HeadUnitBrightnessRepository.isAvailable(appContext)) {
                AutomationActionResult.failure(ruEn("Автояркость экрана ГУ недоступна", "HU screen auto-brightness unavailable"))
            } else {
                val ok = withContext(Dispatchers.Main) {
                    HeadUnitBrightnessRepository.writeAutoBrightness(appContext, action.boolValue)
                }
                AutomationActionResult(
                    ok,
                    if (ok) {
                        if (action.boolValue) {
                            ruEn("Автояркость экрана ГУ включена", "HU screen auto-brightness on")
                        } else {
                            ruEn("Автояркость экрана ГУ выключена", "HU screen auto-brightness off")
                        }
                    } else {
                        ruEn("Не удалось изменить автояркость экрана ГУ", "Could not change HU screen auto-brightness")
                    },
                )
            }
        }

        AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET -> {
            val triggerId = action.stringValue.trim()
            if (triggerId.isEmpty()) {
                AutomationActionResult.failure(ruEn("ID триггера пуст", "Trigger ID is empty"))
            } else {
                when (builtinActionTriggerWidgetCommand(action)) {
                    AutomationTriggerWidgetCommand.ACTIVATE -> {
                        AutomationTriggerWidgetState.setActive(triggerId, true)
                        AutomationActionResult.ok(ruEn("Триггер-виджет активирован", "Trigger widget activated"))
                    }
                    AutomationTriggerWidgetCommand.DEACTIVATE -> {
                        AutomationTriggerWidgetState.setActive(triggerId, false)
                        AutomationActionResult.ok(ruEn("Триггер-виджет деактивирован", "Trigger widget deactivated"))
                    }
                    AutomationTriggerWidgetCommand.TOGGLE -> {
                        val nextActive = !AutomationTriggerWidgetState.isActive(triggerId)
                        AutomationTriggerWidgetState.setActive(triggerId, nextActive)
                        AutomationActionResult.ok(
                            if (nextActive) {
                                ruEn("Триггер-виджет активирован", "Trigger widget activated")
                            } else {
                                ruEn("Триггер-виджет деактивирован", "Trigger widget deactivated")
                            },
                        )
                    }
                }
            }
        }

        AutomationBuiltinActionType.SET_AUTOMATION_ENABLED -> {
            val automationId = action.stringValue.trim()
            if (automationId.isEmpty()) {
                AutomationActionResult.failure(ruEn("ID автоматизации пуст", "Automation ID is empty"))
            } else {
                serviceActions.setAutomationEnabled(automationId, action.boolValue)
            }
        }

        AutomationBuiltinActionType.SHOW_TOAST -> {
            val text = action.stringValue.trim()
            if (text.isEmpty()) {
                AutomationActionResult.failure(ruEn("Текст пуст", "Text is empty"))
            } else {
                AutomationUserMessageOverlay.showToast(appContext, text)
                AutomationActionResult.ok(ruEn("Toast показан", "Toast shown"))
            }
        }

        AutomationBuiltinActionType.SHOW_ALERT -> {
            val text = action.stringValue.trim()
            if (text.isEmpty()) {
                AutomationActionResult.failure(ruEn("Текст пуст", "Text is empty"))
            } else if (
                !AutomationUserMessageOverlay.showCloseableMessage(
                    context = appContext,
                    text = text,
                    autoCloseMillis = action.intValue.toLong().coerceAtLeast(0L),
                )
            ) {
                AutomationActionResult.failure(
                    ruEn("Нет разрешения «поверх других окон» для сообщения на экране", "No “display over other apps” permission for the on-screen message"),
                )
            } else {
                AutomationActionResult.ok(ruEn("Сообщение закрыто", "Message closed"))
            }
        }

        AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET,
        AutomationBuiltinActionType.CRUISE_PAUSE,
        AutomationBuiltinActionType.CRUISE_FULL_OFF,
        AutomationBuiltinActionType.CRUISE_RESUME,
        AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED,
        AutomationBuiltinActionType.CRUISE_NUDGE,
        -> AutomationCruiseActions.execute(action)

        AutomationBuiltinActionType.START_VAD_VOICE -> startVadVoiceListen()

        AutomationBuiltinActionType.PLACE_CALL -> placeCall(action.stringValue)
        AutomationBuiltinActionType.ANSWER_CALL -> answerCall()
        AutomationBuiltinActionType.HANGUP_CALL -> hangupCall()

        AutomationBuiltinActionType.CAMD_WINDOW -> executeCamdWindow(action)
        AutomationBuiltinActionType.CAMD_KEEP_AVM -> executeCamd(
            camdKeepAvmCommandLine(action.boolValue),
            if (action.boolValue) {
                ruEn("Штатный AVM удерживается выключенным", "Stock AVM is held off")
            } else {
                ruEn("Штатный AVM включён", "Stock AVM on")
            },
        )
        AutomationBuiltinActionType.CAMD_RECORDER -> executeCamd(
            camdRecorderCommandLine(action.boolValue),
            if (action.boolValue) {
                ruEn("Регистратор Камеры ГУ включён", "HU Cameras recorder on")
            } else {
                ruEn("Регистратор Камеры ГУ выключен", "HU Cameras recorder off")
            },
        )
        AutomationBuiltinActionType.CAMD_RECORDER_SOS -> {
            val result = withContext(Dispatchers.IO) {
                CamdWindowClient.command(camdRecorderSosCommandLine(), context = appContext)
            }
            when (parseCamdRecorderSosResult(result)) {
                CamdRecorderSosResult.Started -> AutomationActionResult.ok(ruEn("Важный кадр сохраняется", "Important frame is being saved"))
                CamdRecorderSosResult.Busy -> AutomationActionResult.ok(ruEn("Сохранение уже идёт", "A save is already in progress"))
                CamdRecorderSosResult.RecorderOff ->
                    AutomationActionResult.failure(ruEn("Регистратор выключен — включите его плиткой «Регистратор»", "The recorder is off — turn it on with the “Recorder” tile"))
                CamdRecorderSosResult.NoSosFolder ->
                    AutomationActionResult.failure(ruEn("Регистратор: нет папки SOS в «Камерах ГУ»", "Recorder: no SOS folder in “HU Cameras”"))
                CamdRecorderSosResult.Failed ->
                    AutomationActionResult.failure(ruEn("Камеры ГУ не отвечают", "HU Cameras is not responding"))
            }
        }

        AutomationBuiltinActionType.DCL_SET_SMART_ACC -> executeDclSetSmartAcc(action)

        AutomationBuiltinActionType.DCL_TOGGLE_SMART_ACC -> executeDclToggleSmartAcc()
    }

    private suspend fun executeDclSetSmartAcc(
        action: AutomationAction.Builtin,
    ): AutomationActionResult {
        val targetSpeed = action.intValue.takeIf { it > 0 }
        val configEnabled = when (action.stringValue.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            "" -> null
            else -> {
                return AutomationActionResult.failure(
                    ruEn("configEnabled: true / false или пусто", "configEnabled: true / false or empty"),
                )
            }
        }
        return when (
            val result = DclLauncherApiRepository.setSmartAcc(
                enabled = action.boolValue,
                targetSpeed = targetSpeed,
                configEnabled = configEnabled,
                context = appContext,
            )
        ) {
            is DclCallResult.Ok -> AutomationActionResult.ok(
                if (action.boolValue) ruEn("DCL Умный ACC включён", "DCL Smart ACC on") else ruEn("DCL Умный ACC выключен", "DCL Smart ACC off"),
            )
            is DclCallResult.Error -> AutomationActionResult.failure(
                result.error.message
                    ?: result.error.errorCode
                    ?: ruEn("DCL Smart ACC: ошибка", "DCL Smart ACC: error"),
            )
            DclCallResult.Unavailable -> AutomationActionResult.failure(
                ruEn("DCL Launcher API недоступен", "DCL Launcher API is unavailable"),
            )
        }
    }

    private suspend fun executeDclToggleSmartAcc(): AutomationActionResult =
        when (val result = DclLauncherApiRepository.toggleSmartAcc(appContext)) {
            is DclCallResult.Ok -> AutomationActionResult.ok(ruEn("DCL Умный ACC переключён", "DCL Smart ACC toggled"))
            is DclCallResult.Error -> AutomationActionResult.failure(
                result.error.message
                    ?: result.error.errorCode
                    ?: ruEn("DCL Smart ACC: ошибка", "DCL Smart ACC: error"),
            )
            DclCallResult.Unavailable -> AutomationActionResult.failure(
                ruEn("DCL Launcher API недоступен", "DCL Launcher API is unavailable"),
            )
        }

    private suspend fun executeCamdWindow(action: AutomationAction.Builtin): AutomationActionResult {
        val line = camdAutomationCommandLine(action.intValue, action.stringValue)
            ?: return AutomationActionResult.failure(ruEn("Некорректные параметры окна камеры", "Invalid camera window parameters"))
        val mode = parseCamdAutomationWindowArgs(action.stringValue)?.mode
        val ok = when (mode) {
            CAMD_ACTION_CLOSE_ALL -> ruEn("Окна камер закрыты", "Camera windows closed")
            CAMD_ACTION_CLOSE -> ruEn("Окно камеры закрыто", "Camera window closed")
            CAMD_ACTION_OPEN -> ruEn("Окно камеры открыто", "Camera window opened")
            else -> ruEn("Команда камеры выполнена", "Camera command completed")
        }
        return executeCamd(line, ok)
    }

    private suspend fun executeCamd(line: String, okMessage: String): AutomationActionResult {
        val result = withContext(Dispatchers.IO) {
            CamdWindowClient.command(line, context = appContext)
        }
        return result.fold(
            onSuccess = { AutomationActionResult.ok(okMessage) },
            onFailure = { error ->
                val detail = error.message.orEmpty()
                val text = if (detail.startsWith("ERR") || detail.contains("state=")) {
                    detail
                } else {
                    ruEn("Камеры ГУ не отвечают", "HU Cameras is not responding")
                }
                AutomationActionResult.failure(text)
            },
        )
    }

    private fun placeCall(rawNumber: String): AutomationActionResult {
        return when (val result = placeOutgoingCall(appContext, rawNumber)) {
            PlaceCallResult.Success -> AutomationActionResult.ok(ruEn("Звонок начат", "Call started"))
            is PlaceCallResult.Failure -> {
                if (result.reason == PlaceCallFailureReason.NO_CALL_PHONE_PERMISSION) {
                    // First-failure path for API / automation users without a dial tile (S9).
                    PhoneDialCallActivity.startPermissionRequest(appContext)
                }
                AutomationActionResult.failure(
                    placeCallFailureUserMessage(result.reason, result.detail),
                )
            }
        }
    }

    private fun answerCall(): AutomationActionResult {
        return when (
            val result = vad.dashing.tbox.phone.PhoneCallStateRepository.answerIncomingCall()
        ) {
            vad.dashing.tbox.phone.PhoneCallControlResult.Success ->
                AutomationActionResult.ok(ruEn("Ответ на звонок", "Answer call"))
            is vad.dashing.tbox.phone.PhoneCallControlResult.Failure ->
                AutomationActionResult.failure(result.message)
        }
    }

    private fun hangupCall(): AutomationActionResult {
        return when (val result = vad.dashing.tbox.phone.PhoneCallStateRepository.hangupCall()) {
            vad.dashing.tbox.phone.PhoneCallControlResult.Success ->
                AutomationActionResult.ok(ruEn("Звонок сброшен", "Call ended"))
            is vad.dashing.tbox.phone.PhoneCallControlResult.Failure ->
                AutomationActionResult.failure(result.message)
        }
    }

    private fun startVadVoiceListen(): AutomationActionResult {
        val intent = Intent(VadVoiceLaunchContract.ACTION_LISTEN).apply {
            setPackage(VadVoiceLaunchContract.PACKAGE)
            putExtra(VadVoiceLaunchContract.EXTRA_SOURCE, VadVoiceLaunchContract.SOURCE_AUTOMATION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            appContext.startActivity(intent)
            AutomationActionResult.ok(ruEn("VAD Voice: слушаю", "VAD Voice: listening"))
        }.getOrElse { error ->
            AutomationActionResult.failure(
                error.message?.takeIf { it.isNotBlank() }
                    ?: ruEn("VAD Voice не установлен", "VAD Voice is not installed"),
            )
        }
    }

    private suspend fun setPlatformVolume(
        channel: PlatformAudioDomain.VolumeChannel,
        value: Int,
        label: String,
    ): AutomationActionResult {
        // OpenOS and Adayo SettingsSvc accept mixer writes on the main looper,
        // the same thread the volume widget uses. The external API runs actions
        // on a worker thread, and that write comes back as a volume error.
        val ok = withContext(Dispatchers.Main) {
            PlatformAudioRepository.setVolume(channel, value)
        }
        return AutomationActionResult(
            ok,
            if (ok) ruEn("Громкость $label установлена", "Volume $label set") else ruEn("Ошибка громкости $label", "Volume $label error"),
        )
    }

    private suspend fun mediaAction(
        action: AutomationAction.Builtin,
        holdForPlayerLaunch: Boolean = false,
        block: (Set<String>, String) -> Unit,
    ): AutomationActionResult {
        val preferred = action.stringValue.trim()
        // Empty package = active media session (voice / API without a picker).
        val packages = if (preferred.isEmpty()) emptySet() else setOf(preferred)
        // MediaController.registerCallback / startActivity / transportControls on API 28 need a
        // Looper; automation runs on DefaultDispatcher. Main hop plus main-Handler in
        // SharedMediaControlService cover both the first command and later play retries.
        return withContext(Dispatchers.Main) {
            SharedMediaControlService.updateSourceSelection(appContext, MEDIA_SOURCE_ID, packages)
            runCatching {
                block(packages, preferred)
                AutomationActionResult.ok(ruEn("Медиа-команда отправлена", "Media command sent"))
            }.getOrElse {
                AutomationActionResult.failure(it.message ?: ruEn("Ошибка медиа-команды", "Media command error"))
            }.also { result ->
                if (holdForPlayerLaunch && result.success) {
                    SharedMediaControlService.scheduleSourceSelectionRelease(
                        MEDIA_SOURCE_ID,
                        MEDIA_AUTOMATION_SOURCE_HOLD_MS,
                    )
                } else {
                    SharedMediaControlService.clearSourceSelectionIfNotHeld(MEDIA_SOURCE_ID)
                }
            }
        }
    }

    private fun bringMainActivityToFront(): Boolean = runCatching {
        val intent = MainActivityIntentHelper.createBringToFrontIntent(appContext)
        if (appContext !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
        true
    }.getOrDefault(false)

    private suspend fun awaitAfterWindowModeExit(action: () -> Boolean): Boolean =
        windowActionMutex.withLock {
            val completion = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) {
                FreeformLaunchHelper.runAfterExitingWindowMode(appContext) {
                    if (completion.isActive) {
                        completion.complete(runCatching(action).getOrDefault(false))
                    }
                }
            }
            val result = withTimeoutOrNull(WINDOW_ACTION_TIMEOUT_MS) {
                completion.await()
            }
            if (result == null) completion.cancel()
            result ?: false
        }

    companion object {
        private const val MEDIA_SOURCE_ID = "user-automations"
        private const val WINDOW_ACTION_TIMEOUT_MS = 10_000L
        internal const val CAN_READY_WAIT_TIMEOUT_MS = 15_000L
        internal const val CAN_READY_POLL_MS = 250L
        internal const val CAN_EXEC_RETRY_WINDOW_MS = 12_000L
        internal const val CAN_EXEC_RETRY_DELAY_MS = 500L
        internal const val CAN_EXEC_MAX_WRITE_FAILURE_RETRIES = 2
        internal val CAN_NOT_READY_MESSAGE = ruEn("CAN ГУ не готов", "HU CAN is not ready")
    }
}

internal suspend fun awaitCanBackendReadyForAction(
    isAvailable: () -> Boolean,
    nowElapsedMillis: () -> Long,
    delayFor: suspend (Long) -> Unit,
    timeoutMs: Long = AutomationActionExecutor.CAN_READY_WAIT_TIMEOUT_MS,
    pollMs: Long = AutomationActionExecutor.CAN_READY_POLL_MS,
): Boolean {
    if (isAvailable()) return true
    if (timeoutMs <= 0L) return false
    val deadline = nowElapsedMillis() + timeoutMs
    while (true) {
        val remaining = deadline - nowElapsedMillis()
        if (remaining <= 0L) return isAvailable()
        delayFor(min(pollMs, remaining))
        if (isAvailable()) return true
    }
}

/** Backend not bound yet or pre-read failed: nothing reached the bus. */
internal fun isCanBackendNotReadyFailure(message: String): Boolean {
    val normalized = message.trim().lowercase()
    if (normalized.isEmpty()) return false
    return normalized.contains("mbcan unavailable") ||
        normalized.contains("vhal unavailable") ||
        normalized.contains("car service") ||
        normalized.contains("pre-read failed") ||
        normalized.startsWith("unavailable")
}

/**
 * Write was attempted and rejected. VHAL reports any exception (including unsupported
 * property / value) as "Set failed", so these may be permanent.
 */
internal fun isCanWriteFailure(message: String): Boolean {
    val normalized = message.trim().lowercase()
    return normalized.contains("set command failed") ||
        normalized.contains("set audio command failed") ||
        normalized == "set failed"
}

internal fun isTransientCanStartupFailure(message: String): Boolean =
    isCanBackendNotReadyFailure(message) || isCanWriteFailure(message)

internal suspend fun executeCanWithStartupRetry(
    execute: suspend () -> MbCanCommandResult,
    nowElapsedMillis: () -> Long,
    delayFor: suspend (Long) -> Unit,
    retryWindowMs: Long = AutomationActionExecutor.CAN_EXEC_RETRY_WINDOW_MS,
    retryDelayMs: Long = AutomationActionExecutor.CAN_EXEC_RETRY_DELAY_MS,
    maxWriteFailureRetries: Int = AutomationActionExecutor.CAN_EXEC_MAX_WRITE_FAILURE_RETRIES,
): MbCanCommandResult {
    var result = execute()
    if (result.success || retryWindowMs <= 0L || !isTransientCanStartupFailure(result.message)) {
        return result
    }
    val deadline = nowElapsedMillis() + retryWindowMs
    var writeFailureRetries = 0
    while (!result.success && isTransientCanStartupFailure(result.message)) {
        if (isCanWriteFailure(result.message)) {
            if (writeFailureRetries >= maxWriteFailureRetries) break
            writeFailureRetries += 1
        }
        val remaining = deadline - nowElapsedMillis()
        if (remaining <= 0L) break
        delayFor(min(retryDelayMs, remaining))
        result = execute()
    }
    return result
}
