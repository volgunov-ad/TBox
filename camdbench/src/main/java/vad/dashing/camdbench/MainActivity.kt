package vad.dashing.camdbench

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private var pendingSeconds = 0

    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            BenchService.start(this, pendingSeconds)
        } else {
            BenchState.update {
                it.copy(phase = BenchPhase.ERROR, message = "Без доступа к памяти отчёт в «Загрузки» не сохранить")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF4F6F8)) {
                    BenchScreen(onStart = ::startBench, onStop = { BenchService.stop() })
                }
            }
        }
    }

    private fun startBench(seconds: Int) {
        pendingSeconds = seconds
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            BenchService.start(this, seconds)
        }
    }
}

private val DURATIONS_MIN = listOf(5, 10, 15, 30)

private class CamdInfo(val version: String, val grab: String, val status: String)

@Composable
private fun BenchScreen(onStart: (Int) -> Unit, onStop: () -> Unit) {
    val ui by BenchState.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var minutes by remember { mutableIntStateOf(10) }
    var info by remember { mutableStateOf(CamdInfo("…", "…", "…")) }
    var runs by remember { mutableStateOf(listOf<File>()) }
    var selected by remember { mutableStateOf(listOf<File>()) }
    var compareText by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(ui.busy, refresh) {
        while (true) {
            info = withContext(Dispatchers.IO) {
                CamdInfo(CamdProbe.camdVersion(context), CamdProbe.grabVersion(), CamdProbe.status())
            }
            runs = withContext(Dispatchers.IO) { savedRuns() }
            if (ui.busy) break
            delay(5_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Тест нагрузки камер", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Включите запись в «Камеры ГУ», закройте окна камер, подождите минуту и начните сбор. " +
                "Затем установите другую версию camd и повторите с той же длительностью.",
            color = Color(0xFF555555),
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Камеры ГУ", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Версия: ${info.version}   camd_grab: ${info.grab}")
                val fields = BenchLogs.statusFields(info.status)
                val recording = fields["rec_active"] == "1"
                Text(
                    if (recording) "Запись идёт" else "Запись НЕ идёт — замер будет некорректным",
                    color = if (recording) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontWeight = FontWeight.SemiBold,
                )
                val windows = fields["windows"]
                if (windows != null && windows != "none") {
                    Text("Открыты окна камер: $windows — лучше закрыть", color = Color(0xFFEF6C00))
                }
                Text(info.status, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF777777))
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Сбор данных", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Длительность:")
                    DURATIONS_MIN.forEach { value ->
                        FilterChip(
                            selected = minutes == value,
                            onClick = { if (!ui.busy) minutes = value },
                            label = { Text("$value мин") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { compareText = ""; onStart(minutes * 60) }, enabled = !ui.busy) {
                        Text("Начать сбор")
                    }
                    OutlinedButton(onClick = onStop, enabled = ui.phase == BenchPhase.RUNNING) {
                        Text("Остановить и сохранить")
                    }
                }
                StatusBlock(ui)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Сравнение замеров", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Отметьте два замера: первый отмеченный — A, второй — B.", color = Color(0xFF555555))
                if (runs.isEmpty()) Text("Сохранённых замеров пока нет")
                runs.forEach { run ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = run in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) (selected + run).takeLast(2) else selected - run
                            },
                        )
                        val mark = when (selected.indexOf(run)) {
                            0 -> "A  "
                            1 -> "B  "
                            else -> ""
                        }
                        Text(mark + run.name, fontFamily = FontFamily.Monospace)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            compareText = runCatching { compare(selected[0], selected[1]) }
                                .getOrElse { "Не удалось сравнить: ${it.message}" }
                            refresh += 1
                        },
                        enabled = selected.size == 2,
                    ) { Text("Сравнить") }
                    OutlinedButton(onClick = { refresh += 1 }) { Text("Обновить список") }
                }
                if (compareText.isNotEmpty()) {
                    SelectionContainer {
                        Text(
                            compareText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StatusBlock(ui: BenchUi) {
    when (ui.phase) {
        BenchPhase.IDLE -> Text("Результаты сохраняются в Загрузки/camdbench", color = Color(0xFF555555))
        BenchPhase.STARTING, BenchPhase.FINISHING -> Text(ui.message)
        BenchPhase.RUNNING -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Идёт сбор: ${clock(ui.elapsedSec)} из ${clock(ui.plannedSec)}",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Сейчас: процессор ${BenchReport.fmt(ui.cpuBusyPct)} % всех ядер · " +
                    "camd ${BenchReport.fmt(ui.camdPct)} % ядра · camd_grab ${BenchReport.fmt(ui.grabPct)} % ядра",
            )
            Text("Экран можно свернуть — замер идёт в фоне.", color = Color(0xFF777777))
        }
        BenchPhase.DONE -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ui.resultHeadline, fontWeight = FontWeight.SemiBold)
            Text(ui.message, color = if (ui.message == "Готово") Color(0xFF2E7D32) else Color(0xFFEF6C00))
            Text("Сохранено: ${ui.resultFolder}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        BenchPhase.ERROR -> Text("Ошибка: ${ui.message}", color = Color(0xFFC62828))
    }
}

private fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun savedRuns(): List<File> =
    BenchSession.resultsRoot().listFiles()
        ?.filter { File(it, BenchSession.SUMMARY).isFile }
        ?.sortedByDescending { it.name }
        .orEmpty()

private fun compare(a: File, b: File): String {
    val text = BenchCompare.text(
        BenchCompare.parse(File(a, BenchSession.SUMMARY).readText()),
        BenchCompare.parse(File(b, BenchSession.SUMMARY).readText()),
        a.name,
        b.name,
    )
    val out = File(BenchSession.resultsRoot(), "compare_${a.name}__vs__${b.name}.txt")
    out.writeText(text)
    return text + "\nСохранено: ${out.absolutePath}\n"
}
