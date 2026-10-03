package app.ren.data.repository

import app.ren.data.local.CollocationEntity
import app.ren.data.local.FSRSCardEntity
import app.ren.data.local.KanaEntity
import app.ren.data.local.KanjiEntity
import app.ren.data.local.LessonUnitEntity
import app.ren.data.local.MinedSentenceEntity
import app.ren.data.local.RadicalEntity
import app.ren.data.local.ReviewLogEntity
import app.ren.data.local.StoryEntity
import app.ren.data.local.VocabularyEntity
import app.ren.domain.fsrs.CardState
import app.ren.domain.fsrs.FsrsCard
import app.ren.domain.model.Collocation
import app.ren.domain.model.Component
import app.ren.domain.model.ItemType
import app.ren.domain.model.Kana
import app.ren.domain.model.Kanji
import app.ren.domain.model.LessonUnit
import app.ren.domain.model.MinedSentence
import app.ren.domain.model.Radical
import app.ren.domain.model.ReviewLog
import app.ren.domain.model.Script
import app.ren.domain.model.Story
import app.ren.domain.model.StoryCategory
import app.ren.domain.model.StorySentence
import app.ren.domain.model.StudyCard
import app.ren.domain.model.UnitType
import app.ren.domain.model.Vocab
import app.ren.domain.model.VocabCardType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class ComponentDto(
    @SerialName("e") val element: String,
    @SerialName("r") val radicalId: String? = null,
    @SerialName("p") val position: String? = null,
    @SerialName("s") val strokes: List<Int>,
)

@Serializable
private data class SentenceDto(val jp: String, val en: String)

private fun String.jsonList(): List<String> = if (isBlank()) emptyList() else json.decodeFromString(this)
private fun String.strokes(): List<String> = split('\n').filter { it.isNotBlank() }

fun KanaEntity.toDomain() = Kana(
    id, character, romaji, Script.valueOf(script), strokePathsSvg.strokes(), mnemonic, exampleWord, exampleMeaning,
)

fun RadicalEntity.toDomain() = Radical(id, character, englishMeaning, strokeCount, strokePathsSvg.strokes(), mnemonicStory)

fun KanjiEntity.toDomain() = Kanji(
    id = id,
    character = character,
    meaning = primaryMeaning,
    onyomi = onyomi.jsonList(),
    kunyomi = kunyomi.jsonList(),
    strokeCount = strokeCount,
    strokes = strokePathsSvg.strokes(),
    radicalIds = radicalIds.jsonList(),
    components = json.decodeFromString<List<ComponentDto>>(componentsJson)
        .map { Component(it.element, it.radicalId, it.position, it.strokes) },
    mnemonicEquation = mnemonicEquation,
    mnemonicStory = mnemonicStory,
    jlptLevel = jlptLevel,
)

fun VocabularyEntity.toDomain() = Vocab(
    id, text, reading, pitchAccentPattern, englishMeaning, kanjiIds.jsonList(), audioAssetPath,
    exampleSentenceJp, exampleSentenceEn, VocabCardType.valueOf(cardType),
)

fun Vocab.toEntity() = VocabularyEntity(
    id, text, reading, pitchAccent, meaning, json.encodeToString(kanjiIds), audioAssetPath, exampleJp, exampleEn, cardType.name,
)

fun CollocationEntity.toDomain() = Collocation(
    vocabId, noun, nounReading, nounGloss, particle, particleRole, verb, verbReading, verbGloss, note,
)

fun MinedSentenceEntity.toDomain() = MinedSentence(vocabId, storyId, sentenceIndex, audioFirst, createdAt)

fun StoryEntity.toDomain() = Story(
    id = id,
    title = title,
    titleEn = titleEn,
    category = StoryCategory.valueOf(category),
    iconChar = iconChar,
    tint = tint,
    durationSec = durationSec,
    audioAssetPath = audioAssetPath,
    sentences = json.decodeFromString<List<SentenceDto>>(sentencesJson).map { StorySentence(it.jp, it.en) },
)

fun LessonUnitEntity.toDomain() = LessonUnit(
    id, UnitType.valueOf(type), stage, sequence, title, itemIds.jsonList(), completedAt,
)

fun FSRSCardEntity.toDomain() = StudyCard(
    itemId = itemId,
    itemType = ItemType.valueOf(itemType),
    fsrs = FsrsCard(
        state = CardState.of(state),
        stability = stability,
        difficulty = difficulty,
        elapsedDays = elapsedDays,
        scheduledDays = scheduledDays,
        reps = reps,
        lapses = lapses,
        lastReview = lastReviewTimestamp,
        due = dueTimestamp,
    ),
    unitId = unitId,
    stage = stage,
    sequence = sequence,
)

fun StudyCard.toEntity(f: FsrsCard = fsrs) = FSRSCardEntity(
    itemId = itemId,
    itemType = itemType.name,
    state = f.state.value,
    stability = f.stability,
    difficulty = f.difficulty,
    elapsedDays = f.elapsedDays,
    scheduledDays = f.scheduledDays,
    reps = f.reps,
    lapses = f.lapses,
    lastReviewTimestamp = f.lastReview,
    dueTimestamp = f.due,
    unitId = unitId,
    stage = stage,
    sequence = sequence,
)

fun ReviewLogEntity.toDomain() = ReviewLog(itemId, rating, stateBefore, reviewedAt, durationMs)
