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
) {
    val idHex: String get() = PhoneBleCodec.idHex(id)

    fun withCounter(next: Long): PhoneDirectRecord = PhoneDirectRecord(id, key, name, next)
}

/**
 * Head-unit side of the direct RFCOMM link: the same rules the companion firmware applies
 * in `ble_phone.c`, without Bluetooth. Pairing needs an open window and a "yes" on the
 * head unit; sealed packets need a known id, the stored key and a counter above the last one.
 * Thread-safe.
 */
class PhoneDirectHost(initial: List<PhoneDirectRecord>) {
    sealed interface Result {
        /** New phone asks to pair; the head unit shows "Allow?". */
        data class PairRequest(val idHex: String, val name: String) : Result

        data class Command(val idHex: String, val op: Int, val seat: Int, val arg: Int) : Result

        /** Answer with [sealSnapshot] using this [counter]; the phone drops pages of older requests. */
        data class Refresh(val idHex: String, val counter: Long, val textHash: Int, val groups: Int) : Result

        /** A valid packet that needs no action (repeated PAIR of a known phone). */
        data object Accepted : Result

        data object Ignored : Result
    }

    private val lock = Any()
    private val phones = initial.take(MAX_PHONES).toMutableList()
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
        when (payload[0].toInt() and 0xFF) {
            PhoneBleCodec.TYPE_PAIR -> onPair(payload)
            PhoneBleCodec.TYPE_CMD, PhoneBleCodec.TYPE_REFRESH -> onSealed(payload)
            else -> Result.Ignored
        }
    }

    /** Stores the pending phone. False when it is not the one waiting or the list is full. */
    fun allow(idHex: String): Boolean = synchronized(lock) {
        val material = pending?.takeIf { PhoneBleCodec.idHex(it.id) == idHex } ?: return false
        val index = phones.indexOfFirst { it.id.contentEquals(material.id) }
        if (index < 0 && phones.size >= MAX_PHONES) return false
        // A new key starts a new counter; the same key keeps replay protection.
        val counter = phones.getOrNull(index)?.takeIf { it.key.contentEquals(material.key) }?.counter ?: 0L
        val record = PhoneDirectRecord(material.id, material.key, material.name, counter)
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
        if (!PhoneBleCodec.counterAccepted(record.counter, open.counter)) return Result.Ignored
        phones[index] = record.withCounter(open.counter)
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
