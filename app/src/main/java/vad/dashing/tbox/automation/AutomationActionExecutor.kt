package vad.dashing.tbox.automation

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
import vad.dashing.tbox.CarDataRepository
import vad.dashing.tbox.HeadUnitDayNightRepository
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.MirrorAdjustModeRepository
import vad.dashing.tbox.PagingStateNormalizer
import vad.dashing.tbox.PlatformAudioDomain
import vad.dashing.tbox.PlatformAudioRepository
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.browserUrlFromHttpRequestYaml
import vad.dashing.tbox.executeHttpRequestWidget
import vad.dashing.tbox.freeform.FreeformCompanionSession
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchHelper
import vad.dashing.tbox.httpRequestWidgetErrorMessage
import vad.dashing.tbox.httpRequestWidgetIsSuccess
import vad.dashing.tbox.location.GeoDebugLogRecorder
import vad.dashing.tbox.location.MockLocationWidgetCycle
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.openHttpRequestWidgetUrlInBrowser
import vad.dashing.tbox.parseHttpRequestWidgetYaml
import vad.dashing.tbox.ui.LeftMenuLayout

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
                    "Действие ${index + 1}: ${result.message}",
                )
            }
        }
        return AutomationActionResult.ok("Выполнено действий: ${actions.size}")
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
                    ?: return@withLock AutomationActionResult.failure("CAN-команда не разрешена")
                if (!entry.isActionAllowed(action)) {
                    return@withLock AutomationActionResult.failure(
                        "Недопустимая операция или значение CAN",
                    )
                }
                if (!entry.supports(UniversalCanRepository.mode.value)) {
                    return@withLock AutomationActionResult.failure(
                        "CAN-действие не подтверждено для текущего backend ГУ",
                    )
                }
                val command = when (action.bus) {
                    AutomationCanBus.VEHICLE -> when (action.operation) {
                        AutomationCanOperation.SET ->
                            MbCanCommand.SetProperty(action.propertyId, action.value)

                        AutomationCanOperation.TOGGLE ->
                            MbCanCommand.ToggleProperty(action.propertyId)

                        AutomationCanOperation.TRUNK_PULSE ->
                            MbCanCommand.TrunkPulse(action.value)
                    }

                    AutomationCanBus.AUDIO -> when (action.operation) {
                        AutomationCanOperation.SET ->
                            MbCanCommand.SetAudioProperty(action.propertyId, action.value)

                        AutomationCanOperation.TOGGLE ->
                            MbCanCommand.ToggleAudioProperty(action.propertyId)

                        AutomationCanOperation.TRUNK_PULSE ->
                            return@withLock AutomationActionResult.failure(
                                "Импульс багажника не относится к аудио",
                            )
                    }
                }
                val result = UniversalCanRepository.execute(command)
                AutomationActionResult(result.success, result.message)
            }
        }
    }

    private suspend fun launchApplication(
        action: AutomationAction.LaunchApplication,
    ): AutomationActionResult {
        val packageName = action.packageName.trim()
        if (packageName.isEmpty()) {
            return AutomationActionResult.failure("Не выбрано приложение")
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
                    AutomationActionResult.ok("Freeform запущен")
                } else {
                    AutomationActionResult.failure("Не удалось запустить freeform")
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
                    if (launched) "Приложение запущено" else "Запуск приложения не удался",
                )
            }

            AppLauncherLaunchMode.FULLSCREEN -> {
                if (appContext.packageManager.getLaunchIntentForPackage(packageName) == null) {
                    return AutomationActionResult.failure("Приложение не установлено")
                }
                val launched = awaitAfterWindowModeExit {
                    launchFullscreen(packageName)
                }
                AutomationActionResult(
                    launched,
                    if (launched) "Приложение запущено" else "Запуск приложения не удался",
                )
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

    private suspend fun openMainScreen(
        action: AutomationAction.OpenMainScreen,
    ): AutomationActionResult {
        val pageCount = settingsManager.mainScreenPageCountFlow.first()
        val page = PagingStateNormalizer.normalizeCurrentPage(action.page, pageCount)
        return when (action.target) {
            AutomationMainScreenTarget.CURRENT_WINDOW -> {
                if (FreeformCompanionSession.pinOverlayPage(page)) {
                    AutomationActionResult.ok("Страница оконного режима: $page")
                } else {
                    AutomationActionResult.failure("Оконный режим не активен")
                }
            }

            AutomationMainScreenTarget.FULLSCREEN -> {
                settingsManager.saveMainScreenCurrentPage(page)
                settingsManager.saveSelectedTab(SettingsManager.MAIN_SCREEN_TAB_KEY)
                val opened = awaitAfterWindowModeExit(::bringMainActivityToFront)
                AutomationActionResult(
                    opened,
                    if (opened) "Главный экран, страница $page" else "Главный экран не открыт",
                )
            }
        }
    }

    private suspend fun executeHttp(
        action: AutomationAction.HttpRequest,
    ): AutomationActionResult {
        if (action.openBrowser) {
            val url = browserUrlFromHttpRequestYaml(action.yaml).getOrElse {
                return AutomationActionResult.failure(it.message ?: "Некорректный URL")
            }
            return if (openHttpRequestWidgetUrlInBrowser(appContext, url)) {
                AutomationActionResult.ok("Браузер открыт")
            } else {
                AutomationActionResult.failure("Не удалось открыть браузер")
            }
        }
        val config = parseHttpRequestWidgetYaml(action.yaml).getOrElse {
            return AutomationActionResult.failure(it.message ?: "Некорректный HTTP YAML")
        }
        val result = executeHttpRequestWidget(config)
        return AutomationActionResult(
            success = httpRequestWidgetIsSuccess(result),
            message = if (httpRequestWidgetIsSuccess(result)) {
                "HTTP выполнен"
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
            AutomationActionResult.ok("Меню открыто")
        }

        AutomationBuiltinActionType.FINISH_AND_START_TRIP ->
            serviceActions.finishAndStartTrip()

        AutomationBuiltinActionType.RESET_MOTOR_HOURS -> {
            CarDataRepository.setMotorHours(0f)
            appDataManager.saveMotorHours(0f)
            CarDataRepository.markPersisted(0f)
            AutomationActionResult.ok("Моточасы сброшены")
        }

        AutomationBuiltinActionType.RESTART_TBOX ->
            serviceActions.restartTbox()

        AutomationBuiltinActionType.TOGGLE_APP_DAY_NIGHT_THEME -> {
            val ok = withContext(Dispatchers.Main) {
                HeadUnitDayNightRepository.toggleManualTheme(appContext)
            }
            AutomationActionResult(ok, if (ok) "Тема переключена" else "Тема не переключена")
        }

        AutomationBuiltinActionType.ENABLE_HEAD_UNIT_AUTO_THEME -> {
            val ok = withContext(Dispatchers.Main) {
                HeadUnitDayNightRepository.enableAutoMode(appContext)
            }
            AutomationActionResult(ok, if (ok) "Автотема включена" else "Автотема недоступна")
        }

        AutomationBuiltinActionType.TOGGLE_MIRROR_ADJUST_MODE -> {
            val ok = runCatching {
                MirrorAdjustModeRepository.toggleMirrorAdjustMode(appContext)
                true
            }.getOrDefault(false)
            AutomationActionResult(ok, if (ok) "Режим зеркал переключён" else "Ошибка режима зеркал")
        }

        AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS ->
            serviceActions.toggleHideFloatingPanels()

        AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED ->
            serviceActions.toggleFloatingPanelsEnabled()

        AutomationBuiltinActionType.ESP_RELAY_SET ->
            serviceActions.setEspRelayMask(action.intValue)

        AutomationBuiltinActionType.ESP_RELAY_TOGGLE ->
            serviceActions.toggleEspRelay(action.intValue)

        AutomationBuiltinActionType.ESP_RELAY_PULSE -> serviceActions.pulseEspRelay(
            channel = action.intValue,
            durationMillis = action.stringValue.toLongOrNull()?.takeIf { it > 0L },
        )

        AutomationBuiltinActionType.MEDIA_PREVIOUS -> mediaAction(action) {
            packages, preferred -> SharedMediaControlService.skipToPrevious(packages, preferred)
        }

        AutomationBuiltinActionType.MEDIA_PLAY_PAUSE -> mediaAction(action) { packages, preferred ->
            SharedMediaControlService.playPause(appContext, packages, preferred)
        }

        AutomationBuiltinActionType.MEDIA_PLAY -> mediaAction(action) { packages, preferred ->
            SharedMediaControlService.play(appContext, packages, preferred)
        }

        AutomationBuiltinActionType.MEDIA_NEXT -> mediaAction(action) { packages, preferred ->
            SharedMediaControlService.skipToNext(packages, preferred)
        }

        AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE -> mediaAction(action) { packages, preferred ->
            SharedMediaControlService.toggleHeartRating(packages, preferred)
        }

        AutomationBuiltinActionType.SET_MEDIA_VOLUME -> {
            val ok = PlatformAudioRepository.setVolume(
                PlatformAudioDomain.VolumeChannel.Media,
                action.intValue,
            )
            AutomationActionResult(ok, if (ok) "Громкость установлена" else "Ошибка громкости")
        }

        AutomationBuiltinActionType.CYCLE_MOCK_LOCATION_MODE -> {
            val next = MockLocationWidgetCycle.next(
                settingsManager.mockPowerStateFlow.first(),
                settingsManager.mockCanSpeedModeFlow.first(),
            )
            settingsManager.saveMockPowerAndModeSetting(next.power, next.mode)
            AutomationActionResult.ok("Режим подмены геопозиции переключён")
        }

        AutomationBuiltinActionType.GNSS_MODULE_REBOOT ->
            serviceActions.rebootGnssModule()

        AutomationBuiltinActionType.SET_SIMULATED_LOCATION_SOURCE_LOSS ->
            serviceActions.setSimulatedLocationSourceLoss(action.boolValue)

        AutomationBuiltinActionType.SET_GEO_DEBUG_LOG -> {
            if (action.boolValue) GeoDebugLogRecorder.start() else GeoDebugLogRecorder.stop()
            AutomationActionResult.ok(
                if (action.boolValue) "Запись гео-журнала запущена" else "Запись остановлена",
            )
        }

        AutomationBuiltinActionType.SHOW_TOAST -> {
            val text = action.stringValue.trim()
            if (text.isEmpty()) {
                AutomationActionResult.failure("Текст пуст")
            } else {
                AutomationUserMessageOverlay.showToast(appContext, text)
                AutomationActionResult.ok("Toast показан")
            }
        }

        AutomationBuiltinActionType.SHOW_ALERT -> {
            val text = action.stringValue.trim()
            if (text.isEmpty()) {
                AutomationActionResult.failure("Текст пуст")
            } else if (
                !AutomationUserMessageOverlay.showCloseableMessage(
                    context = appContext,
                    text = text,
                    autoCloseMillis = action.intValue.toLong().coerceAtLeast(0L),
                )
            ) {
                AutomationActionResult.failure(
                    "Нет разрешения «поверх других окон» для сообщения на экране",
                )
            } else {
                AutomationActionResult.ok("Сообщение закрыто")
            }
        }
    }

    private fun mediaAction(
        action: AutomationAction.Builtin,
        block: (Set<String>, String) -> Unit,
    ): AutomationActionResult {
        val preferred = action.stringValue.trim()
        if (preferred.isEmpty()) {
            return AutomationActionResult.failure("Не выбран медиаплеер")
        }
        val packages = setOf(preferred)
        SharedMediaControlService.updateSourceSelection(appContext, MEDIA_SOURCE_ID, packages)
        return runCatching {
            block(packages, preferred)
            AutomationActionResult.ok("Медиа-команда отправлена")
        }.getOrElse {
            AutomationActionResult.failure(it.message ?: "Ошибка медиа-команды")
        }.also {
            SharedMediaControlService.clearSourceSelection(MEDIA_SOURCE_ID)
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
    }
}
