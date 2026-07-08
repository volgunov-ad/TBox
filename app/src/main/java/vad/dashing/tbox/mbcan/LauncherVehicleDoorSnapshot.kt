package vad.dashing.tbox.mbcan

/** Raw door/trunk bytes from [com.mengbo.mbCan.entity.MBCanVehicleDoor]. */
data class LauncherVehicleDoorSnapshot(
    val driverDoor: Byte,
    val passengerDoor: Byte,
    val rearLeftDoor: Byte,
    val rearRightDoor: Byte,
    val trunk: Byte,
    val hood: Byte,
)
