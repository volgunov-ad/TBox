package vad.dashing.dataresize.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.dataresize.Action
import vad.dashing.dataresize.Banner
import vad.dashing.dataresize.BannerKind
import vad.dashing.dataresize.Operation
import vad.dashing.dataresize.StepStatus
import vad.dashing.dataresize.StepUi
import vad.dashing.dataresize.UiState

private val OkColor = Color(0xFF2E9E4F)
private val RunColor = Color(0xFF0066CC)
private val FailColor = Color(0xFFD32F2F)
private val WarnColor = Color(0xFFE6A100)

@Composable
fun ResizeScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onMove: () -> Unit,
    onStage2: () -> Unit,
    onRollback: () -> Unit,
) {
    var confirm by remember { mutableStateOf<Action?>(null) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Section("Проверка", state.preflight) }
                items(state.operations) { op -> OperationSection(op) }
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Расширение /data", style = MaterialTheme.typography.headlineSmall)
                state.banner?.let { BannerCard(it) }
                if (state.facts.isNotEmpty()) Facts(state.facts)
                Actions(state, onRefresh = onRefresh, onStage2 = onStage2, onConfirm = { confirm = it })
                JournalCard(state.events, state.journal)
            }
        }
    }
    when (confirm) {
        Action.Move -> ConfirmDialog(
            title = "Перенести /data?",
            text = "Android остановится, экран замрёт примерно на 2 минуты, затем ГУ перезагрузится само. " +
                "Не выключайте зажигание и не трогайте ГУ. После загрузки программа откроется сама и расширит раздел.",
            confirmText = "Перенести",
            onConfirm = { confirm = null; onMove() },
            onDismiss = { confirm = null },
        )
        Action.Rollback -> ConfirmDialog(
            title = "Вернуть старую разметку?",
            text = "/data вернётся на старое место в том виде, каким был в момент переноса: " +
                "всё, что установлено и сохранено после переноса, пропадёт. ГУ перезагрузится.",
            confirmText = "Откатить",
            onConfirm = { confirm = null; onRollback() },
            onDismiss = { confirm = null },
        )
        else -> Unit
    }
}

@Composable
private fun Section(title: String, steps: List<StepUi>, error: String? = null) {
    Panel {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(8.dp))
        steps.forEach { StepRow(it) }
        error?.let {
            Spacer(Modifier.size(8.dp))
            Text(it, color = FailColor, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun OperationSection(op: Operation) = Section(op.title, op.steps, op.error)

@Composable
private fun StepRow(step: StepUi) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            when (step.status) {
                StepStatus.Running -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                else -> Box(
                    modifier = Modifier.size(20.dp).background(statusColor(step.status), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(statusMark(step.status), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                step.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (step.status == StepStatus.Pending) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            if (step.detail.isNotBlank()) {
                Text(step.detail, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun statusColor(status: StepStatus): Color = when (status) {
    StepStatus.Ok -> OkColor
    StepStatus.Failed -> FailColor
    StepStatus.Running -> RunColor
    StepStatus.Pending -> Color(0xFF9AA0A6)
}

private fun statusMark(status: StepStatus): String = when (status) {
    StepStatus.Ok -> "✓"
    StepStatus.Failed -> "✕"
    StepStatus.Running, StepStatus.Pending -> ""
}

@Composable
private fun BannerCard(banner: Banner) {
    val color = when (banner.kind) {
        BannerKind.Info -> RunColor
        BannerKind.Warning -> WarnColor
        BannerKind.Success -> OkColor
        BannerKind.Error -> FailColor
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(banner.text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Facts(facts: List<Pair<String, String>>) {
    Panel {
        facts.forEach { (k, v) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(k, modifier = Modifier.weight(0.4f), fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v, modifier = Modifier.weight(0.6f), fontSize = 19.sp)
            }
        }
    }
}

@Composable
private fun Actions(state: UiState, onRefresh: () -> Unit, onStage2: () -> Unit, onConfirm: (Action) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (Action.Move in state.actions) {
            Button(onClick = { onConfirm(Action.Move) }) { Text("Перенести и расширить", fontSize = 20.sp) }
        }
        if (Action.Stage2 in state.actions) {
            Button(onClick = onStage2) { Text("Расширить", fontSize = 20.sp) }
        }
        if (Action.Rollback in state.actions) {
            OutlinedButton(onClick = { onConfirm(Action.Rollback) }) { Text("Откатить", fontSize = 20.sp) }
        }
        if (Action.Refresh in state.actions) {
            OutlinedButton(onClick = onRefresh) { Text("Проверить заново", fontSize = 20.sp) }
        }
        if (state.busy) CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun JournalCard(events: List<String>, journal: List<String>) {
    if (events.isEmpty() && journal.isEmpty()) return
    Panel {
        Text("Журнал", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(6.dp))
        (events + journal).forEach {
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = FailColor)) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
