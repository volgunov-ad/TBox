package vad.dashing.tbox.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import vad.dashing.tbox.R

enum class UiIconCategory {
    NAVIGATION,
    VEHICLE,
    CLIMATE,
    SEAT,
    CONNECTIVITY,
    LOCATION,
    MEDIA,
    STEPPER,
}

/**
 * A localizable string source for an icon catalog entry.
 *
 * [variantLabel] distinguishes state-specific visuals until dedicated format resources are added.
 */
data class UiIconTextSource(
    @StringRes val resourceId: Int,
    val variantLabel: String = "",
)

data class UiIconCatalogEntry(
    val key: String,
    val category: UiIconCategory,
    val nameSource: UiIconTextSource,
    val descriptionSource: UiIconTextSource,
    @DrawableRes val drawableRes: Int? = null,
)

/**
 * Stable semantic keys for every replaceable icon rendered by the left menu and dashboard widgets.
 *
 * Keys describe the visual/state rather than a widget instance, so one sidecar override is shared
 * everywhere that visual is used.
 */
object UiIconCatalog {
    const val MENU_OPEN = "navigation.menu.open"
    const val MENU_CLOSE = "navigation.menu.close"
    const val MENU_HOME = "navigation.home"
    const val MENU_UPDATE = "navigation.update"

    private val catalogEntries: List<UiIconCatalogEntry> = buildList {
        fun addIcon(
            key: String,
            category: UiIconCategory,
            @StringRes nameRes: Int,
            @DrawableRes drawableRes: Int? = null,
            variant: String = "",
            @StringRes descriptionRes: Int = nameRes,
        ) {
            add(
                UiIconCatalogEntry(
                    key = key,
                    category = category,
                    nameSource = UiIconTextSource(nameRes, variant),
                    descriptionSource = UiIconTextSource(descriptionRes, variant),
                    drawableRes = drawableRes,
                ),
            )
        }

        addIcon(MENU_OPEN, UiIconCategory.NAVIGATION, R.string.menu_show, R.drawable.menu_icon_open)
        addIcon(MENU_CLOSE, UiIconCategory.NAVIGATION, R.string.menu_hide, R.drawable.menu_icon_close)
        addIcon(MENU_HOME, UiIconCategory.NAVIGATION, R.string.menu_navigate_home, R.drawable.ic_menu_home)
        addIcon(MENU_UPDATE, UiIconCategory.NAVIGATION, R.string.update_menu_available, R.drawable.ic_menu_update)
        addIcon("menu.tab.modem", UiIconCategory.NAVIGATION, R.string.tab_modem, R.drawable.menu_icon_modem)
        addIcon("menu.tab.at_commands", UiIconCategory.NAVIGATION, R.string.tab_at_commands, R.drawable.menu_icon_at)
        addIcon("menu.tab.geoposition", UiIconCategory.NAVIGATION, R.string.tab_geoposition)
        addIcon("menu.tab.esp_companion", UiIconCategory.NAVIGATION, R.string.tab_esp_companion)
        addIcon("menu.tab.car_data", UiIconCategory.NAVIGATION, R.string.tab_car_data)
        addIcon("menu.tab.trips", UiIconCategory.NAVIGATION, R.string.tab_trips)
        addIcon("menu.tab.refuels", UiIconCategory.NAVIGATION, R.string.tab_refuels, R.drawable.ic_menu_refuels)
        addIcon("menu.tab.automations", UiIconCategory.NAVIGATION, R.string.tab_automations, R.drawable.ic_menu_automations)
        addIcon("menu.tab.settings", UiIconCategory.NAVIGATION, R.string.tab_settings)
        addIcon(
            "menu.tab.floating_panels_settings",
            UiIconCategory.NAVIGATION,
            R.string.tab_floating_panels_settings,
            R.drawable.ic_tab_floating_panels_settings,
        )
        addIcon("menu.tab.themes", UiIconCategory.NAVIGATION, R.string.tab_themes, R.drawable.ic_menu_themes)
        addIcon("menu.tab.logs", UiIconCategory.NAVIGATION, R.string.tab_logs, R.drawable.menu_icon_log)
        addIcon("menu.tab.info", UiIconCategory.NAVIGATION, R.string.tab_info)
        addIcon("menu.tab.can", UiIconCategory.NAVIGATION, R.string.tab_can, R.drawable.menu_icon_data)
        addIcon("menu.tab.widgets", UiIconCategory.NAVIGATION, R.string.tab_widgets, R.drawable.menu_icon_widgets)
        addIcon(
            "menu.tab.main_screen_settings",
            UiIconCategory.NAVIGATION,
            R.string.tab_main_screen_settings,
            R.drawable.ic_tab_main_screen_settings,
        )
        addIcon(
            "menu.tab.car_settings",
            UiIconCategory.NAVIGATION,
            R.string.tab_car_settings,
            R.drawable.ic_tab_car_settings,
        )

        addIcon(
            "dashboard.vehicle.steering_wheel_heat",
            UiIconCategory.VEHICLE,
            R.string.data_title_steering_wheel_heat_widget,
            R.drawable.ic_widget_steering_wheel_heat,
        )
        addIcon("dashboard.vehicle.trunk", UiIconCategory.VEHICLE, R.string.data_title_trunk_door_widget, R.drawable.ic_widget_trunk)
        addIcon("dashboard.vehicle.rear_fog", UiIconCategory.VEHICLE, R.string.data_title_rear_fog_widget, R.drawable.ic_widget_rear_fog)
        addIcon(
            "dashboard.vehicle.rear_window_mirrors_defrost",
            UiIconCategory.VEHICLE,
            R.string.data_title_rear_window_mirrors_defrost_widget,
            R.drawable.ic_widget_rear_window_mirrors_defrost,
        )
        addIcon(
            "dashboard.vehicle.parking_radar",
            UiIconCategory.VEHICLE,
            R.string.data_title_parking_radar_widget,
            R.drawable.ic_widget_parking_radar,
        )
        addIcon("dashboard.vehicle.esp_off", UiIconCategory.VEHICLE, R.string.data_title_esp_off_widget, R.drawable.ic_widget_esp_off)
        addIcon("dashboard.vehicle.avh", UiIconCategory.VEHICLE, R.string.data_title_avh_widget, R.drawable.ic_widget_avh)
        addIcon("dashboard.vehicle.hdc", UiIconCategory.VEHICLE, R.string.data_title_hdc_widget, R.drawable.ic_widget_hdc)
        addIcon(
            "dashboard.vehicle.front_windscreen_heat",
            UiIconCategory.VEHICLE,
            R.string.data_title_front_windscreen_heat_widget,
            R.drawable.ic_widget_front_windscreen_heat,
        )
        addIcon(
            "dashboard.vehicle.mirror.adjust",
            UiIconCategory.VEHICLE,
            R.string.data_title_mirror_adjust_mode_widget,
            R.drawable.ic_widget_mirror_adjust,
        )
        addIcon(
            "dashboard.vehicle.mirror.fold",
            UiIconCategory.VEHICLE,
            R.string.data_title_mirror_fold_widget,
            R.drawable.ic_widget_mirror_fold,
        )
        addIcon(
            "dashboard.vehicle.cruise.acc",
            UiIconCategory.VEHICLE,
            R.string.data_title_acc_cruise_widget,
            R.drawable.ic_widget_acc_cruise,
        )
        addIcon("dashboard.vehicle.adas.ldw", UiIconCategory.VEHICLE, R.string.data_title_ldw_widget, R.drawable.ic_widget_label_ldw)
        addIcon("dashboard.vehicle.adas.lka", UiIconCategory.VEHICLE, R.string.data_title_lka_widget, R.drawable.ic_widget_label_lka)
        addIcon(
            "dashboard.vehicle.adas.tja_ica",
            UiIconCategory.VEHICLE,
            R.string.data_title_tja_ica_widget,
            R.drawable.ic_widget_label_tja_ica,
        )
        addIcon("dashboard.vehicle.adas.hma", UiIconCategory.VEHICLE, R.string.data_title_hma_widget, R.drawable.ic_widget_label_hma)

        listOf(
            Triple("eco", R.drawable.ic_widget_label_eco, "ECO"),
            Triple("normal", R.drawable.ic_widget_label_nor, "NOR"),
            Triple("sport", R.drawable.ic_widget_label_spt, "SPT"),
            Triple("sand", R.drawable.ic_widget_label_sand, "SAND"),
            Triple("mud", R.drawable.ic_widget_label_mud, "MUD"),
            Triple("snow", R.drawable.ic_widget_label_snow, "SNOW"),
        ).forEach { (state, drawable, label) ->
            addIcon(
                "dashboard.vehicle.drive_mode.$state",
                UiIconCategory.VEHICLE,
                R.string.data_title_drive_mode_widget,
                drawable,
                label,
            )
        }
        listOf(
            Triple("auto", R.drawable.ic_widget_label_auto, "AUTO"),
            Triple("park", R.drawable.ic_widget_label_park, "PARK"),
            Triple("low", R.drawable.ic_widget_label_low, "LOW"),
            Triple("off", R.drawable.ic_widget_label_off, "OFF"),
        ).forEach { (state, drawable, label) ->
            addIcon(
                "dashboard.vehicle.headlight.$state",
                UiIconCategory.VEHICLE,
                R.string.data_title_headlight_mode_cycle_widget,
                drawable,
                label,
            )
        }
        listOf(
            Triple("off", R.drawable.ic_widget_wiper_windshield, "OFF"),
            Triple("intermittent", R.drawable.ic_widget_wiper_windshield_int, "INT"),
            Triple("low", R.drawable.ic_widget_wiper_windshield_low, "LOW"),
            Triple("high", R.drawable.ic_widget_wiper_windshield_high, "HIGH"),
        ).forEach { (state, drawable, label) ->
            addIcon(
                "dashboard.vehicle.wiper.$state",
                UiIconCategory.VEHICLE,
                R.string.data_title_wiper_maintenance_widget,
                drawable,
                label,
            )
        }
        listOf(
            Triple("light.manual", R.drawable.ic_widget_day_night_light_mode, "LIGHT"),
            Triple("light.auto", R.drawable.ic_widget_day_night_light_mode_auto, "LIGHT AUTO"),
            Triple("dark.manual", R.drawable.ic_widget_day_night_dark_mode, "DARK"),
            Triple("dark.auto", R.drawable.ic_widget_day_night_dark_mode_auto, "DARK AUTO"),
        ).forEach { (state, drawable, label) ->
            addIcon(
                "dashboard.theme.$state",
                UiIconCategory.NAVIGATION,
                R.string.data_title_day_night_theme_widget,
                drawable,
                label,
            )
        }

        listOf(
            Triple("ac", R.drawable.ic_widget_hvac_ac, "A/C"),
            Triple("ac_max", R.drawable.ic_widget_hvac_ac_max, "A/C MAX"),
            Triple("ac_clean_when_locked", R.drawable.ic_widget_hvac_ac_clean_when_locked, "A/C CLEAN"),
            Triple("auto", R.drawable.ic_widget_hvac_auto, "AUTO"),
            Triple("recirculation", R.drawable.ic_widget_hvac_air_recirculation, "RECIRCULATION"),
            Triple("defrost_front", R.drawable.ic_widget_hvac_defroster_front, "DEFROST"),
            Triple("sync", R.drawable.ic_widget_hvac_sync, "SYNC"),
            Triple("fan", R.drawable.ic_widget_hvac_fan, "FAN"),
            Triple("blow.face", R.drawable.ic_widget_hvac_blow_face, "FACE"),
            Triple("blow.foot", R.drawable.ic_widget_hvac_blow_foot, "FOOT"),
            Triple("blow.face_foot", R.drawable.ic_widget_hvac_blow_face_foot, "FACE + FOOT"),
            Triple("blow.defrost_foot", R.drawable.ic_widget_hvac_blow_defrost_foot, "DEFROST + FOOT"),
            Triple("mode.eco", R.drawable.ic_widget_hvac_mode_eco, "ECO"),
            Triple("mode.comfort", R.drawable.ic_widget_hvac_mode_comfort, "COMFORT"),
            Triple("mode.strong", R.drawable.ic_widget_hvac_mode_strong, "STRONG"),
        ).forEach { (state, drawable, label) ->
            addIcon(
                "dashboard.hvac.$state",
                UiIconCategory.CLIMATE,
                R.string.data_title_hvac_custom_mode_cycle_widget,
                drawable,
                label,
            )
        }

        listOf(
            Triple("base", R.drawable.ic_widget_seat, "SEAT"),
            Triple("rear_left", R.drawable.ic_widget_seat_back_left, "REAR LEFT"),
            Triple("rear_right", R.drawable.ic_widget_seat_back_right, "REAR RIGHT"),
            Triple("heat.1", R.drawable.ic_widget_seat_heat_1, "HEAT 1"),
            Triple("heat.2", R.drawable.ic_widget_seat_heat_2, "HEAT 2"),
            Triple("heat.3", R.drawable.ic_widget_seat_heat_3, "HEAT 3"),
            Triple("vent.0", R.drawable.ic_widget_seat_vent_0, "VENT 0"),
            Triple("vent.1", R.drawable.ic_widget_seat_vent_1, "VENT 1"),
            Triple("vent.2", R.drawable.ic_widget_seat_vent_2, "VENT 2"),
            Triple("vent.3", R.drawable.ic_widget_seat_vent_3, "VENT 3"),
        ).forEach { (state, drawable, label) ->
            addIcon(
                "dashboard.seat.$state",
                UiIconCategory.SEAT,
                R.string.data_title_front_left_seat_heat_vent_widget,
                drawable,
                label,
            )
        }

        val oldNetworkTypes = listOf(
            "2g" to "e",
            "3g" to "3g",
            "4g" to "4g",
            "cellular" to "",
        )
        oldNetworkTypes.forEach { (typeKey, drawableToken) ->
            (0..4).forEach { level ->
                val lightDrawable = when ("$drawableToken:$level") {
                    "e:0" -> R.drawable.ic_signal_e_cellular_0_sharp_outlined
                    "e:1" -> R.drawable.ic_signal_e_cellular_1_sharp_outlined
                    "e:2" -> R.drawable.ic_signal_e_cellular_2_sharp_outlined
                    "e:3" -> R.drawable.ic_signal_e_cellular_3_sharp_outlined
                    "e:4" -> R.drawable.ic_signal_e_cellular_4_sharp_outlined
                    "3g:0" -> R.drawable.ic_signal_3g_cellular_0_sharp_outlined
                    "3g:1" -> R.drawable.ic_signal_3g_cellular_1_sharp_outlined
                    "3g:2" -> R.drawable.ic_signal_3g_cellular_2_sharp_outlined
                    "3g:3" -> R.drawable.ic_signal_3g_cellular_3_sharp_outlined
                    "3g:4" -> R.drawable.ic_signal_3g_cellular_4_sharp_outlined
                    "4g:0" -> R.drawable.ic_signal_4g_cellular_0_sharp_outlined
                    "4g:1" -> R.drawable.ic_signal_4g_cellular_1_sharp_outlined
                    "4g:2" -> R.drawable.ic_signal_4g_cellular_2_sharp_outlined
                    "4g:3" -> R.drawable.ic_signal_4g_cellular_3_sharp_outlined
                    "4g:4" -> R.drawable.ic_signal_4g_cellular_4_sharp_outlined
                    ":0" -> R.drawable.ic_signal_cellular_0_sharp_outlined
                    ":1" -> R.drawable.ic_signal_cellular_1_sharp_outlined
                    ":2" -> R.drawable.ic_signal_cellular_2_sharp_outlined
                    ":3" -> R.drawable.ic_signal_cellular_3_sharp_outlined
                    else -> R.drawable.ic_signal_cellular_4_sharp_outlined
                }
                val darkDrawable = when ("$drawableToken:$level") {
                    "e:0" -> R.drawable.ic_signal_e_cellular_0_sharp_outlined_dark
                    "e:1" -> R.drawable.ic_signal_e_cellular_1_sharp_outlined_dark
                    "e:2" -> R.drawable.ic_signal_e_cellular_2_sharp_outlined_dark
                    "e:3" -> R.drawable.ic_signal_e_cellular_3_sharp_outlined_dark
                    "e:4" -> R.drawable.ic_signal_e_cellular_4_sharp_outlined_dark
                    "3g:0" -> R.drawable.ic_signal_3g_cellular_0_sharp_outlined_dark
                    "3g:1" -> R.drawable.ic_signal_3g_cellular_1_sharp_outlined_dark
                    "3g:2" -> R.drawable.ic_signal_3g_cellular_2_sharp_outlined_dark
                    "3g:3" -> R.drawable.ic_signal_3g_cellular_3_sharp_outlined_dark
                    "3g:4" -> R.drawable.ic_signal_3g_cellular_4_sharp_outlined_dark
                    "4g:0" -> R.drawable.ic_signal_4g_cellular_0_sharp_outlined_dark
                    "4g:1" -> R.drawable.ic_signal_4g_cellular_1_sharp_outlined_dark
                    "4g:2" -> R.drawable.ic_signal_4g_cellular_2_sharp_outlined_dark
                    "4g:3" -> R.drawable.ic_signal_4g_cellular_3_sharp_outlined_dark
                    "4g:4" -> R.drawable.ic_signal_4g_cellular_4_sharp_outlined_dark
                    ":0" -> R.drawable.ic_signal_cellular_0_sharp_outlined_dark
                    ":1" -> R.drawable.ic_signal_cellular_1_sharp_outlined_dark
                    ":2" -> R.drawable.ic_signal_cellular_2_sharp_outlined_dark
                    ":3" -> R.drawable.ic_signal_cellular_3_sharp_outlined_dark
                    else -> R.drawable.ic_signal_cellular_4_sharp_outlined_dark
                }
                addIcon(
                    "dashboard.net.legacy.$typeKey.level_$level.light",
                    UiIconCategory.CONNECTIVITY,
                    R.string.data_title_net_widget,
                    lightDrawable,
                    "$typeKey $level LIGHT",
                    R.string.dashboard_net_content_desc,
                )
                addIcon(
                    "dashboard.net.legacy.$typeKey.level_$level.dark",
                    UiIconCategory.CONNECTIVITY,
                    R.string.data_title_net_widget,
                    darkDrawable,
                    "$typeKey $level DARK",
                    R.string.dashboard_net_content_desc,
                )
            }
        }
        listOf(
            Triple("level_0", R.drawable.signal_0, "SIGNAL 0"),
            Triple("level_1", R.drawable.signal_1, "SIGNAL 1"),
            Triple("level_2", R.drawable.signal_2, "SIGNAL 2"),
            Triple("level_3", R.drawable.signal_3, "SIGNAL 3"),
            Triple("level_4", R.drawable.signal_4, "SIGNAL 4"),
            Triple("type_2g", R.drawable.signal_2g, "2G"),
            Triple("type_3g", R.drawable.signal_3g, "3G"),
            Triple("type_4g", R.drawable.signal_4g, "4G"),
        ).forEach { (state, drawable, label) ->
            addIcon(
                "dashboard.net.$state",
                UiIconCategory.CONNECTIVITY,
                R.string.data_title_net_widget_new,
                drawable,
                label,
                R.string.dashboard_net_content_desc,
            )
        }

        listOf(
            Triple("none", R.drawable.loc_0_err, "NONE"),
            Triple("lost", R.drawable.loc_0_warn, "LOST"),
            Triple("retaining", R.drawable.loc_0_retain, "RETAINING"),
            Triple("live", R.drawable.loc_0_ok, "LIVE"),
        ).forEach { (state, drawable, label) ->
            addIcon(
                "dashboard.location.$state",
                UiIconCategory.LOCATION,
                R.string.data_title_loc_widget,
                drawable,
                label,
                R.string.dashboard_loc_content_desc,
            )
        }

        listOf(
            Triple("play", R.drawable.play, "PLAY"),
            Triple("pause", R.drawable.pause, "PAUSE"),
            Triple("previous", R.drawable.skip_previous, "PREVIOUS"),
            Triple("next", R.drawable.next_track, "NEXT"),
            Triple("like.outline", R.drawable.media_like_outline, "LIKE"),
            Triple("like.filled", R.drawable.media_like_filled, "LIKED"),
            Triple("volume.mute", R.drawable.ic_media_volume_mute, "MUTE"),
            Triple("volume.audio", R.drawable.ic_media_volume_audio, "AUDIO"),
            Triple("player.bluetooth", R.drawable.player_bluetooth, "BLUETOOTH"),
            Triple("player.unknown", R.drawable.player_unknown, "UNKNOWN PLAYER"),
        ).forEach { (state, drawable, label) ->
            addIcon(
                "dashboard.media.$state",
                UiIconCategory.MEDIA,
                R.string.data_title_music_widget,
                drawable,
                label,
            )
        }

        listOf(
            Triple("increase", R.drawable.ic_media_volume_plus, "PLUS"),
            Triple("decrease", R.drawable.ic_media_volume_minus, "MINUS"),
            Triple("up", R.drawable.ic_stepper_arrow_up, "UP"),
            Triple("down", R.drawable.ic_stepper_arrow_down, "DOWN"),
            Triple("left", R.drawable.ic_stepper_arrow_left, "LEFT"),
            Triple("right", R.drawable.ic_stepper_arrow_right, "RIGHT"),
        ).forEach { (state, drawable, label) ->
            addIcon(
                "dashboard.stepper.$state",
                UiIconCategory.STEPPER,
                R.string.data_title_media_volume_widget_horizontal,
                drawable,
                label,
            )
        }
    }

    val entries: List<UiIconCatalogEntry> = catalogEntries

    private val byKey = entries.associateBy(UiIconCatalogEntry::key)
    private val byDrawable = entries.mapNotNull { entry ->
        entry.drawableRes?.let { it to entry }
    }.toMap()

    fun entry(key: String): UiIconCatalogEntry? = byKey[key]

    fun keyForDrawable(@DrawableRes drawableRes: Int): String? = byDrawable[drawableRes]?.key
}
