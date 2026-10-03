package app.ren.domain.usecase

import app.ren.domain.fsrs.FSRSScheduler
import app.ren.domain.model.Japanese
import app.ren.domain.model.Mastery
import app.ren.domain.model.Segment
import app.ren.domain.model.Story
import app.ren.domain.model.StudyCard
import app.ren.domain.model.Vocab
import app.ren.domain.repository.ContentLibrary
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ------------------------------------------------------------------ Matrix

enum class NodeKind { RADICAL, KANJI }

data class GraphNode(
    val id: String,
    val glyph: String,
    val meaning: String,
    val kind: NodeKind,
    val mastery: Mastery,
    val equation: String,
    val status: String,
    val words: List<Vocab>,
    val x: Float,
    val y: Float,
)

data class GraphEdge(val from: Int, val to: Int, val lit: Boolean)

/** World is [width]×[height] in dp-like units; nodes are laid out inside it. */
data class KnowledgeGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val width: Float,
    val height: Float,
    val litKanji: Int,
) {
    companion object {
        val Empty = KnowledgeGraph(emptyList(), emptyList(), 320f, 330f, 0)
    }
}

/**
 * The radial knowledge graph (spec §5): learned radicals are hubs, kanji hang off the radicals
 * they share, and a few locked kanji show what is next. Layout is a seeded force simulation,
 * so the same progress always draws the same picture.
 */
object KnowledgeGraphBuilder {
    private const val FRONTIER = 10

    fun build(lib: ContentLibrary, cards: Map<String, StudyCard>): KnowledgeGraph {
        // Pictographs that are their own radical (日, 木…) already appear as that radical's node.
        val kanjiOrder = lib.kanji.values
            .filter { k -> k.radicalIds.any { lib.partGlyph(it) != k.character } }
            .sortedBy { cards[it.id]?.sequence ?: Int.MAX_VALUE }
        val learned = kanjiOrder.filter { cards[it.id]?.isIntroduced == true }
        val litCount = lib.kanji.values.count { cards[it.id]?.isIntroduced == true }
        val learnedRadicals = lib.radicals.keys.filter { cards[it]?.isIntroduced == true }.toSet()

        val visibleKanji = LinkedHashSet<String>()
        if (learned.isEmpty()) {
            // Nothing lit yet: preview the first kanji of the path, all locked.
            kanjiOrder.filter { it.components.size >= 2 }.take(8).forEach { visibleKanji += it.id }
        } else {
            learned.forEach { visibleKanji += it.id }
            val known = learnedRadicals + learned.map { it.id }
            kanjiOrder.asSequence()
                .filter { it.id !in visibleKanji && it.radicalIds.any { r -> r in known } }
                .take(FRONTIER).forEach { visibleKanji += it.id }
        }
        val visibleRadicals = LinkedHashSet<String>()
        for (k in visibleKanji) lib.kanji[k]?.radicalIds?.filter { it in lib.radicals }?.forEach { visibleRadicals += it }

        val ids = visibleRadicals.toList() + visibleKanji.toList()
        val index = ids.withIndex().associate { it.value to it.index }
        val edges = buildList {
            for (k in visibleKanji) {
                val kanji = lib.kanji[k] ?: continue
                for (part in kanji.radicalIds.distinct()) {
                    val a = index[part] ?: continue
                    val b = index.getValue(k)
                    add(GraphEdge(a, b, lit = cards[k]?.isIntroduced == true && cards[part]?.isIntroduced == true))
                }
            }
        }

        val n = ids.size
        val scale = max(1f, sqrt(n / 14f))
        val width = 320f * scale
        val height = 330f * scale
        val pos = layout(n, edges, width, height, isHub = { it < visibleRadicals.size })

        val wordsByKanji = lib.vocab.values.groupBy { it.kanjiIds.lastOrNull() }
        val nodes = ids.mapIndexed { i, id ->
            val card = cards[id]
            val mastery = card?.mastery ?: Mastery.Locked
            val radical = lib.radicals[id]
            if (radical != null) {
                val uses = lib.kanji.values.count { id in it.radicalIds }
                GraphNode(
                    id, radical.character, radical.meaning, NodeKind.RADICAL, mastery,
                    equation = "Radical. Builds $uses kanji",
                    status = if (card?.isIntroduced == true) "Learned" else "Locked",
                    words = emptyList(), x = pos[i].first, y = pos[i].second,
                )
            } else {
                val k = lib.kanji.getValue(id)
                val parts = k.radicalIds.mapNotNull { lib.partGlyph(it) }
                GraphNode(
                    id, k.character, k.meaning, NodeKind.KANJI, mastery,
                    equation = if (parts.size >= 2) parts.joinToString(" + ") else "Pictograph",
                    status = statusOf(card, mastery),
                    words = wordsByKanji[id].orEmpty().filter { cards[it.id]?.isIntroduced == true }.take(3),
                    x = pos[i].first, y = pos[i].second,
                )
            }
        }
        return KnowledgeGraph(nodes, edges, width, height, litCount)
    }

    fun statusOf(card: StudyCard?, mastery: Mastery): String = when (mastery) {
        Mastery.Locked -> "Locked"
        Mastery.New -> "New"
        Mastery.Learning -> "Learning"
        Mastery.Mature -> "Mature, ${card?.fsrs?.stability?.roundToInt() ?: 0} days"
    }

    /**
     * Fruchterman–Reingold with a fixed seed, plus gravity so separate clusters stay near the
     * centre instead of drifting to the walls. Radicals (hubs) are pulled in a little harder.
     */
    private fun layout(n: Int, edges: List<GraphEdge>, w: Float, h: Float, isHub: (Int) -> Boolean): List<Pair<Float, Float>> {
        if (n == 0) return emptyList()
        val margin = 34f
        val cx = w / 2
        val cy = h / 2
        val x = FloatArray(n) { i -> cx + cos(i * 2.39996f) * (w / 3) * sqrt((i + 1f) / n) }
        val y = FloatArray(n) { i -> cy + sin(i * 2.39996f) * (h / 3) * sqrt((i + 1f) / n) }
        val k = sqrt((w - 2 * margin) * (h - 2 * margin) / n) * 0.75f
        val reach = 3 * k
        var temp = w / 8
        val dx = FloatArray(n)
        val dy = FloatArray(n)
        repeat(320) {
            dx.fill(0f); dy.fill(0f)
            for (i in 0 until n) for (j in i + 1 until n) {
                var ddx = x[i] - x[j]
                var ddy = y[i] - y[j]
                var d2 = ddx * ddx + ddy * ddy
                if (d2 < 0.01f) { ddx = 0.1f * (i - j); ddy = 0.1f; d2 = ddx * ddx + ddy * ddy }
                if (d2 > reach * reach) continue
                val f = k * k / d2
                dx[i] += ddx * f; dy[i] += ddy * f
                dx[j] -= ddx * f; dy[j] -= ddy * f
            }
            for (e in edges) {
                val ddx = x[e.from] - x[e.to]
                val ddy = y[e.from] - y[e.to]
                val d = sqrt(ddx * ddx + ddy * ddy).coerceAtLeast(0.01f)
                val f = d / k
                dx[e.from] -= ddx * f; dy[e.from] -= ddy * f
                dx[e.to] += ddx * f; dy[e.to] += ddy * f
            }
            for (i in 0 until n) {
                val g = if (isHub(i)) 0.09f else 0.05f
                dx[i] += (cx - x[i]) * g * k / 10
                dy[i] += (cy - y[i]) * g * k / 10
                val d = sqrt(dx[i] * dx[i] + dy[i] * dy[i]).coerceAtLeast(0.01f)
                val step = min(d, temp)
                x[i] = (x[i] + dx[i] / d * step).coerceIn(margin, w - margin)
                y[i] = (y[i] + dy[i] / d * step).coerceIn(margin, h - margin)
            }
            temp = max(1.5f, temp * 0.985f)
        }
        return List(n) { x[it] to y[it] }
    }
}

// ------------------------------------------------------------------ Reader

data class StoryInsight(
    val story: Story,
    val knownShare: Double,
    val newWords: List<Vocab>,
    val missingKanji: List<String>,
    val missingKana: Int,
) {
    /** Krashen's i+1: at most one unknown kanji, and the kana must be in place. */
    val locked get() = missingKanji.size > 1 || missingKana > 2
    val lockReason: String
        get() = when {
            missingKana > 2 -> "Finish the Kana Sprint first"
            else -> "Needs ${missingKanji.size} more kanji"
        }
}

object StoryAnalyzer {
    fun analyze(story: Story, lib: ContentLibrary, cards: Map<String, StudyCard>): StoryInsight {
        val introducedKana = lib.kana.values.filter { cards[it.id]?.isIntroduced == true }
            .map { Japanese.toHiragana(it.character) }.toSet()
        var total = 0
        var known = 0
        val missingKanji = LinkedHashSet<String>()
        var missingKana = 0
        val newWords = LinkedHashMap<String, Vocab>()
        for (sentence in story.sentences) {
            for (seg in sentence.segments) {
                for (c in seg.text) {
                    when {
                        Japanese.isKanji(c) -> {
                            total++
                            val k = lib.kanjiByChar[c.toString()]
                            if (k != null && cards[k.id]?.isIntroduced == true) known++ else missingKanji += c.toString()
                        }
                        Japanese.isKana(c) && c != 'ー' -> {
                            total++
                            val base = Japanese.toHiragana(c.toString())
                            if (base in introducedKana || base in SMALL_KANA) known++ else missingKana++
                        }
                    }
                }
                if (seg is Segment.Word) {
                    val v = lib.vocabByText[seg.lemma] ?: continue
                    val kanjiKnown = v.kanjiIds.all { cards[it]?.isIntroduced == true }
                    if (cards[v.id]?.isIntroduced != true && kanjiKnown && isTeachable(v, cards)) newWords[v.id] = v
                }
            }
        }
        // Distinct kana count is what matters for the gate, not every occurrence.
        val distinctMissingKana = story.sentences.flatMap { it.plain.toList() }
            .filter { Japanese.isKana(it) && it != 'ー' }
            .map { Japanese.toHiragana(it.toString()) }
            .filter { it !in introducedKana && it !in SMALL_KANA }.toSet().size
        return StoryInsight(
            story = story,
            knownShare = if (total == 0) 1.0 else known.toDouble() / total,
            newWords = newWords.values.toList(),
            missingKanji = missingKanji.toList(),
            missingKana = if (missingKana == 0) 0 else distinctMissingKana,
        )
    }

    /**
     * Words the reader flags as new: kanji words and curriculum words. Everyday kana phrases
     * (ありがとう, コーヒー) can still be tapped and added, but are not counted against i+1.
     */
    fun isTeachable(v: Vocab, cards: Map<String, StudyCard>): Boolean =
        Japanese.hasKanji(v.text) || cards[v.id]?.unitId?.isNotEmpty() == true

    private val SMALL_KANA = setOf("ゃ", "ゅ", "ょ", "っ", "ぁ", "ぃ", "ぅ", "ぇ", "ぉ", "ゎ", "ゕ", "ゖ") +
        "がぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽ".map { it.toString() }

    /** Furigana fading (spec §3): show ruby while any kanji in the word is not yet Mature. */
    fun needsFurigana(word: String, lib: ContentLibrary, cards: Map<String, StudyCard>): Boolean =
        Japanese.kanjiIn(word).any { ch ->
            val card = lib.kanjiByChar[ch]?.let { cards[it.id] }
            card == null || card.fsrs.stability <= FSRSScheduler.MATURE_STABILITY_DAYS || !card.isIntroduced
        }
}

// ------------------------------------------------------------------ Answers

object AnswerChecker {
    /** Typed answers are compared script-insensitively; the expected value may be a full phrase. */
    fun matches(typed: String, vararg expected: String): Boolean {
        val t = Japanese.normalizeAnswer(typed)
        if (t.isEmpty()) return false
        return expected.any { Japanese.normalizeAnswer(it) == t }
    }

    /** Polite -ます form for a dictionary verb, so 乗ります is accepted for 乗る. */
    fun politeForms(reading: String): List<String> {
        if (reading.isEmpty()) return emptyList()
        val last = reading.last()
        val stem = reading.dropLast(1)
        val iRow = mapOf('う' to 'い', 'く' to 'き', 'ぐ' to 'ぎ', 'す' to 'し', 'つ' to 'ち', 'ぬ' to 'に', 'ぶ' to 'び', 'む' to 'み', 'る' to 'り')
        return buildList {
            if (reading.endsWith("る")) add(stem + "ます")
            iRow[last]?.let { add(stem + it + "ます") }
            if (reading.endsWith("する")) add(reading.dropLast(2) + "します")
            if (reading == "くる") add("きます")
        }
    }
}
