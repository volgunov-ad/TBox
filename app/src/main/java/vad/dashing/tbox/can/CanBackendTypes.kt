package vad.dashing.tbox.can

enum class CanBackendType {
    MbCan,
    Vhal,
    None
}

data class CanBackendSelection(
    val type: CanBackendType,
    val reason: String
)
