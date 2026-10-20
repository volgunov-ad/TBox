package vad.dashing.tbox.phone

import android.content.Context
import android.os.SystemClock
import vad.dashing.tbox.phoneble.PhoneBleCodec

/** Which way the phone reaches the car; chosen on the Connect page. */
enum class PhonePath { AUTO, DIRECT, COMPANION }

enum class ActivePath { NONE, DIRECT, COMPANION }

/** One transport to the car: BLE to the companion or RFCOMM to the head unit. Main thread only. */
interface PhoneLink {
    val linkUp: Boolean

    /** The peer on the current link knows our key: a sealed snapshot arrived. */
    val confirmed: Boolean
    var storeKey: ByteArray

    /**
     * Snapshot pages are accepted for REFRESH counters in [acceptFromCounter]..[waitingCounter]:
     * a late answer still shows the current page, but nothing from before the last page
     * switch or command overwrites what the user just changed.
     */
    var acceptFromCounter: Long
    var waitingCounter: Long

    /** Idempotent; also runs the stale-link checks. */
    fun start()

    fun stop()

    /** After a long background pause: drop a stale attempt or link and search again. */
    fun kickSearch()

    fun write(payload: ByteArray)
}

/**
 * Runs the transports [path] allows. AUTO tries the head unit first and adds the companion
 * search after [DIRECT_FIRST_MS] or after a full pass over the bonded head units without
 * an answer. The first link that delivers a sealed snapshot wins and the other stops until
 * it drops; until then PAIR and REFRESH go to every open link.
 */
class PhoneLinkRouter(
    context: Context,
    private val store: PhoneStore,
    onSnap: (PhoneBleCodec.Snapshot) -> Unit,
) : PhoneLink {
    private val assembler = PhoneBleCodec.SnapshotAssembler()
    private val radio = PhoneRadio(context, assembler, onSnap)
    private val direct = PhoneDirectLink(context, store, assembler, onSnap)
    private var running = false
    private var radioOn = false
    private var directOn = false
    private var searchSince = 0L

    var path: PhonePath = store.path
        set(value) {
            if (field == value) return
            field = value
            store.path = value
            if (running) {
                stopAll()
                start()
            }
        }

    override val linkUp: Boolean get() = direct.linkUp || radio.linkUp

    override val confirmed: Boolean get() = direct.confirmed || radio.confirmed

    val active: ActivePath
        get() = when {
            direct.confirmed -> ActivePath.DIRECT
            radio.confirmed -> ActivePath.COMPANION
            direct.linkUp -> ActivePath.DIRECT
            radio.linkUp -> ActivePath.COMPANION
            else -> ActivePath.NONE
        }

    /** Sent in REFRESH so the peer skips text pages the phone already shows. */
    val textHash: Int get() = assembler.snapshot.textHash

    val bluetoothOn: Boolean get() = radio.bluetoothOn

    override var storeKey: ByteArray = ByteArray(PhoneBleCodec.KEY_LEN)
        set(value) {
            field = value
            radio.storeKey = value
            direct.storeKey = value
        }

    override var acceptFromCounter: Long = -1L
        set(value) {
            field = value
            radio.acceptFromCounter = value
            direct.acceptFromCounter = value
        }

    override var waitingCounter: Long = -1L
        set(value) {
            field = value
            radio.waitingCounter = value
            direct.waitingCounter = value
        }

    override fun start() {
        running = true
        when (path) {
            PhonePath.DIRECT -> {
                setRadio(false)
                setDirect(true)
            }
            PhonePath.COMPANION -> {
                setDirect(false)
                setRadio(true)
            }
            PhonePath.AUTO -> when {
                direct.confirmed -> {
                    searchSince = 0L
                    setRadio(false)
                    setDirect(true)
                }
                radio.confirmed -> {
                    searchSince = 0L
                    setDirect(false)
                    setRadio(true)
                }
                else -> {
                    val now = SystemClock.elapsedRealtime()
                    if (searchSince == 0L) searchSince = now
                    setDirect(true)
                    if (radioOn || now - searchSince >= DIRECT_FIRST_MS || direct.passesWithoutLink > 0) {
                        setRadio(true)
                    }
                }
            }
        }
    }

    override fun stop() {
        running = false
        stopAll()
    }

    override fun kickSearch() {
        if (directOn) direct.kickSearch()
        if (radioOn) radio.kickSearch()
    }

    override fun write(payload: ByteArray) {
        when {
            direct.confirmed -> direct.write(payload)
            radio.confirmed -> radio.write(payload)
            else -> {
                if (directOn) direct.write(payload)
                if (radioOn) radio.write(payload)
            }
        }
    }

    private fun stopAll() {
        setRadio(false)
        setDirect(false)
        searchSince = 0L
    }

    /** start() repeats for the stale-link checks; stop() only on a real change. */
    private fun setRadio(on: Boolean) {
        if (on) radio.start() else if (radioOn) radio.stop()
        radioOn = on
    }

    private fun setDirect(on: Boolean) {
        if (on) direct.start() else if (directOn) direct.stop()
        directOn = on
    }

    private companion object {
        const val DIRECT_FIRST_MS = 8_000L
    }
}
