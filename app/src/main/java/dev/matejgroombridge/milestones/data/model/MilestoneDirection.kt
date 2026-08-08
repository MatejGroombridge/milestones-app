package dev.matejgroombridge.milestones.data.model

import kotlinx.serialization.Serializable

/**
 * Which way a milestone improves.
 *
 * Most goals are "bigger is better" (books read, distance run, revenue), but
 * anything timed is the opposite — a faster 5km is a *smaller* number. The
 * direction is what makes [isBetter] the single source of truth for "does
 * this new value beat the record?", so nothing else in the app needs to
 * special-case timed goals.
 */
@Serializable
enum class MilestoneDirection {
    /** A larger value is a better result. */
    HigherIsBetter,

    /** A smaller value is a better result — times, paces, weights to cut. */
    LowerIsBetter,
    ;

    /**
     * Whether [candidate] beats [current]. A milestone with no record yet
     * passes `null` for [current], in which case any value is a new best.
     *
     * Equal values deliberately do *not* count: matching your record isn't
     * beating it, and letting equal values through would fill the history
     * with duplicate entries.
     */
    fun isBetter(candidate: Double, current: Double?): Boolean {
        if (current == null) return true
        return when (this) {
            HigherIsBetter -> candidate > current
            LowerIsBetter -> candidate < current
        }
    }

    /** Human-readable summary for chips and pickers. */
    val label: String
        get() = when (this) {
            HigherIsBetter -> "Higher is better"
            LowerIsBetter -> "Lower is better"
        }

    /** Short form for tight spaces (cards, list rows). */
    val shortLabel: String
        get() = when (this) {
            HigherIsBetter -> "Higher"
            LowerIsBetter -> "Lower"
        }

    companion object {
        val Default: MilestoneDirection = HigherIsBetter
    }
}
