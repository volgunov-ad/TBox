package vad.dashing.tbox

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import vad.dashing.tbox.ui.launcher.resolveLaunchIntent

/**
 * Trampoline for launching third-party apps from [LauncherHomeActivity].
 * Some head units block visible app transitions when the caller is a HOME handler;
 * this regular (non-HOME) activity starts the target and exits immediately.
 */
class LauncherAppProxyActivity : Activity() {

    companion object {
        private const val TAG = "LauncherAppLaunch"
        const val EXTRA_PACKAGE = "extra_launch_package"
        const val EXTRA_COMPONENT = "extra_launch_component"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val component = intent.getStringExtra(EXTRA_COMPONENT)
            ?.let(ComponentName::unflattenFromString)
        Log.w(TAG, "proxy onCreate pkg=$packageName component=$component")

        val launchIntent = when {
            component != null -> Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(component)
            }
            packageName.isNotBlank() -> resolveLaunchIntent(this, packageName)
            else -> null
        }
        if (launchIntent == null) {
            Log.w(TAG, "proxy no launch intent for pkg=$packageName")
            finish()
            return
        }
        MainActivityIntentHelper.applyExternalAppLaunchFlags(launchIntent, this)
        runCatching { startActivity(launchIntent) }
            .onSuccess {
                LastAppTracker.recordLaunch(this, packageName)
                Log.w(TAG, "proxy startActivity OK pkg=$packageName component=${launchIntent.component}")
            }
            .onFailure {
                Log.w(TAG, "proxy startActivity failed pkg=$packageName", it)
            }
        window.decorView.post { finish() }
    }
}
