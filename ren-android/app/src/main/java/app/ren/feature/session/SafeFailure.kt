package app.ren.feature.session

/**
 * Safe-failure rules for a review round: a miss never stalls the progress bar, a missed card comes
 * back at the end of the round, and the round closes on a card the learner recalled.
 */
class SafeFailure(
    private val maxRequeues: Int = 3,
    private val maxRescues: Int = 3,
) {
    /** Never decreases, even when requeued cards grow the round. */
    var progress = 0f
        private set

    private val requeues = HashMap<String, Int>()
    private var rescues = 0
    private var lastMissed: String? = null

    fun advanceTo(fraction: Float): Float {
        progress = maxOf(progress, fraction.coerceIn(0f, 1f))
        return progress
    }

    /** The learner has answered [itemId]; a miss is remembered until a later card is recalled. */
    fun record(itemId: String, recalled: Boolean) {
        lastMissed = if (recalled) null else itemId
    }

    /** Whether a card that was missed (or is still learning) may be queued again this round. */
    fun requeue(itemId: String): Boolean {
        val n = requeues.getOrDefault(itemId, 0)
        if (n >= maxRequeues) return false
        requeues[itemId] = n + 1
        return true
    }

    /**
     * Called when the queue runs dry: the card to show once more (with its answer as a hint) so the
     * round ends on a success, or null when the last card was recalled or the retries are spent.
     */
    fun rescue(): String? {
        val id = lastMissed ?: return null
        if (rescues >= maxRescues) return null
        rescues++
        return id
    }
}
