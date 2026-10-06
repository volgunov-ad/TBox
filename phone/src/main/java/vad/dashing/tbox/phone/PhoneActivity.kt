package vad.dashing.tbox.phone

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import vad.dashing.tbox.phoneble.PhoneBleCodec

class PhoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PhoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PhoneScreen()
                }
            }
        }
    }
}

private enum class PairRequest { NONE, SENT, NO_LINK }

/** A paired phone with a live link but no snapshot this long was probably forgotten by the head unit. */
private const val NO_SNAP_REOPEN_MS = 10_000L

@Composable
private fun PhoneScreen() {
    val context = LocalContext.current
    val store = remember { PhoneStore(context) }
    var snap by remember { mutableStateOf(PhoneBleCodec.Snapshot()) }
    var lastSnapAt by remember { mutableLongStateOf(0L) }
    var refreshAt by remember { mutableLongStateOf(0L) }
    var linkedAt by remember { mutableLongStateOf(0L) }
    var lastVolume by remember { mutableIntStateOf(10) }
    var ready by remember { mutableStateOf(false) }
    var bluetoothOn by remember { mutableStateOf(true) }
    var linked by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(store.paired) }
    var connectOpen by remember { mutableStateOf(!store.paired) }
    var pairRequest by remember { mutableStateOf(PairRequest.NONE) }
    val fallbackName = stringResource(R.string.phone_name)
    val phoneName = remember { Build.MODEL?.trim().orEmpty().ifEmpty { fallbackName } }
    val radio = remember {
        PhoneRadio(context) { next ->
            snap = next
            lastSnapAt = SystemClock.elapsedRealtime()
            if (next.volume != null && next.volume > 0) lastVolume = next.volume
            if (!paired) {
                paired = true
                store.paired = true
                connectOpen = false
                pairRequest = PairRequest.NONE
            }
        }
    }
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        ready = granted.values.all { it }
    }
    DisposableEffect(ready) {
        radio.storeKey = store.key
        if (ready) radio.start()
        onDispose { radio.stop() }
    }
    LaunchedEffect(Unit) {
        launcher.launch(permissions)
    }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        while (isActive) {
            bluetoothOn = radio.bluetoothOn
            val now = SystemClock.elapsedRealtime()
            val linkUp = radio.linkUp
            if (linkUp && !linked) linkedAt = now
            linked = linkUp
            if (bluetoothOn) radio.start()
            val fresh = lastSnapAt != 0L && now - lastSnapAt < 2_000L
            if (linkUp && !fresh && now - refreshAt >= 2_000L) {
                refreshAt = now
                val counter = store.nextCounter()
                radio.waitingCounter = counter
                radio.write(
                    PhoneBleCodec.seal(
                        store.key,
                        PhoneBleCodec.TYPE_REFRESH,
                        store.id,
                        counter,
                        PhoneBleCodec.refreshBody(),
                    ),
                )
            }
            if (linkUp && snap.gen == 0 && now - linkedAt >= NO_SNAP_REOPEN_MS) {
                connectOpen = true
            }
            delay(500)
        }
    }

    fun send(op: Int, seat: Int, arg: Int) {
        if (!ready) {
            launcher.launch(permissions)
            return
        }
        if (!radio.linkUp) return
        val counter = store.nextCounter()
        val payload = PhoneBleCodec.seal(
            store.key,
            PhoneBleCodec.TYPE_CMD,
            store.id,
            counter,
            PhoneBleCodec.commandBody(op, seat, arg),
        )
        snap = applyLocally(snap, op, seat, arg)
        lastSnapAt = 0L
        refreshAt = 0L
        radio.write(payload)
    }

    fun pair() {
        if (!ready) {
            launcher.launch(permissions)
            return
        }
        if (!radio.linkUp) {
            pairRequest = PairRequest.NO_LINK
            return
        }
        radio.write(PhoneBleCodec.pairPacket(store.id, store.key, phoneName))
        pairRequest = PairRequest.SENT
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        val status = when {
            !ready -> null
            !bluetoothOn -> stringResource(R.string.bluetooth_off)
            !linked -> stringResource(R.string.looking)
            snap.gen == 0 -> stringResource(R.string.waiting)
            else -> stringResource(R.string.connected)
        }
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!ready) {
            Button(onClick = { launcher.launch(permissions) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.need_permission))
            }
        }

        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { connectOpen = !connectOpen }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.connection),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (connectOpen) "▴" else "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (connectOpen) {
                Text(
                    text = stringResource(R.string.connection_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = ::pair, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pair))
                }
                val pairText = when (pairRequest) {
                    PairRequest.NONE -> null
                    PairRequest.SENT -> stringResource(R.string.pair_sent)
                    PairRequest.NO_LINK -> stringResource(R.string.pair_no_link)
                }
                if (pairText != null) {
                    Text(
                        text = pairText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card {
            SectionTitle(stringResource(R.string.climate))
            Stepper(stringResource(R.string.driver), formatTenths(snap.leftTenths)) {
                val next = PhoneBleCodec.stepTemp(snap.leftTenths, it) ?: return@Stepper
                send(PhoneBleCodec.OP_TEMP_LEFT, 0, next)
            }
            Stepper(stringResource(R.string.passenger), formatTenths(snap.rightTenths)) {
                val next = PhoneBleCodec.stepTemp(snap.rightTenths, it) ?: return@Stepper
                send(PhoneBleCodec.OP_TEMP_RIGHT, 0, next)
            }
            Stepper(stringResource(R.string.fan), snap.fan?.toString() ?: "—") { up ->
                val current = snap.fan ?: return@Stepper
                val next = (current + if (up) 1 else -1).coerceIn(0, 7)
                send(PhoneBleCodec.OP_FAN, 0, next)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton(
                    text = stringResource(R.string.auto),
                    selected = snap.auto == 1,
                    modifier = Modifier.weight(1f),
                ) { send(PhoneBleCodec.OP_AUTO, 0, if (snap.auto == 1) 0 else 1) }
                ModeButton(
                    text = stringResource(R.string.sync),
                    selected = snap.sync == 1,
                    modifier = Modifier.weight(1f),
                ) { send(PhoneBleCodec.OP_SYNC, 0, if (snap.sync == 1) 0 else 1) }
            }
            Label(stringResource(R.string.mode))
            Choices(
                listOf(
                    stringResource(R.string.eco),
                    stringResource(R.string.comfort),
                    stringResource(R.string.strong),
                ),
                snap.mode,
            ) { send(PhoneBleCodec.OP_MODE, 0, it) }
            Label(stringResource(R.string.blow))
            Choices(
                listOf(
                    stringResource(R.string.blow_face),
                    stringResource(R.string.blow_feet),
                    stringResource(R.string.blow_face_feet),
                    stringResource(R.string.blow_glass),
                    stringResource(R.string.blow_glass_feet),
                ),
                snap.blow,
            ) { send(PhoneBleCodec.OP_BLOW, 0, it) }
        }

        Card {
            SectionTitle(stringResource(R.string.seats))
            SeatRow(stringResource(R.string.driver), snap.seats.getOrNull(0), 7) { send(PhoneBleCodec.OP_SEAT, 0, it) }
            SeatRow(stringResource(R.string.passenger), snap.seats.getOrNull(1), 7) { send(PhoneBleCodec.OP_SEAT, 1, it) }
            SeatRow(stringResource(R.string.rear_left), snap.seats.getOrNull(2), 4) { send(PhoneBleCodec.OP_SEAT, 2, it) }
            SeatRow(stringResource(R.string.rear_right), snap.seats.getOrNull(3), 4) { send(PhoneBleCodec.OP_SEAT, 3, it) }
        }

        Card {
            SectionTitle(stringResource(R.string.media))
            Stepper(stringResource(R.string.volume), snap.volume?.toString() ?: "—") { up ->
                val current = snap.volume ?: return@Stepper
                val next = (current + if (up) 1 else -1).coerceIn(0, 31)
                if (next > 0) lastVolume = next
                send(PhoneBleCodec.OP_VOLUME, 0, next)
            }
            ModeButton(
                text = stringResource(R.string.mute),
                selected = snap.volume == 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val current = snap.volume ?: return@ModeButton
                val next = if (current > 0) 0 else lastVolume.coerceAtLeast(1)
                send(PhoneBleCodec.OP_VOLUME, 0, next)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton(stringResource(R.string.prev), false, Modifier.weight(1f)) {
                    send(PhoneBleCodec.OP_MEDIA_PREV, 0, 0)
                }
                ModeButton(stringResource(R.string.play), false, Modifier.weight(1f)) {
                    send(PhoneBleCodec.OP_MEDIA_PLAY_PAUSE, 0, 0)
                }
                ModeButton(stringResource(R.string.next), false, Modifier.weight(1f)) {
                    send(PhoneBleCodec.OP_MEDIA_NEXT, 0, 0)
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Same look as TBox Monitor `ModeButton`: primary when selected. */
@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (selected) 4.dp else 0.dp),
    ) {
        Text(text = text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Stepper(label: String, value: String, onStep: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        ModeButton("−", false) { onStep(false) }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        ModeButton("+", false) { onStep(true) }
    }
}

@Composable
private fun SeatRow(label: String, current: Int?, levels: Int, onPick: (Int) -> Unit) {
    val all = listOf(
        stringResource(R.string.seat_off),
        stringResource(R.string.seat_heat_1),
        stringResource(R.string.seat_heat_2),
        stringResource(R.string.seat_heat_3),
        stringResource(R.string.seat_vent_1),
        stringResource(R.string.seat_vent_2),
        stringResource(R.string.seat_vent_3),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(label)
        Choices(all.take(levels), current, onPick)
    }
}

/** Values are 1-based: the first label sends 1. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(labels: List<String>, current: Int?, onPick: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val value = index + 1
            ModeButton(label, current == value) { onPick(value) }
        }
    }
}

/** Shows the change at once, so a second tap steps from the new value. */
private fun applyLocally(snap: PhoneBleCodec.Snapshot, op: Int, seat: Int, arg: Int): PhoneBleCodec.Snapshot =
    when (op) {
        PhoneBleCodec.OP_TEMP_LEFT -> snap.copy(leftTenths = arg)
        PhoneBleCodec.OP_TEMP_RIGHT -> snap.copy(rightTenths = arg)
        PhoneBleCodec.OP_FAN -> snap.copy(fan = arg)
        PhoneBleCodec.OP_AUTO -> snap.copy(auto = arg)
        PhoneBleCodec.OP_BLOW -> snap.copy(blow = arg)
        PhoneBleCodec.OP_MODE -> snap.copy(mode = arg)
        PhoneBleCodec.OP_SYNC -> snap.copy(sync = arg)
        PhoneBleCodec.OP_SEAT -> if (seat in snap.seats.indices) {
            snap.copy(seats = snap.seats.toMutableList().also { it[seat] = arg })
        } else {
            snap
        }
        PhoneBleCodec.OP_VOLUME -> snap.copy(volume = arg)
        else -> snap
    }

private fun formatTenths(value: Int?): String {
    if (value == null) return "—"
    val whole = value / 10
    val frac = kotlin.math.abs(value % 10)
    return if (frac == 0) "$whole°" else "$whole.$frac°"
}
