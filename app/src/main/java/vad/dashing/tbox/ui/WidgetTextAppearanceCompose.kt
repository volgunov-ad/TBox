package vad.dashing.tbox.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import vad.dashing.tbox.WIDGET_FONT_WEIGHT_BOLD
import vad.dashing.tbox.WIDGET_FONT_WEIGHT_NORMAL
import vad.dashing.tbox.WIDGET_TEXT_ALIGN_END
import vad.dashing.tbox.WIDGET_TEXT_ALIGN_START
import vad.dashing.tbox.normalizeWidgetFontWeight
import vad.dashing.tbox.normalizeWidgetTextAlign

fun widgetTextAlignToCompose(align: Int): TextAlign = when (normalizeWidgetTextAlign(align)) {
    WIDGET_TEXT_ALIGN_START -> TextAlign.Start
    WIDGET_TEXT_ALIGN_END -> TextAlign.End
    else -> TextAlign.Center
}

fun widgetFontWeightToCompose(weight: Int): FontWeight = when (normalizeWidgetFontWeight(weight)) {
    WIDGET_FONT_WEIGHT_NORMAL -> FontWeight.Normal
    WIDGET_FONT_WEIGHT_BOLD -> FontWeight.Bold
    else -> FontWeight.SemiBold
}

fun widgetColumnHorizontalAlignment(textAlign: TextAlign): Alignment.Horizontal = when (textAlign) {
    TextAlign.Start, TextAlign.Left -> Alignment.Start
    TextAlign.End, TextAlign.Right -> Alignment.End
    else -> Alignment.CenterHorizontally
}
