package vad.dashing.tbox.phoneble

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** A phone paired with the head unit itself; the key never leaves this record. */
class PhoneDirectRecord(
    val id: ByteArray,
    val key: ByteArray,
    val name: String,
    val counter: Long,
    val windowMask: Long = if (counter > 0L) 1L else 0L,
) {
    val idHex: String get() = PhoneBleCodec.idHex(id)

    fun withCounter(next: Long, nextMask: Long = 1L): PhoneDirectRecord =
        PhoneDirectRecord(id, key, name, next, nextMask)
}

/**
 * Head-unit side of the direct RFCOMM link: the same rules the companion firmware applies
 * in `ble_phone.c`, without Bluetooth. Pairing needs an open window and a "yes" on the
 * head unit; sealed packets need a known id, the stored key and a counter accepted by the
 * 32-packet sliding anti-replay window. Thread-safe.
 */
class PhoneDirectHost(initial: List<PhoneDirectRecord>) {
    sealed interface Result {
        /** New phone asks to pair; the head unit shows "Allow?". */
        data class PairRequest(val idHex: String, val name: String) : Result

        data class Command(val idHex: String, val op: Int, val seat: Int, val arg: Int) : Result

        /** Answer with [sealSnapshot] using this [counter]; the phone drops pages of older requests. */
        data class Refresh(val idHex: String, val counter: Long, val textHash: Int, val groups: Int) : Result

        /** Assembled two-page GNSS location (`LOC_POS` + `LOC_MOTION`) from the phone. */
        data class Location(
            val idHex: String,
            val counter: Long,
            val location: PhoneBleCodec.PhoneLocation,
        ) : Result

        /** First page of a two-page location packet; link is valid, waiting for the partner page. */
        data class TelemetryPartial(val idHex: String, val counter: Long) : Result

        /** Gravity-projected gyroscope window (`YAW`) from the phone. */
        data class Yaw(
            val idHex: String,
            val counter: Long,
            val sample: PhoneBleCodec.PhoneYawSample,
        ) : Result

        /** Tilt-compensated magnetometer sample (`MAG`) from the phone. */
        data class Mag(
            val idHex: String,
            val counter: Long,
            val sample: PhoneBleCodec.PhoneMagSample,
        ) : Result

        /** A valid packet that needs no action (repeated PAIR of a known phone). */
        data object Accepted : Result

        data object Ignored : Result
    }

    private val lock = Any()
    private val phones = initial.take(MAX_PHONES).toMutableList()
    private val locAssemblers = HashMap<String, PhoneBleCodec.LocAssembler>()
    private var learnOpen = false
    private var pending: PhoneBleCodec.PairMaterial? = null
    private var deniedIdHex: String? = null
    private var countersDirty = false

    val learning: Boolean get() = synchronized(lock) { learnOpen }

    fun records(): List<PhoneDirectRecord> = synchronized(lock) { phones.toList() }

    fun beginLearn() = synchronized(lock) {
        learnOpen = true
        pending = null
        deniedIdHex = null
    }

    fun endLearn() = synchronized(lock) {
        learnOpen = false
        pending = null
    }

    fun onPayload(payload: ByteArray): Result = synchronized(lock) {
        if (payload.isEmpty()) return Result.Ignored
        val type = payload[0].toInt() and 0xFF
        when {
            type == PhoneBleCodec.TYPE_PAIR -> onPair(payload)
            type == PhoneBleCodec.TYPE_CMD ||
                type == PhoneBleCodec.TYPE_REFRESH ||
                PhoneBleCodec.isTelemetryType(type) -> onSealed(payload)
            else -> Result.Ignored
        }
    }

    /** Stores the pending phone. False when it is not the one waiting or the list is full. */
    fun allow(idHex: String): Boolean = synchronized(lock) {
        val material = pending?.takeIf { PhoneBleCodec.idHex(it.id) == idHex } ?: return false
        val index = phones.indexOfFirst { it.id.contentEquals(material.id) }
        if (index < 0 && phones.size >= MAX_PHONES) return false
        // A new key starts a new counter; the same key keeps replay protection.
        val existing = phones.getOrNull(index)?.takeIf { it.key.contentEquals(material.key) }
        val counter = existing?.counter ?: 0L
        val mask = existing?.windowMask ?: 0L
        val record = PhoneDirectRecord(material.id, material.key, material.name, counter, mask)
        if (index >= 0) phones[index] = record else phones += record
        pending = null
        learnOpen = false
        true
    }

    /** After "no" the same id is ignored until the window closes. */
    fun deny(idHex: String) = synchronized(lock) {
        if (pending?.let { PhoneBleCodec.idHex(it.id) } == idHex) pending = null
        deniedIdHex = idHex
    }

    fun forget(idHex: String): Boolean = synchronized(lock) {
        locAssemblers.remove(idHex)
        phones.removeAll { it.idHex == idHex }
    }

    /** True once after a counter moved; the caller persists [records] then. */
    fun takeCountersDirty(): Boolean = synchronized(lock) {
        val dirty = countersDirty
        countersDirty = false
        dirty
    }

    /** Sealed SNAP pages for one phone, same pages and types as the companion sends. */
    fun sealSnapshot(
        idHex: String,
        counter: Long,
        gen: Int,
        snap: PhoneBleCodec.Snapshot,
        phoneTextHash: Int,
        groups: Int,
    ): List<ByteArray> {
        val record = synchronized(lock) { phones.firstOrNull { it.idHex == idHex } } ?: return emptyList()
        return PhoneBleCodec.snapshotBodies(gen, snap, phoneTextHash, groups).map { body ->
            val page = body[0].toInt() and 0xFF
            PhoneBleCodec.seal(record.key, PhoneBleCodec.snapType(page), record.id, counter, body)
        }
    }

    /** Sealed `TELEMETRY_ACK` packet keeping the phone's RFCOMM link alive and updating `telemetryDemand`. */
    fun sealTelemetryAck(
        idHex: String,
        counter: Long,
        ack: PhoneBleCodec.TelemetryAck,
    ): ByteArray? {
        val record = synchronized(lock) { phones.firstOrNull { it.idHex == idHex } } ?: return null
        val body = PhoneBleCodec.telemetryAckBody(ack)
        return PhoneBleCodec.seal(record.key, PhoneBleCodec.TYPE_TELEMETRY_ACK, record.id, counter, body)
    }

    private fun onPair(payload: ByteArray): Result {
        if (!learnOpen) return Result.Ignored
        val material = PhoneBleCodec.parsePair(payload) ?: return Result.Ignored
        val idHex = PhoneBleCodec.idHex(material.id)
        if (idHex == deniedIdHex) return Result.Ignored
        // One dialog per phone and key, even if the app writes the packet twice.
        val waiting = pending
        if (waiting != null && waiting.id.contentEquals(material.id) && waiting.key.contentEquals(material.key)) {
            return Result.Accepted
        }
        val known = phones.firstOrNull { it.id.contentEquals(material.id) }
        if (known != null && known.key.contentEquals(material.key)) return Result.Accepted
        val name = material.name.filter { it >= ' ' }.trim().ifEmpty { idHex }
        pending = material.copy(name = name)
        return Result.PairRequest(idHex, name)
    }

    private fun onSealed(payload: ByteArray): Result {
        if (payload.size != PhoneBleCodec.SEALED_LEN) return Result.Ignored
        val id = payload.copyOfRange(1, 1 + PhoneBleCodec.ID_LEN)
        val index = phones.indexOfFirst { it.id.contentEquals(id) }
        if (index < 0) return Result.Ignored
        val record = phones[index]
        val open = PhoneBleCodec.open(record.key, payload) ?: return Result.Ignored
        val acceptedWindow = PhoneBleCodec.acceptCounterInWindow(
            maxCounter = record.counter,
            windowMask = record.windowMask,
            next = open.counter,
        ) ?: return Result.Ignored
        phones[index] = record.withCounter(acceptedWindow.first, acceptedWindow.second)
        countersDirty = true
        return when (open.type) {
            PhoneBleCodec.TYPE_CMD -> {
                val cmd = PhoneBleCodec.readCommand(open.body) ?: return Result.Ignored
                Result.Command(record.idHex, cmd.op, cmd.seat, cmd.arg)
            }
            PhoneBleCodec.TYPE_REFRESH -> Result.Refresh(
                idHex = record.idHex,
                counter = open.counter,
                textHash = PhoneBleCodec.readRefreshTextHash(open.body),
                groups = PhoneBleCodec.readRefreshGroups(open.body),
            )
            PhoneBleCodec.TYPE_LOC_POS, PhoneBleCodec.TYPE_LOC_MOTION -> {
                val assembler = locAssemblers.getOrPut(record.idHex) { PhoneBleCodec.LocAssembler() }
                val loc = assembler.accept(open.type, open.body)
                if (loc != null) {
                    Result.Location(record.idHex, open.counter, loc)
                } else {
                    Result.TelemetryPartial(record.idHex, open.counter)
                }
            }
            PhoneBleCodec.TYPE_YAW -> {
                val yaw = PhoneBleCodec.readYaw(open.body) ?: return Result.Ignored
                Result.Yaw(record.idHex, open.counter, yaw)
            }
            PhoneBleCodec.TYPE_MAG -> {
                val mag = PhoneBleCodec.readMag(open.body) ?: return Result.Ignored
                Result.Mag(record.idHex, open.counter, mag)
            }
            else -> Result.Ignored
        }
    }

    companion object {
        const val MAX_PHONES: Int = 10

        fun toJson(records: List<PhoneDirectRecord>): String {
            val array = JSONArray()
            for (record in records) {
                array.put(
                    JSONObject()
                        .put("id", record.idHex)
                        .put("key", Base64.getEncoder().encodeToString(record.key))
                        .put("name", record.name)
                        .put("counter", record.counter),
                )
            }
            return array.toString()
        }

        fun fromJson(json: String?): List<PhoneDirectRecord> {
            if (json.isNullOrBlank()) return emptyList()
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            return buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = PhoneBleCodec.parseIdHex(item.optString("id")) ?: continue
                    val key = runCatching { Base64.getDecoder().decode(item.optString("key")) }.getOrNull()
                    if (key == null || key.size != PhoneBleCodec.KEY_LEN) continue
                    add(PhoneDirectRecord(id, key, item.optString("name"), item.optLong("counter", 0L)))
                }
            }
        }
    }
}
