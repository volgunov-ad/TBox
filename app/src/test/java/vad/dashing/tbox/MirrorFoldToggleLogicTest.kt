package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MirrorFoldLastCommandStoreTest {
    @Test
    fun remembersLastCommandAcrossReads() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_FOLD,
            MirrorFoldLastCommandStore.nextSingleTapValue(context),
        )

        MirrorFoldLastCommandStore.rememberSent(context, MIRROR_FOLD_SWITCH_VALUE_FOLD)
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_UNFOLD,
            MirrorFoldLastCommandStore.nextSingleTapValue(context),
        )

        MirrorFoldLastCommandStore.rememberSent(context, MIRROR_FOLD_SWITCH_VALUE_UNFOLD)
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_FOLD,
            MirrorFoldLastCommandStore.nextSingleTapValue(context),
        )
    }

    @Test
    fun doubleTapFold_thenSingleTapUnfolds() {
        val context = RuntimeEnvironment.getApplication()
        MirrorFoldLastCommandStore.rememberSent(context, MIRROR_FOLD_SWITCH_VALUE_FOLD)
        assertEquals(
            MIRROR_FOLD_SWITCH_VALUE_UNFOLD,
            MirrorFoldLastCommandStore.nextSingleTapValue(context),
        )
    }
}
