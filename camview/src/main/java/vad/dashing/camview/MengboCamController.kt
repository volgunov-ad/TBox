package vad.dashing.camview

import android.graphics.Bitmap
import com.mengbo.libraryoms.MBOms
import com.mengbo.mbavm.MBAvm
import com.mengbo.mbdms.MBDms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CamChannel(val title: String) {
    AVM("AVM (@MengboAVM)"),
    OMS("OMS (@MengboOMS)"),
    DMS("DMS (@MengboDMS)"),
    SHM("Прямой shm-файл")
}

enum class AvmViewCmd(val code: Int, val label: String) {
    KEY_DOWN(5, "Вкл (KeyDown=5)"),
    HARD_KEY_DOWN(6, "HardKey=6"),
    VIEW_2D(1, "2D общий (1)"),
    VIEW_3D(2, "3D общий (2)"),
    FRONT(3, "Перед (3)"),
    BEHIND(4, "Зад (4)"),
    D2_FRONT(14, "2D Перед (14)"),
    D2_REAR(15, "2D Зад (15)"),
    D2_LEFT(16, "2D Лево (16)"),
    D2_RIGHT(17, "2D Право (17)"),
    D3_FRONT(18, "3D Перед (18)"),
    D3_REAR(22, "3D Зад (22)"),
    D3_LEFT(24, "3D Лево (24)"),
    D3_RIGHT(20, "3D Право (20)"),
    MAG_FRONT(26, "Лупа Перед (26)"),
    MAG_REAR(27, "Лупа Зад (27)"),
    SW_EXIT(7, "Выкл (Exit=7)"),
}

data class CamUiState(
    val activeChannel: CamChannel = CamChannel.AVM,
    val decodeMode: PixelDecodeMode = PixelDecodeMode.AUTO,
    val avmInited: Boolean = false,
    val omsInited: Boolean = false,
    val dmsInited: Boolean = false,
    val frameCount: Long = 0,
    val fps: Float = 0f,
    val lastMeta: String = "Кадров пока нет",
    val statusLine: String = "Готов к подключению",
    val logs: List<String> = emptyList(),
    val bitmap: Bitmap? = null,
    val windowWidthDp: Int = 720,
    val windowHeightDp: Int = 450,
    val offsetXDp: Int = 20,
    val offsetYDp: Int = 10,
    val floatingEnabled: Boolean = false
)

class MengboCamController {

    private val _state = MutableStateFlow(CamUiState())
    val state: StateFlow<CamUiState> = _state.asStateFlow()

    private var mbAvm: MBAvm? = null
    private var mbOms: MBOms? = null
    private var mbDms: MBDms? = null

    private var fpsWindowStartMs: Long = 0L
    private var fpsFramesInWindow: Int = 0
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun setChannel(ch: CamChannel) {
        _state.value = _state.value.copy(activeChannel = ch)
    }

    fun setDecodeMode(mode: PixelDecodeMode) {
        _state.value = _state.value.copy(decodeMode = mode)
        appendLog("Режим декодирования: ${mode.label}")
    }

    fun updateWindowGeometry(
        widthDp: Int = _state.value.windowWidthDp,
        heightDp: Int = _state.value.windowHeightDp,
        xDp: Int = _state.value.offsetXDp,
        yDp: Int = _state.value.offsetYDp
    ) {
        _state.value = _state.value.copy(
            windowWidthDp = widthDp.coerceIn(160, 1920),
            windowHeightDp = heightDp.coerceIn(100, 1080),
            offsetXDp = xDp.coerceIn(0, 1600),
            offsetYDp = yDp.coerceIn(0, 900)
        )
    }

    fun setFloatingEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(floatingEnabled = enabled)
    }

    fun startAvm() {
        try {
            if (mbAvm == null) {
                mbAvm = MBAvm()
            }
            val avm = mbAvm!!
            val ret = avm.nativeInitialize(
                { status -> appendLog("AVM showStatusChange: $status") },
                { img ->
                    if (img != null && img.pImageBuf != null) {
                        onRawFrame(
                            source = "AVM",
                            buf = img.pImageBuf,
                            width = img.nWidth.toInt(),
                            height = img.nHeight.toInt(),
                            stride = img.nstride,
                            imageSize = img.nImageSize,
                            colorFmt = img.nColorFmt,
                            frameNum = img.nImageNum
                        )
                    }
                },
                { calState -> appendLog("AVM calibrationState: $calState") },
                { act -> appendLog("AVM activation: uuid=${act?.szUUID_Number}") },
                { st, exp, ev -> appendLog("AVM machineOp: status=$st explain=$exp event=$ev") }
            )
            val err = runCatching { avm.nativeGetErrorStatus()?.toString() }.getOrNull() ?: "n/a"
            _state.value = _state.value.copy(
                avmInited = (ret == 0),
                statusLine = "AVM init=$ret ($err)"
            )
            appendLog("AVM nativeInitialize -> $ret, status=[$err]")
        } catch (t: Throwable) {
            appendLog("Ошибка старта AVM: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun sendAvmView(cmd: AvmViewCmd) {
        val avm = mbAvm
        if (avm == null) {
            appendLog("Сначала инициализируйте AVM")
            return
        }
        try {
            val r = avm.nativeSendViewCtrol(cmd.code)
            val err = runCatching { avm.nativeGetErrorStatus()?.toString() }.getOrNull() ?: ""
            appendLog("AVM viewCtrl(${cmd.label}) -> $r [$err]")
            _state.value = _state.value.copy(statusLine = "AVM cmd ${cmd.label} -> $r")
        } catch (t: Throwable) {
            appendLog("Ошибка AVM viewCtrl: ${t.message}")
        }
    }

    fun stopAvm() {
        try {
            mbAvm?.nativeUninitialize()
            _state.value = _state.value.copy(avmInited = false, statusLine = "AVM остановлен")
            appendLog("AVM nativeUninitialize выполнен")
        } catch (t: Throwable) {
            appendLog("Ошибка остановки AVM: ${t.message}")
        }
    }

    fun startOms() {
        try {
            if (mbOms == null) {
                mbOms = MBOms()
            }
            val oms = mbOms!!
            val ret = oms.nativeOmsInit(
                { items -> appendLog("OMS features: count=${items?.size ?: 0}") },
                { img ->
                    if (img != null && img.pImageBuf != null) {
                        onRawFrame(
                            source = "OMS",
                            buf = img.pImageBuf,
                            width = img.nWidth.toInt(),
                            height = img.nHeight.toInt(),
                            stride = img.nstride,
                            imageSize = img.nImageSize,
                            colorFmt = img.nColorFmt,
                            frameNum = img.nImageNum
                        )
                    }
                },
                { st, _, _ -> appendLog("OMS leftStatus=$st") },
                { st, tp, path -> appendLog("OMS camOp state=$st type=$tp path=$path") }
            )
            // 0 = eOMSAN_Display, 1 = enable
            val swRet = oms.nativeSetSwitch(0, 1)
            val err = runCatching { oms.nativeGetErrorState()?.toString() }.getOrNull() ?: "n/a"
            val ver = runCatching { oms.nativeGetVersion() }.getOrNull() ?: ""
            _state.value = _state.value.copy(
                omsInited = (ret == 0),
                statusLine = "OMS init=$ret sw=$swRet ($err)"
            )
            appendLog("OMS init->$ret, setSwitch(Display,1)->$swRet, ver=$ver, err=[$err]")
        } catch (t: Throwable) {
            appendLog("Ошибка старта OMS: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun stopOms() {
        try {
            mbOms?.nativeSetSwitch(0, 0)
            mbOms?.nativeOmsUnInit()
            _state.value = _state.value.copy(omsInited = false, statusLine = "OMS остановлен")
            appendLog("OMS setSwitch(0,0) + nativeOmsUnInit выполнены")
        } catch (t: Throwable) {
            appendLog("Ошибка остановки OMS: ${t.message}")
        }
    }

    fun startDms() {
        try {
            if (mbDms == null) {
                mbDms = MBDms()
            }
            val dms = mbDms!!
            val ret = dms.nativeInitialize(
                { reg -> appendLog("DMS gaze=$reg") },
                { st -> appendLog("DMS faceStatus=$st") },
                { em -> appendLog("DMS emotion=$em") },
                { acc, _ -> appendLog("DMS feature acc=$acc") },
                { acc, res -> appendLog("DMS regist acc=$acc res=$res") },
                { acc, res -> appendLog("DMS verify acc=$acc res=$res") },
                { _, res -> appendLog("DMS recognize res=$res") },
                { lvl -> appendLog("DMS drowsiness=$lvl") },
                { lvl -> appendLog("DMS distraction=$lvl") },
                { act -> appendLog("DMS danger=$act") },
                { img ->
                    if (img != null && img.pImageBuf != null) {
                        onRawFrame(
                            source = "DMS",
                            buf = img.pImageBuf,
                            width = img.nWidth.toInt(),
                            height = img.nHeight.toInt(),
                            stride = img.nstride,
                            imageSize = img.nImageSize,
                            colorFmt = img.nColorFmt,
                            frameNum = img.nImageNum
                        )
                    }
                },
                { st -> appendLog("DMS errStatus: $st") },
                { _ -> appendLog("DMS paramAllChanged") }
            )
            val showRet = dms.nativeShowDmsImage(1)
            val ver = runCatching { dms.nativeGetVersion() }.getOrNull() ?: ""
            _state.value = _state.value.copy(
                dmsInited = (ret == 0),
                statusLine = "DMS init=$ret show=$showRet ver=$ver"
            )
            appendLog("DMS init->$ret, showDmsImage(1)->$showRet, ver=$ver")
        } catch (t: Throwable) {
            appendLog("Ошибка старта DMS: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun stopDms() {
        try {
            mbDms?.nativeShowDmsImage(0)
            mbDms?.nativeUninitialize()
            _state.value = _state.value.copy(dmsInited = false, statusLine = "DMS остановлен")
            appendLog("DMS showDmsImage(0) + nativeUninitialize выполнены")
        } catch (t: Throwable) {
            appendLog("Ошибка остановки DMS: ${t.message}")
        }
    }

    fun readShareFileOnce(path: String, width: Int, height: Int, offset: Int) {
        val (bmp, msg) = FrameDecoder.readShareFileDirect(
            path = path,
            width = width,
            height = height,
            headerOffset = offset,
            mode = _state.value.decodeMode
        )
        appendLog(msg)
        if (bmp != null) {
            _state.value = _state.value.copy(
                bitmap = bmp,
                frameCount = _state.value.frameCount + 1,
                lastMeta = "SHM ${width}x${height} off=$offset (${path.substringAfterLast('/')})",
                statusLine = msg
            )
        } else {
            _state.value = _state.value.copy(statusLine = msg)
        }
    }

    private fun onRawFrame(
        source: String,
        buf: ByteBuffer,
        width: Int,
        height: Int,
        stride: Int,
        imageSize: Int,
        colorFmt: Int,
        frameNum: Int
    ) {
        val now = System.currentTimeMillis()
        if (fpsWindowStartMs == 0L) {
            fpsWindowStartMs = now
            fpsFramesInWindow = 0
        }
        fpsFramesInWindow++
        val elapsed = now - fpsWindowStartMs
        val currentFps = if (elapsed >= 500L) {
            val f = fpsFramesInWindow * 1000f / elapsed
            fpsWindowStartMs = now
            fpsFramesInWindow = 0
            f
        } else {
            _state.value.fps
        }

        val bmp = FrameDecoder.decodeToBitmap(
            buf = buf,
            width = width,
            height = height,
            stride = stride,
            imageSize = imageSize,
            colorFmt = colorFmt,
            mode = _state.value.decodeMode
        )
        val total = _state.value.frameCount + 1
        val meta = "$source #$frameNum | ${width}x${height} stride=$stride size=$imageSize fmt=$colorFmt cap=${buf.capacity()}"
        if (total == 1L || total % 30L == 0L) {
            appendLog("Кадр $meta")
        }
        _state.value = _state.value.copy(
            frameCount = total,
            fps = currentFps,
            lastMeta = meta,
            bitmap = bmp ?: _state.value.bitmap
        )
    }

    fun stopAll() {
        if (_state.value.avmInited) stopAvm()
        if (_state.value.omsInited) stopOms()
        if (_state.value.dmsInited) stopDms()
    }

    private fun appendLog(line: String) {
        android.util.Log.i("CamViewTest", line)
        val stamp = timeFmt.format(Date())
        val updated = (_state.value.logs + "[$stamp] $line").takeLast(60)
        _state.value = _state.value.copy(logs = updated)
    }
}
