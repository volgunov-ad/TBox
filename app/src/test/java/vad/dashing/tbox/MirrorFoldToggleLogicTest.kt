package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MirrorFoldToggleLogicTest {
    @Test
    fun opposite_swapsFoldAndUnfold() {
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_UNFOLD,
            MirrorFoldToggleLogic.opposite(MIRROR_FOLD_SWITCH_VALUE_FOLD),
        )
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_FOLD,
            MirrorFoldToggleLogic.opposite(MIRROR_FOLD_SWITCH_VALUE_UNFOLD),
        )
    }

    @Test
    fun nextSingleTap_defaultsFromUnfoldToFold() {
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_FOLD,
            MirrorFoldToggleLogic.nextSingleTapValue(MirrorFoldLastCommandStore.DEFAULT_LAST_VALUE),
        )
    }

    @Test
    fun normalizeStoredValue_mapsUnknownToUnfold() {
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_UNFOLD,
            MirrorFoldToggleLogic.normalizeStoredValue(0),
        )
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_FOLD,
            MirrorFoldToggleLogic.normalizeStoredValue(MIRROR_FOLD_SWITCH_VALUE_FOLD),
        )
    }
}

class MirrorFoldLastCommandStoreTest {
    @Before
    fun resetSessionState() {
        MirrorFoldLastCommandStore.resetForTests()
    }

    @Test
    fun remembersLastCommandInSession() {
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_FOLD,
            MirrorFoldLastCommandStore.nextSingleTapValue(),
        )

        MirrorFoldLastCommandStore.rememberSent(MIRROR_FOLD_SWITCH_VALUE_FOLD)
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_UNFOLD,
            MirrorFoldLastCommandStore.nextSingleTapValue(),
        )

        MirrorFoldLastCommandStore.rememberSent(MIRROR_FOLD_SWITCH_VALUE_UNFOLD)
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_FOLD,
            MirrorFoldLastCommandStore.nextSingleTapValue(),
        )
    }

    @Test
    fun doubleTapFold_thenSingleTapUnfolds() {
        MirrorFoldLastCommandStore.rememberSent(MIRROR_FOLD_SWITCH_VALUE_FOLD)
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_UNFOLD,
            MirrorFoldLastCommandStore.nextSingleTapValue(),
        )
    }

    @Test
    fun resetForTests_restoresDefaultUnfolded() {
        MirrorFoldLastCommandStore.rememberSent(MIRROR_FOLD_SWITCH_VALUE_FOLD)
        MirrorFoldLastCommandStore.resetForTests()
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_FOLD,
            MirrorFoldLastCommandStore.nextSingleTapValue(),
        )
    }
}
