package dev.matejgroombridge.milestones.data.model

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.abs

/**
 * A single goal the user is chasing a personal best on — books read, longest
 * run, fastest 5km, best month of revenue.
 *
 * Schema notes:
 *  - The repository decodes JSON with `ignoreUnknownKeys = true` and every
 *    field below has a default, so adding new fields later stays backwards
 *    compatible.
 *  - [records] is the source of truth. Everything else on this class — the
 *    current best, how much you improved by, progress toward a target — is
 *    derived from it, so there's no denormalised "currentValue" to keep in
 *    sync.
 *
 * @param id                Stable identifier. Generated once on creation.
 * @param name              User-supplied name (e.g. "Fastest 5km").
 * @param description       Optional free-text description.
 * @param iconKey           Key into `MilestoneIcons.catalog`. Falls back to default if unknown.
 * @param colorKey          Key into `MilestoneColors.palette`. Falls back to first colour if unknown.
 * @param unit              How values are entered and displayed.
 * @param direction         Whether bigger or smaller numbers are the better result.
 * @param target            Optional value the user is aiming for. Drives the
 *                          progress bar on the card; `null` = open-ended.
 * @param archived          When true, hidden from the main grid and shown only
 *                          on the Archived screen.
 * @param createdAtEpochDay The day the milestone was created, as `LocalDate.toEpochDay()`.
 * @param records           Chronological chain of personal bests, oldest first.
 */
@Serializable
data class Milestone(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val iconKey: String = DEFAULT_ICON_KEY,
    val colorKey: String = DEFAULT_COLOR_KEY,
    val unit: MilestoneUnit = MilestoneUnit.Default,
    val direction: MilestoneDirection = MilestoneDirection.Default,
    val target: Double? = null,
    val archived: Boolean = false,
    val createdAtEpochDay: Long,
    val records: List<MilestoneRecord> = emptyList(),
) {

    /** Whether anything has been logged yet. */
    val hasRecord: Boolean get() = records.isNotEmpty()

    /**
     * The current personal best.
     *
     * Derived with a max/min rather than "last element" so an imported or
     * hand-edited JSON blob with an out-of-order list still reports the right
     * answer instead of quietly showing a worse value as the record.
     */
    val best: MilestoneRecord?
        get() = when (direction) {
            MilestoneDirection.HigherIsBetter -> records.maxByOrNull { it.value }
            MilestoneDirection.LowerIsBetter -> records.minByOrNull { it.value }
        }

    /** The best value, or `null` when nothing has been logged. */
    val bestValue: Double? get() = best?.value

    /** The record that [best] beat — i.e. the second-best entry. */
    val previousBest: MilestoneRecord?
        get() {
            val current = best ?: return null
            val rest = records.filterNot { it.id == current.id }
            return when (direction) {
                MilestoneDirection.HigherIsBetter -> rest.maxByOrNull { it.value }
                MilestoneDirection.LowerIsBetter -> rest.minByOrNull { it.value }
            }
        }

    /** Records ordered for charting: oldest day first, ties broken by list order. */
    val recordsByDate: List<MilestoneRecord> get() = records.sortedBy { it.epochDay }

    /** Whether [value] would be a new personal best. */
    fun beats(value: Double): Boolean = direction.isBetter(value, bestValue)

    /**
     * How much the latest record improved on the one before it, as a positive
     * magnitude. `null` when there's nothing to compare against.
     */
    val lastImprovement: Double?
        get() {
            val current = bestValue ?: return null
            val previous = previousBest?.value ?: return null
            return abs(current - previous)
        }

    /**
     * Total distance travelled from the first record to the best one, as a
     * positive magnitude. This is the headline "how far have I come?" number.
     */
    val totalImprovement: Double?
        get() {
            if (records.size < 2) return null
            val first = recordsByDate.first().value
            val current = bestValue ?: return null
            return abs(current - first)
        }

    /**
     * Progress toward [target] in `0f..1f`, or `null` when no target is set.
     *
     * For "higher is better" the run is measured from the first record (or
     * from zero when there's only one) up to the target. For "lower is
     * better" it's measured downward from the starting point toward the
     * target, so a 30:00 → 25:00 journey against a 24:00 goal reads as most
     * of the way there rather than as a number over 100%.
     */
    val targetProgress: Float?
        get() {
            val goal = target ?: return null
            val current = bestValue ?: return 0f
            val start = recordsByDate.firstOrNull()?.value ?: current
            return when (direction) {
                MilestoneDirection.HigherIsBetter -> {
                    // Counting up from zero reads more naturally than counting
                    // from the first record, which would show a brand-new
                    // milestone at 0% forever until it improved once.
                    if (goal <= 0.0) 1f else (current / goal).toFloat().coerceIn(0f, 1f)
                }
                MilestoneDirection.LowerIsBetter -> {
                    val span = start - goal
                    if (span <= 0.0) 1f else ((start - current) / span).toFloat().coerceIn(0f, 1f)
                }
            }
        }

    /** Whether the target has been met or beaten. */
    val targetReached: Boolean
        get() {
            val goal = target ?: return false
            val current = bestValue ?: return false
            return when (direction) {
                MilestoneDirection.HigherIsBetter -> current >= goal
                MilestoneDirection.LowerIsBetter -> current <= goal
            }
        }

    /** How far the best still is from [target], as a positive magnitude. */
    val remainingToTarget: Double?
        get() {
            val goal = target ?: return null
            val current = bestValue ?: return goal
            if (targetReached) return 0.0
            return abs(goal - current)
        }

    /** Days since the last record was set, or `null` when nothing is logged. */
    fun daysSinceLastRecord(todayEpochDay: Long): Long? {
        val latest = records.maxByOrNull { it.epochDay } ?: return null
        return (todayEpochDay - latest.epochDay).coerceAtLeast(0L)
    }

    /** The best value already formatted for display, or a placeholder dash. */
    val formattedBest: String get() = bestValue?.let(unit::format) ?: "—"

    /** [target] formatted for display, or `null` when open-ended. */
    val formattedTarget: String? get() = target?.let(unit::format)

    /**
     * Adds [record] and returns the updated milestone. Callers are expected to
     * have checked [beats] first — the repository does, which is what keeps
     * [records] a strictly-improving chain.
     */
    fun withRecord(record: MilestoneRecord): Milestone = copy(records = records + record)

    /** Removes the record with [recordId], if present. */
    fun withoutRecord(recordId: String): Milestone =
        copy(records = records.filterNot { it.id == recordId })

    companion object {
        const val DEFAULT_ICON_KEY = "trophy"
        const val DEFAULT_COLOR_KEY = "blush"
    }
}
