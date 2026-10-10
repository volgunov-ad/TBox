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
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import vad.dashing.tbox.phoneble.PhoneBleCodec
import kotlin.math.abs

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

/** Same pages, order and 24×24 icons as the web panel. */
private enum class Page(
    val groups: Int,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    CONNECT(
        PhoneBleCodec.GROUP_HEADER,
        R.string.tab_connect,
        icon("M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z"),
    ),
    CLIMATE(
        PhoneBleCodec.GROUP_CLIMATE,
        R.string.tab_climate,
        icon("M22 11h-4.17l3.24-3.24-1.41-1.42L15 11h-2V9l4.66-4.66-1.42-1.41L13 6.17V2h-2v4.17L7.76 2.93 6.34 4.34 11 9v2H9L4.34 6.34 2.93 7.76 6.17 11H2v2h4.17l-3.24 3.24 1.41 1.42L9 13h2v2l-4.66 4.66 1.42 1.41L11 17.83V22h2v-4.17l3.24 3.24 1.42-1.42L13 15v-2h2l4.66 4.66 1.41-1.42L17.83 13H22z"),
    ),
    SEATS(
        PhoneBleCodec.GROUP_SEATS,
        R.string.tab_seats,
        icon("M7.59 5.41c-.78-.78-.78-2.05 0-2.83.78-.78 2.05-.78 2.83 0 .78.78.78 2.05 0 2.83-.79.79-2.05.79-2.83 0zM6 16V7H4v9c0 2.76 2.24 5 5 5h6v-2H9c-1.66 0-3-1.34-3-3zm14 4.07L14.93 15H11.5v-3.68c1.4 1.15 3.6 2.16 5.5 2.16v-2.16c-1.66.02-3.61-.87-4.67-2.04l-1.4-1.55c-.19-.21-.43-.38-.69-.5-.29-.14-.62-.23-.96-.23h-.03C8.01 7 7 8.01 7 9.25V15c0 1.66 1.34 3 3 3h5.07l3.5 3.5L20 20.07z"),
    ),
    MUSIC(
        PhoneBleCodec.GROUP_MEDIA,
        R.string.tab_music,
        icon("M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"),
    ),
    BODY(
        PhoneBleCodec.GROUP_WINDOWS,
        R.string.tab_body,
        icon("M3 20V11l6-7h10a2 2 0 0 1 2 2v14H3zm2.8-8L10 7h8v5H5.8zM14 14h4v1.6h-4z", evenOdd = true),
    ),
}

private fun icon(path: String, evenOdd: Boolean = false): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
        fill = SolidColor(Color.Black),
    ).build()

private fun storedPage(store: PhoneStore): Page =
    Page.entries.firstOrNull { it.name == store.page && it != Page.CONNECT } ?: Page.CLIMATE

/** A paired phone with a live link but no snapshot this long was probably forgotten by the head unit. */
private const val NO_SNAP_REOPEN_MS = 10_000L

/** An old companion answers with pages 0..4 only: a full answer is at least this many pages. */
private const val OLD_FIRMWARE_PAGES = 8

@Composable
private fun PhoneScreen() {
    val context = LocalContext.current
    val store = remember { PhoneStore(context) }
    var snap by remember { mutableStateOf(PhoneBleCodec.Snapshot()) }
    var lastSnapAt by remember { mutableLongStateOf(0L) }
    var refreshAt by remember { mutableLongStateOf(0L) }
    var linkedAt by remember { mutableLongStateOf(0L) }
    var lastVolume by remember { mutableIntStateOf(10) }
    var positionAt by remember { mutableLongStateOf(0L) }
    var pagesSinceSwitch by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var bluetoothOn by remember { mutableStateOf(true) }
    var linked by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(store.paired) }
    var page by remember { mutableStateOf(if (store.paired) storedPage(store) else Page.CONNECT) }
    var reopened by remember { mutableStateOf(false) }
    var pairRequest by remember { mutableStateOf(PairRequest.NONE) }
    val fallbackName = stringResource(R.string.phone_name)
    val phoneName = remember { Build.MODEL?.trim().orEmpty().ifEmpty { fallbackName } }
    val radio = remember {
        PhoneRadio(context) { next ->
            val now = SystemClock.elapsedRealtime()
            if (next.positionMs != snap.positionMs || next.playing != snap.playing) positionAt = now
            snap = next
            lastSnapAt = now
            pagesSinceSwitch++
            if (next.volume != null && next.volume > 0) lastVolume = next.volume
            if (!paired) {
                paired = true
                store.paired = true
                pairRequest = PairRequest.NONE
                page = storedPage(store)
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

    /** Asks for the pages the current screen shows; a newer request drops answers to the old one. */
    fun requestSnapshot(now: Long) {
        if (!radio.linkUp) return
        refreshAt = now
        val counter = store.nextCounter()
        radio.waitingCounter = counter
        radio.write(
            PhoneBleCodec.seal(
                store.key,
                PhoneBleCodec.TYPE_REFRESH,
                store.id,
                counter,
                PhoneBleCodec.refreshBody(radio.textHash, page.groups),
            ),
        )
    }

    fun showPage(next: Page) {
        if (next == page) return
        page = next
        if (paired && next != Page.CONNECT) store.page = next.name
        pagesSinceSwitch = 0
        lastSnapAt = 0L
        requestSnapshot(SystemClock.elapsedRealtime())
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
            if (linkUp && !linked) {
                linkedAt = now
                reopened = false
            }
            linked = linkUp
            if (bluetoothOn) radio.start()
            val fresh = lastSnapAt != 0L && now - lastSnapAt < 2_000L
            if (linkUp && !fresh && now - refreshAt >= 2_000L) requestSnapshot(now)
            if (linkUp && snap.gen == 0 && !reopened && now - linkedAt >= NO_SNAP_REOPEN_MS) {
                reopened = true
                page = Page.CONNECT
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
        if (op == PhoneBleCodec.OP_MEDIA_PLAY_PAUSE && snap.playing != null) {
            val now = SystemClock.elapsedRealtime()
            snap = snap.copy(
                playing = if (snap.playing == 1) 0 else 1,
                positionMs = shownPositionMs(snap, positionAt, now),
            )
            positionAt = now
        } else {
            snap = applyLocally(snap, op, seat, arg)
        }
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

    val status = when {
        !ready -> null
        !bluetoothOn -> stringResource(R.string.bluetooth_off)
        !linked -> stringResource(R.string.looking)
        snap.gen == 0 -> stringResource(R.string.waiting)
        else -> stringResource(R.string.connected)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            Header(status, snap)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!ready) {
                    Button(
                        onClick = { launcher.launch(permissions) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.need_permission))
                    }
                }
                when (page) {
                    Page.CONNECT -> ConnectPage(
                        linked = paired && snap.gen != 0,
                        pairRequest = pairRequest,
                        onPair = ::pair,
                    )
                    Page.CLIMATE -> ClimatePage(snap, ::send)
                    Page.SEATS -> SeatsPage(snap, ::send)
                    Page.MUSIC -> MusicPage(snap, positionAt, lastVolume, ::send) { lastVolume = it }
                    Page.BODY -> BodyPage(
                        snap = snap,
                        oldFirmware = snap.android10 == null && pagesSinceSwitch >= OLD_FIRMWARE_PAGES,
                        send = ::send,
                    )
                }
            }
        }
        BottomBar(page, ::showPage)
    }
}

@Composable
private fun Header(status: String?, snap: PhoneBleCodec.Snapshot) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.title),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status.orEmpty(),
                fontSize = 13.sp,
                color = PhoneColors.muted,
                maxLines = 2,
            )
        }
        TempChip(stringResource(R.string.outside), formatTenths(snap.outsideTenths))
        TempChip(stringResource(R.string.inside), formatTenths(snap.insideTenths))
    }
}

@Composable
private fun TempChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 76.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = label, fontSize = 12.sp, color = PhoneColors.muted)
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BottomBar(current: Page, onSelect: (Page) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            Page.entries.forEach { entry ->
                val selected = entry == current
                val tint = if (selected) PhoneColors.accentText else PhoneColors.muted
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(entry) }
                        .heightIn(min = 56.dp)
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 30.dp)
                            .background(
                                color = if (selected) PhoneColors.accentSoft else Color.Transparent,
                                shape = RoundedCornerShape(15.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = stringResource(entry.label),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectPage(linked: Boolean, pairRequest: PairRequest, onPair: () -> Unit) {
    Card {
        SectionTitle(stringResource(R.string.connection))
        if (linked) {
            Text(
                text = stringResource(R.string.linked),
                fontWeight = FontWeight.SemiBold,
                color = PhoneColors.accentText,
            )
        }
        Label(stringResource(R.string.connection_hint))
        Button(
            onClick = onPair,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.pair))
        }
        val pairText = when (pairRequest) {
            PairRequest.NONE -> null
            PairRequest.SENT -> stringResource(R.string.pair_sent)
            PairRequest.NO_LINK -> stringResource(R.string.pair_no_link)
        }
        if (pairText != null) Label(pairText)
    }
}

@Composable
private fun ClimatePage(snap: PhoneBleCodec.Snapshot, send: (Int, Int, Int) -> Unit) {
    // Missing layout (legacy firmware) → dual-zone (full UI).
    val layout = snap.climateLayout ?: PhoneBleCodec.CLIMATE_LAYOUT_DUAL
    val showPassenger = layout == PhoneBleCodec.CLIMATE_LAYOUT_DUAL
    val showAuto = layout != PhoneBleCodec.CLIMATE_LAYOUT_ORDINARY
    val showSync = layout == PhoneBleCodec.CLIMATE_LAYOUT_DUAL
    Card {
        SectionTitle(stringResource(R.string.climate_title))
        Stepper(stringResource(R.string.driver), formatTenths(snap.leftTenths)) {
            val next = PhoneBleCodec.stepTemp(snap.leftTenths, it) ?: return@Stepper
            send(PhoneBleCodec.OP_TEMP_LEFT, 0, next)
        }
        if (showPassenger) {
            Divider()
            Stepper(stringResource(R.string.passenger), formatTenths(snap.rightTenths)) {
                val next = PhoneBleCodec.stepTemp(snap.rightTenths, it) ?: return@Stepper
                send(PhoneBleCodec.OP_TEMP_RIGHT, 0, next)
            }
        }
        Divider()
        Stepper(stringResource(R.string.fan), snap.fan?.toString() ?: "—") { up ->
            val current = snap.fan ?: return@Stepper
            val next = (current + if (up) 1 else -1).coerceIn(0, 7)
            send(PhoneBleCodec.OP_FAN, 0, next)
        }
        if (showAuto || showSync) {
            Divider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showAuto) {
                    ModeButton(
                        text = stringResource(R.string.auto),
                        selected = snap.auto == 1,
                        modifier = Modifier.weight(1f),
                    ) { send(PhoneBleCodec.OP_AUTO, 0, if (snap.auto == 1) 0 else 1) }
                }
                if (showSync) {
                    ModeButton(
                        text = stringResource(R.string.sync),
                        selected = snap.sync == 1,
                        modifier = Modifier.weight(1f),
                    ) { send(PhoneBleCodec.OP_SYNC, 0, if (snap.sync == 1) 0 else 1) }
                }
            }
        }
        Divider()
        ButtonRows(
            options = listOf(
                Option(stringResource(R.string.climate_recirc), snap.recirc == 1) {
                    send(PhoneBleCodec.OP_RECIRC, 0, if (snap.recirc == 1) 0 else 1)
                },
                Option(stringResource(R.string.climate_power), snap.front == 1) {
                    send(PhoneBleCodec.OP_FRONT, 0, if (snap.front == 1) 0 else 1)
                },
                Option(stringResource(R.string.climate_ac), snap.ac == 1) {
                    send(PhoneBleCodec.OP_AC, 0, if (snap.ac == 1) 0 else 1)
                },
            ),
            columns = 3,
        )
        Divider()
        Label(stringResource(R.string.mode))
        ButtonRows(
            options = listOf(
                Option(stringResource(R.string.eco), snap.mode == 1, Accent.ECO) { send(PhoneBleCodec.OP_MODE, 0, 1) },
                Option(stringResource(R.string.comfort), snap.mode == 2, Accent.COMFORT) { send(PhoneBleCodec.OP_MODE, 0, 2) },
                Option(stringResource(R.string.strong), snap.mode == 3, Accent.STRONG) { send(PhoneBleCodec.OP_MODE, 0, 3) },
            ),
            columns = 3,
        )
        Divider()
        Label(stringResource(R.string.blow))
        val blows = listOf(
            R.string.blow_face,
            R.string.blow_feet,
            R.string.blow_face_feet,
            R.string.blow_glass,
            R.string.blow_glass_feet,
        )
        ButtonRows(
            options = blows.mapIndexed { index, label ->
                val value = index + 1
                Option(stringResource(label), snap.blow == value) { send(PhoneBleCodec.OP_BLOW, 0, value) }
            },
            columns = 3,
            stretchLastRow = true,
        )
    }
}

@Composable
private fun SeatsPage(snap: PhoneBleCodec.Snapshot, send: (Int, Int, Int) -> Unit) {
    val levels = listOf(
        R.string.seat_off to Accent.PRIMARY,
        R.string.seat_heat_1 to Accent.HEAT,
        R.string.seat_heat_2 to Accent.HEAT,
        R.string.seat_heat_3 to Accent.HEAT,
        R.string.seat_vent_1 to Accent.VENT,
        R.string.seat_vent_2 to Accent.VENT,
        R.string.seat_vent_3 to Accent.VENT,
    )
    val rows = listOf(
        Triple(R.string.driver, 0, 7),
        Triple(R.string.passenger, 1, 7),
        Triple(R.string.rear_left, 2, 4),
        Triple(R.string.rear_right, 3, 4),
    )
    Card {
        SectionTitle(stringResource(R.string.seats))
        rows.forEachIndexed { rowIndex, (label, seat, count) ->
            if (rowIndex > 0) Divider()
            Label(stringResource(label))
            val current = snap.seats.getOrNull(seat)
            ButtonRows(
                options = levels.take(count).mapIndexed { index, (text, accent) ->
                    val value = index + 1
                    Option(stringResource(text), current == value, accent) { send(PhoneBleCodec.OP_SEAT, seat, value) }
                },
                columns = count,
                gap = 5.dp,
            )
        }
    }
}

@Composable
private fun MusicPage(
    snap: PhoneBleCodec.Snapshot,
    positionAt: Long,
    lastVolume: Int,
    send: (Int, Int, Int) -> Unit,
    onVolumeKept: (Int) -> Unit,
) {
    Card {
        SectionTitle(stringResource(R.string.media))
        NowPlaying(snap, positionAt)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton(stringResource(R.string.prev), false, Modifier.weight(1f)) {
                send(PhoneBleCodec.OP_MEDIA_PREV, 0, 0)
            }
            val playing = snap.playing == 1
            ModeButton(
                stringResource(if (playing) R.string.pause else R.string.play),
                playing,
                Modifier.weight(1f),
            ) {
                send(PhoneBleCodec.OP_MEDIA_PLAY_PAUSE, 0, 0)
            }
            ModeButton(stringResource(R.string.next), false, Modifier.weight(1f)) {
                send(PhoneBleCodec.OP_MEDIA_NEXT, 0, 0)
            }
        }
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.volume),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            ModeButton("−", false) {
                val current = snap.volume ?: return@ModeButton
                val next = (current - 1).coerceIn(0, 31)
                if (next > 0) onVolumeKept(next)
                send(PhoneBleCodec.OP_VOLUME, 0, next)
            }
            Text(
                text = snap.volume?.toString() ?: "—",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 40.dp),
            )
            ModeButton("+", false) {
                val current = snap.volume ?: return@ModeButton
                val next = (current + 1).coerceIn(0, 31)
                onVolumeKept(next)
                send(PhoneBleCodec.OP_VOLUME, 0, next)
            }
            ModeButton(stringResource(R.string.mute), (snap.volume ?: 0) > 0) {
                val current = snap.volume ?: return@ModeButton
                val next = if (current > 0) 0 else lastVolume.coerceAtLeast(1)
                send(PhoneBleCodec.OP_VOLUME, 0, next)
            }
        }
    }
}

private class WindowCmd(val cmd: Int, @StringRes val label: Int?, val text: String?, val match: (Int) -> Boolean)

/** Android 9 holds 0 / 20 / 80 / 100 %; Android 10 has close / vent / open. Same as the web panel. */
private val WINDOW_CMDS_A9 = listOf(
    WindowCmd(PhoneBleCodec.WINDOW_CMD_CLOSE, R.string.close, null) { it == 0 },
    WindowCmd(PhoneBleCodec.WINDOW_CMD_VENT, null, "20%") { it == 20 },
    WindowCmd(PhoneBleCodec.WINDOW_CMD_COMFORT, null, "80%") { it == 80 },
    WindowCmd(PhoneBleCodec.WINDOW_CMD_OPEN, R.string.open, null) { it == 100 },
)
private val WINDOW_CMDS_A10 = listOf(
    WindowCmd(PhoneBleCodec.WINDOW_CMD_CLOSE, R.string.close, null) { it == 0 },
    WindowCmd(PhoneBleCodec.WINDOW_CMD_VENT, R.string.vent, null) { it in 1..30 },
    WindowCmd(PhoneBleCodec.WINDOW_CMD_OPEN, R.string.open, null) { it == PhoneBleCodec.WINDOW_BETWEEN || it > 30 },
)
private val ROOF_STEPS = listOf(0, 20, 50, 80, 100)
/** The roof reads 102 or 10 % when tilted. */
private const val ROOF_TILT_PERCENT = 10

@Composable
private fun BodyPage(snap: PhoneBleCodec.Snapshot, oldFirmware: Boolean, send: (Int, Int, Int) -> Unit) {
    if (oldFirmware) {
        Card { Label(stringResource(R.string.need_firmware)) }
    }
    val cmds = if (snap.android10 == true) WINDOW_CMDS_A10 else WINDOW_CMDS_A9
    val panes = listOf(
        R.string.window_front_left,
        R.string.window_front_right,
        R.string.window_rear_left,
        R.string.window_rear_right,
    )
    Card {
        SectionTitle(stringResource(R.string.windows))
        val known = snap.windows.filterNotNull()
        val openCount = known.count(PhoneBleCodec::windowOpen)
        val allState = when {
            known.isEmpty() -> "—"
            openCount == 0 -> stringResource(R.string.state_closed)
            else -> stringResource(R.string.state_open) + " · $openCount/${known.size}"
        }
        BodyBlock(stringResource(R.string.all_windows), allState, openCount > 0)
        ButtonRows(
            options = cmds.map { cmd ->
                val all = known.size == panes.size && known.all(cmd.match)
                Option(cmd.label?.let { stringResource(it) } ?: cmd.text.orEmpty(), all) {
                    send(PhoneBleCodec.OP_WINDOW, PhoneBleCodec.WINDOW_ALL, cmd.cmd)
                }
            },
            columns = cmds.size,
        )
        panes.forEachIndexed { index, label ->
            Divider()
            val value = snap.windows.getOrNull(index)
            BodyBlock(stringResource(label), windowText(value), PhoneBleCodec.windowOpen(value))
            ButtonRows(
                options = cmds.map { cmd ->
                    Option(cmd.label?.let { stringResource(it) } ?: cmd.text.orEmpty(), value != null && cmd.match(value)) {
                        send(PhoneBleCodec.OP_WINDOW, index, cmd.cmd)
                    }
                },
                columns = cmds.size,
            )
        }
    }
    Card {
        val roof = snap.sunroof
        val tilted = roof == PhoneBleCodec.ROOF_TILT || roof == ROOF_TILT_PERCENT
        BodyBlock(stringResource(R.string.sunroof), roofText(roof, allowTilt = true), roof != null && roof != 0, title = true)
        ButtonRows(
            options = ROOF_STEPS.map { percent ->
                Option(roofLabel(percent), roof == percent && !tilted) { send(PhoneBleCodec.OP_SUNROOF, 0, percent) }
            } + Option(stringResource(R.string.tilt), tilted) {
                send(PhoneBleCodec.OP_SUNROOF, 0, PhoneBleCodec.ROOF_TILT)
            },
            columns = 3,
        )
    }
    Card {
        val shade = snap.sunshade
        BodyBlock(stringResource(R.string.sunshade), roofText(shade, allowTilt = false), shade != null && shade != 0, title = true)
        ButtonRows(
            options = ROOF_STEPS.map { percent ->
                Option(roofLabel(percent), shade == percent) { send(PhoneBleCodec.OP_SUNSHADE, 0, percent) }
            },
            columns = 3,
        )
    }
}

@Composable
private fun BodyBlock(label: String, state: String, open: Boolean, title: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            fontSize = if (title) 17.sp else 16.sp,
            fontWeight = if (title) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = state,
            fontSize = 14.sp,
            fontWeight = if (open) FontWeight.SemiBold else FontWeight.Normal,
            color = if (open) PhoneColors.accentText else PhoneColors.muted,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun windowText(value: Int?): String = when {
    value == null -> "—"
    value == 0 -> stringResource(R.string.state_closed)
    value == PhoneBleCodec.WINDOW_BETWEEN -> stringResource(R.string.state_open)
    else -> stringResource(R.string.state_open) + " · $value%"
}

@Composable
private fun roofText(value: Int?, allowTilt: Boolean): String = when {
    value == null -> "—"
    allowTilt && (value == PhoneBleCodec.ROOF_TILT || value == ROOF_TILT_PERCENT) -> stringResource(R.string.state_tilted)
    value == 0 -> stringResource(R.string.state_closed)
    else -> stringResource(R.string.state_open) + " · $value%"
}

@Composable
private fun roofLabel(percent: Int): String = when (percent) {
    0 -> stringResource(R.string.close)
    100 -> stringResource(R.string.open)
    else -> "$percent%"
}

/** Same as the web panel: title, artist, progress bar and elapsed / total time. */
@Composable
private fun NowPlaying(snap: PhoneBleCodec.Snapshot, positionAt: Long) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(snap.playing, positionAt) {
        now = SystemClock.elapsedRealtime()
        while (isActive && snap.playing == 1) {
            delay(500)
            now = SystemClock.elapsedRealtime()
        }
    }
    val title = snap.title ?: snap.artist
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title ?: stringResource(R.string.nothing_playing),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (title != null) MaterialTheme.colorScheme.onSurface else PhoneColors.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (snap.title != null && snap.artist != null) {
            Text(
                text = snap.artist,
                fontSize = 14.sp,
                color = PhoneColors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val duration = snap.durationMs
        if (duration != null && duration > 0L) {
            val position = shownPositionMs(snap, positionAt, now)
            LinearProgressIndicator(
                progress = { ((position ?: 0L).toFloat() / duration).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 2.dp)
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text(
                text = "${formatClock(position)} / ${formatClock(duration)}",
                fontSize = 13.sp,
                color = PhoneColors.muted,
            )
        }
    }
}

/** Playback goes on between snapshots, as in the web panel. */
private fun shownPositionMs(snap: PhoneBleCodec.Snapshot, positionAt: Long, now: Long): Long? {
    val base = snap.positionMs ?: return null
    var position = base
    if (snap.playing == 1 && positionAt > 0L) position += (now - positionAt).coerceAtLeast(0L)
    val duration = snap.durationMs
    if (duration != null && duration > 0L) position = position.coerceAtMost(duration)
    return position
}

private fun formatClock(ms: Long?): String {
    if (ms == null || ms < 0L) return "—"
    val total = ms / 1000L
    val hours = total / 3600L
    val minutes = (total / 60L) % 60L
    val seconds = total % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(total / 60L, seconds)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun Divider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = PhoneColors.muted,
    )
}

/** Web panel button: 12 dp corners, 44 dp tall, accent color when selected. */
@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accent: Accent = Accent.PRIMARY,
    horizontalPadding: Dp = 12.dp,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                accent.container ?: MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) accent.content else MaterialTheme.colorScheme.onSurface,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Text(text = text, textAlign = TextAlign.Center, fontSize = 15.sp, maxLines = 2)
    }
}

private class Option(
    val text: String,
    val selected: Boolean,
    val accent: Accent = Accent.PRIMARY,
    val onClick: () -> Unit,
)

/** Equal-width buttons, [columns] per row, like the web `.grid`. */
@Composable
private fun ButtonRows(
    options: List<Option>,
    columns: Int,
    gap: Dp = 6.dp,
    stretchLastRow: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        options.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row.forEach { option ->
                    ModeButton(
                        text = option.text,
                        selected = option.selected,
                        modifier = Modifier.weight(1f),
                        accent = option.accent,
                        horizontalPadding = 2.dp,
                        onClick = option.onClick,
                    )
                }
                if (!stretchLastRow) {
                    repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
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
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        ModeButton("+", false) { onStep(true) }
    }
}

/** Shows the change at once, so a second tap steps from the new value. Windows, roof and shade follow the car. */
private fun applyLocally(snap: PhoneBleCodec.Snapshot, op: Int, seat: Int, arg: Int): PhoneBleCodec.Snapshot =
    when (op) {
        PhoneBleCodec.OP_TEMP_LEFT -> snap.copy(leftTenths = arg)
        PhoneBleCodec.OP_TEMP_RIGHT -> snap.copy(rightTenths = arg)
        PhoneBleCodec.OP_FAN -> snap.copy(fan = arg)
        PhoneBleCodec.OP_AUTO -> snap.copy(auto = arg)
        PhoneBleCodec.OP_BLOW -> snap.copy(blow = arg)
        PhoneBleCodec.OP_MODE -> snap.copy(mode = arg)
        PhoneBleCodec.OP_SYNC -> snap.copy(sync = arg)
        PhoneBleCodec.OP_RECIRC -> snap.copy(recirc = arg)
        PhoneBleCodec.OP_FRONT -> snap.copy(front = arg)
        PhoneBleCodec.OP_AC -> snap.copy(ac = arg)
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
    val sign = if (value < 0) "-" else ""
    val whole = abs(value) / 10
    val frac = abs(value) % 10
    return if (frac == 0) "$sign$whole°" else "$sign$whole.$frac°"
}
