package vad.dashing.rootsvc

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import vad.dashing.rootsvc.client.RootSvcClient

/** Tiny status screen so the APK is visible in the launcher. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this)
        text.textSize = 18f
        text.setPadding(48, 48, 48, 48)
        text.text = "rootsvc ${BuildConfig.VERSION_NAME}\nпроверка…"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.addView(text)
        setContentView(root)
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
}
