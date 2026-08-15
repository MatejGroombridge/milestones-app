package dev.matejgroombridge.milestones.data.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * The time frame a milestone is scored over — the axis that separates "goals
 * I set for this year" from "records I hold for life".
 *
 * Cadence is a *lens*, not stored state. Every [MilestoneEntry] already
 * carries the day it happened, so a period's entries are just a filter over
 * the one flat list. That means there are no period objects to keep in sync,
 * and past years fall out for free.
 *
 * The enum is deliberately open-ended: adding Monthly or Quarterly later
 * means adding a case and a [periodKeyFor] branch, not reshaping storage.
 */
@Serializable
enum class MilestoneCadence {
    /** Scored over all entries, forever. */
    Lifetime,

    /** Scored over the current calendar year; each year is its own scoreboard. */
    Yearly,
    ;

    /**
     * The period [epochDay] falls into, or `null` for [Lifetime] — which has
     * exactly one, unbounded period.
     *
     * Used as the key that ties a [MilestoneTarget] to the stretch of time
     * it applies to.
     */
    fun periodKeyFor(epochDay: Long): Int? = when (this) {
        Lifetime -> null
        Yearly -> LocalDate.ofEpochDay(epochDay).year
    }

    /** Whether [epochDay] belongs to the period identified by [periodKey]. */
    fun contains(epochDay: Long, periodKey: Int?): Boolean =
        periodKeyFor(epochDay) == periodKey

    val label: String
        get() = when (this) {
            Lifetime -> "All time"
            Yearly -> "Each year"
        }

    val blurb: String
        get() = when (this) {
            Lifetime -> "One lifetime record"
            Yearly -> "Resets every year"
        }

    /** Heading this cadence's milestones sit under on the grid. */
    val sectionTitle: String
        get() = when (this) {
            Lifetime -> "All time"
            Yearly -> "This year"
        }

    companion object {
        val Default: MilestoneCadence = Lifetime
    }
}
