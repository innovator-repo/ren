package app.ren.feature.session

import app.ren.domain.fsrs.Rating
import app.ren.domain.model.Collocation
import app.ren.domain.model.Component
import app.ren.domain.model.Kana
import app.ren.domain.model.Kanji
import app.ren.domain.model.LessonUnit
import app.ren.domain.model.MinedSentence
import app.ren.domain.model.Radical
import app.ren.domain.model.StudyCard
import app.ren.domain.model.Vocab
import app.ren.domain.repository.ContentLibrary

// ------------------------------------------------------------------ lesson steps (spec §6A steps 1–4)

sealed interface LessonStep {
    val title: String
    val subtitle: String

    /** Step 1: the slider pulls the kanji apart into its radicals. */
    data class PullApart(val kanji: Kanji, val parts: List<Part>, override val subtitle: String) : LessonStep {
        override val title = "Pull it apart"
    }

    /** A kanji that is itself a known building block: meet it as a word. */
    data class MeetKanji(val kanji: Kanji, override val subtitle: String) : LessonStep {
        override val title = "Meet ${kanji.character}"
    }

    /** Step 2: a picture story with the key words highlighted, plus audio. */
    data class PictureIt(
        val glyph: String,
        val strokes: List<String>,
        val components: List<Component>,
        val story: String,
        val keywords: List<Keyword>,
        val audioText: String,
        override val subtitle: String = "A story sticks better than a shape.",
    ) : LessonStep {
        override val title = "Picture it"
    }

    /** Step 3: trace the character stroke by stroke. */
    data class WriteIt(val glyph: String, val strokes: List<String>, val tip: String?, val tipGlyphs: List<String>) : LessonStep {
        override val title = "Write $glyph"
        override val subtitle = "Follow the red dot. Draw each stroke in order."
    }

    /** Step 4: hear a word, then tap the piece just learned inside it. */
    data class FindIt(
        val word: Vocab,
        val kanji: Kanji?,
        val targetKanjiIndex: Int,
        val targetComponent: Int?,
        val targetName: String,
        val otherWords: List<Vocab>,
        override val subtitle: String,
        /** Stroke paths for each character of the word, empty where none exist. */
        val glyphStrokes: List<List<String>> = emptyList(),
    ) : LessonStep {
        override val title = "Find the $targetName"
    }

    /** Kana: see it, hear it, link it to a picture and a word. */
    data class MeetKana(val kana: Kana, val showRomaji: Boolean) : LessonStep {
        override val title = "Meet ${kana.character}"
        override val subtitle = if (kana.script.name == "KATAKANA") "Katakana: same sounds, sharper strokes." else "One sound, one shape."
    }

    /** §7: a verb pair taught as one unit, particle called out. */
    data class LearnPair(val vocab: Vocab, val pair: Collocation) : LessonStep {
        override val title = "Learn the pair"
        override val subtitle = "Words travel in pairs. Keep the particle with its verb."
    }
}

data class Part(val glyph: String, val meaning: String, val strokes: List<Int>, val isRadical: Boolean)
data class Keyword(val word: String, val isTarget: Boolean)

// ------------------------------------------------------------------ review prompts (steps 5–6)

enum class ReviewKind {
    /** Draw the kanji or radical from its meaning. */
    DRAW,
    /** Hear a kana and draw it. */
    DRAW_KANA,
    /** See a word, type its reading. */
    TYPE_READING,
    /** See a kana-only word's meaning, type the word. */
    TYPE_WORD,
    /** See the noun and the English, type particle + verb (§7). */
    TYPE_PAIR,
    /** Mined sentence, audio first (§7). */
    LISTEN,
    /** Mined sentence, text first. */
    READ,
}

data class ReviewPrompt(
    val card: StudyCard,
    val kind: ReviewKind,
    val glyph: String,
    val meaning: String,
    val strokes: List<String> = emptyList(),
    val kanji: Kanji? = null,
    val radical: Radical? = null,
    val kana: Kana? = null,
    val vocab: Vocab? = null,
    val pair: Collocation? = null,
    val mined: MinedSentence? = null,
    /** Accepted typed answers, already in hiragana. */
    val answers: List<String> = emptyList(),
)

sealed interface ReviewPhase {
    data object Answering : ReviewPhase
    /** Mined sentences: the learner reveals the text, then grades themself. */
    data class Revealed(val typed: String) : ReviewPhase
    data class Result(val correct: Boolean, val typed: String?, val suggested: Rating?) : ReviewPhase
}

data class ReviewUi(
    val prompt: ReviewPrompt,
    val phase: ReviewPhase = ReviewPhase.Answering,
    val intervals: Map<Rating, String> = emptyMap(),
    val attempt: Int = 0,
    val shake: Int = 0,
)

data class LessonUi(
    val unit: LessonUnit,
    val steps: List<LessonStep>,
    val index: Int = 0,
    val ready: Boolean = false,
) {
    val step: LessonStep get() = steps[index]
    val isLast get() = index == steps.lastIndex
}

data class SummaryUi(
    val reviewed: Int,
    val correct: Int,
    val learned: List<String>,
    val minutes: Int,
    val streak: Int,
    val lessonOnly: Boolean,
    val canReviewNow: Int,
)

sealed interface SessionPage {
    data object Loading : SessionPage
    data object Empty : SessionPage
    data class Review(val ui: ReviewUi) : SessionPage
    data class Lesson(val ui: LessonUi) : SessionPage
    data class Summary(val ui: SummaryUi) : SessionPage
}

data class SessionUiState(
    val page: SessionPage = SessionPage.Loading,
    val position: Int = 0,
    val total: Int = 0,
    val showRomaji: Boolean = false,
    val library: ContentLibrary = ContentLibrary.Empty,
)

sealed interface SessionIntent {
    data class SubmitTyped(val text: String) : SessionIntent
    data class DrawingFinished(val mistakes: Int, val hints: Int) : SessionIntent
    data class Reveal(val typed: String) : SessionIntent
    data class Rate(val rating: Rating) : SessionIntent
    data object ContinueAfterMiss : SessionIntent
    data object Speak : SessionIntent
    data class SpeakText(val text: String) : SessionIntent
    data object StepReady : SessionIntent
    data object NextStep : SessionIntent
    data object PreviousStep : SessionIntent
    data object ReviewNow : SessionIntent
    data object Close : SessionIntent
}

sealed interface SessionEffect {
    data object PlayErrorHaptic : SessionEffect
    data object PlaySuccessHaptic : SessionEffect
    data object Finished : SessionEffect
    data object Close : SessionEffect
}
