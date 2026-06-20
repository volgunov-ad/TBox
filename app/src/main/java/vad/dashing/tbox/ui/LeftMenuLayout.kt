package vad.dashing.tbox.ui

import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager

/**
 * Identifiers for configurable left-sidebar menu tabs.
 * [id] is persisted in settings JSON.
 */
enum class LeftMenuTabField(
    val id: String,
    @StringRes val labelRes: Int,
    val locked: Boolean = false,
) {
    MODEM("modem", R.string.tab_modem),
    AT_COMMANDS("at_commands", R.string.tab_at_commands),
    GEOPOSITION("geoposition", R.string.tab_geoposition),
    CAR_DATA("car_data", R.string.tab_car_data),
    TRIPS("trips", R.string.tab_trips),
    REFUELS("refuels", R.string.tab_refuels),
    SETTINGS("settings", R.string.tab_settings, locked = true),
    FLOATING_PANELS_SETTINGS("floating_panels_settings", R.string.tab_floating_panels_settings),
    THEMES("themes", R.string.tab_themes),
    LOGS("logs", R.string.tab_logs),
    INFO("info", R.string.tab_info),
    CAN("can", R.string.tab_can),
    WIDGETS("widgets", R.string.tab_widgets),
    MAIN_SCREEN_SETTINGS("main_screen_settings", R.string.tab_main_screen_settings, locked = true),
    CAR_SETTINGS("car_settings", R.string.tab_car_settings),
    ;

    companion object {
        private val byId: Map<String, LeftMenuTabField> = entries.associateBy { it.id }

        fun fromId(id: String): LeftMenuTabField? = byId[id]

        fun defaultOrder(): List<LeftMenuTabField> = listOf(
            MODEM,
            AT_COMMANDS,
            GEOPOSITION,
            CAR_DATA,
            TRIPS,
            REFUELS,
            SETTINGS,
            FLOATING_PANELS_SETTINGS,
            THEMES,
            MAIN_SCREEN_SETTINGS,
            CAR_SETTINGS,
            LOGS,
            INFO,
            CAN,
            WIDGETS,
        )
    }
}

data class LeftMenuLayout(
    val rows: List<Row>,
) {
    data class Row(
        val field: LeftMenuTabField,
        val enabled: Boolean,
    )

    companion object {
        fun defaultEnabled(field: LeftMenuTabField): Boolean =
            field.locked ||
                field == LeftMenuTabField.FLOATING_PANELS_SETTINGS ||
                field == LeftMenuTabField.THEMES

        fun default(): LeftMenuLayout =
            LeftMenuLayout(
                defaultOrder().map { field ->
                    Row(field, enabled = defaultEnabled(field))
                },
            )

        fun parse(raw: String): LeftMenuLayout {
            if (raw.isBlank()) return default()
            return try {
                val root = JSONObject(raw)
                val arr = root.getJSONArray("rows")
                val seen = mutableSetOf<String>()
                val parsed = mutableListOf<Row>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val field = LeftMenuTabField.fromId(id) ?: continue
                    if (id in seen) continue
                    seen.add(id)
                    parsed.add(Row(field, o.optBoolean("enabled", defaultEnabled(field))))
                }
                for (field in defaultOrder()) {
                    if (field.id !in seen) {
                        parsed.add(Row(field, enabled = defaultEnabled(field)))
                    }
                }
                LeftMenuLayout(enforceLocked(parsed))
            } catch (_: Exception) {
                default()
            }
        }

        fun serialize(layout: LeftMenuLayout): String {
            val arr = JSONArray()
            for (r in layout.rows) {
                arr.put(
                    JSONObject()
                        .put("id", r.field.id)
                        .put("enabled", r.enabled),
                )
            }
            return JSONObject().put("rows", arr).toString()
        }

        fun enabledTabKeys(layout: LeftMenuLayout): List<String> =
            layout.rows.filter { it.enabled }.map { it.field.id }

        fun firstVisibleTabKey(layout: LeftMenuLayout): String =
            enabledTabKeys(layout).firstOrNull() ?: LeftMenuTabField.SETTINGS.id

        fun enforceLocked(rows: List<Row>): List<Row> =
            rows.map { row ->
                if (row.field.locked) row.copy(enabled = true) else row
            }

        private fun defaultOrder(): List<LeftMenuTabField> = LeftMenuTabField.defaultOrder()

        fun parseSelectedTabKey(raw: String?): String {
            if (raw.isNullOrBlank()) return SettingsManager.MAIN_SCREEN_TAB_KEY
            if (raw == SettingsManager.MAIN_SCREEN_TAB_KEY) return raw
            if (raw == SettingsManager.UPDATE_TAB_KEY) return raw
            return if (LeftMenuTabField.fromId(raw) != null) raw else SettingsManager.MAIN_SCREEN_TAB_KEY
        }

        fun resolveSelectedTab(rawKey: String, layout: LeftMenuLayout): String {
            val key = parseSelectedTabKey(rawKey)
            if (key == SettingsManager.MAIN_SCREEN_TAB_KEY || key == SettingsManager.UPDATE_TAB_KEY) {
                return key
            }
            return if (key in enabledTabKeys(layout)) key else firstVisibleTabKey(layout)
        }

        fun isSidebarTabEnabled(tabKey: String, layout: LeftMenuLayout): Boolean =
            tabKey == SettingsManager.UPDATE_TAB_KEY ||
                (tabKey != SettingsManager.MAIN_SCREEN_TAB_KEY && tabKey in enabledTabKeys(layout))
    }
}
