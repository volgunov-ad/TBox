package vad.dashing.camd

/** Pure parser for Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, unit-testable off-device. */
object AccessibilityGate {

    fun isEnabledInSetting(raw: String?, packageName: String, className: String): Boolean {
        if (raw.isNullOrBlank()) return false
        return raw.split(':').any { component -> matches(component.trim(), packageName, className) }
    }

    private fun matches(component: String, packageName: String, className: String): Boolean {
        val slash = component.indexOf('/')
        if (slash < 0) return false
        if (component.substring(0, slash) != packageName) return false
        val cls = component.substring(slash + 1)
        return cls == className || (cls.startsWith(".") && packageName + cls == className)
    }
}
