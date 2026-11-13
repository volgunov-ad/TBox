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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var controller: CamdController

    private val requestStorage = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startService(Intent(this, CamdService::class.java))
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
                        onInput = controller::setInput,
                        onX = controller::setX,
                        onY = controller::setY,
                        onW = controller::setW,
                        onH = controller::setH,
                         onRotation = controller::setRotation,
                         onFlipH = { controller.setFlip(horizontal = it, vertical = controller.state.value.flipV) },
                         onFlipV = { controller.setFlip(horizontal = controller.state.value.flipH, vertical = it) },
                         onLensK1 = { controller.setLensField(k1 = it) },
                         onLensK2 = { controller.setLensField(k2 = it) },
                         onLensCx = { controller.setLensField(cx = it) },
                         onLensCy = { controller.setLensField(cy = it) },
                         onLensPreset = controller::setLensPreset,
                         onLensReset = controller::resetLens,
                         onCropToggle = controller::toggleCrop,
                         onCropX = { controller.setCropField(x = it) },
                         onCropY = { controller.setCropField(y = it) },
                         onCropW = { controller.setCropField(w = it) },
                         onCropH = { controller.setCropField(h = it) },
                         onCropReset = controller::resetCrop,
                        onOpen = controller::open,
                        onClose = controller::close,
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

data class CamdUiState(
    val inputId: Int = 0,
    val x: Float = 0.02f,
    val y: Float = 0.70f,
    val w: Float = 0.28f,
    val h: Float = 0.28f,
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val lens: CamdLensCorrection = CamdLensCorrection.IDENTITY,
    val crop: CamdCrop = CamdCrop.IDENTITY,
    val busy: Boolean = false,
    val keepAvmStopped: Boolean = false,
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
        val inputId = _state.value.inputId
        _state.value = _state.value.copy(
            keepAvmStopped = windows.keepAvmStopped(),
            lens = windows.lensCorrection(inputId),
            crop = windows.crop(inputId),
        )
    }

    fun shutdown() {
        io.shutdownNow()
        poll.interrupt()
    }

    fun setInput(id: Int) {
        _state.value = _state.value.copy(
            inputId = id,
            lens = windows.lensCorrection(id),
            crop = windows.crop(id),
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

    fun setRotation(degrees: Int) {
        val rotation = when (degrees) {
            90, 180, 270 -> degrees
            else -> 0
        }
        _state.value = _state.value.copy(rotation = rotation)
    }

    fun setFlip(horizontal: Boolean, vertical: Boolean) {
        _state.value = _state.value.copy(flipH = horizontal, flipV = vertical)
    }

    fun setLensField(
        k1: Float = _state.value.lens.k1,
        k2: Float = _state.value.lens.k2,
        cx: Float = _state.value.lens.cx,
        cy: Float = _state.value.lens.cy,
    ) {
        applyLens(
            CamdLensCorrection(
                k1 = k1,
                k2 = k2,
                cx = cx,
                cy = cy,
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

    fun setCropField(
        x: Float = _state.value.crop.x,
        y: Float = _state.value.crop.y,
        w: Float = _state.value.crop.w,
        h: Float = _state.value.crop.h,
    ) {
        applyCrop(CamdCrop(x = x, y = y, w = w, h = h))
    }

    fun resetCrop() {
        applyCrop(CamdCrop.IDENTITY)
    }

    fun toggleCrop(enabled: Boolean) {
        val current = _state.value.crop
        applyCrop(
            if (enabled) {
                if (current.isIdentity()) CamdCrop(0.1f, 0.1f, 0.8f, 0.8f) else current
            } else {
                CamdCrop.IDENTITY
            }
        )
    }

    private fun applyCrop(value: CamdCrop) {
        val inputId = _state.value.inputId
        val saved = windows.setCrop(inputId, value)
        _state.value = _state.value.copy(crop = saved)
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

    fun open() {
        val current = _state.value
        io.execute {
            _state.value = current.copy(busy = true, message = "Открываю окно…")
            val line = StringBuilder(
                String.format(
                    Locale.US,
                    "OPEN %d %.4f %.4f %.4f %.4f %d %d %d",
                    current.inputId,
                    current.x,
                    current.y,
                    current.w,
                    current.h,
                    current.rotation,
                    if (current.flipH) 1 else 0,
                    if (current.flipV) 1 else 0,
                )
            )
            val cropValue = current.crop
            if (!cropValue.isIdentity()) {
                line.append(
                    String.format(
                        Locale.US,
                        " crop=%.4f,%.4f,%.4f,%.4f",
                        cropValue.x,
                        cropValue.y,
                        cropValue.w,
                        cropValue.h,
                    )
                )
            }
            val command = CamdCommands.parse(line.toString())
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
    }

    fun close() {
        val inputId = _state.value.inputId
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
}

@Composable
private fun CamdScreen(
    state: CamdUiState,
    onInput: (Int) -> Unit,
    onX: (Float) -> Unit,
    onY: (Float) -> Unit,
    onW: (Float) -> Unit,
    onH: (Float) -> Unit,
    onRotation: (Int) -> Unit,
    onFlipH: (Boolean) -> Unit,
    onFlipV: (Boolean) -> Unit,
    onLensK1: (Float) -> Unit,
    onLensK2: (Float) -> Unit,
    onLensCx: (Float) -> Unit,
    onLensCy: (Float) -> Unit,
    onLensPreset: (CamdLensCorrection) -> Unit,
    onLensReset: () -> Unit,
    onCropToggle: (Boolean) -> Unit,
    onCropX: (Float) -> Unit,
    onCropY: (Float) -> Unit,
    onCropW: (Float) -> Unit,
    onCropH: (Float) -> Unit,
    onCropReset: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onKeepAvm: (Boolean) -> Unit,
) {
    val input = remember(state.inputId) { KnownInputs.byId(state.inputId) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Камеры ГУ", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
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
        SettingSwitch(
            checked = state.keepAvmStopped,
            enabled = !state.busy,
            title = "Держать штатный AVM отключенным",
            description = "Штатный AVM выключается сразу и остаётся выключенным после перезагрузки. " +
                "Выключение опции закрывает сырые окна и сразу включает его.",
            onCheckedChange = onKeepAvm,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "Кадр ${input?.width}×${input?.height}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FractionSlider("X", state.x, onX)
        FractionSlider("Y", state.y, onY)
        FractionSlider("Ширина", state.w, onW)
        FractionSlider("Высота", state.h, onH)
        Text("Поворот", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 90, 180, 270).forEach { degrees ->
                FilterChip(
                    selected = state.rotation == degrees,
                    onClick = { onRotation(degrees) },
                    label = { Text("$degrees°", style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
        Text(
            "Зеркалирование",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.flipH,
                onClick = { onFlipH(!state.flipH) },
                label = { Text("По горизонтали", style = MaterialTheme.typography.labelLarge) },
            )
            FilterChip(
                selected = state.flipV,
                onClick = { onFlipV(!state.flipV) },
                label = { Text("По вертикали", style = MaterialTheme.typography.labelLarge) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpen, enabled = !state.busy) {
                Text("Открыть", style = MaterialTheme.typography.labelLarge)
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
            "Не калибровка объектива и не Brown–Conrady. Только превью: k1/k2 и опциональный сдвиг центра. " +
                "Сохраняется в «Камеры ГУ»; DCL и Monitor получают уже выпрямленную картинку без коэффициентов в OPEN.",
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
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "Кадрирование (на камеру)",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Выбранная часть кадра (после коррекции объектива) растягивается на всё окно. " +
                "Сохраняется в «Камеры ГУ» и применяется, когда OPEN пришёл без crop=.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingSwitch(
            checked = !state.crop.isIdentity(),
            enabled = !state.busy,
            title = "Кадрировать",
            description = "Включение подставляет центральные 80% кадра; выключение возвращает полный кадр.",
            onCheckedChange = onCropToggle,
        )
        FractionSlider("Слева", state.crop.x, onCropX)
        FractionSlider("Сверху", state.crop.y, onCropY)
        RangedSlider("Ширина", state.crop.w, CamdCrop.MIN_SIZE, 1f, onCropW)
        RangedSlider("Высота", state.crop.h, CamdCrop.MIN_SIZE, 1f, onCropH)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.crop.isIdentity(),
                onClick = onCropReset,
                label = { Text("Сброс", style = MaterialTheme.typography.labelLarge) },
            )
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
            Text(text = description, style = MaterialTheme.typography.bodyLarge, color = descColor)
        }
    }
}
