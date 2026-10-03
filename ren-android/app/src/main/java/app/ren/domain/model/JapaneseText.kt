package app.ren.domain.model

/** A run of a sentence: either plain text or a tappable word linked to a vocabulary entry. */
sealed interface Segment {
    val text: String

    data class Plain(override val text: String) : Segment

    /** [lemma] is the dictionary form used to find the vocab row, e.g. 食べます → 食べる. */
    data class Word(override val text: String, val reading: String?, val lemma: String) : Segment
}

/**
 * Inline markup used by every Japanese sentence in the content set:
 * `{base|reading|lemma}`, `{base|reading}` or `{base}` (kana-only word).
 * Example: `{日曜日|にちようび}は{休日|きゅうじつ}です。`
 */
object RubyMarkup {
    private val token = Regex("""\{([^{}|]+)(?:\|([^{}|]*))?(?:\|([^{}|]*))?\}""")

    fun parse(markup: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var last = 0
        for (m in token.findAll(markup)) {
            if (m.range.first > last) out += Segment.Plain(markup.substring(last, m.range.first))
            val base = m.groupValues[1]
            val reading = m.groupValues[2].ifEmpty { null }
            val lemma = m.groupValues[3].ifEmpty { base }
            out += Segment.Word(base, reading, lemma)
            last = m.range.last + 1
        }
        if (last < markup.length) out += Segment.Plain(markup.substring(last))
        return out
    }

    fun plain(markup: String): String = parse(markup).joinToString("") { it.text }

    /** Hiragana reading of the whole sentence, for answer checking and audio. */
    fun reading(markup: String): String = parse(markup).joinToString("") {
        when (it) {
            is Segment.Plain -> it.text
            is Segment.Word -> it.reading ?: it.text
        }
    }
}

object Japanese {
    fun isKanji(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿' || c == '々'
    fun isHiragana(c: Char): Boolean = c in 'ぁ'..'ゟ'
    fun isKatakana(c: Char): Boolean = c in '゠'..'ヿ'
    fun isKana(c: Char): Boolean = isHiragana(c) || isKatakana(c)
    fun hasKanji(s: String): Boolean = s.any(::isKanji)
    fun kanjiIn(s: String): List<String> = s.filter(::isKanji).map { it.toString() }.distinct()

    fun toHiragana(s: String): String = buildString {
        for (c in s) append(if (c in 'ァ'..'ヶ') c - 0x60 else c)
    }

    /** Lenient comparison for typed answers: script-insensitive, ignores spaces and punctuation. */
    fun normalizeAnswer(s: String): String =
        toHiragana(s).filter { isKana(it) || isKanji(it) || it == 'ー' }

    /**
     * Splits a word into ruby pairs so furigana sits only over kanji:
     * 食べる / たべる → [食:た, べる:null]. Leading and trailing kana are matched against the reading.
     */
    fun alignRuby(base: String, reading: String): List<Pair<String, String?>> {
        if (!hasKanji(base) || reading.isEmpty()) return listOf(base to null)
        val b = base
        val r = toHiragana(reading)
        var prefix = 0
        while (prefix < b.length && prefix < r.length && !isKanji(b[prefix]) &&
            toHiragana(b[prefix].toString()) == r[prefix].toString()
        ) prefix++
        var suffix = 0
        while (suffix < b.length - prefix && suffix < r.length - prefix &&
            !isKanji(b[b.length - 1 - suffix]) &&
            toHiragana(b[b.length - 1 - suffix].toString()) == r[r.length - 1 - suffix].toString()
        ) suffix++
        val core = b.substring(prefix, b.length - suffix)
        val coreReading = reading.substring(prefix, reading.length - suffix)
        return buildList {
            if (prefix > 0) add(b.substring(0, prefix) to null)
            add(core to coreReading.ifEmpty { null })
            if (suffix > 0) add(b.substring(b.length - suffix) to null)
        }
    }

    /** Morae of a reading; small kana (ゃゅょぁぃぅぇぉ) join the previous mora. Used by the pitch graph. */
    fun morae(reading: String): List<String> {
        val small = "ゃゅょぁぃぅぇぉャュョァィゥェォ"
        val out = mutableListOf<String>()
        for (c in reading) {
            if (c in small && out.isNotEmpty()) out[out.lastIndex] = out.last() + c else out += c.toString()
        }
        return out
    }

    /**
     * Tokyo pitch pattern as high/low per mora plus the following particle (n+1 entries).
     * 0 = heiban (low-high…high, particle high), 1 = atamadaka, n = drop after mora n.
     */
    fun pitchContour(moraCount: Int, accent: Int): List<Boolean> = List(moraCount + 1) { i ->
        when {
            accent == 0 -> i != 0
            accent == 1 -> i == 0
            else -> i in 1 until accent
        }
    }

    fun pitchName(accent: Int, moraCount: Int): String = when {
        accent < 0 -> "Unknown"
        accent == 0 -> "Flat [0]. Low, then high, and the particle stays high."
        accent == 1 -> "Head-high [1]. Starts high, then drops."
        accent == moraCount -> "Tail-high [$accent]. Rises, then the particle drops."
        else -> "Middle [$accent]. Rises, then drops after mora $accent."
    }
}
