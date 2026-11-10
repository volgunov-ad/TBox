package vad.dashing.camd

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val requestStorage = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startService(Intent(this, CamdService::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startService(Intent(this, CamdService::class.java))
        } else {
            requestStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val controller = CamdController(CamdRuntime.engine(this), CamdRuntime.windows(this)) {
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
            MaterialTheme(colorScheme = darkColorScheme()) {
                val state by controller.state.collectAsState()
                CamdScreen(
                    state = state,
                    onInput = controller::setInput,
                    onX = controller::setX,
                    onY = controller::setY,
                    onW = controller::setW,
                    onH = controller::setH,
                    onOpen = controller::open,
                    onClose = controller::close,
                    onProbeKeepAvm = { controller.probe(false) },
                    onProbeStopAvm = { controller.probe(true) }
                )
            }
        }
    }
}

data class CamdUiState(
    val inputId: Int = 0,
    val x: Float = 0.02f,
    val y: Float = 0.70f,
    val w: Float = 0.28f,
    val h: Float = 0.28f,
    val busy: Boolean = false,
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

    init {
        Thread {
            while (!Thread.currentThread().isInterrupted) {
                if (!_state.value.busy) {
                    _state.value = _state.value.copy(
                        status = windows.status(),
                        log = engine.logTail()
                    )
                }
                Thread.sleep(1000)
            }
        }.apply { isDaemon = true; name = "camd-poll"; start() }
    }

    fun setInput(id: Int) {
        _state.value = _state.value.copy(inputId = id)
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

    fun open() {
        val current = _state.value
        io.execute {
            _state.value = current.copy(busy = true, message = "Открываю окно…")
            val line = String.format(
                Locale.US,
                "OPEN %d %.4f %.4f %.4f %.4f",
                current.inputId,
                current.x,
                current.y,
                current.w,
                current.h
            )
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

    fun probe(stopAvm: Boolean) {
        val input = KnownInputs.byId(_state.value.inputId) ?: return
        io.execute {
            val title = if (stopAvm) {
                "AVM остановлен на 20 секунд. Смотрите центр экрана."
            } else {
                "AVM работает. 20 секунд смотрите центр экрана."
            }
            _state.value = _state.value.copy(busy = true, message = title)
            val result = engine.runProbe(stopAvm, input)
            _state.value = _state.value.copy(
                busy = false,
                message = "Лог: $result",
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
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onProbeKeepAvm: () -> Unit,
    onProbeStopAvm: () -> Unit
) {
    val input = remember(state.inputId) { KnownInputs.byId(state.inputId) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Окно камеры", style = MaterialTheme.typography.titleLarge)
        Text(
            "Окно приложения, по одному на камеру. Крестик в углу закрывает его. " +
                "Повторное открытие той же камеры только двигает окно. " +
                "API 127.0.0.1:${DaemonScripts.API_PORT}: OPEN id x y w h / CLOSE id / CLOSE / STATUS",
            fontSize = 13.sp
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KnownInputs.all.forEach { camera ->
                FilterChip(
                    selected = camera.id == state.inputId,
                    onClick = { onInput(camera.id) },
                    label = { Text(camera.title, fontSize = 12.sp) }
                )
            }
        }
        if (input?.heldByAvm == true) {
            Text(
                "Этот вход держит штатный AVM. Пока такое окно открыто, AVM будет остановлен.",
                fontSize = 13.sp
            )
        }
        Text("Кадр ${input?.width}×${input?.height}", fontSize = 13.sp)
        FractionSlider("X", state.x, onX)
        FractionSlider("Y", state.y, onY)
        FractionSlider("Ширина", state.w, onW)
        FractionSlider("Высота", state.h, onH)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpen, enabled = !state.busy) { Text("Открыть") }
            Button(onClick = onClose, enabled = !state.busy) { Text("Закрыть выбранную") }
        }
        Text(
            "Проба 20 секунд, окно по центру. Лог появится в Загрузках.",
            fontSize = 13.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onProbeKeepAvm, enabled = !state.busy) { Text("Проба, AVM работает") }
            Button(onClick = onProbeStopAvm, enabled = !state.busy) { Text("Проба, AVM остановлен") }
        }
        Text(state.message, fontSize = 13.sp)
        if (state.status.isNotBlank()) {
            Text(state.status, fontSize = 12.sp)
        }
        if (state.log.isNotBlank()) {
            Text(state.log, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FractionSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label  ${String.format(Locale.US, "%.2f", value)}", fontSize = 13.sp)
        Slider(value = value.coerceIn(0f, 1f), onValueChange = onChange, valueRange = 0f..1f)
    }
}
