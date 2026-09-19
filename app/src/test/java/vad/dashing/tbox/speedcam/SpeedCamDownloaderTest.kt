package vad.dashing.tbox.speedcam

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedCamDownloaderTest {

    @Test
    fun isTransientNetworkFailure_matchesSslAbort() {
        assertTrue(
            SpeedCamDownloader.isTransientNetworkFailure(
                "Read error: ssl=0x7d9df17708: I/O error during system call, Software caused connection abort",
            ),
        )
        assertTrue(SpeedCamDownloader.isTransientNetworkFailure("Connection reset by peer"))
        assertTrue(SpeedCamDownloader.isTransientNetworkFailure("SSL handshake timed out"))
        assertFalse(SpeedCamDownloader.isTransientNetworkFailure("HTTP 404"))
        assertFalse(SpeedCamDownloader.isTransientNetworkFailure("Not an iGO speedcam CSV"))
        assertFalse(SpeedCamDownloader.isTransientNetworkFailure(null))
    }
}
