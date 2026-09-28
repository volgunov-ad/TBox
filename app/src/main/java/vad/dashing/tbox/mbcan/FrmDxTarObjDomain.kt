package vad.dashing.tbox.mbcan

/**
 * FRM target-object distance raw (`FRM_3_DxTarObj`) gated by `FRM_3_ObjValid`.
 *
 * A9 deep-session evidence: interesting `dx` (1…3) almost always arrives with **ObjValid=2**;
 * **ObjValid=1** often has `dx=0`. Emitting only on `valid==1` made the widget nearly always null.
 *
 * Safer UI gate: emit [dx] when [valid] is **1 or 2** (not 0 / missing / other).
 * Journal always logs both raw fields separately (`frm_dx_tar_obj`). Units of dx are **not**
 * confirmed as metres.
 */
object FrmDxTarObjDomain {
    fun decode(dx: Int?, valid: Int?): Int? {
        if (dx == null) return null
        if (valid != 1 && valid != 2) return null
        return dx
    }

    /** True when ObjValid is in the emit set used by [decode]. */
    fun isObjValidForUi(valid: Int?): Boolean = valid == 1 || valid == 2
}
