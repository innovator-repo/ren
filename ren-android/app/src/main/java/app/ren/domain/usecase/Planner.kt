package app.ren.domain.usecase

import app.ren.domain.fsrs.CardState
import app.ren.domain.fsrs.FSRSScheduler
import app.ren.domain.model.Curriculum
import app.ren.domain.model.ItemType
import app.ren.domain.model.LessonUnit
import app.ren.domain.model.Mastery
import app.ren.domain.model.ReviewLog
import app.ren.domain.model.Stage
import app.ren.domain.model.StudyCard
import app.ren.domain.model.UnitType
import app.ren.domain.model.UserPreferences
import kotlin.math.roundToInt

data class StageProgress(
    val stage: Stage,
    val unitsTotal: Int,
    val unitsDone: Int,
    /** Headline counters, e.g. Kanji 36/120 and Words 152/600 for stage 2. */
    val primaryDone: Int,
    val primaryTotal: Int,
    val secondaryDone: Int,
    val secondaryTotal: Int,
    val graduatedShare: Double,
    val unlocked: Boolean,
) {
    val complete get() = unitsTotal > 0 && unitsDone == unitsTotal
}

/** Decides what is open on the Path and what the next new cards are (spec §4, §5). */
object CurriculumPlanner {

    fun stageProgress(units: List<LessonUnit>, cards: List<StudyCard>, unlockAll: Boolean): List<StageProgress> {
        val cardsByStage = cards.filter { it.unitId.isNotEmpty() }.groupBy { it.stage }
        val unitsByStage = units.groupBy { it.stage }
        val out = mutableListOf<StageProgress>()
        for (stage in Curriculum.stages) {
            val su = unitsByStage[stage.index].orEmpty()
            val sc = cardsByStage[stage.index].orEmpty()
            val primaryType = when (stage.index) {
                0 -> ItemType.KANA
                1 -> ItemType.RADICAL
                else -> ItemType.KANJI
            }
            val primary = sc.filter { it.itemType == primaryType }
            val secondary = sc.filter { it.itemType == ItemType.VOCAB }
            val graduated = graduatedShare(stage.index, sc)
            val prev = out.lastOrNull()
            val unlocked = unlockAll || stage.index == 0 ||
                (prev != null && prev.unlocked && prev.complete && prev.graduatedShare >= Curriculum.UNLOCK_SHARE)
            out += StageProgress(
                stage = stage,
                unitsTotal = su.size,
                unitsDone = su.count { it.isComplete },
                primaryDone = primary.count { it.isIntroduced },
                primaryTotal = primary.size,
                secondaryDone = secondary.count { it.isIntroduced },
                secondaryTotal = secondary.size,
                graduatedShare = graduated,
                unlocked = unlocked,
            )
        }
        return out
    }

    /** Stage 2 must be stable (a week of stability) before Intermediate opens; earlier stages just graduate. */
    private fun graduatedShare(stage: Int, cards: List<StudyCard>): Double {
        if (cards.isEmpty()) return 0.0
        val ok = cards.count {
            it.fsrs.state == CardState.Review &&
                (stage != 2 || it.fsrs.stability >= Curriculum.INTERMEDIATE_STABILITY_DAYS)
        }
        return ok.toDouble() / cards.size
    }

    /** The current stage: the first unlocked stage that still has lessons left. */
    fun currentStage(progress: List<StageProgress>): Int =
        progress.firstOrNull { it.unlocked && !it.complete }?.stage?.index
            ?: progress.lastOrNull { it.unlocked }?.stage?.index ?: 0

    fun nextUnits(units: List<LessonUnit>, progress: List<StageProgress>, count: Int): List<LessonUnit> {
        if (count <= 0) return emptyList()
        val open = progress.filter { it.unlocked }.map { it.stage.index }.toSet()
        return units.asSequence().filter { !it.isComplete && it.stage in open }
            .sortedWith(compareBy({ it.stage }, { it.sequence })).take(count).toList()
    }

    fun nextUnitInStage(units: List<LessonUnit>, stage: Int): LessonUnit? =
        units.filter { it.stage == stage && !it.isComplete }.minByOrNull { it.sequence }
}

data class TodaySummary(
    val dueReviews: Int,
    val newRemaining: Int,
    val reviewsDone: Int,
    val reviewGoal: Int,
    val newDone: Int,
    val newGoal: Int,
    val minutesDone: Int,
    val minutesGoal: Int,
    val estimatedMinutes: Int,
    val streak: Int,
    val freezes: Int,
    /** Units completed today plus the ones still waiting, in order. */
    val newToday: List<LessonUnit>,
    val nextDueAt: Long?,
) {
    val sessionItems get() = dueReviews + newRemaining
}

object TodayCalculator {
    fun summarize(
        now: Long,
        dayStart: Long,
        today: Long,
        cards: List<StudyCard>,
        units: List<LessonUnit>,
        logsToday: List<ReviewLog>,
        prefs: UserPreferences,
        progress: List<StageProgress>,
    ): TodaySummary {
        val due = cards.count { it.isIntroduced && it.fsrs.due <= now }
        val doneToday = units.filter { (it.completedAt ?: -1) >= dayStart }.sortedBy { it.completedAt }
        val remaining = (prefs.newUnitsPerDay - doneToday.size).coerceAtLeast(0)
        val upcoming = CurriculumPlanner.nextUnits(units, progress, remaining)
        val minutes = if (prefs.studyEpochDay == today) (prefs.studyMsToday / 60_000.0).roundToInt() else 0
        val nextDue = cards.filter { it.isIntroduced && it.fsrs.due > now }.minOfOrNull { it.fsrs.due }
        return TodaySummary(
            dueReviews = due,
            newRemaining = upcoming.size,
            reviewsDone = logsToday.count { it.rating > 0 },
            reviewGoal = prefs.reviewGoal,
            newDone = doneToday.size,
            newGoal = prefs.newUnitsPerDay,
            minutesDone = minutes,
            minutesGoal = prefs.minutesGoal,
            estimatedMinutes = ((due * 25 + upcoming.size * 120) / 60.0).roundToInt().coerceAtLeast(if (due + upcoming.size > 0) 1 else 0),
            streak = Streak.current(prefs, today),
            freezes = prefs.streakFreezes,
            newToday = doneToday + upcoming,
            nextDueAt = nextDue,
        )
    }
}

/** One stop in a study session. */
sealed interface SessionStep {
    data class Review(val itemId: String) : SessionStep
    data class Lesson(val unitId: String) : SessionStep
}

object SessionPlanner {
    /** Reviews first, most-forgotten first, capped for micro-dosing; then today's remaining lessons. */
    fun daily(cards: List<StudyCard>, newUnits: List<LessonUnit>, now: Long, cap: Int): List<SessionStep> {
        val due = cards.filter { it.isIntroduced && it.fsrs.due <= now }
            .sortedBy { retrievability(it, now) }
            .take(cap)
        return interleave(due).map { SessionStep.Review(it.itemId) } + newUnits.map { SessionStep.Lesson(it.id) }
    }

    fun quick(cards: List<StudyCard>, now: Long, size: Int): List<SessionStep> =
        cards.filter { it.isIntroduced && it.fsrs.due <= now }
            .sortedBy { retrievability(it, now) }.take(size).map { SessionStep.Review(it.itemId) }

    private fun retrievability(card: StudyCard, now: Long): Double =
        if (card.fsrs.state == CardState.New) 2.0 // brand-new cards after true reviews
        else FSRSScheduler.calculateRetrievability((now - card.fsrs.lastReview).toDouble() / FSRSScheduler.DAY_MS, card.fsrs.stability)

    /** Avoid long runs of one card type so production switches between drawing and typing. */
    private fun interleave(cards: List<StudyCard>): List<StudyCard> {
        val byType = cards.groupBy { it.itemType }.mapValues { ArrayDeque(it.value) }
        val out = ArrayList<StudyCard>(cards.size)
        var last: ItemType? = null
        while (out.size < cards.size) {
            val next = byType.entries.filter { it.value.isNotEmpty() }
                .sortedByDescending { it.value.size }
                .firstOrNull { it.key != last } ?: byType.entries.first { it.value.isNotEmpty() }
            out += next.value.removeFirst()
            last = next.key
        }
        return out
    }
}

fun LessonUnit.headlineType(): ItemType = when (type) {
    UnitType.KANA_GROUP -> ItemType.KANA
    UnitType.RADICAL -> ItemType.RADICAL
    UnitType.KANJI -> ItemType.KANJI
    UnitType.COLLOCATION -> ItemType.VOCAB
}

fun Map<String, StudyCard>.mastery(itemId: String): Mastery = this[itemId]?.mastery ?: Mastery.Locked
