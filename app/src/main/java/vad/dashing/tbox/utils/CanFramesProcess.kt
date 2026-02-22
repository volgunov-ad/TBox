package vad.dashing.tbox.utils

import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.Wheels

object CanFramesProcess {

    private val GEAR_BOX_7_DRIVE_MODES = setOf(0x1B, 0x2B, 0x3B, 0x4B, 0x5B, 0x6B, 0x7B)
        .map { it.toByte() }
        .toSet()

    private val GEAR_BOX_7_PREPARED_DRIVE_MODES = setOf(0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70)
        .map { it.toByte() }
        .toSet()

    private val fuelLevelPercentageBuffer = FuelLevelBuffer(10)
    private val canIdStringCache = mutableMapOf<Int, String>()

    private var carType: String = "1.5_6MT"

    private const val FRAME_SIZE = 17
    private const val CAN_ID_OFFSET = 4
    private const val PAYLOAD_OFFSET = 9

    private const val CAN_ID_STEER = 0x000000C4
    private const val CAN_ID_ENGINE_PARAMS = 0x000000FA
    private const val CAN_ID_PARAM_3 = 0x00000200
    private const val CAN_ID_PARAM_4 = 0x00000278
    private const val CAN_ID_DISTANCE_TO_MAINTENANCE = 0x00000287
    private const val CAN_ID_BREAKING_FORCE = 0x000002E9
    private const val CAN_ID_GEARBOX = 0x00000300
    private const val CAN_ID_CRUISE = 0x00000305
    private const val CAN_ID_WHEEL_SPEED = 0x00000310
    private const val CAN_ID_SPEED_VOLTAGE_FUEL = 0x00000430
    private const val CAN_ID_ENGINE_TEMP = 0x00000501
    private const val CAN_ID_SPEED_ACCURATE = 0x00000502
    private const val CAN_ID_WHEELS_TPMS = 0x0000051B
    private const val CAN_ID_CLIMATE_SET = 0x0000052F
    private const val CAN_ID_DISTANCE_TO_FUEL_EMPTY = 0x00000530
    private const val CAN_ID_IN_OUT_TEMP = 0x00000535
    private const val CAN_ID_AIR_QUALITY = 0x0000053A
    private const val CAN_ID_SEAT_MODES = 0x000005C4
    private const val CAN_ID_WINDOWS_BLOCKED = 0x000005FF

    fun process(data: ByteArray, maxFrames: Int) {
        TboxRepository.updateCanFrameTime()
        val rawValue = data.copyOfRange(4, data.size)
        for (i in 0 until rawValue.size step FRAME_SIZE) {
            try {
                if (i + FRAME_SIZE > rawValue.size) {
                    break
                }

                val canId = readCanId(rawValue, i + CAN_ID_OFFSET)
                if (canId == 0) {
                    continue
                }

                val payloadStart = i + PAYLOAD_OFFSET
                val singleData = rawValue.copyOfRange(payloadStart, payloadStart + 8)
                CanDataRepository.addCanFrameStructured(
                    canIdToHexString(canId),
                    singleData,
                    maxFrames
                )

                val b0 = rawValue[payloadStart]
                val b1 = rawValue[payloadStart + 1]
                val b2 = rawValue[payloadStart + 2]
                val b3 = rawValue[payloadStart + 3]
                val b4 = rawValue[payloadStart + 4]
                val b5 = rawValue[payloadStart + 5]
                val b6 = rawValue[payloadStart + 6]
                val b7 = rawValue[payloadStart + 7]

                if (canId == CAN_ID_STEER) {
                    val angleRaw = readUInt16BigEndian(rawValue, payloadStart).toFloat()
                    val angle = if (angleRaw == 65535f) {
                        null
                    } else {
                        (angleRaw - 32767f) / 16f
                    }
                    val speed = b2.toInt()
                    CanDataRepository.updateSteerAngle(angle)
                    CanDataRepository.updateSteerSpeed(speed)
                } else if (canId == CAN_ID_ENGINE_PARAMS) {
                    val rpm = readUInt16BigEndian(rawValue, payloadStart).toFloat() / 4f
                    val param1 = unsignedByte(b3).toFloat() / 100f
                    val param2 = readUInt16BigEndian(rawValue, payloadStart + 4).toFloat()
                    CanDataRepository.updateEngineRPM(rpm)
                    CanDataRepository.updateParam1(param1)
                    CanDataRepository.updateParam2(param2)
                } else if (canId == CAN_ID_PARAM_3) {
                    val param3 = readUInt16BigEndian(rawValue, payloadStart + 4).toFloat()
                    CanDataRepository.updateParam3(param3)
                } else if (canId == CAN_ID_PARAM_4) {
                    val param4 = unsignedByte(b5).toFloat()
                    CanDataRepository.updateParam4(param4)
                } else if (canId == CAN_ID_DISTANCE_TO_MAINTENANCE) {
                    val distanceToNextMaintenance =
                        readUInt16BigEndian(rawValue, payloadStart + 4).toUInt()
                    CanDataRepository.updateDistanceToNextMaintenance(distanceToNextMaintenance)
                } else if (canId == CAN_ID_BREAKING_FORCE) {
                    val breakingForce = unsignedByte(b2).toUInt()
                    CanDataRepository.updateBreakingForce(breakingForce)
                } else if (canId == CAN_ID_GEARBOX) {
                    if (carType != "1.6") {
                        carType = "1.5_6DCT"
                    }
                    val gearBoxMode: String
                    val gearBoxCurrentGear: Int
                    val gearBoxPreparedGear: Int

                    if (b0 in GEAR_BOX_7_DRIVE_MODES) {
                        gearBoxMode = "D"
                        gearBoxCurrentGear = b0.getLeftNibble()
                    } else if (b0 == 0xBE.toByte()) {
                        gearBoxMode = "P"
                        gearBoxCurrentGear = 0
                    } else if (b0 == 0xAC.toByte()) {
                        gearBoxMode = "N"
                        gearBoxCurrentGear = 0
                    } else if (b0 == 0xAD.toByte()) {
                        gearBoxMode = "R"
                        gearBoxCurrentGear = 0
                    } else {
                        gearBoxMode = "N/A"
                        gearBoxCurrentGear = 0
                    }

                    val gearBoxChangeGear = b1.extractBitsToUInt(6, 1) == 1u

                    val gearBoxDriveModeByte = b1.getRightNibble()
                    val gearBoxDriveMode = when (gearBoxDriveModeByte) {
                        0 -> {
                            "ECO"
                        }

                        1 -> {
                            "NOR"
                        }

                        2 -> {
                            "SPT"
                        }

                        else -> {
                            "N/A"
                        }
                    }

                    val gearBoxOilTemperature = unsignedByte(b2) - 40

                    if (b3 in GEAR_BOX_7_PREPARED_DRIVE_MODES) {
                        gearBoxPreparedGear = (b3.toInt() and 0xF0) ushr 4
                    } else {
                        gearBoxPreparedGear = 0
                    }

                    val gearBoxWork = if (b5 == 0x00.toByte()) {
                        "0"
                    } else if (b5 == 0xA1.toByte()) {
                        "1"
                    } else if (b5 == 0x5E.toByte()) {
                        "2"
                    } else if (b5 == 0x42.toByte()) {
                        "3"
                    } else if (b5 == 0x30.toByte()) {
                        "4"
                    } else if (b5 == 0x26.toByte()) {
                        "5"
                    } else if (b5 == 0x1F.toByte()) {
                        "6"
                    } else if (b5 == 0x1B.toByte()) {
                        "7"
                    } else {
                        hexByte(b5)
                    }

                    CanDataRepository.updateGearBoxMode(gearBoxMode)
                    CanDataRepository.updateGearBoxCurrentGear(gearBoxCurrentGear)
                    CanDataRepository.updateGearBoxPreparedGear(gearBoxPreparedGear)
                    CanDataRepository.updateGearBoxChangeGear(gearBoxChangeGear)
                    CanDataRepository.updateGearBoxOilTemperature(gearBoxOilTemperature)
                    CanDataRepository.updateGearBoxDriveMode(gearBoxDriveMode)
                    CanDataRepository.updateGearBoxWork(gearBoxWork)
                } else if (canId == CAN_ID_CRUISE) {
                    carType = "1.6"
                    val cruiseSpeed = unsignedByte(b0).toUInt()
                    CanDataRepository.updateCruiseSetSpeed(cruiseSpeed)
                } else if (canId == CAN_ID_WHEEL_SPEED) {
                    val speed1 = readUInt16BigEndian(rawValue, payloadStart).toFloat() / 16f
                    val speed2 = readUInt16BigEndian(rawValue, payloadStart + 2).toFloat() / 16f
                    val speed3 = readUInt16BigEndian(rawValue, payloadStart + 4).toFloat() / 16f
                    val speed4 = readUInt16BigEndian(rawValue, payloadStart + 6).toFloat() / 16f
                    CanDataRepository.updateWheelsSpeed(Wheels(speed1, speed2, speed3, speed4))
                } else if (canId == CAN_ID_SPEED_VOLTAGE_FUEL) {
                    val speed = readUInt16BigEndian(rawValue, payloadStart).toFloat() / 16f
                    val voltage = unsignedByte(b2).toFloat() / 10f
                    val fuelLevelPercentage = unsignedByte(b4).toUInt()
                    val odometer = readUInt20FromNibbleBigEndian(rawValue, payloadStart + 5)
                    CanDataRepository.updateCarSpeed(speed)
                    CanDataRepository.updateOdometer(odometer)
                    CanDataRepository.updateVoltage(voltage)
                    CanDataRepository.updateFuelLevelPercentage(fuelLevelPercentage)
                    if (fuelLevelPercentageBuffer.addValue(fuelLevelPercentage)) {
                        CanDataRepository.updateFuelLevelPercentageFiltered(fuelLevelPercentage)
                    }
                } else if (canId == CAN_ID_ENGINE_TEMP) {
                    val engineTemperature = unsignedByte(b2).toFloat() * 0.75f - 48f
                    CanDataRepository.updateEngineTemperature(engineTemperature)
                    if (carType == "1.5_6DCT") {
                        if (b1.toInt() == 1) {
                            val cruiseSpeed = unsignedByte(b4).toUInt()
                            CanDataRepository.updateCruiseSetSpeed(cruiseSpeed)
                        } else if (b1.toInt() == 0) {
                            CanDataRepository.updateCruiseSetSpeed(0u)
                        }
                    } else if (carType == "1.5_6MT") {
                        if (b1.toInt() == 1) {
                            val cruiseSpeed = unsignedByte(b4).toUInt()
                            CanDataRepository.updateCruiseSetSpeed(cruiseSpeed)
                        } else if (b1.toInt() == 0) {
                            CanDataRepository.updateCruiseSetSpeed(0u)
                        }
                    }
                } else if (canId == CAN_ID_SPEED_ACCURATE) {
                    val speed = if (b2 != 0x00.toByte()) {
                        readUInt16BigEndian(rawValue, payloadStart + 1).toFloat() / 16f
                    } else {
                        0f
                    }
                    CanDataRepository.updateCarSpeedAccurate(speed)
                } else if (canId == CAN_ID_WHEELS_TPMS) {
                    val temperature = if (b3 != 0xFF.toByte()) {
                        unsignedByte(b3).toFloat() - 60f
                    } else {
                        null
                    }

                    val wheelsTemperature = CanDataRepository.wheelsTemperature.value
                    val wheelIndex = b2.toInt()

                    val newWheelsTemperature = when (wheelIndex) {
                        0 -> wheelsTemperature.copy(wheel1 = temperature)
                        1 -> wheelsTemperature.copy(wheel2 = temperature)
                        2 -> wheelsTemperature.copy(wheel3 = temperature)
                        3 -> wheelsTemperature.copy(wheel4 = temperature)
                        else -> null
                    }

                    newWheelsTemperature?.let {
                        CanDataRepository.updateWheelsTemperature(it)
                    }

                    val pressure1 = if (b4 != 0xFF.toByte()) {
                        unsignedByte(b4).toFloat() / 36f
                    } else {
                        null
                    }
                    val pressure2 = if (b5 != 0xFF.toByte()) {
                        unsignedByte(b5).toFloat() / 36f
                    } else {
                        null
                    }
                    val pressure3 = if (b6 != 0xFF.toByte()) {
                        unsignedByte(b6).toFloat() / 36f
                    } else {
                        null
                    }
                    val pressure4 = if (b7 != 0xFF.toByte()) {
                        unsignedByte(b7).toFloat() / 36f
                    } else {
                        null
                    }
                    CanDataRepository.updateWheelsPressure(
                        Wheels(
                            pressure1,
                            pressure2,
                            pressure3,
                            pressure4
                        )
                    )
                } else if (canId == CAN_ID_CLIMATE_SET) {
                    val setTemperature = unsignedByte(b5).toFloat() / 4f
                    if (setTemperature != 0f) {
                        val setTemperature1 = unsignedByte(b5).toFloat() / 4f
                        CanDataRepository.updateClimateSetTemperature1(setTemperature1)
                    }
                } else if (canId == CAN_ID_DISTANCE_TO_FUEL_EMPTY) {
                    val distanceToFuelEmpty =
                        readUInt16BigEndian(rawValue, payloadStart + 2).toUInt()
                    CanDataRepository.updateDistanceToFuelEmpty(distanceToFuelEmpty)
                } else if (canId == CAN_ID_IN_OUT_TEMP) {
                    val insideTemperature = unsignedByte(b5).toFloat() * 0.5f - 40f
                    val outsideTemperature = unsignedByte(b6).toFloat() * 0.5f - 40f
                    if (outsideTemperature >= -40f && outsideTemperature < 87f) {
                        CanDataRepository.updateOutsideTemperature(outsideTemperature)
                    } else {
                        CanDataRepository.updateOutsideTemperature(null)
                    }
                    if (insideTemperature >= -40f && insideTemperature < 87f) {
                        CanDataRepository.updateInsideTemperature(insideTemperature)
                    } else {
                        CanDataRepository.updateInsideTemperature(null)
                    }
                } else if (canId == CAN_ID_AIR_QUALITY) {
                    val insideAirQuality = readUInt16BigEndian(rawValue, payloadStart).toUInt()
                    val outsideAirQuality = readUInt16BigEndian(rawValue, payloadStart + 2).toUInt()
                    if (insideAirQuality > 0u && insideAirQuality < 65535u) {
                        CanDataRepository.updateInsideAirQuality(insideAirQuality)
                    } else {
                        CanDataRepository.updateInsideAirQuality(null)
                    }
                    if (outsideAirQuality > 0u && outsideAirQuality < 65535u) {
                        CanDataRepository.updateOutsideAirQuality(outsideAirQuality)
                    } else {
                        CanDataRepository.updateOutsideAirQuality(null)
                    }
                } else if (canId == CAN_ID_SEAT_MODES) {
                    val frontRightSeatMode = b4.extractBitsToUInt(3, 3)
                    val frontLeftSeatMode = b4.extractBitsToUInt(0, 3)
                    CanDataRepository.updateFrontLeftSeatMode(frontLeftSeatMode)
                    CanDataRepository.updateFrontRightSeatMode(frontRightSeatMode)
                } else if (canId == CAN_ID_WINDOWS_BLOCKED) {
                    val isWindowsBlocked = b4.extractBitsToUInt(0, 1) == 1u
                    CanDataRepository.updateIsWindowsBlocked(isWindowsBlocked)
                }
            } catch (e: Exception) {
                TboxRepository.addLog(
                    "ERROR", "CRT response",
                    "Error get CAN Frame $i: $e"
                )
            }
        }
        TboxRepository.addLog(
            "DEBUG", "CRT response",
            "Get CAN Frame ${rawValue.size} bytes"
        )
    }

    private fun readCanId(data: ByteArray, offset: Int): Int {
        return (unsignedByte(data[offset]) shl 24) or
            (unsignedByte(data[offset + 1]) shl 16) or
            (unsignedByte(data[offset + 2]) shl 8) or
            unsignedByte(data[offset + 3])
    }

    private fun readUInt16BigEndian(data: ByteArray, offset: Int): Int {
        return (unsignedByte(data[offset]) shl 8) or unsignedByte(data[offset + 1])
    }

    private fun readUInt20FromNibbleBigEndian(data: ByteArray, offset: Int): UInt {
        return (((unsignedByte(data[offset]) and 0x0F) shl 16) or
            (unsignedByte(data[offset + 1]) shl 8) or
            unsignedByte(data[offset + 2])).toUInt()
    }

    private fun canIdToHexString(canId: Int): String {
        return canIdStringCache.getOrPut(canId) {
            val b1 = (canId ushr 24) and 0xFF
            val b2 = (canId ushr 16) and 0xFF
            val b3 = (canId ushr 8) and 0xFF
            val b4 = canId and 0xFF
            "%02X %02X %02X %02X".format(b1, b2, b3, b4)
        }
    }

    private fun unsignedByte(value: Byte): Int = value.toInt() and 0xFF

    private fun hexByte(value: Byte): String = "%02X".format(unsignedByte(value))

    fun Byte.toUInt(): UInt {
        return this.toUByte().toUInt()
    }

    fun Byte.extractBitsToUInt(startPos: Int, length: Int): UInt {
        require(startPos in 0..7) { "startPos must be between 0 and 7" }
        require(length in 1..8) { "length must be between 1 and 8" }
        require(startPos + length <= 8) { "startPos + length must not exceed 8" }

        val value = this.toUInt() and 0xFFu
        // Создаем маску для нужного количества битов
        val bitMask = (1u shl length) - 1u
        // Сдвигаем маску в нужную позицию и применяем
        return (value shr startPos) and bitMask
    }

    fun Byte.getLeftNibble(): Int = (this.toInt() shr 4) and 0x0F
    fun Byte.getRightNibble(): Int = this.toInt() and 0x0F

    fun ByteArray.toUInt20FromNibbleBigEndian(): UInt {
        require(this.size >= 3) { "ByteArray must have at least 3 bytes" }
        val byte1 = (this[0].toUInt() and 0x0FU) shl 16
        val byte2 = (this[1].toUInt() and 0xFFU) shl 8
        val byte3 = this[2].toUInt() and 0xFFU
        return byte1 or byte2 or byte3
    }

    fun ByteArray.toUInt16BigEndian(): UInt {
        require(this.size >= 2) { "ByteArray must have at least 2 bytes" }
        val byte1 = (this[0].toUInt() and 0xFFU) shl 8
        val byte2 = this[1].toUInt() and 0xFFU
        return byte1 or byte2
    }

    fun ByteArray.toDouble(format: String = "UINT16_BE"): Double {
        return when (format) {
            "UINT16_BE" -> {
                require(this.size >= 2) { "ByteArray must have at least 2 bytes for UINT16_BE" }
                val intValue = ((this[0].toInt() and 0xFF) shl 8) or
                        (this[1].toInt() and 0xFF)
                intValue.toDouble()
            }
            "UINT16_LE" -> {
                require(this.size >= 2) { "ByteArray must have at least 2 bytes for UINT16_LE" }
                val intValue = ((this[1].toInt() and 0xFF) shl 8) or
                        (this[0].toInt() and 0xFF)
                intValue.toDouble()
            }
            "UINT24_BE" -> {
                require(this.size >= 3) { "ByteArray must have at least 3 bytes for UINT24_BE" }
                val intValue = ((this[0].toInt() and 0xFF) shl 16) or
                        ((this[1].toInt() and 0xFF) shl 8) or
                        (this[2].toInt() and 0xFF)
                intValue.toDouble()
            }
            "UINT24_LE" -> {
                require(this.size >= 3) { "ByteArray must have at least 3 bytes for UINT24_LE" }
                val intValue = ((this[2].toInt() and 0xFF) shl 16) or
                        ((this[1].toInt() and 0xFF) shl 8) or
                        (this[0].toInt() and 0xFF)
                intValue.toDouble()
            }
            else -> throw IllegalArgumentException("Unknown format: $format. Supported: UINT16_BE, UINT16_LE, UINT24_BE, UINT24_LE")
        }
    }

    fun ByteArray.toFloat(format: String = "UINT16_BE"): Float {
        return when (format) {
            "UINT16_BE" -> {
                require(this.size >= 2) { "ByteArray must have at least 2 bytes for UINT16_BE" }
                val intValue = ((this[0].toInt() and 0xFF) shl 8) or
                        (this[1].toInt() and 0xFF)
                intValue.toFloat()
            }
            "UINT16_LE" -> {
                require(this.size >= 2) { "ByteArray must have at least 2 bytes for UINT16_LE" }
                val intValue = ((this[1].toInt() and 0xFF) shl 8) or
                        (this[0].toInt() and 0xFF)
                intValue.toFloat()
            }
            "UINT24_BE" -> {
                require(this.size >= 3) { "ByteArray must have at least 3 bytes for UINT24_BE" }
                val intValue = ((this[0].toInt() and 0xFF) shl 16) or
                        ((this[1].toInt() and 0xFF) shl 8) or
                        (this[2].toInt() and 0xFF)
                intValue.toFloat()
            }
            "UINT24_LE" -> {
                require(this.size >= 3) { "ByteArray must have at least 3 bytes for UINT24_LE" }
                val intValue = ((this[2].toInt() and 0xFF) shl 16) or
                        ((this[1].toInt() and 0xFF) shl 8) or
                        (this[0].toInt() and 0xFF)
                intValue.toFloat()
            }
            "UINT32_BE" -> {
                require(this.size >= 4) { "ByteArray must have at least 4 bytes for UINT32_BE" }
                val longValue = ((this[0].toLong() and 0xFF) shl 24) or
                        ((this[1].toLong() and 0xFF) shl 16) or
                        ((this[2].toLong() and 0xFF) shl 8) or
                        (this[3].toLong() and 0xFF)
                longValue.toFloat()
            }
            "UINT32_LE" -> {
                require(this.size >= 4) { "ByteArray must have at least 4 bytes for UINT32_LE" }
                val longValue = ((this[3].toLong() and 0xFF) shl 24) or
                        ((this[2].toLong() and 0xFF) shl 16) or
                        ((this[1].toLong() and 0xFF) shl 8) or
                        (this[0].toLong() and 0xFF)
                longValue.toFloat()
            }
            else -> throw IllegalArgumentException("Unknown format: $format. Supported: UINT16_BE, UINT16_LE, UINT24_BE, UINT24_LE, UINT32_BE, UINT32_LE")
        }
    }
}