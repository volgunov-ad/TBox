package vad.dashing.tbox.phone

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.phoneble.PhoneBleCodec

class PhoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PhoneScreen()
            }
        }
    }
}

@Composable
private fun PhoneScreen() {
    val context = LocalContext.current
    val store = remember { PhoneStore(context) }
    val scope = rememberCoroutineScope()
    var snap by remember { mutableStateOf(PhoneBleCodec.Snapshot()) }
    var lastSnapAt by remember { mutableLongStateOf(0L) }
    var lastVolume by remember { mutableIntStateOf(10) }
    var ready by remember { mutableStateOf(false) }
    var bluetoothOn by remember { mutableStateOf(true) }
    val air = remember { Air() }
    val phoneName = stringResource(R.string.phone_name)
    val radio = remember {
        PhoneRadio(context) { next ->
            snap = next
            lastSnapAt = SystemClock.elapsedRealtime()
            if (next.volume != null && next.volume > 0) lastVolume = next.volume
        }
    }
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        ready = granted.values.all { it }
    }
    DisposableEffect(ready) {
        radio.storeKey = store.key
        if (ready) radio.startScan()
        onDispose {
            radio.stopScan()
            radio.stopAdvertise()
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        launcher.launch(permissions)
    }
    androidx.compose.runtime.LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        while (isActive) {
            bluetoothOn = radio.bluetoothOn
            radio.ensureScan()
            val fresh = lastSnapAt != 0L && SystemClock.elapsedRealtime() - lastSnapAt < 2_000L
            if (!fresh && air.job?.isActive != true) {
                val counter = store.nextCounter()
                radio.waitingCounter = counter
                val payload = PhoneBleCodec.seal(
                    store.key,
                    PhoneBleCodec.TYPE_REFRESH,
                    store.id,
                    counter,
                    PhoneBleCodec.refreshBody(),
                )
                air.job = scope.launch {
                    repeat(4) {
                        radio.advertise(payload)
                        delay(250)
                    }
                    radio.stopAdvertise()
                }
            }
            delay(500)
        }
    }

    fun send(op: Int, seat: Int, arg: Int) {
        if (!ready) {
            launcher.launch(permissions)
            return
        }
        val counter = store.nextCounter()
        val payload = PhoneBleCodec.seal(
            store.key,
            PhoneBleCodec.TYPE_CMD,
            store.id,
            counter,
            PhoneBleCodec.commandBody(op, seat, arg),
        )
        snap = applyLocally(snap, op, seat, arg)
        // The ESP listens in short windows between its own replies; ~1 s on air covers several.
        air.job?.cancel()
        air.job = scope.launch {
            repeat(5) {
                radio.advertise(payload)
                delay(200)
            }
            radio.stopAdvertise()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!ready) {
            Button(onClick = { launcher.launch(permissions) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.need_permission))
            }
        } else if (!bluetoothOn) {
            Text(stringResource(R.string.bluetooth_off))
        } else if (!radio.canAdvertise) {
            Text(stringResource(R.string.need_radio))
        }
        Button(
            onClick = {
                if (!ready) {
                    launcher.launch(permissions)
                    return@Button
                }
                air.job?.cancel()
                air.job = scope.launch {
                    val pages = PhoneBleCodec.pairPages(store.id, store.key, phoneName)
                    repeat(4) {
                        for (page in pages) {
                            radio.advertise(page)
                            delay(250)
                        }
                    }
                    radio.stopAdvertise()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.pair))
        }
        if (snap.gen == 0) {
            Text(stringResource(R.string.waiting))
        }
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
            Button(onClick = { send(PhoneBleCodec.OP_AUTO, 0, if (snap.auto == 1) 0 else 1) }) {
                Text(stringResource(R.string.auto))
            }
            Button(onClick = { send(PhoneBleCodec.OP_SYNC, 0, if (snap.sync == 1) 0 else 1) }) {
                Text(stringResource(R.string.sync))
            }
        }
        Choices(
            listOf(
                stringResource(R.string.eco),
                stringResource(R.string.comfort),
                stringResource(R.string.strong),
            ),
            snap.mode,
        ) { send(PhoneBleCodec.OP_MODE, 0, it) }
        val blowLabels = listOf(
            stringResource(R.string.blow_face),
            stringResource(R.string.blow_feet),
            stringResource(R.string.blow_face_feet),
            stringResource(R.string.blow_glass),
            stringResource(R.string.blow_glass_feet),
        )
        Choices(blowLabels, snap.blow) { send(PhoneBleCodec.OP_BLOW, 0, it) }
        Text(stringResource(R.string.seats))
        SeatRow(stringResource(R.string.driver), snap.seats.getOrNull(0), 7) { send(PhoneBleCodec.OP_SEAT, 0, it) }
        SeatRow(stringResource(R.string.passenger), snap.seats.getOrNull(1), 7) { send(PhoneBleCodec.OP_SEAT, 1, it) }
        SeatRow(stringResource(R.string.rear_left), snap.seats.getOrNull(2), 4) { send(PhoneBleCodec.OP_SEAT, 2, it) }
        SeatRow(stringResource(R.string.rear_right), snap.seats.getOrNull(3), 4) { send(PhoneBleCodec.OP_SEAT, 3, it) }
        Stepper(stringResource(R.string.sound), snap.volume?.toString() ?: "—") { up ->
            val current = snap.volume ?: return@Stepper
            val next = (current + if (up) 1 else -1).coerceIn(0, 31)
            if (next > 0) lastVolume = next
            send(PhoneBleCodec.OP_VOLUME, 0, next)
        }
        Button(
            onClick = {
                val current = snap.volume ?: return@Button
                val next = if (current > 0) 0 else lastVolume.coerceAtLeast(1)
                send(PhoneBleCodec.OP_VOLUME, 0, next)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sound))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { send(PhoneBleCodec.OP_MEDIA_PREV, 0, 0) }) { Text(stringResource(R.string.prev)) }
            Button(onClick = { send(PhoneBleCodec.OP_MEDIA_PLAY_PAUSE, 0, 0) }) { Text(stringResource(R.string.play)) }
            Button(onClick = { send(PhoneBleCodec.OP_MEDIA_NEXT, 0, 0) }) { Text(stringResource(R.string.next)) }
        }
    }
}

private class Air {
    var job: Job? = null
}

@Composable
private fun Stepper(label: String, value: String, onStep: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$label $value", modifier = Modifier.weight(1f))
        Button(onClick = { onStep(false) }) { Text("−") }
        Button(onClick = { onStep(true) }) { Text("+") }
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
    Column {
        Text(label)
        Choices(all.take(levels), current, onPick)
    }
}

/** Values are 1-based: the first label sends 1. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(labels: List<String>, current: Int?, onPick: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val value = index + 1
            Button(onClick = { onPick(value) }) {
                Text(if (current == value) "[$label]" else label)
            }
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
