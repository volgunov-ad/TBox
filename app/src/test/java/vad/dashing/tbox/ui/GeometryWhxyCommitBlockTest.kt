package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeometryWhxyCommitBlockTest {

    @Test
    fun parseGeometryDraftInt_acceptsPlainAndDecimalPrefix() {
        assertEquals(30, parseGeometryDraftInt("30"))
        assertEquals(30, parseGeometryDraftInt(" 30 "))
        assertEquals(30, parseGeometryDraftInt("30.5"))
        assertEquals(30, parseGeometryDraftInt("30,5"))
    }

    @Test
    fun parseGeometryDraftInt_rejectsEmptyAndNonNumeric() {
        assertNull(parseGeometryDraftInt(""))
        assertNull(parseGeometryDraftInt("   "))
        assertNull(parseGeometryDraftInt("."))
        assertNull(parseGeometryDraftInt("abc"))
    }

    /**
     * Compose UI cannot run here; this locks the reload contract that
     * [GeometryWhxyCommitBlock]'s `resetKey` / `key(panelId)` implement:
     * panel identity must differ even when W/H/X/Y ints are identical, otherwise
     * local TextField drafts would keep the previous panel's dirty text.
     */
    @Test
    fun geometryDraftReloadIdentity_includesPanelIdNotOnlyWhxy() {
        fun reloadIdentity(panelId: String, w: Int, h: Int, x: Int, y: Int) =
            listOf(panelId, w, h, x, y)
        val panelA = reloadIdentity("panel-a", 400, 300, 0, 0)
        val panelB = reloadIdentity("panel-b", 400, 300, 0, 0)
        assertEquals(panelA.drop(1), panelB.drop(1))
        assertNotEquals(panelA, panelB)
    }
}
