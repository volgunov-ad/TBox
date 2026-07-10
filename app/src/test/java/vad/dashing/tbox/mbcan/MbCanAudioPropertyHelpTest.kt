package vad.dashing.tbox.mbcan

import com.mengbo.mbCan.defines.MBAudioProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MbCanAudioPropertyHelpTest {

    @Test
    fun allEditableAudioProperties_haveHelpEntries() {
        val editable = MBAudioProperty.values()
            .filter { it != MBAudioProperty.eAUDIO_PROPERTY_COUNT }
            .map { it.value }
            .toSet()

        val documented = MbCanAudioPropertyHelp.get(1)?.propertyId?.let {
            MBAudioProperty.values()
                .filter { it != MBAudioProperty.eAUDIO_PROPERTY_COUNT }
                .mapNotNull { property -> MbCanAudioPropertyHelp.get(property.value)?.propertyId }
                .toSet()
        }

        assertEquals(editable.size, documented?.size)
        editable.forEach { propertyId ->
            assertNotNull("Missing help for MBAudioProperty id=$propertyId", MbCanAudioPropertyHelp.get(propertyId))
        }
    }
}
