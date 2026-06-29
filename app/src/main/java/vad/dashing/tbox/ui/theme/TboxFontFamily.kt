package vad.dashing.tbox.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import vad.dashing.tbox.R

/** App font presets: system families and bundled Google Fonts. */
enum class TboxFontFamily(val id: Int, val slug: String) {
    Default(0, "default"),
    SansSerif(1, "sans_serif"),
    Serif(2, "serif"),
    Monospace(3, "monospace"),
    Cabin(5, "cabin"),
    Nunito(6, "nunito"),
    Roboto(7, "roboto"),
    ;

    fun toComposeFontFamily(): FontFamily = when (this) {
        Default -> FontFamily.Default
        SansSerif -> FontFamily.SansSerif
        Serif -> FontFamily.Serif
        Monospace -> FontFamily.Monospace
        Cabin -> Bundled.cabin
        Nunito -> Bundled.nunito
        Roboto -> Bundled.roboto
    }

    companion object {
        val all: List<TboxFontFamily> = entries

        fun fromId(id: Int): TboxFontFamily =
            all.firstOrNull { it.id == id } ?: Default

        fun fromSlug(slug: String?): TboxFontFamily? =
            slug?.takeIf { it.isNotBlank() }?.let { s -> all.firstOrNull { it.slug == s } }
    }

    private object Bundled {
        val cabin = fontFamilyWithMedium(
            regular = R.font.cabin_regular,
            medium = R.font.cabin_medium,
            semiBold = R.font.cabin_semibold,
            bold = R.font.cabin_bold,
        )
        val nunito = fontFamilyWithMedium(
            regular = R.font.nunito_regular,
            medium = R.font.nunito_medium,
            semiBold = R.font.nunito_semibold,
            bold = R.font.nunito_bold,
        )
        val roboto = fontFamilyWithMedium(
            regular = R.font.roboto_regular,
            medium = R.font.roboto_medium,
            semiBold = R.font.roboto_semibold,
            bold = R.font.roboto_bold,
        )
    }
}

private fun fontFamilyWithMedium(
    regular: Int,
    medium: Int,
    semiBold: Int,
    bold: Int,
): FontFamily = FontFamily(
    Font(regular, FontWeight.Normal),
    Font(medium, FontWeight.Medium),
    Font(semiBold, FontWeight.SemiBold),
    Font(bold, FontWeight.Bold),
)

fun resolveFontFamily(fontFamilyId: Int): FontFamily =
    TboxFontFamily.fromId(fontFamilyId).toComposeFontFamily()
