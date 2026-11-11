package vad.dashing.camd

/**
 * Stock XML filenames say front/rear/left/right as avmf/avmb/avml/avmr.
 * On this car the picture does not match those names: 4 is left, 5 is right,
 * 8 is front, 9 is rear. Titles below follow the picture. Input ids stay.
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
        CameraInput(4, "4 · лево", 1280, 800, heldByAvm = true),
        CameraInput(5, "5 · право", 1280, 800, heldByAvm = true),
        CameraInput(8, "8 · перед", 1280, 800, heldByAvm = true),
        CameraInput(9, "9 · зад", 1280, 800, heldByAvm = true)
    )

    fun byId(id: Int): CameraInput? = all.firstOrNull { it.id == id }
}
