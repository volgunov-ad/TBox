package vad.dashing.tbox.utils

import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.Wheels
import vad.dashing.tbox.toHexString

object CanFramesProcess {

    private val GEAR_BOX_7_DRIVE_MODES = setOf(0x1B, 0x2B, 0x3B, 0x4B, 0x5B, 0x6B, 0x7B)
        .map { it.toByte() }
        .toSet()

    private val GEAR_BOX_7_PREPARED_DRIVE_MODES = setOf(0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70)
        .map { it.toByte() }
        .toSet()

    private val fuelLevelPercentageBuffer = FuelLevelBuffer(10)

    private var carType: String = "1.5_6MT"

    fun process(data: ByteArray) {
        TboxRepository.updateCanFrameTime()
        val rawValue = data.copyOfRange(4, data.size)
        //CanDataRepository.addCanFrame(toHexString(rawValue))
        for (i in 0 until rawValue.size step 17) {
            try {
                val rawFrame = rawValue.copyOfRange(i, i + 17)
                val timeStamp = rawFrame.copyOfRange(0, 4)
                val canID = rawFrame.copyOfRange(4, 8)
                val dlc = rawFrame[8]
                val singleData = rawFrame.copyOfRange(9, 17)

                if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
                    continue
                }

                CanDataRepository.addCanFrameStructured(
                    toHexString(canID),
                    singleData
                )

                if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0xC4.toByte()))) {
                    val angle =
                        (singleData.copyOfRange(0, 2).toFloat("UINT16_BE") - 32768f) * 6f / 100f
                    val speed = singleData[2].toInt()
                    CanDataRepository.updateSteerAngle(angle)
                    CanDataRepository.updateSteerSpeed(speed)
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0xFA.toByte()))) {
                    val rpm = singleData.copyOfRange(0, 2).toFloat("UINT16_BE") / 4f
                    CanDataRepository.updateEngineRPM(rpm)
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x02, 0x87.toByte()))) {
                    val distanceToNextMaintenance = singleData.copyOfRange(4, 6).toUInt16BigEndian()
                    CanDataRepository.updateDistanceToNextMaintenance(distanceToNextMaintenance)
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x02, 0xE9.toByte()))) {
                    val breakingForce = singleData[2].toUInt()
                    CanDataRepository.updateBreakingForce(breakingForce)
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x03, 0x00))) {
                    if (carType != "1.6") {
                        carType = "1.5_6DCT"
                    }
                    val gearBoxMode: String
                    val gearBoxCurrentGear: Int
                    val gearBoxPreparedGear: Int

                    if (singleData[0] in GEAR_BOX_7_DRIVE_MODES) {
                        gearBoxMode = "D"
                        gearBoxCurrentGear = singleData[0].getLeftNibble()
                    } else if (singleData[0] == 0xBE.toByte()) {
                        gearBoxMode = "P"
                        gearBoxCurrentGear = 0
                    } else if (singleData[0] == 0xAC.toByte()) {
                        gearBoxMode = "N"
                        gearBoxCurrentGear = 0
                    } else if (singleData[0] == 0xAD.toByte()) {
                        gearBoxMode = "R"
                        gearBoxCurrentGear = 0
                    } else {
                        gearBoxMode = "N/A"
                        gearBoxCurrentGear = 0
                    }

                    val gearBoxChangeGear = singleData[1].extractBitsToUInt(6, 1) == 1u

                    val gearBoxDriveModeByte = singleData[1].getRightNibble()
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

                    val gearBoxOilTemperature = singleData[2].toUByte().toInt() - 40

                    if (singleData[3] in GEAR_BOX_7_PREPARED_DRIVE_MODES) {
                        gearBoxPreparedGear = (singleData[3].toInt() and 0xF0) ushr 4
                    } else {
                        gearBoxPreparedGear = 0
                    }

                    val gearBoxWork = if (singleData[5] == 0x00.toByte()) {
                        "0"
                    } else if (singleData[5] == 0xA1.toByte()) {
                        "1"
                    } else if (singleData[5] == 0x5E.toByte()) {
                        "2"
                    } else if (singleData[5] == 0x42.toByte()) {
                        "3"
                    } else if (singleData[5] == 0x30.toByte()) {
                        "4"
                    } else if (singleData[5] == 0x26.toByte()) {
                        "5"
                    } else if (singleData[5] == 0x1F.toByte()) {
                        "6"
                    } else if (singleData[5] == 0x1B.toByte()) {
                        "7"
                    } else {
                        toHexString(byteArrayOf(singleData[5]))
                    }

                    CanDataRepository.updateGearBoxMode(gearBoxMode)
                    CanDataRepository.updateGearBoxCurrentGear(gearBoxCurrentGear)
                    CanDataRepository.updateGearBoxPreparedGear(gearBoxPreparedGear)
                    CanDataRepository.updateGearBoxChangeGear(gearBoxChangeGear)
                    CanDataRepository.updateGearBoxOilTemperature(gearBoxOilTemperature)
                    CanDataRepository.updateGearBoxDriveMode(gearBoxDriveMode)
                    CanDataRepository.updateGearBoxWork(gearBoxWork)
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x03, 0x05))) {
                    carType = "1.6"
                    val cruiseSpeed = singleData[0].toUInt()
                    CanDataRepository.updateCruiseSetSpeed(cruiseSpeed)
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x03, 0x10))) {
                    val speed1 = singleData.copyOfRange(0, 2).toFloat("UINT16_BE") * 0.065f
                    val speed2 = singleData.copyOfRange(2, 4).toFloat("UINT16_BE") * 0.065f
                    val speed3 = singleData.copyOfRange(4, 6).toFloat("UINT16_BE") * 0.065f
                    val speed4 = singleData.copyOfRange(6, 8).toFloat("UINT16_BE") * 0.065f
                    CanDataRepository.updateWheelsSpeed(Wheels(speed1, speed2, speed3, speed4))
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x04, 0x30))) {
                    val speed = singleData.copyOfRange(0, 2).toFloat("UINT16_BE") / 16f
                    val voltage = singleData[2].toUInt().toFloat() / 10f
                    val fuelLevelPercentage = singleData[4].toUInt()
                    val odometer = singleData.copyOfRange(5, 8).toUInt20FromNibbleBigEndian()
                    CanDataRepository.updateCarSpeed(speed)
                    CanDataRepository.updateOdometer(odometer)
                    CanDataRepository.updateVoltage(voltage)
                    CanDataRepository.updateFuelLevelPercentage(fuelLevelPercentage)
                    if (fuelLevelPercentageBuffer.addValue(fuelLevelPercentage)) {
                        CanDataRepository.updateFuelLevelPercentageFiltered(fuelLevelPercentage)
                    }
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x05, 0x01))) {
                    val engineTemperature = singleData[2].toUInt().toFloat() * 0.75f - 48f
                    CanDataRepository.updateEngineTemperature(engineTemperature)
                    if (carType == "1.5_6DCT") {
                        if (singleData[1].toInt() == 1) {
                            //val cruiseSpeed = (singleData[4].toUInt().toDouble() * 1.60934400579).toUInt()
                            val cruiseSpeed = singleData[4].toUInt()
                            CanDataRepository.updateCruiseSetSpeed(cruiseSpeed)
                        } else if (singleData[1].toInt() == 0) {
                            CanDataRepository.updateCruiseSetSpeed(0u)
                        }
                    } else if (carType == "1.5_6MT") {
                        if (singleData[1].toInt() == 1) {
                            val cruiseSpeed =
                                //singleData[4].toUInt() - 3u + if (singleData[5].toUInt() >= 192u) 1u else 0u
                                singleData[4].toUInt()
                            CanDataRepository.updateCruiseSetSpeed(cruiseSpeed)
                        } else if (singleData[1].toInt() == 0) {
                            CanDataRepository.updateCruiseSetSpeed(0u)
                        }
                    }
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x05, 0x02))) {
                    val speed = if (singleData[2] != 0x00.toByte()) {
                        singleData.copyOfRange(1, 3).toFloat("UINT16_BE") / 16f
                    } else {
                        0f
                    }
                    CanDataRepository.updateCarSpeedAccurate(speed)
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x05, 0x1B))) {
                    val temperature = if (singleData[3] != 0xFF.toByte()) {
                        singleData[3].toUInt().toFloat() * 0.75f - 45f
                    } else {
                        null
                    }

                    val wheelsTemperature = CanDataRepository.wheelsTemperature.value
                    val wheelIndex = singleData[2].toInt()

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

                    val pressure1 = if (singleData[4] != 0xFF.toByte()) {
                        singleData[4].toUInt().toFloat() / 36f
                    } else {
                        null
                    }
                    val pressure2 = if (singleData[5] != 0xFF.toByte()) {
                        singleData[5].toUInt().toFloat() / 36f
                    } else {
                        null
                    }
                    val pressure3 = if (singleData[6] != 0xFF.toByte()) {
                        singleData[6].toUInt().toFloat() / 36f
                    } else {
                        null
                    }
                    val pressure4 = if (singleData[7] != 0xFF.toByte()) {
                        singleData[7].toUInt().toFloat() / 36f
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
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x05, 0x2F))) {
                    val setTemperature = singleData[5].toUInt().toFloat() / 4f
                    if (setTemperature != 0f) {
                        val setTemperature1 = singleData[5].toUInt().toFloat() / 4f
                        CanDataRepository.updateClimateSetTemperature1(setTemperature1)
                    }
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x05, 0x30))) {
                    val distanceToFuelEmpty = singleData.copyOfRange(2, 4).toUInt16BigEndian()
                    CanDataRepository.updateDistanceToFuelEmpty(distanceToFuelEmpty)
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x05, 0x35))) {
                    val insideTemperature = singleData[5].toUInt().toFloat() * 0.5f - 40f
                    val outsideTemperature = singleData[6].toUInt().toFloat() * 0.5f - 40f
                    if (outsideTemperature >= -40f && outsideTemperature <= 60f) {
                        CanDataRepository.updateOutsideTemperature(outsideTemperature)
                    } else {
                        CanDataRepository.updateOutsideTemperature(null)
                    }
                    if (insideTemperature >= -40f && insideTemperature <= 60f) {
                        CanDataRepository.updateInsideTemperature(insideTemperature)
                    } else {
                        CanDataRepository.updateInsideTemperature(null)
                    }
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x05, 0xC4.toByte()))) {
                    val frontRightSeatMode = singleData[4].extractBitsToUInt(3, 3)
                    val frontLeftSeatMode = singleData[4].extractBitsToUInt(0, 3)
                    CanDataRepository.updateFrontLeftSeatMode(frontLeftSeatMode)
                    CanDataRepository.updateFrontRightSeatMode(frontRightSeatMode)
                } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x05, 0xFF.toByte()))) {
                    val isWindowsBlocked = singleData[4].extractBitsToUInt(0, 1) == 1u
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
            "Get CAN Frame"
        )
    }

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
            else -> throw IllegalArgumentException("Unknown format: $format. Supported: UINT16_BE, UINT16_LE, UINT24_BE, UINT24_LE")
        }
    }
}