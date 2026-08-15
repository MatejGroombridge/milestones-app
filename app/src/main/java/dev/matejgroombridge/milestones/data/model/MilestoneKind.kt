package dev.matejgroombridge.milestones.data.model

import kotlinx.serialization.Serializable

/**
 * What kind of number a milestone holds — the axis that decides what an
 * entry *means*.
 *
 * This is the difference between "my furthest run" and "countries I've
 * visited". Both climb, but one is the best of many attempts and the other
 * is a count of events, and conflating them is what makes logging a tally
 * feel wrong: you end up typing the running total (`33`) when what actually
 * happened was a single event (`+1`, "Japan").
 */
@Serializable
enum class MilestoneKind {
    /**
     * The best single result. Entries are attempts; the milestone's value is
     * whichever one wins per [MilestoneDirection]. Only entries that beat the
     * current best are stored, which keeps the history a strictly-improving
     * chain.
     */
    Record,

    /**
     * A running count. Entries are events and each carries an amount to add
     * (usually 1); the milestone's value is their sum. Always climbs, so
     * [MilestoneDirection] doesn't apply.
     */
    Tally,
    ;

    /** Whether [MilestoneDirection] is meaningful for this kind. */
    val hasDirection: Boolean get() = this == Record

    val label: String
        get() = when (this) {
            Record -> "Personal best"
            Tally -> "Running total"
        }

    val blurb: String
        get() = when (this) {
            Record -> "Best single result"
            Tally -> "Counts up as you go"
        }

    companion object {
        val Default: MilestoneKind = Record
    }
}
