package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CamdApiListenBackoffTest {
    @Test
    fun doublesUntilCap() {
        assertEquals(1_000L, CamdApiListenBackoff.next(500L))
        assertEquals(2_000L, CamdApiListenBackoff.next(1_000L))
        assertEquals(CamdApiListenBackoff.MAX_MS, CamdApiListenBackoff.next(20_000L))
        assertEquals(CamdApiListenBackoff.MAX_MS, CamdApiListenBackoff.next(CamdApiListenBackoff.MAX_MS))
    }
}

class OverlayLatchMetricsTest {
    @Test
    fun countsShowAndHideTimeoutsIndependently() {
        OverlayLatchMetrics.resetForTest()
        assertEquals(0L, OverlayLatchMetrics.showTimeoutCount())
        assertEquals(0L, OverlayLatchMetrics.hideTimeoutCount())
        assertEquals(1L, OverlayLatchMetrics.onShowTimeout())
        assertEquals(2L, OverlayLatchMetrics.onShowTimeout())
        assertEquals(1L, OverlayLatchMetrics.onHideTimeout())
        assertEquals(2L, OverlayLatchMetrics.showTimeoutCount())
        assertEquals(1L, OverlayLatchMetrics.hideTimeoutCount())
        OverlayLatchMetrics.resetForTest()
        assertEquals(0L, OverlayLatchMetrics.showTimeoutCount())
    }
}

class CamdApiServerSuperviseTest {
    @Test
    fun stopClosesListenSocketSoPortCanRebind() {
        val engine = CamdEngine(java.io.File("build/camd-test-files").apply { mkdirs() })
        val port = freePort()
        val server = CamdApiServer(engine, port = port)
        server.start()
        assertTrue("listen should come up", awaitPortOpen(port, 3_000))
        server.stop()
        assertTrue("stop should release the port", awaitPortClosedThenRebind(port, 3_000))
        assertFalse(server.isRunning())
    }

    @Test
    fun retriesBindAfterTransientFailure() {
        val engine = CamdEngine(java.io.File("build/camd-test-files").apply { mkdirs() })
        val port = freePort()
        val attempts = AtomicInteger(0)
        val sleeps = AtomicInteger(0)
        val slept = CountDownLatch(1)
        val server = CamdApiServer(
            engine,
            port = port,
            sleeper = { _ ->
                sleeps.incrementAndGet()
                slept.countDown()
                Thread.sleep(20)
            },
            binder = { socket ->
                if (attempts.getAndIncrement() == 0) {
                    throw java.net.BindException("simulated busy port")
                }
                socket.bind(InetSocketAddress("127.0.0.1", port), 8)
            },
        )
        server.start()
        assertTrue("supervisor should backoff once", slept.await(3, TimeUnit.SECONDS))
        assertTrue("listen recovers after bind succeeds", awaitPortOpen(port, 5_000))
        assertTrue(sleeps.get() >= 1)
        assertTrue(attempts.get() >= 2)
        server.stop()
        awaitPortClosedThenRebind(port, 3_000)
    }

    private fun freePort(): Int =
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }

    private fun awaitPortOpen(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.Socket().use { client ->
                    client.soTimeout = 500
                    client.connect(InetSocketAddress("127.0.0.1", port), 500)
                    return true
                }
            } catch (_: Throwable) {
                Thread.sleep(50)
            }
        }
        return false
    }

    private fun awaitPortClosedThenRebind(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                ServerSocket().use { probe ->
                    probe.reuseAddress = true
                    probe.bind(InetSocketAddress("127.0.0.1", port), 1)
                    return true
                }
            } catch (_: Throwable) {
                Thread.sleep(50)
            }
        }
        return false
    }
}
