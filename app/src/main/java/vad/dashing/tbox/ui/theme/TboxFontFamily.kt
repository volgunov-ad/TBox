package vad.dashing.tbox.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import vad.dashing.tbox.R

/** App font presets: system families and bundled Google Fonts. */
enum class TboxFontFamily(val id: Int, val slug: String) {
    Default(0, "default"),
    SansSerif(1, "sans_serif"),
    Serif(2, "serif"),
    Monospace(3, "monospace"),
    Roboto(4, "roboto"),
    Inter(5, "inter"),
    Montserrat(6, "montserrat"),
    CrimsonText(7, "crimson_text"),
    Cabin(8, "cabin"),
    Nunito(9, "nunito"),
    ;

    fun toComposeFontFamily(): FontFamily = when (this) {
        Default -> FontFamily.Default
        SansSerif -> FontFamily.SansSerif
        Serif -> FontFamily.Serif
        Monospace -> FontFamily.Monospace
        Roboto -> Bundled.roboto
        Inter -> Bundled.inter
        Montserrat -> Bundled.montserrat
        CrimsonText -> Bundled.crimsonText
        Cabin -> Bundled.cabin
        Nunito -> Bundled.nunito
    }

    companion object {
        val all: List<TboxFontFamily> = entries

        fun fromId(id: Int): TboxFontFamily =
            all.firstOrNull { it.id == id } ?: Default

        fun fromSlug(slug: String?): TboxFontFamily? =
            slug?.takeIf { it.isNotBlank() }?.let { s -> all.firstOrNull { it.slug == s } }
    }

    private object Bundled {
        val roboto = FontFamily(Font(R.font.roboto_regular))
        val inter = FontFamily(Font(R.font.inter_regular))
        val montserrat = FontFamily(Font(R.font.montserrat_regular))
        val crimsonText = FontFamily(Font(R.font.crimson_text_regular))
        val cabin = FontFamily(Font(R.font.cabin_regular))
        val nunito = FontFamily(Font(R.font.nunito_regular))
    }
}

fun resolveFontFamily(fontFamilyId: Int): FontFamily =
    TboxFontFamily.fromId(fontFamilyId).toComposeFontFamily()
