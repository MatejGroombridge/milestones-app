package dev.matejgroombridge.milestones.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A number the user is currently working toward, and — once cleared — a
 * record that they did.
 *
 * Targets are a history rather than a single mutable field so that reaching
 * one isn't a dead end. When an entry crosses the active target it gets
 * stamped with [reachedOnEpochDay] and the user is offered a fresh one on the
 * same milestone, which is the whole point: chasing 100kg on the bench and
 * then chasing 110kg is one story, not two milestones.
 *
 * Cleared targets also earn their keep visually — they render as faded dashed
 * lines on the progress chart, turning the goal history into a picture of how
 * far the milestone has come.
 *
 * @param id                Stable identifier.
 * @param value             The number to reach, in the milestone's unit.
 * @param setOnEpochDay     When the user committed to it.
 * @param periodKey         The period this target belongs to — the year for a
 *                          `Yearly` milestone, `null` for a `Lifetime` one.
 *                          See [MilestoneCadence.periodKeyFor].
 * @param reachedOnEpochDay When it was cleared, or `null` while still active.
 */
@Serializable
data class MilestoneTarget(
    val id: String = UUID.randomUUID().toString(),
    val value: Double,
    val setOnEpochDay: Long,
    val periodKey: Int? = null,
    val reachedOnEpochDay: Long? = null,
) {
    val isReached: Boolean get() = reachedOnEpochDay != null

    /**
     * Whether [current] satisfies this target.
     *
     * Tallies and "higher is better" records need to reach *or exceed* it;
     * a "lower is better" record needs to come in at or under.
     */
    fun isMetBy(current: Double?, kind: MilestoneKind, direction: MilestoneDirection): Boolean {
        if (current == null) return false
        val climbing = kind == MilestoneKind.Tally ||
            direction == MilestoneDirection.HigherIsBetter
        return if (climbing) current >= value else current <= value
    }
}
