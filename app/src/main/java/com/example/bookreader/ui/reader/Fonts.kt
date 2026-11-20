package com.example.bookreader.ui.reader

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import com.example.bookreader.R
import com.example.bookreader.data.settings.FontFamilyOpt

/** Шрифты для Compose (интерфейс, режим ленты). */
object AppFonts {
    val Head = FontFamily(
        Font(R.font.philosopher, FontWeight.Normal),
        Font(R.font.philosopher_bold, FontWeight.Bold)
    )
    val Garamond = FontFamily(
        Font(R.font.eb_garamond, FontWeight.Normal),
        Font(R.font.eb_garamond, FontWeight.Bold)
    )
    val OldStandard = FontFamily(
        Font(R.font.old_standard, FontWeight.Normal),
        Font(R.font.old_standard_bold, FontWeight.Bold)
    )
    val Alegreya = FontFamily(
        Font(R.font.alegreya, FontWeight.Normal),
        Font(R.font.alegreya, FontWeight.Bold)
    )

    fun compose(opt: FontFamilyOpt): FontFamily = when (opt) {
        FontFamilyOpt.GARAMOND -> Garamond
        FontFamilyOpt.OLD_STANDARD -> OldStandard
        FontFamilyOpt.ALEGREYA -> Alegreya
        FontFamilyOpt.SERIF -> FontFamily.Serif
        FontFamilyOpt.SANS -> FontFamily.SansSerif
        FontFamilyOpt.MONO -> FontFamily.Monospace
    }
}

/** Typeface для android.text.StaticLayout (постраничная вёрстка). */
class FontSet(private val ctx: Context) {
    private fun load(id: Int, fallback: Typeface): Typeface =
        runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: fallback

    private val garamond = load(R.font.eb_garamond, Typeface.SERIF)
    private val oldStd = load(R.font.old_standard, Typeface.SERIF)
    private val oldStdBold = load(R.font.old_standard_bold, Typeface.create(Typeface.SERIF, Typeface.BOLD))
    private val alegreya = load(R.font.alegreya, Typeface.SERIF)
    val heading: Typeface = load(R.font.philosopher_bold, Typeface.create(Typeface.SERIF, Typeface.BOLD))

    fun body(opt: FontFamilyOpt, bold: Boolean): Typeface {
        if (opt == FontFamilyOpt.OLD_STANDARD && bold) return oldStdBold
        val base = when (opt) {
            FontFamilyOpt.GARAMOND -> garamond
            FontFamilyOpt.OLD_STANDARD -> oldStd
            FontFamilyOpt.ALEGREYA -> alegreya
            FontFamilyOpt.SERIF -> Typeface.SERIF
            FontFamilyOpt.SANS -> Typeface.SANS_SERIF
            FontFamilyOpt.MONO -> Typeface.MONOSPACE
        }
        return if (bold) Typeface.create(base, Typeface.BOLD) else base
    }
}
