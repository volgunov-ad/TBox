package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
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
}
