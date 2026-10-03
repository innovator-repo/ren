package app.ren.domain.fsrs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** The four honest answers a learner can give (spec §8). */
enum class Rating(val value: Int) {
    Again(1), Hard(2), Good(3), Easy(4);

    companion object {
        fun of(value: Int): Rating = entries.first { it.value == value }
    }
}

/** 0=New 1=Learning 2=Review 3=Relearning, matching fsrs_cards.state. */
enum class CardState(val value: Int) {
    New(0), Learning(1), Review(2), Relearning(3);

    companion object {
        fun of(value: Int): CardState = entries.first { it.value == value }
    }
}

/** Memory state of one card. Timestamps are epoch millis. */
data class FsrsCard(
    val state: CardState = CardState.New,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val elapsedDays: Int = 0,
    val scheduledDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReview: Long = 0L,
    val due: Long = 0L,
)

/**
 * Free Spaced Repetition Scheduler, v5 (spec §8).
 *
 * The pure formulas match the spec verbatim. [schedule] adds what the spec leaves implicit:
 * same-day learning steps driven by FSRS-5's short-term stability (w17, w18), the post-lapse
 * stability cap, and the ordering guarantee hard <= good < easy for review intervals.
 */
object FSRSScheduler {
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    const val TARGET_RETENTION = 0.90
    const val MAX_INTERVAL_DAYS = 36_500
    /** A card counts as Mature once its stability passes three weeks (spec §3, furigana fading). */
    const val MATURE_STABILITY_DAYS = 21.0

    const val MINUTE_MS = 60_000L
    const val DAY_MS = 86_400_000L

    private val w = doubleArrayOf(
        0.4072, 1.1829, 3.1262, 15.4722, 7.2102, 0.5316, 1.0651, 0.0234,
        1.616, 0.1544, 1.0824, 1.9813, 0.0953, 0.2975, 0.2242, 0.2407,
        2.9466, 0.5234, 0.7222,
    )

    fun calculateRetrievability(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        return (1.0 + FACTOR * (elapsedDays / stability)).pow(DECAY)
    }

    fun calculateNextInterval(stability: Double, targetRetention: Double = TARGET_RETENTION): Int {
        val interval = (stability / FACTOR) * (targetRetention.pow(1.0 / DECAY) - 1.0)
        return interval.roundToInt().coerceIn(1, MAX_INTERVAL_DAYS)
    }

    fun initNewCard(rating: Int): Pair<Double, Double> {
        val initialStability = w[rating - 1]
        val initialDifficulty = (w[4] - exp(w[5] * (rating - 1)) + 1.0).coerceIn(1.0, 10.0)
        return Pair(initialStability, initialDifficulty)
    }

    fun nextStability(difficulty: Double, stability: Double, retrievability: Double, rating: Int): Double {
        return if (rating == 1) {
            w[11] * difficulty.pow(-w[12]) * ((stability + 1.0).pow(w[13]) - 1.0) *
                exp((1.0 - retrievability) * w[14])
        } else {
            val hardPenalty = if (rating == 2) w[15] else 1.0
            val easyBonus = if (rating == 4) w[16] else 1.0
            stability * (1.0 + exp(w[8]) * (11.0 - difficulty) * stability.pow(-w[9]) *
                (exp((1.0 - retrievability) * w[10]) - 1.0) * hardPenalty * easyBonus)
        }
    }

    fun nextDifficulty(difficulty: Double, rating: Int): Double {
        val deltaD = -w[6] * (rating - 3)
        val linearD = difficulty + deltaD * ((10.0 - difficulty) / 9.0)
        return (w[7] * (w[4] - exp(w[5] * 3) + 1.0) + (1.0 - w[7]) * linearD).coerceIn(1.0, 10.0)
    }

    /** FSRS-5 same-day stability update for cards still in (re)learning. */
    fun shortTermStability(stability: Double, rating: Int): Double =
        max(0.01, stability * exp(w[17] * (rating - 3 + w[18])))

    /** Applies [rating] to [card] at [now] and returns the updated memory state. */
    fun schedule(
        card: FsrsCard,
        rating: Rating,
        now: Long,
        targetRetention: Double = TARGET_RETENTION,
    ): FsrsCard {
        val g = rating.value
        val elapsedDays = if (card.state == CardState.New || card.lastReview == 0L) 0.0
        else max(0.0, (now - card.lastReview).toDouble() / DAY_MS)
        val base = card.copy(elapsedDays = elapsedDays.toInt(), reps = card.reps + 1, lastReview = now)

        return when (card.state) {
            CardState.New -> {
                val (s, d) = initNewCard(g)
                when (rating) {
                    Rating.Again -> base.learning(CardState.Learning, s, d, now, 1)
                    Rating.Hard -> base.learning(CardState.Learning, s, d, now, 5)
                    Rating.Good -> base.learning(CardState.Learning, s, d, now, 10)
                    Rating.Easy -> base.review(s, d, now, calculateNextInterval(s, targetRetention))
                }
            }

            CardState.Learning, CardState.Relearning -> {
                val s = shortTermStability(card.stability, g)
                val d = nextDifficulty(card.difficulty, g)
                when (rating) {
                    Rating.Again -> base.learning(card.state, s, d, now, 5)
                    Rating.Hard -> base.learning(card.state, s, d, now, 10)
                    Rating.Good -> base.review(s, d, now, calculateNextInterval(s, targetRetention))
                    Rating.Easy -> {
                        val good = calculateNextInterval(shortTermStability(card.stability, 3), targetRetention)
                        base.review(s, d, now, max(calculateNextInterval(s, targetRetention), good + 1))
                    }
                }
            }

            CardState.Review -> {
                val r = calculateRetrievability(elapsedDays, card.stability)
                val d = nextDifficulty(card.difficulty, g)
                if (rating == Rating.Again) {
                    // FSRS-5: a lapse can never leave the card more stable than it was.
                    val s = min(nextStability(card.difficulty, card.stability, r, 1), card.stability)
                    base.copy(lapses = card.lapses + 1).learning(CardState.Relearning, s, d, now, 10)
                } else {
                    val intervals = reviewIntervals(card, r, targetRetention)
                    val s = nextStability(card.difficulty, card.stability, r, g)
                    base.review(s, d, now, intervals.getValue(rating))
                }
            }
        }
    }

    /** Projected outcome for each rating, used to label the four answer buttons. */
    fun preview(card: FsrsCard, now: Long, targetRetention: Double = TARGET_RETENTION): Map<Rating, FsrsCard> =
        Rating.entries.associateWith { schedule(card, it, now, targetRetention) }

    private fun reviewIntervals(card: FsrsCard, r: Double, targetRetention: Double): Map<Rating, Int> {
        fun ivl(g: Int) = calculateNextInterval(nextStability(card.difficulty, card.stability, r, g), targetRetention)
        var hard = ivl(2)
        var good = ivl(3)
        var easy = ivl(4)
        hard = min(hard, good)
        good = max(good, hard + 1)
        easy = max(easy, good + 1)
        return mapOf(Rating.Hard to hard, Rating.Good to good, Rating.Easy to easy)
    }

    private fun FsrsCard.learning(state: CardState, s: Double, d: Double, now: Long, minutes: Int) =
        copy(state = state, stability = s, difficulty = d, scheduledDays = 0, due = now + minutes * MINUTE_MS)

    private fun FsrsCard.review(s: Double, d: Double, now: Long, days: Int) =
        copy(state = CardState.Review, stability = s, difficulty = d, scheduledDays = days, due = now + days * DAY_MS)
}

/** Short human label for the gap until [due], e.g. "10 min", "6 days", "3 mo". */
fun formatInterval(now: Long, due: Long): String {
    val minutes = ((due - now) / FSRSScheduler.MINUTE_MS).coerceAtLeast(1)
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 60 * 24 -> "${(minutes / 60.0).roundToInt()} h"
        else -> {
            val days = (minutes / (60.0 * 24)).roundToInt()
            when {
                days == 1 -> "1 day"
                days < 31 -> "$days days"
                days < 365 -> "${(days / 30.0).roundToInt()} mo"
                else -> "%.1f yr".format(days / 365.0)
            }
        }
    }
}
