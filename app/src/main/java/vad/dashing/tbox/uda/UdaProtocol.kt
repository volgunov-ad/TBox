package vad.dashing.tbox.uda

/**
 * TBox UDA (TID `0x38`) UDP commands reverse-engineered from `ydsapp/run/uda` + APP callers.
 *
 * Dispatch table (`Uda_Msg_Table`):
 * - `0x01` GetVersion → `0x81`
 * - `0x02` Suspend / `0x03` Resume / `0x04` Stop
 * - `0x05` DiagReq → ack `0x85` (9 bytes); async report `0x86`, result-ish `0x8a`/`0x8b`
 * - `0x07` FotaReq → `0x87`; report `0x88`
 * - `0x09` AbortReq → `0x89`
 * - `0x28` CanTP PassThrough / `0x29` Ctrl / `0x2e` Error
 *
 * DiagReq payload matches APP `Diag_ReadDtc_Req` / `Diag_ClearDtc_Req` stack buffer:
 * path string at 0, then typed fields at `0x80`…, variable data at `0x8d`.
 */
object UdaProtocol {
    const val TID: Byte = 0x38

    const val CMD_GET_VERSION: Byte = 0x01
    const val CMD_SUSPEND: Byte = 0x02
    const val CMD_RESUME: Byte = 0x03
    const val CMD_STOP: Byte = 0x04
    const val CMD_DIAG_REQ: Byte = 0x05
    const val CMD_FOTA_REQ: Byte = 0x07
    const val CMD_ABORT_REQ: Byte = 0x09
    const val CMD_CANTP_PASSTHROUGH: Byte = 0x28
    const val CMD_CANTP_CTRL: Byte = 0x29

    const val RSP_VERSION: Byte = 0x81.toByte()
    const val RSP_DIAG_REQ: Byte = 0x85.toByte()
    const val RSP_DIAG_REPORT: Byte = 0x86.toByte()
    const val RSP_FOTA_REQ: Byte = 0x87.toByte()
    const val RSP_FOTA_REPORT: Byte = 0x88.toByte()
    const val RSP_ABORT_REQ: Byte = 0x89.toByte()
    const val RSP_PROCESS_INFO: Byte = 0x8A.toByte()
    const val RSP_DIAG_RESULT: Byte = 0x8B.toByte()

    /** APP always points DiagReq at the JX65 CFG shared library. */
    const val DEFAULT_CFG_SO_PATH = "/ydsapp/run/fota/libJX65_n720_CFG.so"

    const val DIAG_HEADER_SIZE = 0x8D
    const val DIAG_OFF_FLAGS = 0x80
    const val DIAG_OFF_TYPE = 0x84
    const val DIAG_OFF_ECU_PARAM = 0x85
    const val DIAG_OFF_DATA_LEN = 0x89
    const val DIAG_OFF_PAYLOAD = 0x8D

    /** Matches APP `Diag_*_Req` stores at offset `0x80`. */
    const val DIAG_FLAGS_DEFAULT = 4

    enum class DiagType(val code: Int) {
        ReadDtc(0),
        ClearDtc(1),
        ReadDid(2),
        WriteDid(3),
    }

    /**
     * Build a DiagReq UDP payload (`0x8d + data.size` bytes).
     *
     * @param ecuParam opaque u32 from APP ECU table (often a handle / channel id).
     * @param payload type-specific bytes (ReadDtc: 2 bytes; ClearDtc: 3-byte groupOfDtc).
     */
    fun buildDiagReq(
        type: DiagType,
        payload: ByteArray,
        ecuParam: Int = 0,
        cfgSoPath: String = DEFAULT_CFG_SO_PATH,
        flags: Int = DIAG_FLAGS_DEFAULT,
    ): ByteArray {
        require(payload.size <= 0x1000) { "DiagReq payload too large: ${payload.size}" }
        val out = ByteArray(DIAG_HEADER_SIZE + payload.size)
        val pathBytes = cfgSoPath.toByteArray(Charsets.UTF_8)
        val pathCopy = minOf(pathBytes.size, DIAG_OFF_FLAGS - 1)
        System.arraycopy(pathBytes, 0, out, 0, pathCopy)
        writeLe32(out, DIAG_OFF_FLAGS, flags)
        out[DIAG_OFF_TYPE] = type.code.toByte()
        writeLe32(out, DIAG_OFF_ECU_PARAM, ecuParam)
        writeLe32(out, DIAG_OFF_DATA_LEN, payload.size)
        System.arraycopy(payload, 0, out, DIAG_OFF_PAYLOAD, payload.size)
        return out
    }

    /** Probe ReadDtc with zeroed ECU/param bytes — validates UDA pipe; may fail UDS without CFG params. */
    fun buildReadDtcProbe(
        ecuParam: Int = 0,
        payload: ByteArray = byteArrayOf(0x00, 0x00),
    ): ByteArray = buildDiagReq(DiagType.ReadDtc, payload, ecuParam = ecuParam)

    fun buildClearDtcProbe(
        groupOfDtc: ByteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
        ecuParam: Int = 0,
    ): ByteArray {
        require(groupOfDtc.size == 3) { "ClearDtc groupOfDtc must be 3 bytes" }
        return buildDiagReq(DiagType.ClearDtc, groupOfDtc, ecuParam = ecuParam)
    }

    private fun writeLe32(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}

/**
 * Vehicle control over CRT CMD `0x26` — fixed **45-byte** MCU frame
 * (APP `yds_mq_vctrl_sendto_mcu`).
 *
 * ## Opcode = TSP.RemoteControlCmdType
 *
 * APP `recv_cmd_vctrl` copies `cmdType` from the MQTT/protobuf message into `frame[0]`,
 * then a jump table packs extra fields and queues the frame. CRT `MsgHandler` forwards
 * cmds `0x20..0x3F` (including `0x26`) to the MCU, so the HU can send the same frames.
 *
 * Enum names/numbers taken from APP `RemoteControlCmdType_names` +
 * `RemoteControlCmdType_entries_by_number` (alphabetical name → number).
 *
 * ## Frame layout (observed)
 *
 * | Offset | Field |
 * |--------|--------|
 * | 0 | opcode (`RemoteControlCmdType`) |
 * | 1..8 | often message id / reserved (copied from TSP header) |
 * | 9 | primary parameter for many simple cmds (on/off, mode, …) |
 * | 10+ | cmd-specific (e.g. FIND_VEHICLE packs extra bytes at 10..11) |
 *
 * ## APP `recv_cmd_vctrl` jump table
 *
 * After copying `cmdType`→`frame[0]` and TSP id→`frame[1..8]`, APP switches on opcode
 * (`cmp #0x67` / `ldrls pc, [pc, r1, lsl #2]`). Non-default arms pack fields (often
 * `frame[9]`) and queue/send; the **DEFAULT** arm is an early return — **no MCU send**.
 * See [APP_PACKED_OPCODES]. Named opcodes that DEFAULT include LOCK/ENGINE/SEAT and
 * most of `0x20..0x2F`. Raw CRT `0x26` from the HU bypasses this table.
 *
 * ## Monitor OPEN/CLOSE
 *
 * Historical `ACTION_CLOSE` / `ACTION_OPEN` use opcode **`0x02` = FRONT_LIGHT**
 * with `frame[9]=0` / `1` — not LOCK (`0x14`).
 *
 * ## MCU (`TBOX_VP.bin`, RH850F1K)
 *
 * VP stores the 45-byte frame at `buf+5`, then switches on `frame[0]` (`0..0x3A`,
 * table `@0x9E008`). **FRONT_LIGHT** handler writes Com signal **`0x183`** with
 * value `1` (on, param=1) or `2` (off, param=0). **LOCK (`0x14`)** on this path is
 * a no-op (`dispose`/return) — same effective dead-end as APP jump-table DEFAULT;
 * BLE has a separate remapped path that can still log `lock ctrl success`.
 */
object CrtVctrlProtocol {
    const val CRT_CMD_VCTRL: Byte = 0x26
    const val FRAME_SIZE = 0x2D

    /** Primary parameter byte used by many simple cmds (FRONT_LIGHT, WINDOWS, POWER_ON_OFF, …). */
    const val OFF_PARAM0 = 9

    // --- RemoteControlCmdType (frame[0]) ---
    const val WINDOWS = 0x00
    const val GET_TBOX_LOG = 0x01
    const val FRONT_LIGHT = 0x02
    const val AIR_CONDITION_LEVEL = 0x03 // proto typo: AIR_CONDETION_LEVEL
    const val SCENE_CTRL = 0x04 // proto typo: SECNE_CTRL
    const val DEFROSTING = 0x05
    const val LIGHT_SHOW_CTRL = 0x06
    const val CLEAN_FAULT_CODE = 0x07
    const val POWER_ON_OFF = 0x08
    const val FIND_VEHICLE = 0x09
    const val SEAT_VEN = 0x0A
    /** Proto string is misspelled `QUERY_CERTIFIVCATE`. */
    const val QUERY_CERTIFICATE = 0x0B
    const val WRITE_CONFIG_CODE = 0x0C
    const val DIGITAL_KEY_CONTROL = 0x0D
    const val ACTION_TEST = 0x0E
    const val AIR_CONDITION_CTRL = 0x0F
    const val AUTOAIR_TIMELY = 0x10
    const val TRUNK_DOOR = 0x11
    const val GET_ECU_CONF_CODE = 0x12
    const val VEHICLE_EXAM = 0x13
    const val LOCK = 0x14
    const val LIGHT_SHOW_MODEL = 0x15
    const val WINDOW_ALL = 0x16
    const val ELE_FENCE = 0x17
    const val DOWNLOAD_CERTIFICATE = 0x18
    const val QUERY_ECUINFO = 0x19
    const val AIR_CONDITION = 0x1A
    const val KEY_UPDATE = 0x1B
    const val SEAT = 0x1C
    const val STERILIZE = 0x1D
    const val WRITE_VIN = 0x1E
    const val GET_CAN_STREAM = 0x1F
    const val ENGINE = 0x20
    const val ROOF_WINDOW = 0x21
    const val RESET_ECU = 0x22
    const val GREENCABIN_AUTO = 0x23
    const val AIR_PURIFIER = 0x24
    const val AUTO_AIR_CONDITION_CTRL = 0x25
    const val RAPID_COOLING = 0x26 // proto typo: REPID_COOLING
    const val MANUALAIR_TIMELY = 0x27
    const val REMOTE_DIAG = 0x28
    const val RAPID_HEATING = 0x29 // proto typo: REPID_HEATING
    const val CHARGE_RESERVE = 0x2A
    const val POWER_PHEV = 0x2B
    const val AIR_CONDITION_CTRL_MODE = 0x2C
    const val STEERING_WHEEL_HEATING = 0x2D
    const val HU_AWAKEN = 0x2E
    const val GREENCABIN_MANUAL = 0x2F

    /**
     * Opcodes with a non-DEFAULT arm in APP `recv_cmd_vctrl` (this firmware).
     * Everything else in `0..0x67` hits DEFAULT (return, no MCU send).
     * Extra slots `0x32..0x3A` / `0x64`/`0x65`/`0x67` have no public enum name.
     */
    val APP_PACKED_OPCODES: Set<Int> = setOf(
        WINDOWS, GET_TBOX_LOG, FRONT_LIGHT, AIR_CONDITION_LEVEL, SCENE_CTRL, DEFROSTING,
        LIGHT_SHOW_CTRL, CLEAN_FAULT_CODE, POWER_ON_OFF, FIND_VEHICLE, SEAT_VEN,
        QUERY_CERTIFICATE, ACTION_TEST, AIR_CONDITION_CTRL, TRUNK_DOOR, GET_ECU_CONF_CODE,
        LIGHT_SHOW_MODEL, WINDOW_ALL, ELE_FENCE, DOWNLOAD_CERTIFICATE, QUERY_ECUINFO,
        AIR_CONDITION, KEY_UPDATE, STERILIZE, WRITE_VIN,
        0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x64, 0x65, 0x67,
    )

    /**
     * Opcodes with a dedicated non-default arm in MCU VP jump table `@0x9E008`
     * (this `TBOX_VP.bin`). Others (incl. LOCK/ENGINE/WINDOW_ALL) hit shared
     * `dispose` return on that path.
     */
    val MCU_ACTIVE_OPCODES: Set<Int> = setOf(
        WINDOWS, GET_TBOX_LOG, FRONT_LIGHT, AIR_CONDITION_LEVEL, SCENE_CTRL, DEFROSTING,
        LIGHT_SHOW_CTRL, CLEAN_FAULT_CODE, POWER_ON_OFF, FIND_VEHICLE, SEAT_VEN,
        DOWNLOAD_CERTIFICATE, AIR_CONDITION, WRITE_VIN,
        0x32, 0x33, 0x34, 0x35, 0x36, 0x38, 0x39, 0x3A,
    )

    /** Com signal id used by MCU FRONT_LIGHT handler (`MOVEA 0x183` → Com set). */
    const val MCU_FRONT_LIGHT_COM_ID = 0x183

    /** Named enum members that `RemoteControlCmdType_IsValid` rejects (`0x20..0x2F`). */
    val ISVALID_REJECTS_NAMED: Set<Int> = setOf(
        ENGINE, ROOF_WINDOW, RESET_ECU, GREENCABIN_AUTO, AIR_PURIFIER,
        AUTO_AIR_CONDITION_CTRL, RAPID_COOLING, MANUALAIR_TIMELY, REMOTE_DIAG,
        RAPID_HEATING, CHARGE_RESERVE, POWER_PHEV, AIR_CONDITION_CTRL_MODE,
        STEERING_WHEEL_HEATING, HU_AWAKEN, GREENCABIN_MANUAL,
    )

    val NAMES: Map<Int, String> = mapOf(
        WINDOWS to "WINDOWS",
        GET_TBOX_LOG to "GET_TBOX_LOG",
        FRONT_LIGHT to "FRONT_LIGHT",
        AIR_CONDITION_LEVEL to "AIR_CONDITION_LEVEL",
        SCENE_CTRL to "SCENE_CTRL",
        DEFROSTING to "DEFROSTING",
        LIGHT_SHOW_CTRL to "LIGHT_SHOW_CTRL",
        CLEAN_FAULT_CODE to "CLEAN_FAULT_CODE",
        POWER_ON_OFF to "POWER_ON_OFF",
        FIND_VEHICLE to "FIND_VEHICLE",
        SEAT_VEN to "SEAT_VEN",
        QUERY_CERTIFICATE to "QUERY_CERTIFICATE",
        WRITE_CONFIG_CODE to "WRITE_CONFIG_CODE",
        DIGITAL_KEY_CONTROL to "DIGITAL_KEY_CONTROL",
        ACTION_TEST to "ACTION_TEST",
        AIR_CONDITION_CTRL to "AIR_CONDITION_CTRL",
        AUTOAIR_TIMELY to "AUTOAIR_TIMELY",
        TRUNK_DOOR to "TRUNK_DOOR",
        GET_ECU_CONF_CODE to "GET_ECU_CONF_CODE",
        VEHICLE_EXAM to "VEHICLE_EXAM",
        LOCK to "LOCK",
        LIGHT_SHOW_MODEL to "LIGHT_SHOW_MODEL",
        WINDOW_ALL to "WINDOW_ALL",
        ELE_FENCE to "ELE_FENCE",
        DOWNLOAD_CERTIFICATE to "DOWNLOAD_CERTIFICATE",
        QUERY_ECUINFO to "QUERY_ECUINFO",
        AIR_CONDITION to "AIR_CONDITION",
        KEY_UPDATE to "KEY_UPDATE",
        SEAT to "SEAT",
        STERILIZE to "STERILIZE",
        WRITE_VIN to "WRITE_VIN",
        GET_CAN_STREAM to "GET_CAN_STREAM",
        ENGINE to "ENGINE",
        ROOF_WINDOW to "ROOF_WINDOW",
        RESET_ECU to "RESET_ECU",
        GREENCABIN_AUTO to "GREENCABIN_AUTO",
        AIR_PURIFIER to "AIR_PURIFIER",
        AUTO_AIR_CONDITION_CTRL to "AUTO_AIR_CONDITION_CTRL",
        RAPID_COOLING to "RAPID_COOLING",
        MANUALAIR_TIMELY to "MANUALAIR_TIMELY",
        REMOTE_DIAG to "REMOTE_DIAG",
        RAPID_HEATING to "RAPID_HEATING",
        CHARGE_RESERVE to "CHARGE_RESERVE",
        POWER_PHEV to "POWER_PHEV",
        AIR_CONDITION_CTRL_MODE to "AIR_CONDITION_CTRL_MODE",
        STEERING_WHEEL_HEATING to "STEERING_WHEEL_HEATING",
        HU_AWAKEN to "HU_AWAKEN",
        GREENCABIN_MANUAL to "GREENCABIN_MANUAL",
    )

    fun nameOf(opcode: Int): String = NAMES[opcode] ?: "UNKNOWN_0x%02X".format(opcode)

    fun buildFrame(opcode: Int, body: ByteArray = ByteArray(0)): ByteArray {
        require(opcode in 0..0xFF) { "opcode out of range" }
        require(body.size <= FRAME_SIZE - 1) { "vctrl body too large: ${body.size}" }
        val out = ByteArray(FRAME_SIZE)
        out[0] = opcode.toByte()
        System.arraycopy(body, 0, out, 1, body.size)
        return out
    }

    /** Simple cmds where APP only sets `frame[9]` from a protobuf bool/enum. */
    fun buildSimple(opcode: Int, param0: Int): ByteArray {
        require(param0 in 0..0xFF)
        return buildFrame(opcode).also { it[OFF_PARAM0] = param0.toByte() }
    }

    fun buildFrontLight(on: Boolean): ByteArray =
        buildSimple(FRONT_LIGHT, if (on) 1 else 0)

    fun buildPowerOnOff(on: Boolean): ByteArray =
        buildSimple(POWER_ON_OFF, if (on) 1 else 0)

    fun buildWindows(param0: Int): ByteArray = buildSimple(WINDOWS, param0)

    fun buildWindowAll(param0: Int): ByteArray = buildSimple(WINDOW_ALL, param0)

    fun buildFindVehicle(param0: Int, param1: Int = 0, param2: Int = 0): ByteArray =
        buildFrame(FIND_VEHICLE).also {
            it[OFF_PARAM0] = param0.toByte()
            it[OFF_PARAM0 + 1] = param1.toByte()
            it[OFF_PARAM0 + 2] = param2.toByte()
        }

    /** Experimental: LOCK hits DEFAULT in APP jump table (MQTT path does not send). */
    fun buildLock(param0: Int): ByteArray = buildSimple(LOCK, param0)

    /** Experimental: ENGINE hits DEFAULT + fails IsValid on this build. */
    fun buildEngine(param0: Int): ByteArray = buildSimple(ENGINE, param0)

    /**
     * Historical aliases for [BackgroundService] ACTION_CLOSE / ACTION_OPEN.
     * These are **FRONT_LIGHT** off/on, not door LOCK.
     */
    fun buildLockClose(): ByteArray = buildFrontLight(on = false)

    fun buildLockOpen(): ByteArray = buildFrontLight(on = true)
}

