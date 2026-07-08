package vad.dashing.tbox

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import vad.dashing.tbox.ui.disposeAppLauncherPickerIconCache
import vad.dashing.tbox.ui.launcher.LauncherOverlayElevator
import vad.dashing.tbox.ui.launcher.TeslaLauncherScreen
import androidx.compose.runtime.DisposableEffect

/**
 * Android HOME handler — Tesla-style launcher surface for the head unit.
 */
class LauncherHomeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LauncherHome"
    }

    private lateinit var settingsManager: SettingsManager
    private lateinit var appDataManager: AppDataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherHomeActivityHolder.instance = this
        Log.w("LauncherAppLaunch", "LauncherHomeActivity onCreate v${BuildConfig.VERSION_NAME}")
        applyLauncherWindowFlags()

        settingsManager = SettingsManager(this)
        appDataManager = AppDataManager(this)

        lifecycleScope.launch {
            try {
                LauncherPresetInitializer.ensureDefaultLayoutIfNeeded(this@LauncherHomeActivity, settingsManager)
            } catch (e: Exception) {
                Log.w(TAG, "Launcher preset init failed", e)
            }
        }

        setContent {
            DisposableEffect(Unit) {
                onDispose { disposeAppLauncherPickerIconCache() }
            }
            Surface(modifier = Modifier.fillMaxSize()) {
                TeslaLauncherScreen(
                    settingsManager = settingsManager,
                    appDataManager = appDataManager,
                    onTboxRestart = { rebootTBox() },
                    onTripFinishAndStart = {
                        serviceCommand(BackgroundService.ACTION_TRIP_FINISH_AND_START, "", "")
                    },
                )
            }
        }
        startBackgroundService()
    }

    override fun onRestart() {
        super.onRestart()
        LauncherHomeActivityHolder.instance = this
        startBackgroundService()
    }

    override fun onResume() {
        super.onResume()
        LauncherForegroundHandoff.restoreLauncherWindow()
        if (LauncherOverlayElevator.overlayHoldActive) {
            LauncherOverlayElevator.bringLauncherToFront(this)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && LauncherOverlayElevator.overlayHoldActive) {
            LauncherOverlayElevator.bringLauncherToFront(this)
        }
    }

    override fun onDestroy() {
        LauncherOverlayElevator.reset()
        if (LauncherHomeActivityHolder.instance === this) {
            LauncherHomeActivityHolder.instance = null
        }
        super.onDestroy()
    }

    private fun applyLauncherWindowFlags() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            show(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startBackgroundService() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_START
        }
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start background service", e)
        }
    }

    private fun serviceCommand(sendAction: String, extraName: String, extraValue: String) {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = sendAction
            if (extraName.isNotEmpty() && extraValue.isNotEmpty()) {
                putExtra(extraName, extraValue)
            }
        }
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Service command failed", e)
        }
    }

    private fun rebootTBox() {
        serviceCommand(BackgroundService.ACTION_TBOX_REBOOT, "", "")
    }
}
