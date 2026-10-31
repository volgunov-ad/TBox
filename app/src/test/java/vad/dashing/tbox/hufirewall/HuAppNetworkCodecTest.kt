package vad.dashing.tbox.hufirewall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuAppNetworkCodecTest {
    @Test
    fun command_targetsBothChainsAndAppUids() {
        val command = HuAppNetworkCodec.SHELL_COMMAND
        HuAppNetworkCodec.CHAINS.forEach { assertTrue(command.contains(it)) }
        assertTrue(command.contains("--uid-owner 10000-99999 -j RETURN"))
        assertTrue(command.contains("iptables -w -C"))
    }

    @Test
    fun parse_addedAndPresent() {
        val outcome = HuAppNetworkCodec.parseOutput(
            "TBOX_FW_ADDED adayo_output_wifi\nTBOX_FW_PRESENT adayo_output_mobile\nTBOX_FW_END\n",
        )
        assertEquals(HuAppNetworkOutcome.Open(chains = 2, added = 1), outcome)
    }

    @Test
    fun parse_noChains() {
        assertEquals(HuAppNetworkOutcome.NoFirewall, HuAppNetworkCodec.parseOutput("TBOX_FW_END\n"))
    }

    @Test
    fun parse_noSu() {
        assertEquals(HuAppNetworkOutcome.NoSu, HuAppNetworkCodec.parseOutput("TBOX_FW_ERR nosu\n"))
    }

    @Test
    fun parse_failureAndMissingReply() {
        assertEquals(
            HuAppNetworkOutcome.Failed("adayo_output_mobile"),
            HuAppNetworkCodec.parseOutput(
                "TBOX_FW_PRESENT adayo_output_wifi\nTBOX_FW_FAIL adayo_output_mobile\nTBOX_FW_END\n",
            ),
        )
        assertEquals(HuAppNetworkOutcome.Failed("noreply"), HuAppNetworkCodec.parseOutput("su: not allowed\n"))
    }
}
