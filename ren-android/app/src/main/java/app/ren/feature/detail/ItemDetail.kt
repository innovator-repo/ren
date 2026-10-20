package app.ren.feature.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.ren.core.audio.AudioEngineManager
import app.ren.core.designsystem.components.Card
import app.ren.core.designsystem.components.CircleIconButton
import app.ren.designsystem.GroupedList
import app.ren.core.designsystem.components.HSpace
import app.ren.designsystem.NavBar
import app.ren.designsystem.NavBarHeight
import app.ren.core.designsystem.components.PartChip
import app.ren.core.designsystem.components.PrimaryButton
import app.ren.core.designsystem.components.RubySentence
import app.ren.core.designsystem.components.RubyWord
import app.ren.core.designsystem.components.SectionHeader
import app.ren.core.designsystem.components.SpeakerButton
import app.ren.core.designsystem.components.StrokeGlyph
import app.ren.core.designsystem.components.Tag
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.components.VSpace
import app.ren.designsystem.glassSource
import app.ren.designsystem.pressable
import app.ren.core.designsystem.components.rememberStrokePaths
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.core.designsystem.theme.jp
import app.ren.core.mvi.CollectEffects
import app.ren.core.mvi.MviViewModel
import app.ren.domain.fsrs.formatInterval
import app.ren.domain.model.Japanese
import app.ren.domain.model.Mastery
import app.ren.domain.model.RubyMarkup
import app.ren.domain.model.StudyCard
import app.ren.domain.model.Vocab
import app.ren.domain.repository.ContentLibrary
import app.ren.domain.repository.StudyRepository
import app.ren.domain.usecase.Clock
import app.ren.domain.usecase.KnowledgeGraphBuilder
import app.ren.domain.usecase.StudySnapshotSource
import app.ren.feature.reader.PitchGraph
import app.ren.feature.reader.PitchUi
import app.ren.feature.session.ReadingsRow
import app.ren.navigation.ItemRoute
import app.ren.navigation.LocalNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class ItemDetailUiState(
    val loading: Boolean = true,
    val itemId: String = "",
    val library: ContentLibrary = ContentLibrary.Empty,
    val card: StudyCard? = null,
    val now: Long = 0,
    val cards: Map<String, StudyCard> = emptyMap(),
)

sealed interface ItemDetailIntent {
    data class Speak(val text: String, val asset: String = "") : ItemDetailIntent
    data class Open(val itemId: String) : ItemDetailIntent
    data object AddToReviews : ItemDetailIntent
}

sealed interface ItemDetailEffect {
    data class Open(val itemId: String) : ItemDetailEffect
}

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    source: StudySnapshotSource,
    private val audio: AudioEngineManager,
    private val study: StudyRepository,
    private val clock: Clock,
) : MviViewModel<ItemDetailUiState, ItemDetailIntent, ItemDetailEffect>(ItemDetailUiState()) {
    private val itemId = handle.toRoute<ItemRoute>().itemId

    init {
        viewModelScope.launch {
            source.snapshot.filterNotNull().collect { s ->
                setState { copy(loading = false, itemId = itemId, library = s.library, card = s.cards[itemId], now = s.now, cards = s.cards) }
            }
        }
    }

    override fun onIntent(intent: ItemDetailIntent) {
        when (intent) {
            is ItemDetailIntent.Speak -> audio.say(intent.text, intent.asset)
            is ItemDetailIntent.Open -> emit(ItemDetailEffect.Open(intent.itemId))
            ItemDetailIntent.AddToReviews -> viewModelScope.launch { study.introduce(itemId, clock.now()) }
        }
    }
}

@Composable
fun ItemDetailRoute(vm: ItemDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    CollectEffects(vm.effects) { e -> if (e is ItemDetailEffect.Open) nav.item(e.itemId) }
    ItemDetailScreen(state, nav::back, vm::onIntent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemDetailScreen(state: ItemDetailUiState, onBack: () -> Unit, onIntent: (ItemDetailIntent) -> Unit) {
    val c = Ren.colors
    val haze = remember { HazeState() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val lib = state.library
    val id = state.itemId
    val kanji = lib.kanji[id]
    val radical = lib.radicals[id]
    val kana = lib.kana[id]
    val vocab = lib.vocab[id]
    val strokes = kanji?.strokes ?: radical?.strokes ?: kana?.strokes ?: emptyList()
    val title = kanji?.character ?: radical?.character ?: kana?.character ?: vocab?.text ?: ""

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        LazyColumn(
            Modifier.fillMaxSize().glassSource(haze),
            contentPadding = PaddingValues(top = top + NavBarHeight + 12.dp, bottom = bottom + 40.dp),
        ) {
            item {
                Card(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(20.dp)) {
                    if (strokes.isNotEmpty()) {
                        StrokeOrder(strokes)
                        VSpace(8.dp)
                        Txt(kanji?.meaning ?: radical?.meaning ?: kana?.romaji.orEmpty(), Ren.type.title2.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
                        Txt(
                            when {
                                kanji != null -> "Kanji · ${kanji.strokeCount} strokes · N${kanji.jlptLevel}"
                                radical != null -> "Radical · ${radical.strokeCount} strokes"
                                kana != null -> "${kana.script.name.lowercase().replaceFirstChar { it.uppercase() }} · ${kana.strokes.size} strokes"
                                else -> ""
                            },
                            Ren.type.footnote, color = c.ink2, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center,
                        )
                    } else if (vocab != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                RubyWord(vocab.text, vocab.reading, Ren.type.title1.jp(if (vocab.text.length > 5) 24.sp else 32.sp, FontWeight.SemiBold), rubyStyle = Ren.type.furigana.copy(fontSize = 11.sp))
                                Txt(vocab.meaning, Ren.type.body, color = c.ink2)
                            }
                            SpeakerButton(size = 38.dp) { onIntent(ItemDetailIntent.Speak(vocab.reading, vocab.audioAssetPath)) }
                        }
                    }
                    VSpace(14.dp)
                    MemoryRow(state.card, state.now)
                }
            }
            if (state.card?.isIntroduced == false && vocab != null && state.card.unitId.isEmpty()) {
                item {
                    PrimaryButton("Add to my reviews", Modifier.padding(16.dp)) { onIntent(ItemDetailIntent.AddToReviews) }
                }
            }
            kanji?.let { k ->
                item {
                    VSpace(12.dp)
                    Card(Modifier.padding(horizontal = 16.dp)) {
                        ReadingsRow(k.onyomi, k.kunyomi)
                        if (k.radicalIds.size >= 2) {
                            VSpace(14.dp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                k.radicalIds.forEach { r ->
                                    PartChip(lib.partGlyph(r).orEmpty(), lib.partMeaning(r).orEmpty()) { onIntent(ItemDetailIntent.Open(r)) }
                                }
                            }
                        }
                        VSpace(12.dp)
                        Txt(k.mnemonicStory, Ren.type.callout, color = c.ink2)
                    }
                }
                val words = lib.vocab.values.filter { k.id in it.kanjiIds }.sortedBy { state.cards[it.id]?.sequence ?: Int.MAX_VALUE }
                if (words.isNotEmpty()) {
                    item { SectionHeader("Words with ${k.character}", Modifier.padding(horizontal = 16.dp)) }
                    item { WordList(words.take(8), state, onIntent) }
                }
            }
            radical?.let { r ->
                item {
                    VSpace(12.dp)
                    Card(Modifier.padding(horizontal = 16.dp)) { Txt(r.mnemonic, Ren.type.body, color = c.ink2) }
                }
                val uses = lib.kanji.values.filter { r.id in it.radicalIds }.sortedBy { state.cards[it.id]?.sequence ?: Int.MAX_VALUE }
                if (uses.isNotEmpty()) {
                    item { SectionHeader("Inside ${uses.size} kanji", Modifier.padding(horizontal = 16.dp)) }
                    item {
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uses.forEach { k ->
                                val m = state.cards[k.id]?.mastery ?: Mastery.Locked
                                Column(
                                    Modifier.width(64.dp).pressable { onIntent(ItemDetailIntent.Open(k.id)) }.clip(RoundedCornerShape(13.dp))
                                        .background(if (m == Mastery.Locked) c.group else c.soft(c.bamboo)).padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Txt(k.character, Ren.type.title2.copy(fontWeight = FontWeight.Normal), color = if (m == Mastery.Locked) c.ink2 else c.ink)
                                    Txt(k.meaning.substringBefore(','), Ren.type.caption2, color = c.ink2, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            kana?.let { k ->
                item {
                    VSpace(12.dp)
                    Card(Modifier.padding(horizontal = 16.dp)) {
                        Txt(k.mnemonic, Ren.type.body, color = c.ink2)
                        VSpace(10.dp)
                        Row(Modifier.pressable { onIntent(ItemDetailIntent.Speak(k.exampleWord)) }, verticalAlignment = Alignment.CenterVertically) {
                            Txt(k.exampleWord, Ren.type.title3.jp(19.sp, FontWeight.Medium))
                            HSpace(10.dp)
                            Txt(k.exampleMeaning, Ren.type.callout, color = c.ink2)
                        }
                    }
                }
            }
            vocab?.let { v ->
                if (v.pitchAccent >= 0) {
                    item {
                        SectionHeader("Pitch accent", Modifier.padding(horizontal = 16.dp))
                        Card(Modifier.padding(horizontal = 16.dp)) {
                            val morae = Japanese.morae(v.reading)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PitchGraph(PitchUi(morae, Japanese.pitchContour(morae.size, v.pitchAccent), ""), Modifier.width(200.dp).height(64.dp))
                                HSpace(12.dp)
                                Txt(Japanese.pitchName(v.pitchAccent, morae.size), Ren.type.footnote, color = c.ink2, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (v.exampleJp.isNotBlank()) {
                    item {
                        SectionHeader("Example", Modifier.padding(horizontal = 16.dp))
                        Card(Modifier.padding(horizontal = 16.dp).pressable { onIntent(ItemDetailIntent.Speak(RubyMarkup.reading(v.exampleJp))) }) {
                            RubySentence(v.exampleJp, Ren.type.readJp.copy(fontSize = 18.sp))
                            VSpace(4.dp)
                            Txt(v.exampleEn, Ren.type.footnote, color = c.ink2)
                        }
                    }
                }
                if (v.kanjiIds.isNotEmpty()) {
                    item {
                        SectionHeader("Kanji", Modifier.padding(horizontal = 16.dp))
                        GroupedList(Modifier.padding(horizontal = 16.dp)) {
                            v.kanjiIds.mapNotNull { lib.kanji[it] }.forEach { k ->
                                row(onClick = { onIntent(ItemDetailIntent.Open(k.id)) }) {
                                    Txt(k.character, Ren.type.title2.copy(fontWeight = FontWeight.Normal))
                                    HSpace(14.dp)
                                    Txt(k.meaning, Ren.type.body, modifier = Modifier.weight(1f))
                                    val m = state.cards[k.id]?.mastery ?: Mastery.Locked
                                    Tag(KnowledgeGraphBuilder.statusOf(state.cards[k.id], m), if (m == Mastery.Mature) c.bamboo else if (m == Mastery.Locked) c.ink2 else c.amber)
                                }
                            }
                        }
                    }
                }
            }
        }
        NavBar(title, backLabel = "Back", onBack = onBack, haze = haze)
    }
}

@Composable
private fun WordList(words: List<Vocab>, state: ItemDetailUiState, onIntent: (ItemDetailIntent) -> Unit) {
    val c = Ren.colors
    GroupedList(Modifier.padding(horizontal = 16.dp)) {
        words.forEach { w ->
            row(onClick = { onIntent(ItemDetailIntent.Open(w.id)) }) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Txt(w.text, Ren.type.body.jp(17.sp, FontWeight.Medium))
                        HSpace(8.dp)
                        Txt(w.reading, Ren.type.callout, color = c.ink2)
                    }
                    Txt(w.meaning, Ren.type.footnote, color = c.ink2, maxLines = 1)
                }
                val m = state.cards[w.id]?.mastery ?: Mastery.Locked
                if (m != Mastery.Locked) Tag(if (m == Mastery.Mature) "Mature" else "Learning", if (m == Mastery.Mature) c.bamboo else c.amber)
                HSpace(8.dp)
                CircleIconButton(RenIcons.Speaker, "Hear ${w.text}", background = c.soft(c.radical), tint = c.radical, size = 32.dp, iconSize = 16.dp) {
                    onIntent(ItemDetailIntent.Speak(w.reading, w.audioAssetPath))
                }
            }
        }
    }
}

/** Animated stroke order with a replay button. */
@Composable
private fun StrokeOrder(strokes: List<String>) {
    val c = Ren.colors
    val paths = rememberStrokePaths(strokes)
    var replay by remember { mutableIntStateOf(0) }
    val still = LocalInspectionMode.current
    val progress = remember(strokes) { Animatable(if (still) strokes.size.toFloat() else 0f) }
    LaunchedEffect(strokes, replay) {
        if (still) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(strokes.size.toFloat(), tween(380 * strokes.size, easing = LinearEasing))
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(140.dp).clip(RoundedCornerShape(14.dp)).background(c.washi)) {
            StrokeGlyph(paths, Modifier.fillMaxSize(), colorOf = { c.ink3.copy(alpha = 0.5f) }, width = 5f)
            StrokeGlyph(paths, Modifier.fillMaxSize(), colorOf = { c.ink }, revealOf = { i -> (progress.value - i).coerceIn(0f, 1f) })
        }
        CircleIconButton(RenIcons.Repeat, "Replay stroke order", Modifier.align(Alignment.TopEnd), size = 32.dp, iconSize = 16.dp) { replay++ }
    }
}

@Composable
private fun MemoryRow(card: StudyCard?, now: Long) {
    val c = Ren.colors
    val m = card?.mastery ?: Mastery.Locked
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        when (m) {
            Mastery.Locked -> Tag("Not learned yet", c.ink2)
            Mastery.New -> Tag("Waiting in today's queue", c.amber)
            Mastery.Learning -> Tag("Learning", c.amber)
            Mastery.Mature -> Tag("Mature, ${card!!.fsrs.stability.roundToInt()} days", c.bamboo)
        }
        if (card != null && card.isIntroduced && card.fsrs.reps > 0) {
            Tag(if (card.fsrs.due <= now) "Due now" else "Next in ${formatInterval(now, card.fsrs.due)}", c.radical)
            if (card.fsrs.lapses > 0) Tag("${card.fsrs.lapses} lapses", c.akane)
        }
    }
}
