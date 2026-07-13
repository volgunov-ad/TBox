package vad.dashing.tbox.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Active-state accent colors from stock head-unit WT theme
 * ([wt_primary_main_color], [wt_secondary_main_color] in ACSettings / CarSettings).
 *
 * Blue: climate toggles (AC auto, blow modes, sync, recirculation, …).
 * Orange: heat / defrost toggles (steering wheel, seats, front & rear defrost, …).
 */
object WidgetActiveColors {
  /** Stock `wt_primary_main_color` (#FF2180F3). */
  val Primary = Color(0xFF2180F3)

  /** Stock `wt_secondary_main_color` (#FFF3A721). */
  val Secondary = Color(0xFFF3A721)
}
