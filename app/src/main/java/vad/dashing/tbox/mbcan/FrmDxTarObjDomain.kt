package vad.dashing.tbox.mbcan

/**
 * FRM target-object distance raw (`FRM_3_DxTarObj`) gated by `FRM_3_ObjValid`.
 *
 * **ObjValid 1** → emit [dx] raw as-is; **0** or any other/missing valid → null.
 */
object FrmDxTarObjDomain {
    fun decode(dx: Int?, valid: Int?): Int? {
        if (valid != 1) return null
        return dx
    }
}
