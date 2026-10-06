package vad.dashing.tbox.phoneble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * GATT link between the phone and the ESP32 companion.
 *
 * Service 0x7B0E. The phone writes inbox 0x7B0F and subscribes to snapshot 0x7B10.
 * ATT payload needs MTU 64: a sealed packet is 24 bytes and a pair packet is up to 47.
 *
 * PAIR (clear), type 1:
 *   u8 type, u32le id, key[16], name utf-8 (26 bytes max)
 *
 * Sealed CMD=2, REFRESH=3, SNAP=4, 24 bytes:
 *   u8 type, u32le id, maskedCounter[4], cipher[11], tag[4]
 *   mask = AES-128-ECB(key, id||type||0xA5||zeros)[0:4]
 *   counter_le = masked XOR mask
 *   nonce = id||type||counter_le||0xC7||zeros
 *   cipher = body XOR AES-CTR(key, nonce)
 *   tag = HMAC-SHA256(key, type||id||counter_le||body)[0:4]
 *
 * CMD body: op u8, seat u8, arg i16le
 *   OP_WINDOW: seat 0..3 = FL, FR, RL, RR, 4 = all; arg WINDOW_CMD_*
 *   OP_SUNROOF: arg percent 0..100 step 10, or ROOF_TILT. OP_SUNSHADE: arg percent
 * REFRESH body: textHash u16le the phone already shows (0 = none), groups u8 the screen
 *   shows (GROUP_*, 0 = all for older apps), then zeros. Page 6 is sent for every group;
 *   GROUP_HEADER alone asks for page 6 only.
 * SNAP body: page u8, gen u8, data[9]
 *   page 0: left i16le, right i16le, fan u8. Missing temp 0x7FFF, fan 0xFF
 *   page 1: mode, auto, blow, sync. Missing 0xFF
 *   page 2: four seats. Missing 0xFF
 *   page 3: media volume. Missing 0xFF. 0 is mute
 *   page 4: playing u8 (1/0, missing 0xFF), position s u16le, duration s u16le
 *           (missing 0xFFFF), textHash u16le, textLen u8
 *   page 5: windows FL, FR, RL, RR (0..100 %, 0xFE between stops, missing 0xFF),
 *           sunroof (0..100 %, 102 tilt), sunshade (0..100 %), head unit 9 / 10
 *   page 6: outside i16le, cabin i16le tenths of °C. Missing 0x7FFF
 *   pages 8..18: now-playing text, 9 bytes each: title utf-8, 0x00, artist utf-8.
 *           Sent only when textHash differs from the one in REFRESH.
 *   Pages 0..3 are sealed as type 4 (older apps). Page 4 and later are sealed as type
 *   0x20 + page, so each has its own nonce.
 */
object PhoneBleCodec {
    const val SERVICE_UUID16: Int = 0x7B0E
    const val INBOX_UUID16: Int = 0x7B0F
    const val SNAP_UUID16: Int = 0x7B10
    val SERVICE_UUID: UUID = UUID.fromString("00007b0e-0000-1000-8000-00805f9b34fb")
    val INBOX_UUID: UUID = UUID.fromString("00007b0f-0000-1000-8000-00805f9b34fb")
    val SNAP_UUID: UUID = UUID.fromString("00007b10-0000-1000-8000-00805f9b34fb")
    const val ATT_MTU: Int = 64

    const val TYPE_PAIR: Int = 1
    const val TYPE_CMD: Int = 2
    const val TYPE_REFRESH: Int = 3
    const val TYPE_SNAP: Int = 4
    const val TYPE_SNAP_PAGE_BASE: Int = 0x20

    const val ID_LEN: Int = 4
    const val KEY_LEN: Int = 16
    const val BODY_LEN: Int = 11
    const val NAME_MAX: Int = 26
    const val SEALED_LEN: Int = 1 + ID_LEN + 4 + BODY_LEN + 4
    const val PAYLOAD_MAX: Int = SEALED_LEN
    const val SNAP_PAGES: Int = 4
    const val PAGE_MEDIA: Int = 4
    const val PAGE_BODY: Int = 5
    const val PAGE_CABIN: Int = 6
    const val PAGE_TEXT_FIRST: Int = 8
    const val TEXT_CHUNK: Int = 9
    const val TITLE_MAX: Int = 60
    const val ARTIST_MAX: Int = 30
    const val TEXT_MAX: Int = TITLE_MAX + 1 + ARTIST_MAX
    const val TEXT_PAGES: Int = (TEXT_MAX + TEXT_CHUNK - 1) / TEXT_CHUNK
    const val MISSING_U16: Int = 0xFFFF
    private const val PAGE_MAX: Int = PAGE_TEXT_FIRST + TEXT_PAGES - 1

    fun snapType(page: Int): Int = if (page >= PAGE_MEDIA) TYPE_SNAP_PAGE_BASE + page else TYPE_SNAP

    fun isSnapType(type: Int): Boolean =
        type == TYPE_SNAP || type in (TYPE_SNAP_PAGE_BASE + PAGE_MEDIA)..(TYPE_SNAP_PAGE_BASE + PAGE_MAX)

    private fun isSealedType(type: Int): Boolean =
        type == TYPE_CMD || type == TYPE_REFRESH || isSnapType(type)
    const val MISSING_TEMP: Int = 0x7FFF
    const val MISSING_U8: Int = 0xFF
    const val WIRE_WINDOW_BETWEEN: Int = 0xFE
    /** Window between the stops 0 / 20 / 80 / 100 (A9 BCM reads −1). */
    const val WINDOW_BETWEEN: Int = -1
    const val ROOF_TILT: Int = 102

    const val GROUP_CLIMATE: Int = 1
    const val GROUP_SEATS: Int = 2
    const val GROUP_MEDIA: Int = 4
    const val GROUP_WINDOWS: Int = 8
    const val GROUP_ALL: Int = GROUP_CLIMATE or GROUP_SEATS or GROUP_MEDIA or GROUP_WINDOWS
    /** Header temperatures only (page 6 is part of every answer). */
    const val GROUP_HEADER: Int = 16
    private const val GROUP_MASK: Int = GROUP_ALL or GROUP_HEADER

    const val WINDOW_ALL: Int = 4
    const val WINDOW_CMD_CLOSE: Int = 0
    /** 20 % on Android 9, vent command on Android 10. */
    const val WINDOW_CMD_VENT: Int = 1
    /** 80 % on Android 9; Android 10 opens fully. */
    const val WINDOW_CMD_COMFORT: Int = 2
    const val WINDOW_CMD_OPEN: Int = 3

    const val OP_TEMP_LEFT: Int = 1
    const val OP_TEMP_RIGHT: Int = 2
    const val OP_FAN: Int = 3
    const val OP_AUTO: Int = 4
    const val OP_BLOW: Int = 5
    const val OP_MODE: Int = 6
    const val OP_SYNC: Int = 7
    const val OP_SEAT: Int = 8
    const val OP_VOLUME: Int = 16
    const val OP_MEDIA_PREV: Int = 17
    const val OP_MEDIA_PLAY_PAUSE: Int = 18
    const val OP_MEDIA_NEXT: Int = 19
    const val OP_WINDOW: Int = 24
    const val OP_SUNROOF: Int = 25
    const val OP_SUNSHADE: Int = 26

    const val TEMP_MIN: Int = 160
    const val TEMP_MAX: Int = 300
    const val TEMP_STEP: Int = 5

    data class PairMaterial(
        val id: ByteArray,
        val key: ByteArray,
        val name: String,
    )

    data class OpenPacket(
        val type: Int,
        val id: ByteArray,
        val counter: Long,
        val body: ByteArray,
    )

    data class Command(
        val op: Int,
        val seat: Int,
        val arg: Int,
    )

    data class Snapshot(
        val leftTenths: Int? = null,
        val rightTenths: Int? = null,
        val fan: Int? = null,
        val mode: Int? = null,
        val auto: Int? = null,
        val blow: Int? = null,
        val sync: Int? = null,
        val seats: List<Int?> = listOf(null, null, null, null),
        val volume: Int? = null,
        val playing: Int? = null,
        val positionMs: Long? = null,
        val durationMs: Long? = null,
        val title: String? = null,
        val artist: String? = null,
        val textHash: Int = 0,
        /** FL, FR, RL, RR: 0..100 %, [WINDOW_BETWEEN], or null. */
        val windows: List<Int?> = listOf(null, null, null, null),
        /** 0..100 %, [ROOF_TILT] when tilted. */
        val sunroof: Int? = null,
        val sunshade: Int? = null,
        val android10: Boolean? = null,
        val outsideTenths: Int? = null,
        val insideTenths: Int? = null,
        val gen: Int = 0,
    )

    /** Pages the companion sends for [groups] (0 = all), without text pages. */
    fun pagesForGroups(groups: Int): List<Int> {
        val wanted = if (groups and GROUP_MASK == 0) GROUP_ALL else groups
        return buildList {
            if (wanted and GROUP_CLIMATE != 0) addAll(listOf(0, 1))
            if (wanted and GROUP_SEATS != 0) add(2)
            if (wanted and GROUP_MEDIA != 0) addAll(listOf(3, PAGE_MEDIA))
            if (wanted and GROUP_WINDOWS != 0) add(PAGE_BODY)
            add(PAGE_CABIN)
        }
    }

    fun windowOpen(raw: Int?): Boolean = raw == WINDOW_BETWEEN || (raw != null && raw in 1..100)

    fun idHex(id: ByteArray): String =
        id.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    fun parseIdHex(hex: String): ByteArray? {
        val clean = hex.trim().lowercase()
        if (clean.length != ID_LEN * 2) return null
        return runCatching {
            ByteArray(ID_LEN) { index ->
                clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    fun utf8Truncate(text: String, maxBytes: Int): ByteArray {
        val raw = text.encodeToByteArray()
        if (raw.size <= maxBytes) return raw
        var end = maxBytes
        while (end > 0 && (raw[end].toInt() and 0xC0) == 0x80) end--
        return raw.copyOf(end)
    }

    fun pairPacket(id: ByteArray, key: ByteArray, name: String): ByteArray {
        require(id.size == ID_LEN && key.size == KEY_LEN)
        val nameBytes = utf8Truncate(name, NAME_MAX)
        val payload = ByteArray(1 + ID_LEN + KEY_LEN + nameBytes.size)
        payload[0] = TYPE_PAIR.toByte()
        id.copyInto(payload, 1)
        key.copyInto(payload, 1 + ID_LEN)
        nameBytes.copyInto(payload, 1 + ID_LEN + KEY_LEN)
        return payload
    }

    fun parsePair(payload: ByteArray): PairMaterial? {
        if (payload.size < 1 + ID_LEN + KEY_LEN) return null
        if (payload[0].toInt() and 0xFF != TYPE_PAIR) return null
        val nameBytes = payload.copyOfRange(1 + ID_LEN + KEY_LEN, payload.size)
        val name = if (nameBytes.size <= NAME_MAX) nameBytes else nameBytes.copyOf(NAME_MAX)
        return PairMaterial(
            id = payload.copyOfRange(1, 1 + ID_LEN),
            key = payload.copyOfRange(1 + ID_LEN, 1 + ID_LEN + KEY_LEN),
            name = name.decodeToString(),
        )
    }

    fun commandBody(op: Int, seat: Int, arg: Int): ByteArray {
        val body = ByteArray(BODY_LEN)
        body[0] = op.toByte()
        body[1] = seat.toByte()
        putI16(body, 2, arg)
        return body
    }

    fun readCommand(body: ByteArray): Command? {
        if (body.size < 4) return null
        return Command(
            op = body[0].toInt() and 0xFF,
            seat = body[1].toInt() and 0xFF,
            arg = getI16(body, 2),
        )
    }

    fun refreshBody(textHash: Int = 0, groups: Int = 0): ByteArray =
        ByteArray(BODY_LEN).also {
            putU16(it, 0, textHash and 0xFFFF)
            it[2] = (groups and GROUP_MASK).toByte()
        }

    fun readRefreshTextHash(body: ByteArray): Int = if (body.size < 2) 0 else getU16(body, 0)

    fun readRefreshGroups(body: ByteArray): Int = if (body.size < 3) 0 else body[2].toInt() and GROUP_MASK

    /** Drops characters the firmware JSON reader does not unescape, then fits [maxBytes]. */
    fun clipText(text: String?, maxBytes: Int): String {
        if (text == null) return ""
        val clean = text.filter { it >= ' ' && it != '\u2028' && it != '\u2029' }.trim()
        return utf8Truncate(clean, maxBytes).decodeToString()
    }

    /** title, 0x00, artist; empty when both are blank. */
    fun textBytes(title: String?, artist: String?): ByteArray {
        val t = utf8Truncate(clipText(title, TITLE_MAX), TITLE_MAX)
        val a = utf8Truncate(clipText(artist, ARTIST_MAX), ARTIST_MAX)
        if (t.isEmpty() && a.isEmpty()) return ByteArray(0)
        return t + byteArrayOf(0) + a
    }

    /** FNV-1a 32 folded to 16 bits, never 0 for non-empty text. Same as the firmware. */
    fun textHash(bytes: ByteArray): Int {
        if (bytes.isEmpty()) return 0
        var h = 0x811C9DC5.toInt()
        for (b in bytes) {
            h = h xor (b.toInt() and 0xFF)
            h *= 0x01000193
        }
        val folded = (h xor (h ushr 16)) and 0xFFFF
        return if (folded == 0) 1 else folded
    }

    /** Reference encoder for the pages the firmware builds; [phoneTextHash], [groups] as in REFRESH. */
    fun snapshotBodies(gen: Int, snap: Snapshot, phoneTextHash: Int = 0, groups: Int = 0): List<ByteArray> {
        val generation = gen and 0xFF
        val text = textBytes(snap.title, snap.artist)
        val hash = textHash(text)
        val valuePages = pagesForGroups(groups)
        val pages = buildList {
            addAll(valuePages)
            if (PAGE_MEDIA in valuePages && text.isNotEmpty() && hash != phoneTextHash) {
                addAll(PAGE_TEXT_FIRST until PAGE_TEXT_FIRST + (text.size + TEXT_CHUNK - 1) / TEXT_CHUNK)
            }
        }
        return pages.map { page ->
            val body = ByteArray(BODY_LEN)
            body[0] = page.toByte()
            body[1] = generation.toByte()
            when (page) {
                0 -> {
                    putI16(body, 2, snap.leftTenths ?: MISSING_TEMP)
                    putI16(body, 4, snap.rightTenths ?: MISSING_TEMP)
                    body[6] = (snap.fan ?: MISSING_U8).toByte()
                }
                1 -> {
                    body[2] = (snap.mode ?: MISSING_U8).toByte()
                    body[3] = (snap.auto ?: MISSING_U8).toByte()
                    body[4] = (snap.blow ?: MISSING_U8).toByte()
                    body[5] = (snap.sync ?: MISSING_U8).toByte()
                }
                2 -> {
                    repeat(4) { seat ->
                        body[2 + seat] = (snap.seats.getOrNull(seat) ?: MISSING_U8).toByte()
                    }
                }
                3 -> body[2] = (snap.volume ?: MISSING_U8).toByte()
                PAGE_MEDIA -> {
                    body[2] = (snap.playing ?: MISSING_U8).toByte()
                    putU16(body, 3, seconds(snap.positionMs))
                    putU16(body, 5, seconds(snap.durationMs))
                    putU16(body, 7, hash)
                    body[9] = text.size.toByte()
                }
                PAGE_BODY -> {
                    repeat(4) { index ->
                        body[2 + index] = windowWire(snap.windows.getOrNull(index)).toByte()
                    }
                    body[6] = (snap.sunroof ?: MISSING_U8).toByte()
                    body[7] = (snap.sunshade ?: MISSING_U8).toByte()
                    body[8] = when (snap.android10) {
                        true -> 10
                        false -> 9
                        null -> MISSING_U8
                    }.toByte()
                }
                PAGE_CABIN -> {
                    putI16(body, 2, snap.outsideTenths ?: MISSING_TEMP)
                    putI16(body, 4, snap.insideTenths ?: MISSING_TEMP)
                }
                else -> {
                    val from = (page - PAGE_TEXT_FIRST) * TEXT_CHUNK
                    text.copyInto(body, 2, from, minOf(text.size, from + TEXT_CHUNK))
                }
            }
            body
        }
    }

    fun windowWire(raw: Int?): Int = when {
        raw == WINDOW_BETWEEN -> WIRE_WINDOW_BETWEEN
        raw != null && raw in 0..100 -> raw
        else -> MISSING_U8
    }

    private fun windowFromWire(wire: Int): Int? = when (wire) {
        WIRE_WINDOW_BETWEEN -> WINDOW_BETWEEN
        in 0..100 -> wire
        else -> null
    }

    private fun seconds(ms: Long?): Int =
        if (ms == null || ms < 0) MISSING_U16 else (ms / 1000L).coerceAtMost(MISSING_U16 - 1L).toInt()

    /**
     * Joins SNAP pages into one [Snapshot]. Text pages of one gen are buffered until all
     * of them and the media page with the matching length have arrived; until then the
     * previous title stays on screen.
     */
    class SnapshotAssembler {
        var snapshot: Snapshot = Snapshot()
            private set
        private val chunks = arrayOfNulls<ByteArray>(TEXT_PAGES)
        private var chunkGen = -1
        private var pendingGen = -1
        private var pendingLen = 0
        private var pendingHash = 0

        /** [type] is the sealed packet type; it must match the page the body claims. */
        fun accept(type: Int, body: ByteArray): Snapshot {
            if (body.size < BODY_LEN) return snapshot
            val page = body[0].toInt() and 0xFF
            val gen = body[1].toInt() and 0xFF
            if (type != snapType(page)) return snapshot
            if (page < PAGE_TEXT_FIRST) {
                snapshot = overlaySnapshot(snapshot, body)
                if (page == PAGE_MEDIA) acceptTextHeader(gen, getU16(body, 7), body[9].toInt() and 0xFF)
            } else if (page - PAGE_TEXT_FIRST < TEXT_PAGES) {
                if (gen != chunkGen) {
                    chunks.fill(null)
                    chunkGen = gen
                }
                chunks[page - PAGE_TEXT_FIRST] = body.copyOfRange(2, BODY_LEN)
            }
            completeText()
            return snapshot
        }

        /** Local change (e.g. play/pause tap) shown before the next snapshot. */
        fun replace(next: Snapshot) {
            snapshot = next
        }

        private fun acceptTextHeader(gen: Int, hash: Int, len: Int) {
            when {
                hash == snapshot.textHash -> pendingGen = -1
                len == 0 -> {
                    snapshot = snapshot.copy(title = null, artist = null, textHash = hash)
                    pendingGen = -1
                }
                else -> {
                    pendingGen = gen
                    pendingLen = len.coerceAtMost(TEXT_MAX)
                    pendingHash = hash
                }
            }
        }

        private fun completeText() {
            if (pendingGen < 0 || pendingGen != chunkGen) return
            val needed = (pendingLen + TEXT_CHUNK - 1) / TEXT_CHUNK
            val text = ByteArray(needed * TEXT_CHUNK)
            for (index in 0 until needed) {
                val chunk = chunks[index] ?: return
                chunk.copyInto(text, index * TEXT_CHUNK)
            }
            val bytes = text.copyOf(pendingLen)
            val split = bytes.indexOf(0.toByte()).let { if (it < 0) bytes.size else it }
            val title = bytes.copyOfRange(0, split).decodeToString().ifEmpty { null }
            val artist = if (split < bytes.size) {
                bytes.copyOfRange(split + 1, bytes.size).decodeToString().ifEmpty { null }
            } else {
                null
            }
            snapshot = snapshot.copy(title = title, artist = artist, textHash = pendingHash)
            pendingGen = -1
        }
    }

    /**
     * Every snapshot has a new gen and its pages arrive one by one. Fields of pages not
     * yet received keep the previous values; resetting them would blink a dash on screen.
     */
    fun overlaySnapshot(base: Snapshot, body: ByteArray): Snapshot {
        if (body.size < 6) return base
        val page = body[0].toInt() and 0xFF
        val gen = body[1].toInt() and 0xFF
        return when (page) {
            0 -> base.copy(
                gen = gen,
                leftTenths = optionalTemp(getI16(body, 2)),
                rightTenths = optionalTemp(getI16(body, 4)),
                fan = optionalU8(body[6].toInt() and 0xFF),
            )
            1 -> base.copy(
                gen = gen,
                mode = optionalU8(body[2].toInt() and 0xFF),
                auto = optionalU8(body[3].toInt() and 0xFF),
                blow = optionalU8(body[4].toInt() and 0xFF),
                sync = optionalU8(body[5].toInt() and 0xFF),
            )
            2 -> base.copy(
                gen = gen,
                seats = List(4) { index -> optionalU8(body[2 + index].toInt() and 0xFF) },
            )
            3 -> base.copy(
                gen = gen,
                volume = optionalU8(body[2].toInt() and 0xFF),
            )
            PAGE_MEDIA -> if (body.size < 7) base else base.copy(
                gen = gen,
                playing = optionalU8(body[2].toInt() and 0xFF),
                positionMs = optionalU16(getU16(body, 3))?.let { it * 1000L },
                durationMs = optionalU16(getU16(body, 5))?.let { it * 1000L },
            )
            PAGE_BODY -> if (body.size < 9) base else base.copy(
                gen = gen,
                windows = List(4) { index -> windowFromWire(body[2 + index].toInt() and 0xFF) },
                sunroof = optionalU8(body[6].toInt() and 0xFF)?.takeIf { it in 0..100 || it == ROOF_TILT },
                sunshade = optionalU8(body[7].toInt() and 0xFF)?.takeIf { it in 0..100 },
                android10 = when (body[8].toInt() and 0xFF) {
                    10 -> true
                    9 -> false
                    else -> null
                },
            )
            PAGE_CABIN -> base.copy(
                gen = gen,
                outsideTenths = optionalTemp(getI16(body, 2)),
                insideTenths = optionalTemp(getI16(body, 4)),
            )
            else -> base
        }
    }

    fun seal(key: ByteArray, type: Int, id: ByteArray, counter: Long, body: ByteArray): ByteArray {
        require(key.size == KEY_LEN && id.size == ID_LEN)
        require(isSealedType(type))
        val counterLe = counter.toInt()
        val padded = ByteArray(BODY_LEN)
        body.copyInto(padded, 0, 0, minOf(body.size, BODY_LEN))
        val masked = mask(key, type, id).let { maskBytes ->
            ByteArray(4) { index ->
                (counterByte(counterLe, index).toInt() xor (maskBytes[index].toInt() and 0xFF)).toByte()
            }
        }
        val cipher = aesCtr(key, nonce(type, id, counterLe), padded)
        val tag = hmacTag(key, type, id, counterLe, padded)
        val out = ByteArray(SEALED_LEN)
        out[0] = type.toByte()
        id.copyInto(out, 1)
        masked.copyInto(out, 5)
        cipher.copyInto(out, 9)
        tag.copyInto(out, 9 + BODY_LEN)
        return out
    }

    fun open(key: ByteArray, payload: ByteArray): OpenPacket? {
        if (key.size != KEY_LEN || payload.size != SEALED_LEN) return null
        val type = payload[0].toInt() and 0xFF
        if (!isSealedType(type)) return null
        val id = payload.copyOfRange(1, 5)
        val maskBytes = mask(key, type, id)
        var counter = 0
        for (index in 0 until 4) {
            val plain = (payload[5 + index].toInt() and 0xFF) xor (maskBytes[index].toInt() and 0xFF)
            counter = counter or (plain shl (8 * index))
        }
        val body = aesCtr(key, nonce(type, id, counter), payload.copyOfRange(9, 9 + BODY_LEN))
        val tag = hmacTag(key, type, id, counter, body)
        val got = payload.copyOfRange(9 + BODY_LEN, SEALED_LEN)
        if (!tag.contentEquals(got)) return null
        return OpenPacket(type = type, id = id, counter = counter.toLong() and 0xFFFF_FFFFL, body = body)
    }

    fun counterAccepted(last: Long, next: Long): Boolean = next > last

    fun stepTemp(current: Int?, increase: Boolean): Int? {
        val base = current ?: return null
        val rem = Math.floorMod(base - TEMP_MIN, TEMP_STEP)
        var next = if (increase) {
            if (rem == 0) base + TEMP_STEP else base + (TEMP_STEP - rem)
        } else {
            if (rem == 0) base - TEMP_STEP else base - rem
        }
        if (next < TEMP_MIN) next = TEMP_MIN
        if (next > TEMP_MAX) next = TEMP_MAX
        return next
    }

    private fun optionalTemp(raw: Int): Int? = raw.takeIf { it != MISSING_TEMP }

    private fun optionalU8(raw: Int): Int? = raw.takeIf { it != MISSING_U8 }

    private fun optionalU16(raw: Int): Int? = raw.takeIf { it != MISSING_U16 }

    private fun counterByte(counter: Int, index: Int): Byte =
        ((counter ushr (8 * index)) and 0xFF).toByte()

    private fun mask(key: ByteArray, type: Int, id: ByteArray): ByteArray {
        val block = ByteArray(16)
        id.copyInto(block, 0)
        block[4] = type.toByte()
        block[5] = 0xA5.toByte()
        return aesEcb(key, block).copyOf(4)
    }

    private fun nonce(type: Int, id: ByteArray, counter: Int): ByteArray {
        val block = ByteArray(16)
        id.copyInto(block, 0)
        block[4] = type.toByte()
        for (index in 0 until 4) {
            block[5 + index] = counterByte(counter, index)
        }
        block[9] = 0xC7.toByte()
        return block
    }

    private fun hmacTag(key: ByteArray, type: Int, id: ByteArray, counter: Int, body: ByteArray): ByteArray {
        val msg = ByteArray(1 + ID_LEN + 4 + BODY_LEN)
        msg[0] = type.toByte()
        id.copyInto(msg, 1)
        for (index in 0 until 4) {
            msg[5 + index] = counterByte(counter, index)
        }
        body.copyInto(msg, 9, 0, BODY_LEN)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(msg).copyOf(4)
    }

    private fun aesEcb(key: ByteArray, block: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(block)
    }

    private fun aesCtr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun putI16(dst: ByteArray, offset: Int, value: Int) {
        val le = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
        le.array().copyInto(dst, offset)
    }

    private fun putU16(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = value.toByte()
        dst[offset + 1] = (value ushr 8).toByte()
    }

    private fun getU16(src: ByteArray, offset: Int): Int =
        (src[offset].toInt() and 0xFF) or ((src[offset + 1].toInt() and 0xFF) shl 8)

    private fun getI16(src: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(src, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
}
