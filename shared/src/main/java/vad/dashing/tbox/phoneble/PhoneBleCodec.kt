package vad.dashing.tbox.phoneble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Legacy BLE advertisement for the phone companion.
 *
 * Service data UUID 0x7B0E. Payload max 24 bytes.
 * Legacy advertising is 31 bytes, Android adds a 3-byte flags field, and the
 * 16-bit service-data AD header takes 4 more, so the service payload is 24.
 *
 * PAIR (clear), type 1, up to 3 pages:
 *   u8 type, u32le id, u8 (index<<4)|count, chunk
 *   page 0 chunk: key[16] + name utf-8
 *   later pages: the rest of the name (26 bytes max)
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
 * REFRESH body: zeros
 * SNAP body: page u8, gen u8, data[9]
 *   page 0: left i16le, right i16le, fan u8. Missing temp 0x7FFF, fan 0xFF
 *   page 1: mode, auto, blow, sync. Missing 0xFF
 *   page 2: four seats. Missing 0xFF
 *   page 3: media volume. Missing 0xFF. 0 is mute
 */
object PhoneBleCodec {
    const val SERVICE_UUID16: Int = 0x7B0E
    val SERVICE_UUID: UUID = UUID.fromString("00007b0e-0000-1000-8000-00805f9b34fb")

    const val TYPE_PAIR: Int = 1
    const val TYPE_CMD: Int = 2
    const val TYPE_REFRESH: Int = 3
    const val TYPE_SNAP: Int = 4

    const val ID_LEN: Int = 4
    const val KEY_LEN: Int = 16
    const val BODY_LEN: Int = 11
    const val NAME_MAX: Int = 26
    const val PAYLOAD_MAX: Int = 24
    const val PAIR_HEADER: Int = 6
    const val PAIR_CHUNK: Int = PAYLOAD_MAX - PAIR_HEADER
    const val SEALED_LEN: Int = 1 + ID_LEN + 4 + BODY_LEN + 4
    const val SNAP_PAGES: Int = 4
    const val MISSING_TEMP: Int = 0x7FFF
    const val MISSING_U8: Int = 0xFF

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

    const val TEMP_MIN: Int = 160
    const val TEMP_MAX: Int = 300
    const val TEMP_STEP: Int = 5

    data class PairPage(
        val id: ByteArray,
        val index: Int,
        val count: Int,
        val chunk: ByteArray,
    )

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
        val gen: Int = 0,
    )

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

    fun pairPages(id: ByteArray, key: ByteArray, name: String): List<ByteArray> {
        require(id.size == ID_LEN && key.size == KEY_LEN)
        val nameBytes = utf8Truncate(name, NAME_MAX)
        val chunks = ArrayList<ByteArray>(3)
        val firstName = minOf(nameBytes.size, PAIR_CHUNK - KEY_LEN)
        chunks.add(key + nameBytes.copyOfRange(0, firstName))
        var offset = firstName
        while (offset < nameBytes.size) {
            val end = minOf(offset + PAIR_CHUNK, nameBytes.size)
            chunks.add(nameBytes.copyOfRange(offset, end))
            offset = end
        }
        return chunks.mapIndexed { index, chunk ->
            val payload = ByteArray(PAIR_HEADER + chunk.size)
            payload[0] = TYPE_PAIR.toByte()
            id.copyInto(payload, 1)
            payload[5] = ((index shl 4) or chunks.size).toByte()
            chunk.copyInto(payload, PAIR_HEADER)
            payload
        }
    }

    fun parsePairPage(payload: ByteArray): PairPage? {
        if (payload.size < PAIR_HEADER || payload[0].toInt() and 0xFF != TYPE_PAIR) return null
        val packed = payload[5].toInt() and 0xFF
        val index = packed shr 4
        val count = packed and 0x0F
        if (count !in 1..3 || index >= count) return null
        return PairPage(
            id = payload.copyOfRange(1, 5),
            index = index,
            count = count,
            chunk = payload.copyOfRange(PAIR_HEADER, payload.size),
        )
    }

    fun assemblePair(pages: List<PairPage>): PairMaterial? {
        if (pages.isEmpty()) return null
        val count = pages.first().count
        if (pages.any { it.count != count || !it.id.contentEquals(pages.first().id) }) return null
        val byIndex = pages.associateBy { it.index }
        if ((0 until count).any { it !in byIndex }) return null
        val first = byIndex.getValue(0).chunk
        if (first.size < KEY_LEN) return null
        val name = ByteArray(NAME_MAX)
        var nameLen = 0
        val head = first.copyOfRange(KEY_LEN, first.size)
        val take = minOf(head.size, NAME_MAX)
        head.copyInto(name, 0, 0, take)
        nameLen = take
        for (index in 1 until count) {
            val rest = byIndex.getValue(index).chunk
            val room = NAME_MAX - nameLen
            if (room <= 0) break
            val n = minOf(rest.size, room)
            rest.copyInto(name, nameLen, 0, n)
            nameLen += n
        }
        return PairMaterial(
            id = pages.first().id.copyOf(),
            key = first.copyOfRange(0, KEY_LEN),
            name = name.copyOf(nameLen).decodeToString(),
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

    fun refreshBody(): ByteArray = ByteArray(BODY_LEN)

    fun snapshotBodies(gen: Int, snap: Snapshot): List<ByteArray> {
        val generation = gen and 0xFF
        return (0 until SNAP_PAGES).map { page ->
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
                else -> body[2] = (snap.volume ?: MISSING_U8).toByte()
            }
            body
        }
    }

    fun overlaySnapshot(base: Snapshot, body: ByteArray): Snapshot {
        if (body.size < 6) return base
        val page = body[0].toInt() and 0xFF
        val gen = body[1].toInt() and 0xFF
        val fresh = if (base.gen == gen) base else Snapshot(gen = gen)
        return when (page) {
            0 -> fresh.copy(
                gen = gen,
                leftTenths = optionalTemp(getI16(body, 2)),
                rightTenths = optionalTemp(getI16(body, 4)),
                fan = optionalU8(body[6].toInt() and 0xFF),
            )
            1 -> fresh.copy(
                gen = gen,
                mode = optionalU8(body[2].toInt() and 0xFF),
                auto = optionalU8(body[3].toInt() and 0xFF),
                blow = optionalU8(body[4].toInt() and 0xFF),
                sync = optionalU8(body[5].toInt() and 0xFF),
            )
            2 -> fresh.copy(
                gen = gen,
                seats = List(4) { index -> optionalU8(body[2 + index].toInt() and 0xFF) },
            )
            3 -> fresh.copy(
                gen = gen,
                volume = optionalU8(body[2].toInt() and 0xFF),
            )
            else -> fresh
        }
    }

    fun seal(key: ByteArray, type: Int, id: ByteArray, counter: Long, body: ByteArray): ByteArray {
        require(key.size == KEY_LEN && id.size == ID_LEN)
        require(type == TYPE_CMD || type == TYPE_REFRESH || type == TYPE_SNAP)
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
        if (type != TYPE_CMD && type != TYPE_REFRESH && type != TYPE_SNAP) return null
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

    private fun getI16(src: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(src, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
}
