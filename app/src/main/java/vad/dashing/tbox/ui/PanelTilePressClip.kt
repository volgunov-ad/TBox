package vad.dashing.tbox.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import vad.dashing.tbox.normalizeWidgetShape

/**
 * Corner shape shared by panel-tile Card background and press Indication.
 * Radius `0` is a sharp rectangle (same as the visible tile).
 *
 * Cell insets come from [WidgetCellContentPadding] around the scaffold; the press
 * surface fills that inner box, so padding/gaps are respected without a second inset.
 *
 * Put `combinedClickable` **inside** the Card content so Material elevation shadows
 * stay outside the clip; Card/Surface already clips children to this shape.
 */
fun panelTileCornerShape(shapeDp: Dp): RoundedCornerShape = RoundedCornerShape(shapeDp)

/**
 * Normalized corner radius (dp int) for tile background + press clip.
 * Same rules as [normalizeWidgetShape] / widget `shape` setting.
 */
fun panelTilePressClipRadiusDp(rawShape: Int): Int = normalizeWidgetShape(rawShape)
