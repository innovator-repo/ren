package app.ren.designsystem

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import app.ren.R

/** Interface voice. */
val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

/** Reading voice for English prose: mnemonic stories and translations. */
val Newsreader = FontFamily(
    Font(R.font.newsreader_regular, FontWeight.Normal),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold),
)

/** Japanese voice (Mincho) for kanji, vocabulary and story text, subset to Ren's own text by tools/fonts/subset_jp.py. */
val NotoSerifJp = FontFamily(
    Font(R.font.noto_serif_jp_regular, FontWeight.Normal),
    Font(R.font.noto_serif_jp_semibold, FontWeight.SemiBold),
)

/** Japanese glyph forms everywhere: without this, Han characters fall back to Chinese shapes on non-JP devices. */
val JaLocale = LocaleList("ja-JP")

/** Phrase-level breaks honouring kinsoku rules, so words like コーヒー never split across lines. */
val KinsokuLineBreak = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Strict, LineBreak.WordBreak.Phrase)
