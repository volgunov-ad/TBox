package vad.dashing.mqtt.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class KeystoreFailureTest {
    @Test
    fun earlyBootIsNotReadyAndABadKeysetIsCorrupt() {
        assertEquals(
            KeystoreFailure.NOT_READY,
            classifyKeystoreFailure(IllegalStateException("Keystore timed out waiting")),
        )
        assertEquals(
            KeystoreFailure.CORRUPT,
            classifyKeystoreFailure(IllegalArgumentException("AEADBadTagException: mac verification failed")),
        )
        assertEquals(KeystoreFailure.OTHER, classifyKeystoreFailure(IllegalStateException("disk full")))
    }

    @Test
    fun retriesThreeTimesAndSleepsBetweenAttempts() {
        val sleeps = mutableListOf<Long>()
        var calls = 0
        val failed = retryKeystore(times = 3, backoffMs = 500L, sleep = { sleeps += it }) {
            calls += 1
            throw IllegalStateException("not ready")
        }
        assertEquals(3, calls)
        assertEquals(listOf(500L, 500L), sleeps)
        assertEquals("not ready", failed.error?.message)

        var opened = 0
        val recovered = retryKeystore(times = 3, backoffMs = 500L, sleep = { }) {
            opened += 1
            if (opened < 3) throw IllegalStateException("not ready")
            "prefs"
        }
        assertEquals("prefs", recovered.value)
        assertEquals(2, recovered.sleeps)
    }
}
