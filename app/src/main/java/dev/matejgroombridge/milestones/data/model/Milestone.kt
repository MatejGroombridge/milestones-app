package dev.matejgroombridge.milestones.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A goal the user is chasing — a personal best to beat, or a count to run up,
 * scored either for life or for the current year.
 *
 * Two axes describe every milestone:
 *  - [kind] — is this the *best* of many attempts, or the *sum* of events?
 *  - [cadence] — does it run forever, or reset each year?
 *
 * That 2×2 is what separates "my furthest run" from "books read this year",
 * and it's why almost every derived figure here is computed *for a period*
 * rather than over the whole list. See [statsOn], which is the single place
 * those four combinations are resolved.
 *
 * Schema notes:
 *  - The repository decodes JSON with `ignoreUnknownKeys = true` and every
 *    field has a default, so adding fields later stays backwards compatible.
 *  - [entries] keeps the wire name `records` from the first release, and
 *    [legacyTarget] is folded into [targets] by [migrated] on load, so v0.1.0
 *    data opens unchanged.
 *  - [entries] and [targets] are the source of truth. Nothing denormalised is
 *    stored, so there's no cached "current value" to fall out of sync.
 *
 * @param id                Stable identifier. Generated once on creation.
 * @param name              User-supplied name (e.g. "Fastest 5km").
 * @param description       Optional free-text description.
 * @param iconKey           Key into `MilestoneIcons.catalog`. Unknown keys fall back.
 * @param colorKey          Key into `MilestoneColors.palette`. Unknown keys fall back.
 * @param kind              Best-of-attempts, or a running count.
 * @param cadence           Scored for life, or per calendar year.
 * @param unit              How values are entered and displayed.
 * @param direction         Which way a record improves. Ignored for tallies.
 * @param archived          Hidden from the grid, shown only on the Archived screen.
 * @param createdAtEpochDay The day the milestone was created.
 * @param entries           Everything logged, in insertion order.
 * @param targets           Targets set against this milestone, active and cleared.
 */
@Serializable
data class Milestone(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val iconKey: String = DEFAULT_ICON_KEY,
    val colorKey: String = DEFAULT_COLOR_KEY,
    val kind: MilestoneKind = MilestoneKind.Default,
    val cadence: MilestoneCadence = MilestoneCadence.Default,
    val unit: MilestoneUnit = MilestoneUnit.Default,
    val direction: MilestoneDirection = MilestoneDirection.Default,
    val archived: Boolean = false,
    val createdAtEpochDay: Long,
    @SerialName("records")
    val entries: List<MilestoneEntry> = emptyList(),
    val targets: List<MilestoneTarget> = emptyList(),
    /**
     * v0.1.0 stored a single `target: Double?`. Kept only so that JSON still
     * parses; [migrated] converts it into a [MilestoneTarget] and clears it.
     * Never read anywhere else — use [targets].
     */
    @SerialName("target")
    val legacyTarget: Double? = null,
) {

    /** Entries in the order they happened. */
    val entriesByDate: List<MilestoneEntry> get() = entries.sortedBy { it.epochDay }

    /** Whether anything has been logged at all. */
    val hasEntries: Boolean get() = entries.isNotEmpty()

    /**
     * Folds the v0.1.0 [legacyTarget] into [targets] so the rest of the app
     * only ever deals with the modern shape.
     *
     * Idempotent — once [legacyTarget] is null this returns `this`, so calling
     * it on every load is free after the first save.
     */
    fun migrated(): Milestone {
        val legacy = legacyTarget ?: return this
        // A legacy target was always lifetime-scoped (v0.1.0 had no cadence)
        // and never carried a "reached" stamp, so it becomes an active target
        // with the milestone's creation day as its start.
        val converted = MilestoneTarget(
            value = legacy,
            setOnEpochDay = createdAtEpochDay,
            periodKey = null,
        )
        return copy(
            targets = if (targets.isEmpty()) listOf(converted) else targets,
            legacyTarget = null,
        )
    }

    // --- Period-scoped reads -------------------------------------------------

    /** The period [todayEpochDay] falls into: the year, or null for lifetime. */
    fun currentPeriodKey(todayEpochDay: Long): Int? = cadence.periodKeyFor(todayEpochDay)

    /** Entries belonging to [periodKey], oldest first. */
    fun entriesIn(periodKey: Int?): List<MilestoneEntry> =
        entriesByDate.filter { cadence.contains(it.epochDay, periodKey) }

    /**
     * The headline number for [entriesInPeriod] — best-of for a record, sum
     * for a tally.
     *
     * Records return `null` when there's nothing logged (the UI shows a dash);
     * tallies return `0.0`, because "no books yet" genuinely is zero books.
     */
    fun valueOf(entriesInPeriod: List<MilestoneEntry>): Double? = when (kind) {
        MilestoneKind.Tally -> entriesInPeriod.sumOf { it.value }
        MilestoneKind.Record -> bestOf(entriesInPeriod)?.value
    }

    /** The winning entry among [candidates], per [direction]. Records only. */
    fun bestOf(candidates: List<MilestoneEntry>): MilestoneEntry? = when (direction) {
        MilestoneDirection.HigherIsBetter -> candidates.maxByOrNull { it.value }
        MilestoneDirection.LowerIsBetter -> candidates.minByOrNull { it.value }
    }

    /**
     * Whether [value] would be stored if logged on [todayEpochDay].
     *
     * Tallies accept anything — every event counts. Records only accept a
     * value that beats the current period's best, which is what keeps their
     * history strictly improving. Note the comparison is against the
     * *period's* best, so a yearly record milestone starts fresh each January
     * rather than being locked behind an all-time peak.
     */
    fun accepts(value: Double, todayEpochDay: Long): Boolean {
        if (kind == MilestoneKind.Tally) return true
        val periodBest = valueOf(entriesIn(currentPeriodKey(todayEpochDay)))
        return direction.isBetter(value, periodBest)
    }

    // --- Targets -------------------------------------------------------------

    /** Targets belonging to [periodKey]. */
    fun targetsIn(periodKey: Int?): List<MilestoneTarget> =
        targets.filter { it.periodKey == periodKey }

    /** The target currently being chased in [periodKey], if any. */
    fun activeTargetIn(periodKey: Int?): MilestoneTarget? =
        targetsIn(periodKey).lastOrNull { !it.isReached }

    /** The most recently cleared target in [periodKey], if any. */
    fun lastReachedTargetIn(periodKey: Int?): MilestoneTarget? =
        targetsIn(periodKey).filter { it.isReached }.maxByOrNull { it.reachedOnEpochDay ?: 0L }

    /**
     * Brings the current period's targets in line with what's actually been
     * logged: stamps one that's now been met, and un-stamps one that no longer
     * is (which happens when the user deletes the entry that cleared it).
     *
     * Deliberately scoped to the current period. Reconciling every target
     * would let a rollover un-stamp goals the user genuinely hit in past
     * years, and history shouldn't rewrite itself.
     */
    fun settleTargets(todayEpochDay: Long): Milestone {
        val periodKey = currentPeriodKey(todayEpochDay)
        val current = valueOf(entriesIn(periodKey))
        var changed = false
        val settled = targets.map { target ->
            if (target.periodKey != periodKey) return@map target
            val met = target.isMetBy(current, kind, direction)
            when {
                met && !target.isReached -> {
                    changed = true
                    target.copy(reachedOnEpochDay = todayEpochDay)
                }
                !met && target.isReached -> {
                    changed = true
                    target.copy(reachedOnEpochDay = null)
                }
                else -> target
            }
        }
        return if (changed) copy(targets = settled) else this
    }

    /**
     * Sets [value] as the target for the current period, replacing the active
     * one if there is one. Passing `null` clears it.
     */
    fun withTarget(value: Double?, todayEpochDay: Long): Milestone {
        val periodKey = currentPeriodKey(todayEpochDay)
        val active = activeTargetIn(periodKey)
        val next = when {
            value == null -> targets.filterNot { it.id == active?.id }
            active != null -> targets.map {
                if (it.id == active.id) it.copy(value = value, setOnEpochDay = todayEpochDay) else it
            }
            else -> targets + MilestoneTarget(
                value = value,
                setOnEpochDay = todayEpochDay,
                periodKey = periodKey,
            )
        }
        return copy(targets = next)
    }

    // --- Mutations -----------------------------------------------------------

    /** Adds [entry]. Callers check [accepts] first; the repository does. */
    fun withEntry(entry: MilestoneEntry): Milestone = copy(entries = entries + entry)

    /** Removes the entry with [entryId], if present. */
    fun withoutEntry(entryId: String): Milestone =
        copy(entries = entries.filterNot { it.id == entryId })

    companion object {
        const val DEFAULT_ICON_KEY = "trophy"
        const val DEFAULT_COLOR_KEY = "blush"
    }
}
