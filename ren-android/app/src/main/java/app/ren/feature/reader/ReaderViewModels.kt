package app.ren.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.ren.core.audio.AudioEngineManager
import app.ren.core.audio.StoryPlayback
import app.ren.core.mvi.MviViewModel
import app.ren.domain.model.FuriganaMode
import app.ren.domain.model.Japanese
import app.ren.domain.model.Mastery
import app.ren.domain.model.RubyMarkup
import app.ren.domain.model.Segment
import app.ren.domain.model.Story
import app.ren.domain.model.StoryCategory
import app.ren.domain.model.Vocab
import app.ren.domain.repository.MiningRepository
import app.ren.domain.repository.StudyRepository
import app.ren.domain.usecase.Clock
import app.ren.domain.usecase.StoryAnalyzer
import app.ren.domain.usecase.StoryInsight
import app.ren.domain.usecase.StudySnapshot
import app.ren.domain.usecase.StudySnapshotSource
import app.ren.feature.common.PartUi
import app.ren.navigation.StoryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

// ------------------------------------------------------------------ library

data class LibraryUiState(
    val loading: Boolean = true,
    val category: Int = 0,
    val stories: List<StoryInsight> = emptyList(),
) {
    val visible: List<StoryInsight>
        get() = stories.filter { it.story.category == StoryCategory.entries[category] }
            .sortedWith(compareBy({ it.locked }, { -it.knownShare }))
}

sealed interface LibraryIntent {
    data class SelectCategory(val index: Int) : LibraryIntent
    data class Open(val storyId: String) : LibraryIntent
    data object OpenSettings : LibraryIntent
}

sealed interface LibraryEffect {
    data class Story(val id: String) : LibraryEffect
    data object Settings : LibraryEffect
}

@HiltViewModel
class LibraryViewModel @Inject constructor(source: StudySnapshotSource) :
    MviViewModel<LibraryUiState, LibraryIntent, LibraryEffect>(LibraryUiState()) {
    init {
        viewModelScope.launch {
            source.snapshot.filterNotNull().collect { s ->
                val insights = s.library.stories.map { StoryAnalyzer.analyze(it, s.library, s.cards) }
                setState { copy(loading = false, stories = insights) }
            }
        }
    }

    override fun onIntent(intent: LibraryIntent) = when (intent) {
        is LibraryIntent.SelectCategory -> setState { copy(category = intent.index) }
        is LibraryIntent.Open -> emit(LibraryEffect.Story(intent.storyId))
        LibraryIntent.OpenSettings -> emit(LibraryEffect.Settings)
    }
}

// ------------------------------------------------------------------ story

data class WordUi(
    val showRuby: Boolean,
    val isNew: Boolean,
    val selected: Boolean,
)

data class SentenceUi(val index: Int, val jp: String, val en: String, val words: List<WordUi>)

data class PitchUi(val morae: List<String>, val contour: List<Boolean>, val description: String)

data class WordSheetUi(
    val vocab: Vocab,
    val parts: List<PartUi>,
    val pitch: PitchUi?,
    val inReviews: Boolean,
    val status: String,
)

data class MiningUi(
    val sentence: Int,
    val start: Int,
    val end: Int,
    val markup: String,
    val plain: String,
    val english: String,
    val audioFirst: Boolean,
    val savedId: String? = null,
)

data class StoryUiState(
    val loading: Boolean = true,
    val story: Story? = null,
    val sentences: List<SentenceUi> = emptyList(),
    val playback: StoryPlayback = StoryPlayback(),
    val sheet: WordSheetUi? = null,
    val mining: MiningUi? = null,
    val savedCount: Int = 0,
)

sealed interface StoryIntent {
    data object TogglePlay : StoryIntent
    data class PlaySentence(val index: Int) : StoryIntent
    data class TapWord(val sentence: Int, val word: Int) : StoryIntent
    data class LongPressWord(val sentence: Int, val word: Int) : StoryIntent
    data object DismissSheet : StoryIntent
    data object AddToReviews : StoryIntent
    data object SpeakWord : StoryIntent
    data object OpenWordDetails : StoryIntent
    data object CancelMining : StoryIntent
    data object ToggleAudioFirst : StoryIntent
    data object SaveMined : StoryIntent
    data object SpeakFragment : StoryIntent
}

sealed interface StoryEffect {
    data class Item(val itemId: String) : StoryEffect
    data object Saved : StoryEffect
}

@HiltViewModel
class StoryViewModel @Inject constructor(
    handle: SavedStateHandle,
    source: StudySnapshotSource,
    private val audio: AudioEngineManager,
    private val study: StudyRepository,
    private val mining: MiningRepository,
    private val clock: Clock,
) : MviViewModel<StoryUiState, StoryIntent, StoryEffect>(StoryUiState()) {
    private val storyId = handle.toRoute<StoryRoute>().storyId
    private var snapshot: StudySnapshot? = null
    private val revealed = mutableSetOf<Pair<Int, Int>>()

    init {
        viewModelScope.launch {
            combine(source.snapshot.filterNotNull(), audio.story) { s, p -> s to p }.collect { (s, p) ->
                snapshot = s
                val story = s.library.stories.firstOrNull { it.id == storyId } ?: return@collect
                setState {
                    copy(
                        loading = false,
                        story = story,
                        sentences = sentencesFor(story, s),
                        playback = if (p.storyId == storyId) p else StoryPlayback(totalSec = story.durationSec.toFloat()),
                        sheet = sheet?.let { sheetFor(it.vocab, s) },
                    )
                }
            }
        }
    }

    private fun sentencesFor(story: Story, s: StudySnapshot): List<SentenceUi> = story.sentences.mapIndexed { si, sentence ->
        val words = sentence.segments.filterIsInstance<Segment.Word>()
        SentenceUi(si, sentence.jp, sentence.en, words.mapIndexed { wi, w ->
            val vocab = s.library.vocabByText[w.lemma]
            val ruby = when (s.prefs.furigana) {
                FuriganaMode.ALWAYS -> true
                FuriganaMode.NEVER -> false
                FuriganaMode.FADE -> StoryAnalyzer.needsFurigana(w.text, s.library, s.cards)
            } || (si to wi) in revealed
            val m = current.mining
            WordUi(
                showRuby = ruby && Japanese.hasKanji(w.text),
                isNew = vocab != null && StoryAnalyzer.isTeachable(vocab, s.cards) && s.cards[vocab.id]?.isIntroduced != true,
                selected = m != null && m.sentence == si && wi in m.start..m.end,
            )
        })
    }

    private fun sheetFor(v: Vocab, s: StudySnapshot): WordSheetUi {
        val parts = v.kanjiIds.flatMap { kid ->
            val k = s.library.kanji[kid] ?: return@flatMap emptyList()
            val ids = k.radicalIds.ifEmpty { emptyList() }
            if (ids.size >= 2) ids.mapNotNull { id -> s.library.partGlyph(id)?.let { PartUi(it, s.library.partMeaning(id).orEmpty()) } }
            else listOf(PartUi(k.character, k.meaning))
        }.distinct()
        val morae = Japanese.morae(v.reading)
        val pitch = if (v.pitchAccent >= 0 && v.cardType.name == "SINGLE_WORD") {
            PitchUi(morae, Japanese.pitchContour(morae.size, v.pitchAccent), Japanese.pitchName(v.pitchAccent, morae.size))
        } else null
        val card = s.cards[v.id]
        val status = when (card?.mastery) {
            Mastery.Mature -> "In your reviews · Mature, ${card.fsrs.stability.roundToInt()} days"
            Mastery.Learning -> "In your reviews · Learning"
            Mastery.New -> "In today's queue"
            else -> "New word"
        }
        return WordSheetUi(v, parts, pitch, card?.isIntroduced == true, status)
    }

    override fun onIntent(intent: StoryIntent) {
        val s = snapshot ?: return
        val story = current.story ?: return
        when (intent) {
            StoryIntent.TogglePlay -> audio.toggleStory(story)
            is StoryIntent.PlaySentence -> audio.playStory(story, intent.index)
            is StoryIntent.TapWord -> {
                if (current.mining != null && current.mining!!.sentence == intent.sentence) {
                    extendSelection(story, intent.word)
                    return
                }
                val w = wordAt(story, intent.sentence, intent.word) ?: return
                val vocab = s.library.vocabByText[w.lemma] ?: return
                revealed += intent.sentence to intent.word
                audio.say(vocab.reading.ifEmpty { vocab.text }, vocab.audioAssetPath)
                setState { copy(sheet = sheetFor(vocab, s), mining = null, sentences = sentencesFor(story, s)) }
            }
            is StoryIntent.LongPressWord -> {
                setState { copy(sheet = null, mining = miningFor(story, intent.sentence, intent.word, intent.word, s.prefs.minedAudioFirst)) }
                setState { copy(sentences = sentencesFor(story, s)) }
            }
            StoryIntent.DismissSheet -> setState { copy(sheet = null) }
            StoryIntent.AddToReviews -> current.sheet?.let { sheet ->
                viewModelScope.launch { study.introduce(sheet.vocab.id, clock.now()) }
            }
            StoryIntent.SpeakWord -> current.sheet?.let { audio.say(it.vocab.reading, it.vocab.audioAssetPath) }
            StoryIntent.OpenWordDetails -> current.sheet?.let { emit(StoryEffect.Item(it.vocab.id)) }
            StoryIntent.CancelMining -> {
                setState { copy(mining = null) }
                setState { copy(sentences = sentencesFor(story, s)) }
            }
            StoryIntent.ToggleAudioFirst -> setState { copy(mining = mining?.let { it.copy(audioFirst = !it.audioFirst) }) }
            StoryIntent.SpeakFragment -> current.mining?.let { audio.say(RubyMarkup.reading(it.markup)) }
            StoryIntent.SaveMined -> {
                val m = current.mining ?: return
                viewModelScope.launch {
                    if (m.savedId != null) {
                        mining.remove(m.savedId)
                        setState { copy(mining = mining?.copy(savedId = null), savedCount = savedCount - 1) }
                    } else {
                        val id = mining.mine(story.id, m.sentence, m.markup, m.english, m.audioFirst, clock.now())
                        setState { copy(mining = mining?.copy(savedId = id), savedCount = savedCount + 1) }
                        emit(StoryEffect.Saved)
                    }
                }
            }
        }
    }

    private fun extendSelection(story: Story, word: Int) {
        val m = current.mining ?: return
        val s = snapshot ?: return
        val start = minOf(m.start, word)
        val end = maxOf(m.end, word)
        val next = if (word in m.start..m.end && m.start != m.end) miningFor(story, m.sentence, word, word, m.audioFirst)
        else miningFor(story, m.sentence, start, end, m.audioFirst)
        setState { copy(mining = next) }
        setState { copy(sentences = sentencesFor(story, s)) }
    }

    private fun wordAt(story: Story, sentence: Int, word: Int): Segment.Word? =
        story.sentences.getOrNull(sentence)?.segments?.filterIsInstance<Segment.Word>()?.getOrNull(word)

    /** Builds the fragment between two words, keeping the particles in between (電車に乗ります). */
    private fun miningFor(story: Story, sentence: Int, start: Int, end: Int, audioFirst: Boolean): MiningUi {
        val s = snapshot!!
        val segs = story.sentences[sentence].segments
        val markup = StringBuilder()
        var wi = -1
        var inside = false
        for (seg in segs) {
            if (seg is Segment.Word) wi++
            if (seg is Segment.Word && wi == start) inside = true
            if (inside) markup.append(
                when (seg) {
                    is Segment.Word -> "{${seg.text}${seg.reading?.let { "|$it" } ?: if (seg.lemma != seg.text) "|" else ""}${if (seg.lemma != seg.text) "|${seg.lemma}" else ""}}"
                    is Segment.Plain -> seg.text
                },
            )
            if (seg is Segment.Word && wi == end) break
        }
        val fragment = markup.toString().trim().trimEnd('。', '、')
        val lemmas = RubyMarkup.parse(fragment).filterIsInstance<Segment.Word>().map { it.lemma }.toSet()
        val pair = s.library.collocations.values.firstOrNull { it.noun in lemmas && it.verb in lemmas }
        val english = pair?.let { s.library.vocab[it.vocabId]?.meaning } ?: story.sentences[sentence].en
        return MiningUi(sentence, start, end, fragment, RubyMarkup.plain(fragment), english, audioFirst)
    }
}
