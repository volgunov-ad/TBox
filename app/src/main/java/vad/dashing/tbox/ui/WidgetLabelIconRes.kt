package vad.dashing.tbox.ui

import androidx.annotation.DrawableRes
import vad.dashing.tbox.DriveModeWidgetOption
import vad.dashing.tbox.HeadlightMode
import vad.dashing.tbox.R

@DrawableRes
fun driveModeWidgetLabelIconRes(label: String): Int = when (label) {
    "ECO" -> R.drawable.ic_widget_hvac_mode_eco
    "NOR" -> R.drawable.ic_widget_label_nor
    "SPT" -> R.drawable.ic_widget_label_spt
    "SAND" -> R.drawable.ic_widget_label_sand
    "MUD" -> R.drawable.ic_widget_label_mud
    "SNOW" -> R.drawable.ic_widget_label_snow
    else -> R.drawable.ic_widget_label_nor
}

@DrawableRes
fun driveModeWidgetLabelIconRes(option: DriveModeWidgetOption): Int =
    driveModeWidgetLabelIconRes(option.widgetLabel)

@DrawableRes
fun headlightModeWidgetLabelIconRes(mode: HeadlightMode): Int = when (mode) {
    HeadlightMode.Auto -> R.drawable.ic_widget_label_auto
    HeadlightMode.Park -> R.drawable.ic_widget_label_park
    HeadlightMode.Low -> R.drawable.ic_widget_label_low
    HeadlightMode.Off -> R.drawable.ic_widget_label_off
}
