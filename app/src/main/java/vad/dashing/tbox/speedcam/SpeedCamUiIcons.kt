package vad.dashing.tbox.speedcam

import androidx.annotation.DrawableRes
import vad.dashing.tbox.R

/**
 * Semantic UI-icon keys and default drawables for [SpeedCamCategory] / direction arrows.
 * Overrides go through «Настроить иконки…» / theme `uiIcons`.
 */
object SpeedCamUiIcons {
    const val KEY_FIXED = "dashboard.speedcam.fixed"
    const val KEY_TRAFFIC_LIGHT = "dashboard.speedcam.traffic_light"
    const val KEY_SECTION = "dashboard.speedcam.section"
    const val KEY_MOBILE = "dashboard.speedcam.mobile"
    const val KEY_POLICE_POST = "dashboard.speedcam.police_post"
    const val KEY_RAILWAY = "dashboard.speedcam.railway"
    const val KEY_DUMMY = "dashboard.speedcam.dummy"
    const val KEY_OTHER = "dashboard.speedcam.other"
    const val KEY_ARROW_SAME = "dashboard.speedcam.arrow.same"
    const val KEY_ARROW_ONCOMING = "dashboard.speedcam.arrow.oncoming"

    data class IconRef(val key: String, @DrawableRes val drawableRes: Int)

    fun forCategory(category: SpeedCamCategory): IconRef = when (category) {
        SpeedCamCategory.FIXED -> IconRef(KEY_FIXED, R.drawable.ic_widget_speed_cam_fixed)
        SpeedCamCategory.TRAFFIC_LIGHT ->
            IconRef(KEY_TRAFFIC_LIGHT, R.drawable.ic_widget_speed_cam_traffic_light)
        SpeedCamCategory.SECTION -> IconRef(KEY_SECTION, R.drawable.ic_widget_speed_cam_section)
        SpeedCamCategory.MOBILE -> IconRef(KEY_MOBILE, R.drawable.ic_widget_speed_cam_mobile)
        SpeedCamCategory.POLICE_POST ->
            IconRef(KEY_POLICE_POST, R.drawable.ic_widget_speed_cam_police_post)
        SpeedCamCategory.RAILWAY -> IconRef(KEY_RAILWAY, R.drawable.ic_widget_speed_cam_railway)
        SpeedCamCategory.DUMMY -> IconRef(KEY_DUMMY, R.drawable.ic_widget_speed_cam_dummy)
        SpeedCamCategory.OTHER -> IconRef(KEY_OTHER, R.drawable.ic_widget_speed_cam_other)
    }

    fun forRelative(relative: SpeedCamRelativeDirection): IconRef = when (relative) {
        SpeedCamRelativeDirection.SAME ->
            IconRef(KEY_ARROW_SAME, R.drawable.ic_widget_speed_cam_arrow_same)
        SpeedCamRelativeDirection.ONCOMING ->
            IconRef(KEY_ARROW_ONCOMING, R.drawable.ic_widget_speed_cam_arrow_oncoming)
    }

    val allCategoryKeys: List<String> = listOf(
        KEY_FIXED,
        KEY_TRAFFIC_LIGHT,
        KEY_SECTION,
        KEY_MOBILE,
        KEY_POLICE_POST,
        KEY_RAILWAY,
        KEY_DUMMY,
        KEY_OTHER,
    )

    val allKeys: List<String> = allCategoryKeys + listOf(KEY_ARROW_SAME, KEY_ARROW_ONCOMING)
}
