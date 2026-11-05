package vad.dashing.tbox.phone

import android.bluetooth.BluetoothDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetClientCommandsTest {

    class RefusingClient {
        val invoked = mutableListOf<String>()

        fun acceptCall(device: BluetoothDevice?, flag: Int): Boolean {
            invoked += "acceptCall"
            return false
        }

        fun acceptCall(device: BluetoothDevice?): Boolean {
            invoked += "acceptCall/1"
            return true
        }

        fun rejectCall(device: BluetoothDevice?): Boolean {
            invoked += "rejectCall"
            return false
        }

        fun terminateCall(device: BluetoothDevice?, index: Int): Boolean {
            invoked += "terminateCall"
            return false
        }
    }

    class AcceptingClient {
        val invoked = mutableListOf<String>()

        fun acceptCall(device: BluetoothDevice?, flag: Int): Boolean {
            invoked += "acceptCall:$flag"
            return true
        }

        fun rejectCall(device: BluetoothDevice?): Boolean {
            invoked += "rejectCall"
            return true
        }

        fun terminateCall(device: BluetoothDevice?, index: Int): Boolean {
            invoked += "terminateCall"
            return true
        }
    }

    class TerminateOnlyClient {
        val invoked = mutableListOf<String>()

        fun terminateCall(device: BluetoothDevice?, index: Int): Boolean {
            invoked += "terminateCall"
            return true
        }
    }

    class VoidClient {
        fun acceptCall(device: BluetoothDevice?) = Unit

        fun explode(): Boolean = error("binder died")
    }

    @Test
    fun accept_refusalIsFailure_noFallbackToOtherOverload() {
        val client = RefusingClient()
        assertFalse(HeadsetClientCommands.accept(client, device = null))
        assertEquals(listOf("acceptCall"), client.invoked)
    }

    @Test
    fun accept_successWithAcceptNoneFlag() {
        val client = AcceptingClient()
        assertTrue(HeadsetClientCommands.accept(client, device = null))
        assertEquals(listOf("acceptCall:0"), client.invoked)
    }

    @Test
    fun accept_voidOverloadCountsAsIssued() {
        assertTrue(HeadsetClientCommands.accept(VoidClient(), device = null))
    }

    @Test
    fun accept_missingMethodIsFailure() {
        assertFalse(HeadsetClientCommands.accept(Any(), device = null))
    }

    @Test
    fun hangup_ringingUsesRejectOnly() {
        val client = AcceptingClient()
        assertTrue(HeadsetClientCommands.hangup(client, device = null, call = null, ringing = true))
        assertEquals(listOf("rejectCall"), client.invoked)
    }

    @Test
    fun hangup_ringingNeverFallsBackToTerminate() {
        val client = TerminateOnlyClient()
        assertFalse(HeadsetClientCommands.hangup(client, device = null, call = null, ringing = true))
        assertTrue(client.invoked.isEmpty())
    }

    @Test
    fun hangup_activeCallRefusalIsFailure() {
        val client = RefusingClient()
        assertFalse(HeadsetClientCommands.hangup(client, device = null, call = null, ringing = false))
        assertEquals(listOf("terminateCall"), client.invoked)
    }

    @Test
    fun hangup_activeCallTerminated() {
        val client = AcceptingClient()
        assertTrue(HeadsetClientCommands.hangup(client, device = null, call = Any(), ringing = false))
        assertEquals(listOf("terminateCall"), client.invoked)
    }

    @Test
    fun invokeStackCommand_throwingOrMissingIsNull() {
        assertNull(invokeStackCommand(VoidClient(), "explode", emptyArray()))
        assertNull(invokeStackCommand(VoidClient(), "missing", emptyArray()))
    }
}
