package vad.dashing.camd

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.camd.ui.theme.CamdTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var controller: CamdController

    private val requestStorage = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startService(Intent(this, CamdService::class.java))
    }

    private val exportFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            Thread {
                val json = controller.exportSettings()
                val ok = runCatching {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
                controller.setMessage(if (ok) "Экспорт сохранён: $uri" else "Не удалось записать файл")
            }.start()
        }
    }

    private val importFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            Thread {
                val text = runCatching {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                }.getOrNull()
                if (text == null) {
                    controller.setMessage("Не удалось прочитать файл")
                    return@Thread
                }
                val summary = controller.importSettings(text)
                controller.setMessage(
                    if (summary == null) {
                        "Файл не распознан: нужен JSON экспорта «Камеры ГУ» версии 1"
                    } else {
                        "Импорт: коррекции ${summary.corrections}, пресеты ${summary.userPresets}+${summary.builtinPresets}, AVM=${summary.keepAvm}"
                    }
                )
            }.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startService(Intent(this, CamdService::class.java))
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startService(Intent(this, CamdService::class.java))
        } else {
            requestStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        controller = CamdController(CamdRuntime.engine(this), CamdRuntime.windows(this)) {
            runOnUiThread {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        setContent {
            CamdTheme(dark = isSystemInDarkTheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val state by controller.state.collectAsState()
                    CamdScreen(
                        state = state,
                        onTab = controller::setTab,
                        onInput = controller::setInput,
                        onX = controller::setX,
                        onY = controller::setY,
                        onW = controller::setW,
                        onH = controller::setH,
                        onRound = controller::setRound,
                        onLensK1 = { controller.setLensField(k1 = it) },
                        onLensK2 = { controller.setLensField(k2 = it) },
                        onLensCx = { controller.setLensField(cx = it) },
                        onLensCy = { controller.setLensField(cy = it) },
                        onLensTiltV = { controller.setLensField(tiltV = it) },
                        onLensTiltH = { controller.setLensField(tiltH = it) },
                        onLensPreset = controller::setLensPreset,
                        onLensReset = controller::resetLens,
                        onOpenAsIs = controller::openAsIs,
                        onClose = controller::close,
                        onOpenPreset = controller::openPreset,
                        onClosePreset = controller::closeCamera,
                        onEditPreset = controller::startEditPreset,
                        onEditCamera = controller::setEditCamera,
                        onDeletePreset = controller::deletePreset,
                        onNewPresetName = controller::setNewPresetName,
                        onCreatePreset = controller::createPreset,
                        onEditRotation = controller::setEditRotation,
                        onEditFlipH = { controller.setEditFlip(horizontal = it, vertical = controller.state.value.editingPreset?.flipV ?: false) },
                        onEditFlipV = { controller.setEditFlip(horizontal = controller.state.value.editingPreset?.flipH ?: false, vertical = it) },
                        onEditCropX = { controller.setEditCropField(x = it) },
                        onEditCropY = { controller.setEditCropField(y = it) },
                        onEditCropW = { controller.setEditCropField(w = it) },
                        onEditCropH = { controller.setEditCropField(h = it) },
                        onSavePreset = controller::saveEditPreset,
                        onCancelEditPreset = controller::cancelEditPreset,
                        onShowCloseButton = controller::setShowCloseButton,
                        onTapToClose = controller::setTapToClose,
                        onExport = {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            exportFile.launch("${CamdBackup.FILE_PREFIX}_$stamp.json")
                        },
                        onImport = { importFile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                        onKeepAvm = controller::setKeepAvmStopped,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.shutdown()
        super.onDestroy()
    }
}

enum class CamdTab { CAMERAS, PRESETS, SETTINGS, BACKUP }

data class CamdUiState(
    val tab: CamdTab = CamdTab.CAMERAS,
    val inputId: Int = 0,
    val x: Float = 0.02f,
    val y: Float = 0.70f,
    val w: Float = 0.28f,
    val h: Float = 0.28f,
    val round: Boolean = false,
    val lens: CamdLensCorrection = CamdLensCorrection.IDENTITY,
    val presets: List<CamdPreset> = emptyList(),
    val editingPreset: CamdPreset? = null,
    val newPresetName: String = "",
    val busy: Boolean = false,
    val keepAvmStopped: Boolean = false,
    val showCloseButton: Boolean = true,
    val tapToClose: Boolean = false,
    val message: String = "Окно откроется после «Открыть». Записи пока нет.",
    val status: String = "",
    val log: String = ""
)

class CamdController(
    private val engine: CamdEngine,
    private val windows: CameraWindows,
    private val askOverlay: () -> Unit
) {
    private val _state = MutableStateFlow(CamdUiState())
    val state: StateFlow<CamdUiState> = _state.asStateFlow()
    private val io = Executors.newSingleThreadExecutor()
    private val poll = Thread {
        while (!Thread.currentThread().isInterrupted) {
            if (!_state.value.busy) {
                _state.value = _state.value.copy(
                    status = windows.status(),
                    log = engine.logTail()
                )
            }
            try {
                Thread.sleep(1000)
            } catch (_: InterruptedException) {
                return@Thread
            }
        }
    }.apply { isDaemon = true; name = "camd-poll"; start() }

    init {
        refreshSettings()
    }

    fun shutdown() {
        io.shutdownNow()
        poll.interrupt()
    }

    private fun refreshSettings() {
        val inputId = _state.value.inputId
        _state.value = _state.value.copy(
            keepAvmStopped = windows.keepAvmStopped(),
            showCloseButton = windows.showCloseButton(),
            tapToClose = windows.tapToClose(),
            lens = windows.lensCorrection(inputId),
            presets = windows.presets(),
        )
    }

    fun setTab(tab: CamdTab) {
        _state.value = _state.value.copy(tab = tab)
    }

    fun setInput(id: Int) {
        _state.value = _state.value.copy(
            inputId = id,
            lens = windows.lensCorrection(id),
        )
    }

    fun setX(value: Float) {
        _state.value = _state.value.copy(x = value)
    }

    fun setY(value: Float) {
        _state.value = _state.value.copy(y = value)
    }

    fun setW(value: Float) {
        _state.value = _state.value.copy(w = value)
    }

    fun setH(value: Float) {
        _state.value = _state.value.copy(h = value)
    }

    fun setRound(value: Boolean) {
        _state.value = _state.value.copy(round = value)
    }

    fun setLensField(
        k1: Float = _state.value.lens.k1,
        k2: Float = _state.value.lens.k2,
        cx: Float = _state.value.lens.cx,
        cy: Float = _state.value.lens.cy,
        tiltV: Float = _state.value.lens.tiltV,
        tiltH: Float = _state.value.lens.tiltH,
    ) {
        applyLens(
            CamdLensCorrection(
                k1 = k1,
                k2 = k2,
                cx = cx,
                cy = cy,
                tiltV = tiltV,
                tiltH = tiltH,
            ),
        )
    }

    fun setLensPreset(value: CamdLensCorrection) {
        applyLens(value)
    }

    fun resetLens() {
        applyLens(CamdLensCorrection.IDENTITY)
    }

    private fun applyLens(value: CamdLensCorrection) {
        val inputId = _state.value.inputId
        val saved = windows.setLensCorrection(inputId, value)
        _state.value = _state.value.copy(lens = saved)
    }

    fun setKeepAvmStopped(keep: Boolean) {
        io.execute {
            _state.value = _state.value.copy(busy = true, keepAvmStopped = keep)
            val result = windows.setKeepAvmStopped(keep)
            _state.value = _state.value.copy(
                busy = false,
                keepAvmStopped = windows.keepAvmStopped(),
                message = result,
                status = windows.status(),
            )
        }
    }

    fun setShowCloseButton(value: Boolean) {
        updateUiOptions(showClose = value)
    }

    fun setTapToClose(value: Boolean) {
        updateUiOptions(tap = value)
    }

    private fun updateUiOptions(
        showClose: Boolean = _state.value.showCloseButton,
        tap: Boolean = _state.value.tapToClose,
    ) {
        windows.setUiOptions(showClose, tap)
        _state.value = _state.value.copy(showCloseButton = showClose, tapToClose = tap)
    }

    fun openAsIs() {
        val current = _state.value
        io.execute {
            _state.value = current.copy(busy = true, message = "Открываю окно…")
            val line = buildString {
                append(
                    String.format(
                        Locale.US,
                        "OPEN %d %.4f %.4f %.4f %.4f",
                        current.inputId,
                        current.x,
                        current.y,
                        current.w,
                        current.h,
                    )
                )
                appendShape(current.round)
            }
            runOpenLine(line)
        }
    }

    fun openPreset(name: String) {
        val current = _state.value
        io.execute {
            _state.value = current.copy(busy = true, message = "Открываю окно…")
            val line = buildString {
                append(
                    String.format(
                        Locale.US,
                        "OPEN preset=%s %.4f %.4f %.4f %.4f",
                        name,
                        current.x,
                        current.y,
                        current.w,
                        current.h,
                    )
                )
                appendShape(current.round)
            }
            runOpenLine(line)
        }
    }

    private fun StringBuilder.appendShape(round: Boolean) {
        if (round) append(" shape=circle")
    }

    private fun runOpenLine(line: String) {
        val command = CamdCommands.parse(line)
        val result = if (command is CamdCommand.Open) {
            windows.open(command)
        } else {
            "state=error error=bad_geometry"
        }
        if (result.contains("overlay_permission")) askOverlay()
        _state.value = _state.value.copy(
            busy = false,
            message = if (result.contains("overlay_permission")) {
                "Не удалось выдать показ поверх других окон."
            } else {
                result
            },
            status = windows.status(),
            log = engine.logTail()
        )
    }

    fun close() {
        closeCamera(_state.value.inputId)
    }

    fun closeCamera(inputId: Int) {
        io.execute {
            _state.value = _state.value.copy(busy = true, message = "Закрываю…")
            val result = windows.close(inputId)
            _state.value = _state.value.copy(
                busy = false,
                message = result,
                status = windows.status(),
                log = engine.logTail()
            )
        }
    }

    fun presets(cameraId: Int): List<CamdPreset> = windows.presets(cameraId)

    fun startEditPreset(preset: CamdPreset) {
        _state.value = _state.value.copy(editingPreset = preset)
    }

    fun cancelEditPreset() {
        _state.value = _state.value.copy(editingPreset = null)
    }

    fun setNewPresetName(value: String) {
        _state.value = _state.value.copy(newPresetName = value)
    }

    fun createPreset() {
        val name = _state.value.newPresetName.trim()
        if (!CamdPreset.isValidName(name)) {
            _state.value = _state.value.copy(
                message = "Имя пресета: строчная латиница, цифры и _, 1–32 символа"
            )
            return
        }
        val existing = windows.preset(name)
        _state.value = _state.value.copy(
            newPresetName = "",
            tab = CamdTab.PRESETS,
            message = if (existing != null) {
                "Пресет «$name» уже есть — открыл его настройки"
            } else {
                "Новый пресет «$name»: выберите источник, настройте и нажмите «Сохранить»"
            },
            editingPreset = existing ?: CamdPreset(name, _state.value.inputId),
        )
    }

    fun setEditCamera(id: Int) {
        updateEdit { it.copy(cameraId = id) }
    }

    fun setEditRotation(degrees: Int) {
        updateEdit { it.copy(rotation = CamdPreset.normalizeRotation(degrees)) }
    }

    fun setEditFlip(horizontal: Boolean, vertical: Boolean) {
        updateEdit { it.copy(flipH = horizontal, flipV = vertical) }
    }

    fun setEditCropField(
        x: Float = _state.value.editingPreset?.crop?.x ?: 0f,
        y: Float = _state.value.editingPreset?.crop?.y ?: 0f,
        w: Float = _state.value.editingPreset?.crop?.w ?: 1f,
        h: Float = _state.value.editingPreset?.crop?.h ?: 1f,
    ) {
        updateEdit { it.copy(crop = CamdCrop(x = x, y = y, w = w, h = h)) }
    }

    private fun updateEdit(block: (CamdPreset) -> CamdPreset) {
        _state.value = _state.value.copy(editingPreset = _state.value.editingPreset?.let(block))
    }

    fun saveEditPreset() {
        val draft = _state.value.editingPreset ?: return
        io.execute {
            val error = if (draft.builtin) {
                if (windows.saveBuiltinPreset(draft.name, draft.rotation, draft.flipH, draft.flipV, draft.crop)) {
                    null
                } else {
                    "Не удалось сохранить встроенный пресет"
                }
            } else {
                windows.saveUserPreset(draft)?.let { token -> describePresetError(token) }
            }
            refreshSettings()
            _state.value = _state.value.copy(
                message = error ?: "Пресет «${draft.name}» сохранён",
                editingPreset = if (error == null) null else draft,
            )
        }
    }

    fun deletePreset(name: String) {
        io.execute {
            val removed = windows.deleteUserPreset(name)
            refreshSettings()
            _state.value = _state.value.copy(
                message = if (removed) "Пресет «$name» удалён" else "Пресет «$name» не найден",
            )
        }
    }

    private fun describePresetError(token: String): String = when (token) {
        "bad_name" -> "Имя пресета: строчная латиница, цифры и _, 1–32 символа"
        "name_taken" -> "Такое имя пресета уже занято"
        "unknown_camera" -> "Неизвестная камера"
        else -> token
    }

    fun exportSettings(): String = windows.exportSettings()

    fun importSettings(text: String): CamdImportSummary? {
        val summary = windows.importSettings(text)
        if (summary != null) refreshSettings()
        return summary
    }

    fun setMessage(text: String) {
        _state.value = _state.value.copy(message = text)
    }
}

@Composable
private fun CamdScreen(
    state: CamdUiState,
    onTab: (CamdTab) -> Unit,
    onInput: (Int) -> Unit,
    onX: (Float) -> Unit,
    onY: (Float) -> Unit,
    onW: (Float) -> Unit,
    onH: (Float) -> Unit,
    onRound: (Boolean) -> Unit,
    onLensK1: (Float) -> Unit,
    onLensK2: (Float) -> Unit,
    onLensCx: (Float) -> Unit,
    onLensCy: (Float) -> Unit,
    onLensTiltV: (Float) -> Unit,
    onLensTiltH: (Float) -> Unit,
    onLensPreset: (CamdLensCorrection) -> Unit,
    onLensReset: () -> Unit,
    onOpenAsIs: () -> Unit,
    onClose: () -> Unit,
    onOpenPreset: (String) -> Unit,
    onClosePreset: (Int) -> Unit,
    onEditPreset: (CamdPreset) -> Unit,
    onEditCamera: (Int) -> Unit,
    onDeletePreset: (String) -> Unit,
    onNewPresetName: (String) -> Unit,
    onCreatePreset: () -> Unit,
    onEditRotation: (Int) -> Unit,
    onEditFlipH: (Boolean) -> Unit,
    onEditFlipV: (Boolean) -> Unit,
    onEditCropX: (Float) -> Unit,
    onEditCropY: (Float) -> Unit,
    onEditCropW: (Float) -> Unit,
    onEditCropH: (Float) -> Unit,
    onSavePreset: () -> Unit,
    onCancelEditPreset: () -> Unit,
    onShowCloseButton: (Boolean) -> Unit,
    onTapToClose: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onKeepAvm: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Камеры ГУ", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.tab == CamdTab.CAMERAS,
                onClick = { onTab(CamdTab.CAMERAS) },
                label = { Text("Камеры", style = MaterialTheme.typography.labelLarge) },
            )
            FilterChip(
                selected = state.tab == CamdTab.PRESETS,
                onClick = { onTab(CamdTab.PRESETS) },
                label = { Text("Пресеты", style = MaterialTheme.typography.labelLarge) },
            )
            FilterChip(
                selected = state.tab == CamdTab.SETTINGS,
                onClick = { onTab(CamdTab.SETTINGS) },
                label = { Text("Настройки", style = MaterialTheme.typography.labelLarge) },
            )
            FilterChip(
                selected = state.tab == CamdTab.BACKUP,
                onClick = { onTab(CamdTab.BACKUP) },
                label = { Text("Импорт/экспорт", style = MaterialTheme.typography.labelLarge) },
            )
        }
        when (state.tab) {
            CamdTab.CAMERAS -> CamerasTab(
                state = state,
                onInput = onInput,
                onX = onX,
                onY = onY,
                onW = onW,
                onH = onH,
                onRound = onRound,
                onLensK1 = onLensK1,
                onLensK2 = onLensK2,
                onLensCx = onLensCx,
                onLensCy = onLensCy,
                onLensTiltV = onLensTiltV,
                onLensTiltH = onLensTiltH,
                onLensPreset = onLensPreset,
                onLensReset = onLensReset,
                onOpenAsIs = onOpenAsIs,
                onClose = onClose,
            )
            CamdTab.SETTINGS -> SettingsTab(
                state = state,
                onKeepAvm = onKeepAvm,
                onShowCloseButton = onShowCloseButton,
                onTapToClose = onTapToClose,
            )
            CamdTab.PRESETS -> PresetsTab(
                state = state,
                onX = onX,
                onY = onY,
                onW = onW,
                onH = onH,
                onRound = onRound,
                onOpenPreset = onOpenPreset,
                onClosePreset = onClosePreset,
                onEditPreset = onEditPreset,
                onEditCamera = onEditCamera,
                onDeletePreset = onDeletePreset,
                onNewPresetName = onNewPresetName,
                onCreatePreset = onCreatePreset,
                onEditRotation = onEditRotation,
                onEditFlipH = onEditFlipH,
                onEditFlipV = onEditFlipV,
                onEditCropX = onEditCropX,
                onEditCropY = onEditCropY,
                onEditCropW = onEditCropW,
                onEditCropH = onEditCropH,
                onSavePreset = onSavePreset,
                onCancelEditPreset = onCancelEditPreset,
            )
            CamdTab.BACKUP -> BackupTab(onExport = onExport, onImport = onImport)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(state.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (state.status.isNotBlank()) {
            Text(
                state.status,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.log.isNotBlank()) {
            Text(
                state.log,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CamerasTab(
    state: CamdUiState,
    onInput: (Int) -> Unit,
    onX: (Float) -> Unit,
    onY: (Float) -> Unit,
    onW: (Float) -> Unit,
    onH: (Float) -> Unit,
    onRound: (Boolean) -> Unit,
    onLensK1: (Float) -> Unit,
    onLensK2: (Float) -> Unit,
    onLensCx: (Float) -> Unit,
    onLensCy: (Float) -> Unit,
    onLensTiltV: (Float) -> Unit,
    onLensTiltH: (Float) -> Unit,
    onLensPreset: (CamdLensCorrection) -> Unit,
    onLensReset: () -> Unit,
    onOpenAsIs: () -> Unit,
    onClose: () -> Unit,
) {
    val input = remember(state.inputId) { KnownInputs.byId(state.inputId) }
    Text(
        "Одно окно на камеру. Одиночное нажатие плитки в TBox Monitor открывает его, двойное закрывает. " +
            "Повторное открытие только двигает окно.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KnownInputs.all.forEach { camera ->
            FilterChip(
                selected = camera.id == state.inputId,
                onClick = { onInput(camera.id) },
                label = { Text(camera.title, style = MaterialTheme.typography.labelLarge) }
            )
        }
    }
    if (input?.heldByAvm == true && !state.keepAvmStopped) {
        Text(
            "Этот вход держит штатный AVM. Пока такое окно открыто, AVM будет остановлен.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text(
        "Окно на экране (предпросмотр)",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        "Эти параметры окна — только для предпросмотра в «Камеры ГУ». В своей программе " +
            "(плитка, автоматизация, DCL) положение и размер окна настраиваются отдельно.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Кадр ${input?.width}×${input?.height}",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    FractionSlider("X", state.x, onX)
    FractionSlider("Y", state.y, onY)
    FractionSlider("Ширина", state.w, onW)
    FractionSlider("Высота", state.h, onH)
    SettingSwitch(
        checked = state.round,
        enabled = !state.busy,
        title = "Круглое окно",
        description = "Изображение вписанным кругом с прозрачными углами — как «кругляши» слепых зон.",
        onCheckedChange = onRound,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onOpenAsIs, enabled = !state.busy) {
            Text("Открыть как есть", style = MaterialTheme.typography.labelLarge)
        }
        Button(onClick = onClose, enabled = !state.busy) {
            Text("Закрыть выбранную", style = MaterialTheme.typography.labelLarge)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text(
        "Визуальная коррекция (на камеру)",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        "Не калибровка объектива и не Brown–Conrady. k1/k2 и сдвиг центра — «рыбий глаз», тилт — перспектива «трапецией». " +
            "Коррекция одна на камеру, действует всегда — и на предпросмотр, и на пресеты, и на команды других программ.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.lens.isIdentity(),
            onClick = onLensReset,
            label = { Text("Сброс", style = MaterialTheme.typography.labelLarge) },
        )
        FilterChip(
            selected = state.lens == CamdLensCorrection.PRESET_MILD_BARREL,
            onClick = { onLensPreset(CamdLensCorrection.PRESET_MILD_BARREL) },
            label = { Text("Лёгкая бочка", style = MaterialTheme.typography.labelLarge) },
        )
        FilterChip(
            selected = state.lens == CamdLensCorrection.PRESET_STRONG_BARREL,
            onClick = { onLensPreset(CamdLensCorrection.PRESET_STRONG_BARREL) },
            label = { Text("Сильная бочка", style = MaterialTheme.typography.labelLarge) },
        )
        FilterChip(
            selected = state.lens == CamdLensCorrection.PRESET_MILD_PINCUSHION,
            onClick = { onLensPreset(CamdLensCorrection.PRESET_MILD_PINCUSHION) },
            label = { Text("Лёгкая подушка", style = MaterialTheme.typography.labelLarge) },
        )
    }
    RangedSlider("k1", state.lens.k1, CamdLensCorrection.K_MIN, CamdLensCorrection.K_MAX, onLensK1)
    RangedSlider("k2", state.lens.k2, CamdLensCorrection.K_MIN, CamdLensCorrection.K_MAX, onLensK2)
    RangedSlider("Центр X", state.lens.cx, CamdLensCorrection.C_MIN, CamdLensCorrection.C_MAX, onLensCx)
    RangedSlider("Центр Y", state.lens.cy, CamdLensCorrection.C_MIN, CamdLensCorrection.C_MAX, onLensCy)
    RangedSlider("Тилт В", state.lens.tiltV, CamdLensCorrection.TILT_MIN, CamdLensCorrection.TILT_MAX, onLensTiltV)
    RangedSlider("Тилт Г", state.lens.tiltH, CamdLensCorrection.TILT_MIN, CamdLensCorrection.TILT_MAX, onLensTiltH)
}

@Composable
private fun SettingsTab(
    state: CamdUiState,
    onKeepAvm: (Boolean) -> Unit,
    onShowCloseButton: (Boolean) -> Unit,
    onTapToClose: (Boolean) -> Unit,
) {
    Text(
        "Настройки приложения «Камеры ГУ». Действуют на предпросмотр и на все окна, которые открывают " +
            "другие программы.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Окна",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    SettingSwitch(
        checked = state.showCloseButton,
        enabled = !state.busy,
        title = "Включить крестик для закрытия окна",
        description = "Кнопка ✕ в углу каждого окна камеры. Применяется к уже открытым окнам сразу.",
        onCheckedChange = onShowCloseButton,
    )
    SettingSwitch(
        checked = state.tapToClose,
        enabled = !state.busy,
        title = "Закрывать окно при нажатии на него",
        description = "Нажатие в любом месте окна закрывает его, зона ✕ работает как раньше.",
        onCheckedChange = onTapToClose,
    )
    if (!state.showCloseButton && !state.tapToClose) {
        Text(
            "Оба переключателя выключены: окно можно закрыть только командой CLOSE " +
                "(например, двойным нажатием плитки в TBox Monitor).",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text(
        "Камеры и AVM",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    SettingSwitch(
        checked = state.keepAvmStopped,
        enabled = !state.busy,
        title = "Держать штатный AVM отключенным",
        description = "Штатный AVM выключается сразу и остаётся выключенным после перезагрузки. " +
            "Выключение опции закрывает сырые окна и сразу включает его.",
        onCheckedChange = onKeepAvm,
    )
}

@Composable
private fun PresetsTab(
    state: CamdUiState,
    onX: (Float) -> Unit,
    onY: (Float) -> Unit,
    onW: (Float) -> Unit,
    onH: (Float) -> Unit,
    onRound: (Boolean) -> Unit,
    onOpenPreset: (String) -> Unit,
    onClosePreset: (Int) -> Unit,
    onEditPreset: (CamdPreset) -> Unit,
    onEditCamera: (Int) -> Unit,
    onDeletePreset: (String) -> Unit,
    onNewPresetName: (String) -> Unit,
    onCreatePreset: () -> Unit,
    onEditRotation: (Int) -> Unit,
    onEditFlipH: (Boolean) -> Unit,
    onEditFlipV: (Boolean) -> Unit,
    onEditCropX: (Float) -> Unit,
    onEditCropY: (Float) -> Unit,
    onEditCropW: (Float) -> Unit,
    onEditCropH: (Float) -> Unit,
    onSavePreset: () -> Unit,
    onCancelEditPreset: () -> Unit,
) {
    Text(
        "Обрезка, поворот и зеркала хранятся в пресете вместе с камерой-источником. " +
            "Параметры окна ниже — только для предпросмотра здесь: в своей программе " +
            "(плитка, автоматизация, DCL) окно задаётся отдельно, а настройки пресета из команды не меняются.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Окно для предпросмотра",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    FractionSlider("X", state.x, onX)
    FractionSlider("Y", state.y, onY)
    FractionSlider("Ширина", state.w, onW)
    FractionSlider("Высота", state.h, onH)
    SettingSwitch(
        checked = state.round,
        enabled = !state.busy,
        title = "Круглое окно",
        description = "Вписанный круг с прозрачными углами. Форма передаётся в команде (shape=circle), как и окно.",
        onCheckedChange = onRound,
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text(
        "Пресеты",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    state.presets.forEach { preset ->
        val camera = remember(preset.cameraId) { KnownInputs.byId(preset.cameraId) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = preset.name +
                    " · " + (camera?.title ?: "камера ${preset.cameraId}") +
                    if (preset.builtin) " (встроенный)" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onOpenPreset(preset.name) }, enabled = !state.busy) {
                Text("Открыть", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { onClosePreset(preset.cameraId) }, enabled = !state.busy) {
                Text("Закрыть", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { onEditPreset(preset) }) {
                Text("Настроить", style = MaterialTheme.typography.labelLarge)
            }
            if (!preset.builtin) {
                TextButton(onClick = { onDeletePreset(preset.name) }) {
                    Text("Удалить", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.newPresetName,
            onValueChange = onNewPresetName,
            label = { Text("имя: a-z 0-9 _") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onCreatePreset) {
            Text("Создать", style = MaterialTheme.typography.labelLarge)
        }
    }
    state.editingPreset?.let { draft ->
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "Настройка пресета «${draft.name}»" + if (draft.builtin) " (встроенный)" else "",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val draftCamera = remember(draft.cameraId) { KnownInputs.byId(draft.cameraId) }
        if (draft.builtin) {
            Text(
                "Источник: ${draftCamera?.title ?: "камера ${draft.cameraId}"} (у встроенного не меняется)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Источник (камера)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KnownInputs.all.forEach { camera ->
                    FilterChip(
                        selected = draft.cameraId == camera.id,
                        onClick = { onEditCamera(camera.id) },
                        label = { Text(camera.title, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
        }
        Text("Поворот", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 90, 180, 270).forEach { degrees ->
                FilterChip(
                    selected = draft.rotation == degrees,
                    onClick = { onEditRotation(degrees) },
                    label = { Text("$degrees°", style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.flipH,
                onClick = { onEditFlipH(!draft.flipH) },
                label = { Text("Зеркало Г", style = MaterialTheme.typography.labelLarge) },
            )
            FilterChip(
                selected = draft.flipV,
                onClick = { onEditFlipV(!draft.flipV) },
                label = { Text("Зеркало В", style = MaterialTheme.typography.labelLarge) },
            )
        }
        Text(
            "Вырезка — часть кадра после коррекции; растягивается на всё окно.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FractionSlider("Вырезка: слева", draft.crop.x, onEditCropX)
        FractionSlider("Вырезка: сверху", draft.crop.y, onEditCropY)
        RangedSlider("Вырезка: ширина", draft.crop.w, CamdCrop.MIN_SIZE, 1f, onEditCropW)
        RangedSlider("Вырезка: высота", draft.crop.h, CamdCrop.MIN_SIZE, 1f, onEditCropH)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSavePreset, enabled = !state.busy) {
                Text("Сохранить", style = MaterialTheme.typography.labelLarge)
            }
            Button(onClick = onCancelEditPreset) {
                Text("Отмена", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun BackupTab(
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Text(
        "Экспорт/импорт: коррекции всех камер (включая тилт), все пресеты (свои и изменённые встроенные) " +
            "и режим AVM одним JSON-файлом.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onExport) {
            Text("Экспорт", style = MaterialTheme.typography.labelLarge)
        }
        Button(onClick = onImport) {
            Text("Импорт", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FractionSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    RangedSlider(label, value, 0f, 1f, onChange)
}

@Composable
private fun RangedSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label  ${String.format(Locale.US, "%.3f", value)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max,
        )
    }
}

@Composable
private fun SettingSwitch(
    checked: Boolean,
    enabled: Boolean,
    title: String,
    description: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val descColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = titleColor)
            if (description.isNotBlank()) {
                Text(text = description, style = MaterialTheme.typography.bodyLarge, color = descColor)
            }
        }
    }
}
