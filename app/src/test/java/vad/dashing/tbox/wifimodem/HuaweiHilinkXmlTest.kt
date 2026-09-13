package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuaweiHilinkXmlTest {
    @Test
    fun tagValueReadsLeaf() {
        val xml = "<response><dataswitch>1</dataswitch></response>"
        assertEquals("1", HuaweiHilinkXml.tagValue(xml, "dataswitch"))
        assertNull(HuaweiHilinkXml.tagValue(xml, "missing"))
    }

    @Test
    fun tagValuesCollectsLeaves() {
        val xml = """
            <response>
              <ConnectionStatus>901</ConnectionStatus>
              <SignalIcon>3</SignalIcon>
            </response>
        """.trimIndent()
        val map = HuaweiHilinkXml.tagValues(xml)
        assertEquals("901", map["ConnectionStatus"])
        assertEquals("3", map["SignalIcon"])
    }
}
