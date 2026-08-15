package dev.matejgroombridge.milestones.data.model

import java.time.LocalDate
import kotlin.math.abs

/**
 * Everything the UI needs about one milestone *as of today*, resolved once.
 *
 * The four kind × cadence combinations answer "what's my number?" four
 * different ways, and screens shouldn't each re-derive that. [Milestone.statsOn]
 * is the single place it happens; cards, rows and dialogs read this.
 *
 * @param periodKey        Year being scored, or null for a lifetime milestone.
 * @param periodEntries    Entries inside that period, oldest first.
 * @param value            The headline figure. Null only for a record with
 *                         nothing logged — a tally with no entries is `0.0`.
 * @param formattedValue   [value] through the milestone's unit, or an em dash.
 * @param bestEntry        Records only: the entry holding [value].
 * @param allTimeValue     The same figure computed over every entry. Worth
 *                         showing as a secondary line on yearly milestones,
 *                         and equal to [value] on lifetime ones.
 * @param activeTarget     The target currently being chased, if any.
 * @param targetProgress   Progress toward [activeTarget] in `0f..1f`.
 * @param lastReached      The most recently cleared target this period.
 * @param awaitingNewTarget A target was cleared and nothing has replaced it —
 *                         the prompt to set the next one.
 * @param lastImprovement  Records only: how much [value] beat the previous best by.
 */
data class MilestoneStats(
    val milestone: Milestone,
    val periodKey: Int?,
    val periodEntries: List<MilestoneEntry>,
    val value: Double?,
    val formattedValue: String,
    val bestEntry: MilestoneEntry?,
    val allTimeValue: Double?,
    val activeTarget: MilestoneTarget?,
    val targetProgress: Float?,
    val lastReached: MilestoneTarget?,
    val awaitingNewTarget: Boolean,
    val lastImprovement: Double?,
) {
    val kind: MilestoneKind get() = milestone.kind
    val cadence: MilestoneCadence get() = milestone.cadence
    val unit: MilestoneUnit get() = milestone.unit

    /** How many entries landed in this period. */
    val periodEntryCount: Int get() = periodEntries.size

    /** The day of the most recent entry in this period. */
    val lastEntryDay: Long? get() = periodEntries.maxOfOrNull { it.epochDay }

    /** [activeTarget] formatted, or null when nothing is being chased. */
    val formattedTarget: String? get() = activeTarget?.let { unit.format(it.value) }

    /**
     * The all-time figure, but only when it's worth showing — i.e. a yearly
     * milestone whose lifetime number differs from this year's.
     */
    val formattedAllTimeIfDistinct: String?
        get() {
            if (cadence != MilestoneCadence.Yearly) return null
            val all = allTimeValue ?: return null
            if (value != null && abs(all - value) < 0.0000001) return null
            return unit.format(all)
        }
}

/**
 * Resolves [MilestoneStats] for [todayEpochDay].
 *
 * Cheap enough to call per recomposition — it's a couple of filters and a
 * fold over a list that is, for a personal tracker, tens of entries at most.
 */
fun Milestone.statsOn(todayEpochDay: Long): MilestoneStats {
    val periodKey = currentPeriodKey(todayEpochDay)
    val periodEntries = entriesIn(periodKey)
    val value = valueOf(periodEntries)
    val bestEntry = if (kind == MilestoneKind.Record) bestOf(periodEntries) else null
    val activeTarget = activeTargetIn(periodKey)
    val lastReached = lastReachedTargetIn(periodKey)

    return MilestoneStats(
        milestone = this,
        periodKey = periodKey,
        periodEntries = periodEntries,
        value = value,
        formattedValue = when {
            value != null -> unit.format(value)
            else -> "—"
        },
        bestEntry = bestEntry,
        allTimeValue = valueOf(entriesByDate),
        activeTarget = activeTarget,
        targetProgress = activeTarget?.let { progressToward(it.value, value, periodEntries) },
        lastReached = lastReached,
        awaitingNewTarget = activeTarget == null && lastReached != null,
        lastImprovement = improvementOver(periodEntries, bestEntry),
    )
}

/**
 * Progress toward [target] in `0f..1f`.
 *
 * Climbing measures from zero, which reads naturally for a tally ("12 of 30
 * books") and for a record you're pushing up. A "lower is better" record has
 * no such floor — zero seconds isn't a meaningful start — so it measures the
 * ground covered from the period's *first* entry down toward the target. A
 * 30:00 → 25:00 journey against a 24:00 goal then reads as most of the way
 * there, rather than as a number over 100%.
 */
private fun Milestone.progressToward(
    target: Double,
    current: Double?,
    periodEntries: List<MilestoneEntry>,
): Float {
    val climbing = kind == MilestoneKind.Tally ||
        direction == MilestoneDirection.HigherIsBetter
    if (climbing) {
        val value = current ?: 0.0
        if (target <= 0.0) return 1f
        return (value / target).toFloat().coerceIn(0f, 1f)
    }
    val value = current ?: return 0f
    val start = periodEntries.firstOrNull()?.value ?: value
    val span = start - target
    if (span <= 0.0) return 1f
    return ((start - value) / span).toFloat().coerceIn(0f, 1f)
}

/** How much [best] beat the runner-up by, as a positive magnitude. Records only. */
private fun Milestone.improvementOver(
    periodEntries: List<MilestoneEntry>,
    best: MilestoneEntry?,
): Double? {
    if (kind != MilestoneKind.Record || best == null) return null
    val runnerUp = bestOf(periodEntries.filterNot { it.id == best.id }) ?: return null
    return abs(best.value - runnerUp.value)
}

/** Calendar year [epochDay] falls in — used for grouping history by period. */
fun yearOf(epochDay: Long): Int = LocalDate.ofEpochDay(epochDay).year
