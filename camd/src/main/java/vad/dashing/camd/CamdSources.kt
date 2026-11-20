package vad.dashing.camd

/**
 * Camera ids accepted by the API. Raw ids (KnownInputs) address grabber inputs
 * directly. Stock ids 40..45 address the vendor AVM calibrated views of the same
 * physical inputs (docs/CAMD_CALIBRATION_RU.md): the UYVY frame file is shared,
 * but the picture is drawn with the vendor mesh instead of raw geometry.
 */
object CamdSources {
    val STOCK_IDS: IntRange =
        CamdCalibration.FIRST_SOURCE_ID until CamdCalibration.FIRST_SOURCE_ID + CamdCalibration.VIEW_INPUTS.size

    fun isStock(id: Int): Boolean = id in STOCK_IDS

    /** Physical grabber input behind [id]; null for unknown ids. */
    fun physicalInput(id: Int): Int? {
        KnownInputs.byId(id)?.let { return it.id }
        if (!isStock(id)) return null
        return CamdCalibration.VIEW_INPUTS[id - CamdCalibration.FIRST_SOURCE_ID]
    }

    fun isKnown(id: Int): Boolean = physicalInput(id) != null

    /** True when the stock AVM holds the underlying input open. */
    fun heldByAvm(id: Int): Boolean =
        physicalInput(id)?.let { KnownInputs.byId(it)?.heldByAvm } ?: false

    /** User-facing titles of the six stock views, in STOCK_IDS order. */
    private val STOCK_TITLES = listOf(
        "40 · сток: перед",
        "41 · сток: право",
        "42 · сток: зад",
        "43 · сток: лево",
        "44 · сток: широкий перед",
        "45 · сток: широкий зад",
    )

    /** Raw input or stock view as a UI descriptor; null for unknown ids. */
    fun camera(id: Int): CameraInput? {
        KnownInputs.byId(id)?.let { return it }
        if (!isStock(id)) return null
        val index = id - CamdCalibration.FIRST_SOURCE_ID
        val input = KnownInputs.byId(CamdCalibration.VIEW_INPUTS[index])
        return CameraInput(
            id = id,
            title = STOCK_TITLES[index],
            width = input?.width ?: 0,
            height = input?.height ?: 0,
            heldByAvm = input?.heldByAvm ?: false,
        )
    }

    /** UI source list: raw inputs, then the stock views of the same physical inputs. */
    fun allSources(): List<CameraInput> = KnownInputs.all + STOCK_IDS.mapNotNull { camera(it) }
}
