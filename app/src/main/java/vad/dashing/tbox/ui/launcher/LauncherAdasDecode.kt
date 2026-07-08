package vad.dashing.tbox.ui.launcher

/**
 * Decoders for [com.mengbo.mbCan.entity.MBCanVehicleFrmDectInfo] and LKA status,
 * ported from stock `com.mengbo.adascard` / `SignalManager` thresholds.
 */
internal fun byteToUnsigned(raw: Byte): Int = raw.toInt() and 0xFF

/** Longitudinal distance in metres (byte 0..255, negative bytes shown as 256+n in stock ACC UI). */
internal fun decodeFrmDistanceMetres(raw: Byte): Int? {
    val u = byteToUnsigned(raw)
    if (u == 0) return null
    return if (raw < 0) u else u
}

enum class LauncherAdasAccMode {
    Off,
    Standby,
    ActiveDark,
    ActiveBlue,
    Override,
    Unknown,
}

enum class LauncherAdasFrontObjectType(val code: Int) {
    None(0),
    Car(1),
    Truck(2),
    Motorcycle(3),
    Pedestrian(4),
    Bicycle(5),
    Bus(6),
    Unknown(7),
    ;

    companion object {
        fun fromCode(code: Int): LauncherAdasFrontObjectType =
            entries.firstOrNull { it.code == code } ?: Unknown
    }
}

enum class LauncherAdasLaneVisualization(val code: Int) {
    Hidden(0),
    Tracking(1),
    Intervention(2),
    Warning(3),
    ;

    companion object {
        fun fromCode(code: Int): LauncherAdasLaneVisualization =
            entries.firstOrNull { it.code == code } ?: Hidden
    }
}

data class LauncherAdasFrontObject(
    val valid: Boolean,
    val type: LauncherAdasFrontObjectType,
    /** Smoothed forward position used by stock ADAS card ([MBCanVehicleFrmDectInfo.getFRM_3_Obiect_Dx]). */
    val objectDxM: Int?,
    /** Radar target distance ([MBCanVehicleFrmDectInfo.getFRM_3_DxTarObj]). */
    val targetDxM: Int?,
) {
    val displayDistanceM: Int? = objectDxM ?: targetDxM
}

data class LauncherAdasState(
    val accMode: LauncherAdasAccMode = LauncherAdasAccMode.Off,
    val accSetSpeedKmh: Int? = null,
    val accActive: Boolean = false,
    val accStandby: Boolean = false,
    val accOverride: Boolean = false,
    val accTakeOver: Boolean = false,
    val timeGapLevel: Int? = null,
    val frontObject: LauncherAdasFrontObject = LauncherAdasFrontObject(
        valid = false,
        type = LauncherAdasFrontObjectType.None,
        objectDxM = null,
        targetDxM = null,
    ),
    val fcwActive: Boolean = false,
    val distanceWarning: Boolean = false,
    val aebHint: Boolean = false,
    val leftLane: LauncherAdasLaneVisualization = LauncherAdasLaneVisualization.Hidden,
    val rightLane: LauncherAdasLaneVisualization = LauncherAdasLaneVisualization.Hidden,
    val adasTakeOver: Boolean = false,
    val hasAnyAlert: Boolean = false,
    val hasAnyAssist: Boolean = false,
) {
    val laneDepartureLeft: Boolean get() = leftLane == LauncherAdasLaneVisualization.Warning
    val laneDepartureRight: Boolean get() = rightLane == LauncherAdasLaneVisualization.Warning
}

internal fun decodeAccMode(raw: Byte): LauncherAdasAccMode = when (byteToUnsigned(raw)) {
    0 -> LauncherAdasAccMode.Off
    9 -> LauncherAdasAccMode.Standby
    1, 2, 6, 7 -> LauncherAdasAccMode.ActiveDark
    3, 4, 5 -> LauncherAdasAccMode.ActiveBlue
    else -> LauncherAdasAccMode.Unknown
}

internal fun buildLauncherAdasState(
    accModeRaw: Byte,
    vSetDisRaw: Byte,
    objValidRaw: Byte,
    frontObjectTypeRaw: Byte,
    dxTarObjRaw: Byte,
    objectDxRaw: Byte,
    takeOverRaw: Byte,
    textInfoRaw: Byte,
    fcwPreWarningRaw: Byte,
    distanceWarningRaw: Byte,
    timeGapRaw: Byte,
    leftLaneRaw: Byte,
    rightLaneRaw: Byte,
    adasTakeOverRaw: Byte,
): LauncherAdasState {
    val accMode = decodeAccMode(accModeRaw)
    val accModeCode = byteToUnsigned(accModeRaw)
    val setSpeed = when {
        accMode == LauncherAdasAccMode.Off -> null
        vSetDisRaw < 0 -> byteToUnsigned(vSetDisRaw)
        byteToUnsigned(vSetDisRaw) <= 0 -> null
        else -> byteToUnsigned(vSetDisRaw)
    }
    val objValid = byteToUnsigned(objValidRaw) == 2
    val frontObject = LauncherAdasFrontObject(
        valid = objValid,
        type = LauncherAdasFrontObjectType.fromCode(byteToUnsigned(frontObjectTypeRaw)),
        objectDxM = decodeFrmDistanceMetres(objectDxRaw),
        targetDxM = decodeFrmDistanceMetres(dxTarObjRaw),
    )
    val fcw = byteToUnsigned(fcwPreWarningRaw) == 2 || byteToUnsigned(textInfoRaw) == 18
    val distWarn = byteToUnsigned(distanceWarningRaw) == 2 || byteToUnsigned(textInfoRaw) == 17
    val aeb = byteToUnsigned(textInfoRaw) == 11
    val takeOver = byteToUnsigned(takeOverRaw) == 2
    val override = accModeCode == 7
    val accActive = accMode == LauncherAdasAccMode.ActiveBlue || accMode == LauncherAdasAccMode.ActiveDark
    val accStandby = accMode == LauncherAdasAccMode.Standby
    val timeGap = byteToUnsigned(timeGapRaw).takeIf { it in 0..2 }
    val leftLane = LauncherAdasLaneVisualization.fromCode(byteToUnsigned(leftLaneRaw))
    val rightLane = LauncherAdasLaneVisualization.fromCode(byteToUnsigned(rightLaneRaw))
    val adasTakeOver = byteToUnsigned(adasTakeOverRaw) == 2
    val hasAlert = fcw || distWarn || aeb || takeOver || override || adasTakeOver ||
        leftLane == LauncherAdasLaneVisualization.Warning ||
        rightLane == LauncherAdasLaneVisualization.Warning
    val hasAssist = accActive || accStandby || frontObject.valid || hasAlert
    return LauncherAdasState(
        accMode = accMode,
        accSetSpeedKmh = setSpeed,
        accActive = accActive,
        accStandby = accStandby,
        accOverride = override,
        accTakeOver = takeOver,
        timeGapLevel = timeGap,
        frontObject = frontObject,
        fcwActive = fcw,
        distanceWarning = distWarn,
        aebHint = aeb,
        leftLane = leftLane,
        rightLane = rightLane,
        adasTakeOver = adasTakeOver,
        hasAnyAlert = hasAlert,
        hasAnyAssist = hasAssist,
    )
}

/** Normalized depth on virtual road: 0 = horizon, 1 = bottom. */
internal fun distanceToRoadDepth(distanceM: Int): Float {
    val clamped = distanceM.coerceIn(5, 120)
    return (1f - (clamped - 5f) / 115f).coerceIn(0.12f, 0.82f)
}
