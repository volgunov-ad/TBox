package vad.dashing.mqtt.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStoreException
import java.security.ProviderException

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
        assertEquals(KeystoreFailure.OTHER, classifyKeystoreFailure(IllegalArgumentException("cannot read keyset")))
    }

    @Test
    fun transientKeystoreErrorsAreNotCorrupt() {
        assertEquals(KeystoreFailure.NOT_READY, classifyKeystoreFailure(KeyStoreException("System error")))
        assertEquals(
            KeystoreFailure.NOT_READY,
            classifyKeystoreFailure(
                ProviderException("Keystore operation failed", KeyStoreException("User authentication required")),
            ),
        )
        assertEquals(
            KeystoreFailure.NOT_READY,
            classifyKeystoreFailure(IllegalStateException("key exists but is unusable")),
        )
    }

    @Test
    fun corruptNeedsAPositiveMarker() {
        assertEquals(
            KeystoreFailure.CORRUPT,
            classifyKeystoreFailure(IllegalArgumentException("InvalidProtocolBufferException")),
        )
        assertEquals(
            KeystoreFailure.CORRUPT,
            classifyKeystoreFailure(IllegalStateException("Key blob corrupted")),
        )
        assertEquals(
            KeystoreFailure.CORRUPT,
            classifyKeystoreFailure(IllegalStateException("Key permanently invalidated")),
        )
    }

    @Test
    fun corruptWipeWaitsForAnotherProcessOrAMinute() {
        assertFalse(corruptRepeats(previousAtElapsedMs = 0L, previousPid = 0, nowElapsedMs = 10_000L, pid = 1))
        assertFalse(corruptRepeats(previousAtElapsedMs = 10_000L, previousPid = 7, nowElapsedMs = 25_000L, pid = 7))
        assertTrue(corruptRepeats(previousAtElapsedMs = 10_000L, previousPid = 7, nowElapsedMs = 70_000L, pid = 7))
        assertTrue(corruptRepeats(previousAtElapsedMs = 10_000L, previousPid = 7, nowElapsedMs = 11_000L, pid = 8))
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
