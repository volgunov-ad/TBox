package vad.dashing.tbox.um980fw

import vad.dashing.tbox.usbgnss.UsbNmeaGnssSession

/** [Um980BinaryTransport] over direct USB GNSS session. */
class UsbUm980BinaryTransport(
    private val session: UsbNmeaGnssSession,
) : Um980BinaryTransport {
    override fun currentBaud(): Int = session.currentBaud()

    override fun setBaud(baud: Int): Boolean = session.setBaudLive(baud)

    override fun write(bytes: ByteArray): Boolean = session.writeRaw(bytes)

    override fun read(maxBytes: Int, timeoutMs: Long): ByteArray =
        session.readExclusive(maxBytes, timeoutMs)

    override fun beginExclusive() {
        session.beginExclusiveIo()
    }

    override fun endExclusive() {
        session.endExclusiveIo()
    }
}
