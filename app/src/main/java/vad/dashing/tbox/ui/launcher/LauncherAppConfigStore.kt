package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.content.SharedPreferences
import vad.dashing.tbox.ui.LaunchableAppEntry

private const val PREFS = "tbox_launcher_app_config"
private const val KEY_HIDDEN = "hidden_packages"
private const val KEY_GRID = "grid_packages"
private const val KEY_DOCK = "dock_packages"
private const val KEY_CAR_PAINT = "car_paint_id"
private const val KEY_DEFAULT_MEDIA = "default_media_package"
internal const val GRID_SLOT_COUNT = 9
private const val DOCK_SLOT_COUNT = 4

private val DEFAULT_DOCK = listOf(
    "com.autopai.car.dialer",
    "com.wt.multimedia.platform3",
    "com.tencent.wecarnavi",
    "com.autopai.system.settings",
)

internal object LauncherAppConfigStore {

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun carPaintId(context: Context): String {
        val raw = prefs(context).getString(KEY_CAR_PAINT, LauncherCarPaint.defaultId)
        return LauncherCarPaint.options.firstOrNull { it.id == raw }?.id ?: LauncherCarPaint.defaultId
    }

    fun setCarPaintId(context: Context, paintId: String) {
        prefs(context).edit().putString(KEY_CAR_PAINT, paintId).apply()
    }

    fun defaultMediaPackage(context: Context): String? =
        prefs(context).getString(KEY_DEFAULT_MEDIA, null)?.takeIf { it.isNotBlank() }

    fun setDefaultMediaPackage(context: Context, packageName: String) {
        prefs(context).edit().putString(KEY_DEFAULT_MEDIA, packageName).apply()
    }

    fun hiddenPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_HIDDEN, emptySet()).orEmpty()

    fun setHiddenPackages(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_HIDDEN, packages).apply()
    }

    fun hidePackage(context: Context, packageName: String) {
        val next = hiddenPackages(context).toMutableSet()
        next.add(packageName)
        setHiddenPackages(context, next)
    }

    fun showPackage(context: Context, packageName: String) {
        val next = hiddenPackages(context).toMutableSet()
        next.remove(packageName)
        setHiddenPackages(context, next)
    }

    fun gridPackages(context: Context): List<String> =
        decodeSlots(prefs(context).getString(KEY_GRID, null), GRID_SLOT_COUNT)

    fun dockPackages(context: Context): List<String> =
        decodeSlots(prefs(context).getString(KEY_DOCK, null), DOCK_SLOT_COUNT)
            .mapIndexed { index, pkg ->
                pkg.ifBlank { DEFAULT_DOCK.getOrElse(index) { "" } }
            }

    fun setGridSlot(context: Context, slotIndex: Int, packageName: String) {
        if (slotIndex !in 0 until GRID_SLOT_COUNT) return
        val slots = gridPackages(context).toMutableList()
        slots[slotIndex] = packageName
        prefs(context).edit().putString(KEY_GRID, encodeSlots(slots)).apply()
    }

    fun setDockSlot(context: Context, slotIndex: Int, packageName: String) {
        if (slotIndex !in 0 until DOCK_SLOT_COUNT) return
        val slots = dockPackages(context).toMutableList()
        slots[slotIndex] = packageName
        prefs(context).edit().putString(KEY_DOCK, encodeSlots(slots)).apply()
    }

    fun clearGridSlot(context: Context, slotIndex: Int) {
        setGridSlot(context, slotIndex, "")
    }

    fun gridSlotEntries(
        allVisible: List<LaunchableAppEntry>,
        pinned: List<String>,
    ): List<LaunchableAppEntry?> {
        val byPackage = allVisible.associateBy { it.packageName }
        return pinned.map { pkg ->
            if (pkg.isBlank()) null else byPackage[pkg]
        }
    }

    fun filterVisible(
        entries: List<LaunchableAppEntry>,
        hidden: Set<String>,
    ): List<LaunchableAppEntry> = entries.filter { it.packageName !in hidden }

    fun resolveGridApps(
        allVisible: List<LaunchableAppEntry>,
        pinned: List<String>,
        priority: List<String>,
    ): List<LaunchableAppEntry> {
        val byPackage = allVisible.associateBy { it.packageName }
        val result = mutableListOf<LaunchableAppEntry>()
        val used = mutableSetOf<String>()

        for (pkg in pinned) {
            if (pkg.isBlank()) continue
            byPackage[pkg]?.let {
                result.add(it)
                used.add(pkg)
            }
        }

        val fillOrder = LauncherOemAppSort.sortEntries(allVisible, priority)
        for (entry in fillOrder) {
            if (result.size >= GRID_SLOT_COUNT) break
            if (entry.packageName in used) continue
            result.add(entry)
            used.add(entry.packageName)
        }
        return result.take(GRID_SLOT_COUNT)
    }

    private fun decodeSlots(raw: String?, count: Int): List<String> {
        val parsed = raw?.split('|').orEmpty()
        return List(count) { index -> parsed.getOrElse(index) { "" } }
    }

    private fun encodeSlots(slots: List<String>): String =
        slots.joinToString("|")
}
