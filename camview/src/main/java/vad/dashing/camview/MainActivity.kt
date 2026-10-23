package vad.dashing.camview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val controller = MengboCamController()
    private var overlayContainer: FrameLayout? = null
    private var overlayImageView: ImageView? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCmdIntent(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val state by controller.state.collectAsState()
                LaunchedEffect(
                    state.floatingEnabled,
                    state.bitmap,
                    state.windowWidthDp,
                    state.windowHeightDp
                ) {
                    syncFloatingOverlay(state)
                }
                CamViewScreen(
                    state = state,
                    controller = controller,
                    onToggleFloating = { toggleFloatingOverlay() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCmdIntent(intent)
    }

    private fun handleCmdIntent(intent: Intent?) {
        val cmd = intent?.getStringExtra("cmd")?.trim() ?: return
        when (cmd) {
            "avm_init" -> {
                controller.setChannel(CamChannel.AVM)
                controller.startAvm()
            }
            "avm_view" -> {
                val code = intent.getIntExtra("view", 5)
                val match = AvmViewCmd.entries.firstOrNull { it.code == code } ?: AvmViewCmd.KEY_DOWN
                controller.sendAvmView(match)
            }
            "oms_start" -> {
                controller.setChannel(CamChannel.OMS)
                controller.startOms()
            }
            "dms_start" -> {
                controller.setChannel(CamChannel.DMS)
                controller.startDms()
            }
            "shm_avm" -> {
                controller.setChannel(CamChannel.SHM)
                controller.readShareFileOnce("/early_services/earlydata/avm_sharecamera", 1280, 720, 0)
            }
        }
    }

    private fun toggleFloatingOverlay() {
        val current = controller.state.value.floatingEnabled
        if (!current && !Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        controller.setFloatingEnabled(!current)
    }

    private fun syncFloatingOverlay(state: CamUiState) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (!state.floatingEnabled || !Settings.canDrawOverlays(this)) {
            overlayContainer?.let {
                runCatching { wm.removeView(it) }
            }
            overlayContainer = null
            overlayImageView = null
            overlayParams = null
            return
        }
        val density = resources.displayMetrics.density
        val wPx = (state.windowWidthDp * density).roundToInt().coerceAtLeast(160)
        val hPx = (state.windowHeightDp * density).roundToInt().coerceAtLeast(100)

        if (overlayContainer == null) {
            val container = FrameLayout(this).apply {
                setBackgroundColor(0xDD11161E.toInt())
                setPadding(4, 4, 4, 4)
            }
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_XY
            }
            container.addView(
                iv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            val lp = WindowManager.LayoutParams(
                wPx,
                hPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (state.offsetXDp * density).roundToInt()
                y = (state.offsetYDp * density).roundToInt()
            }

            var downRawX = 0f
            var downRawY = 0f
            var startX = 0
            var startY = 0
            container.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = ev.rawX
                        downRawY = ev.rawY
                        startX = lp.x
                        startY = lp.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = (startX + (ev.rawX - downRawX)).roundToInt()
                        lp.y = (startY + (ev.rawY - downRawY)).roundToInt()
                        runCatching { wm.updateViewLayout(v, lp) }
                        true
                    }
                    else -> false
                }
            }

            runCatching {
                wm.addView(container, lp)
                overlayContainer = container
                overlayImageView = iv
                overlayParams = lp
            }
        } else {
            val lp = overlayParams
            val container = overlayContainer
            if (lp != null && container != null && (lp.width != wPx || lp.height != hPx)) {
                lp.width = wPx
                lp.height = hPx
                runCatching { wm.updateViewLayout(container, lp) }
            }
        }

        state.bitmap?.let { bmp: Bitmap ->
            overlayImageView?.setImageBitmap(bmp)
        }
    }

    override fun onDestroy() {
        controller.setFloatingEnabled(false)
        overlayContainer?.let {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            runCatching { wm.removeView(it) }
        }
        controller.stopAll()
        super.onDestroy()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CamViewScreen(
    state: CamUiState,
    controller: MengboCamController,
    onToggleFloating: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D1117)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Левая панель управления
            Column(
                modifier = Modifier
                    .width(470.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF161B22))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Тест камер Mengbo SDK (Путь 1)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = state.statusLine,
                    fontSize = 12.sp,
                    color = Color(0xFF58A6FF)
                )
                Text(
                    text = "Кадров: ${state.frameCount} | FPS: ${"%.1f".format(state.fps)} | ${state.lastMeta}",
                    fontSize = 11.sp,
                    color = Color(0xFF8B949E)
                )

                // Выбор канала
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CamChannel.entries.forEach { ch ->
                        FilterChip(
                            selected = state.activeChannel == ch,
                            onClick = { controller.setChannel(ch) },
                            label = { Text(ch.title, fontSize = 11.sp) }
                        )
                    }
                }

                // Управление выбранным каналом
                when (state.activeChannel) {
                    CamChannel.AVM -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { controller.startAvm() }) {
                                Text("1. Init @MengboAVM", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { controller.stopAvm() }) {
                                Text("UnInit AVM", fontSize = 12.sp)
                            }
                        }
                        Text(
                            text = "Ракурсы и камеры AVM (nativeSendViewCtrol):",
                            fontSize = 12.sp,
                            color = Color(0xFFC9D1D9)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AvmViewCmd.entries.forEach { cmd ->
                                OutlinedButton(
                                    onClick = { controller.sendAvmView(cmd) }
                                ) {
                                    Text(cmd.label, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    CamChannel.OMS -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { controller.startOms() }) {
                                Text("Старт OMS (@MengboOMS)", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { controller.stopOms() }) {
                                Text("Стоп OMS", fontSize = 12.sp)
                            }
                        }
                    }

                    CamChannel.DMS -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { controller.startDms() }) {
                                Text("Старт DMS (@MengboDMS)", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { controller.stopDms() }) {
                                Text("Стоп DMS", fontSize = 12.sp)
                            }
                        }
                    }

                    CamChannel.SHM -> {
                        Text(
                            text = "Чтение /early_services/earlydata/*_sharecamera напрямую:",
                            fontSize = 12.sp,
                            color = Color(0xFFC9D1D9)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(onClick = {
                                controller.readShareFileOnce(
                                    "/early_services/earlydata/avm_sharecamera",
                                    1280,
                                    720,
                                    0
                                )
                            }) { Text("AVM 1280x720", fontSize = 11.sp) }

                            Button(onClick = {
                                controller.readShareFileOnce(
                                    "/early_services/earlydata/avm_sharecamera",
                                    2560,
                                    1920,
                                    0
                                )
                            }) { Text("AVM 2560x1920", fontSize = 11.sp) }

                            Button(onClick = {
                                controller.readShareFileOnce(
                                    "/early_services/earlydata/oms_sharecamera",
                                    1280,
                                    720,
                                    3
                                )
                            }) { Text("OMS 1280x720 (off=3)", fontSize = 11.sp) }

                            Button(onClick = {
                                controller.readShareFileOnce(
                                    "/early_services/earlydata/dms_sharecamera",
                                    1280,
                                    722,
                                    3
                                )
                            }) { Text("DMS 1280x722 (off=3)", fontSize = 11.sp) }
                        }
                    }
                }

                // Формат декодирования пикселей
                Text(
                    text = "Формат буфера pImageBuf:",
                    fontSize = 12.sp,
                    color = Color(0xFFC9D1D9)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PixelDecodeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.decodeMode == mode,
                            onClick = { controller.setDecodeMode(mode) },
                            label = { Text(mode.label, fontSize = 10.sp) }
                        )
                    }
                }

                // Размер и положение нашего окна
                Text(
                    text = "Размер окна: ${state.windowWidthDp} × ${state.windowHeightDp} dp (положение X=${state.offsetXDp}, Y=${state.offsetYDp} dp, можно тащить мышью/пальцем)",
                    fontSize = 12.sp,
                    color = Color(0xFFC9D1D9)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Ш:", color = Color.White, fontSize = 11.sp)
                    Slider(
                        value = state.windowWidthDp.toFloat(),
                        onValueChange = { controller.updateWindowGeometry(widthDp = it.roundToInt()) },
                        valueRange = 200f..1400f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("В:", color = Color.White, fontSize = 11.sp)
                    Slider(
                        value = state.windowHeightDp.toFloat(),
                        onValueChange = { controller.updateWindowGeometry(heightDp = it.roundToInt()) },
                        valueRange = 120f..950f,
                        modifier = Modifier.weight(1f)
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        controller.updateWindowGeometry(widthDp = 420, heightDp = 260)
                    }) { Text("Малое 420×260", fontSize = 11.sp) }
                    OutlinedButton(onClick = {
                        controller.updateWindowGeometry(widthDp = 720, heightDp = 450)
                    }) { Text("Среднее 720×450", fontSize = 11.sp) }
                    OutlinedButton(onClick = {
                        controller.updateWindowGeometry(widthDp = 1100, heightDp = 700, xDp = 8, yDp = 8)
                    }) { Text("Крупное 1100×700", fontSize = 11.sp) }
                    Button(
                        onClick = onToggleFloating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.floatingEnabled) Color(0xFFDA3633) else Color(0xFF238636)
                        )
                    ) {
                        Text(
                            if (state.floatingEnabled) "Закрыть плавающее окно" else "Плавающее окно поверх ГУ",
                            fontSize = 11.sp
                        )
                    }
                }

                // Журнал
                Text(
                    text = "Журнал событий:",
                    fontSize = 12.sp,
                    color = Color(0xFFC9D1D9)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF090D12), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    state.logs.asReversed().take(18).forEach { ln ->
                        Text(
                            text = ln,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFA5D6FF)
                        )
                    }
                }
            }

            // Правая область холста со свободно перемещаемым и масштабируемым окном камеры
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF05070A))
            ) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = state.offsetXDp.dp.roundToPx(),
                                y = state.offsetYDp.dp.roundToPx()
                            )
                        }
                        .size(state.windowWidthDp.dp, state.windowHeightDp.dp)
                        .border(2.dp, Color(0xFF58A6FF), RoundedCornerShape(8.dp))
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val dxDp = (dragAmount.x / density).roundToInt()
                                val dyDp = (dragAmount.y / density).roundToInt()
                                controller.updateWindowGeometry(
                                    xDp = controller.state.value.offsetXDp + dxDp,
                                    yDp = controller.state.value.offsetYDp + dyDp
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = state.bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Камера",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Окно камеры ${state.windowWidthDp}×${state.windowHeightDp} dp",
                                color = Color(0xFF8B949E),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Перетаскивайте окно пальцем в любое место",
                                color = Color(0xFF484F58),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
