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
    CrimsonText(4, "crimson_text"),
    Cabin(5, "cabin"),
    Nunito(6, "nunito"),
    ;

    fun toComposeFontFamily(): FontFamily = when (this) {
        Default -> FontFamily.Default
        SansSerif -> FontFamily.SansSerif
        Serif -> FontFamily.Serif
        Monospace -> FontFamily.Monospace
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
        val crimsonText = FontFamily(Font(R.font.crimson_text_regular))
        val cabin = FontFamily(Font(R.font.cabin_regular))
        val nunito = FontFamily(Font(R.font.nunito_regular))
    }
}

fun resolveFontFamily(fontFamilyId: Int): FontFamily =
    TboxFontFamily.fromId(fontFamilyId).toComposeFontFamily()
