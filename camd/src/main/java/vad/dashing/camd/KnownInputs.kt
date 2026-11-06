package vad.dashing.camd

/**
 * Cameras named from the stock qcarcam XML on the A9 head unit.
 * Output size is the sensor mode that already started on this device.
 */
data class CameraInput(
    val id: Int,
    val title: String,
    val width: Int,
    val height: Int,
    /** Stock AVM already keeps these inputs open. A second client collides with it. */
    val heldByAvm: Boolean
)

object KnownInputs {
    val all: List<CameraInput> = listOf(
        CameraInput(0, "0 · салон / AR", 1488, 616, heldByAvm = false),
        CameraInput(1, "1 · OMS", 1280, 720, heldByAvm = false),
        CameraInput(3, "3 · DMS", 1280, 800, heldByAvm = false),
        CameraInput(4, "4 · AVM перед", 1280, 800, heldByAvm = true),
        CameraInput(5, "5 · AVM зад", 1280, 800, heldByAvm = true),
        CameraInput(8, "8 · AVM лево", 1280, 800, heldByAvm = true),
        CameraInput(9, "9 · AVM право", 1280, 800, heldByAvm = true)
    )

    fun byId(id: Int): CameraInput? = all.firstOrNull { it.id == id }
}
