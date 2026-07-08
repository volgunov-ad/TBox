package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * SceneView/Filament needs OpenGL ES 3.0+ (ESSL 3.0 shaders).
 * x86 emulators and some GLES 2.0 devices crash natively in libfilament-jni.
 */
internal object LauncherFilamentSupport {

    fun isSupported(context: Context): Boolean {
        if (isLikelyEmulator()) return false
        return glEsMajorVersion(context) >= 3
    }

    fun isLikelyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val model = Build.MODEL.lowercase()
        return fingerprint.contains("generic")
            || fingerprint.contains("emulator")
            || fingerprint.contains("sdk_gphone")
            || hardware.contains("goldfish")
            || hardware.contains("ranchu")
            || product.contains("sdk")
            || product.contains("emulator")
            || model.contains("emulator")
            || model.contains("android sdk built for")
    }

    private fun glEsMajorVersion(context: Context): Int {
        val info = context.getSystemService(ActivityManager::class.java)?.deviceConfigurationInfo
            ?: return 0
        val version = info.glEsVersion ?: return 0
        return version.substringBefore('.').toIntOrNull() ?: 0
    }
}
