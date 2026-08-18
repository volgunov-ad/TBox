package vad.dashing.tbox.location

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager

/**
 * Share / VIEW trampoline: parse a map point and seed the mock shadow, then finish
 * so the navigation app stays in front.
 */
class GeoShareReceiverActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        startBackgroundService()
        val incoming = intent
        val parsed = GeoShareIntentParser.parse(incoming, this)
        if (parsed == null) {
            toast(getString(R.string.toast_geo_share_unparsed))
            finishQuietly()
            return
        }
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                runCatching {
                    SettingsManager(this@GeoShareReceiverActivity)
                        .readBackgroundServiceSettingsSnapshot()
                }.getOrNull()
            }
            val outcome = if (snapshot == null) {
                GeoShareShadowSeed.Outcome.MOCK_OFF
            } else {
                GeoShareShadowSeed.apply(
                    lat = parsed.lat,
                    lon = parsed.lon,
                    power = snapshot.mockPowerState,
                    storedMode = snapshot.mockCanSpeedMode,
                    locationSource = snapshot.locationSource,
                )
            }
            toast(messageFor(outcome, parsed))
            finishQuietly()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun messageFor(
        outcome: GeoShareShadowSeed.Outcome,
        parsed: GeoCoordinateParse.LatLon,
    ): String = when (outcome) {
        GeoShareShadowSeed.Outcome.APPLIED -> getString(
            R.string.toast_geo_share_applied,
            parsed.lat,
            parsed.lon,
        )
        GeoShareShadowSeed.Outcome.INVALID_POINT ->
            getString(R.string.toast_geo_share_unparsed)
        GeoShareShadowSeed.Outcome.MOCK_OFF ->
            getString(R.string.toast_geo_share_mock_off)
        GeoShareShadowSeed.Outcome.WRONG_MODE ->
            getString(R.string.toast_geo_share_wrong_mode)
        GeoShareShadowSeed.Outcome.ANDROID_SOURCE ->
            getString(R.string.toast_geo_share_android_source)
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun startBackgroundService() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_START
        }
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start background service for geo share", e)
        }
    }

    private fun finishQuietly() {
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "GeoShareReceiver"
    }
}
