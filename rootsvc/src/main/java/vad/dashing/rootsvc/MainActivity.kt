package vad.dashing.rootsvc

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import vad.dashing.rootsvc.client.RootSvcClient

/** Tiny status screen so the APK is visible in the launcher. */
class MainActivity : Activity() {
    private lateinit var text: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this)
        text.textSize = 18f
        text.setPadding(48, 48, 48, 48)
        text.text = "rootsvc ${BuildConfig.VERSION_NAME}\nпроверка…"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.addView(text)
        setContentView(root)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                ),
                REQ_STORAGE,
            )
        } else {
            runEnsure()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            runEnsure()
        }
    }

    private fun runEnsure() {
        Thread({
            EnsureDaemon.ensure(this, "ui")
            RootSvcClient.clearCache()
            val up = RootSvcClient.isUp()
            runOnUiThread {
                text.text = "rootsvc ${BuildConfig.VERSION_NAME}\n" +
                    if (up) "служба отвечает (PING)" else "служба не отвечает — ensure вызван"
            }
        }, "rootsvc-ui").start()
    }

    private companion object {
        const val REQ_STORAGE = 41
    }
}
