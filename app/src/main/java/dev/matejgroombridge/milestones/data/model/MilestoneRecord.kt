package dev.matejgroombridge.milestones.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One entry in a milestone's history — a moment the user beat their previous
 * best.
 *
 * Records are only ever created for values that actually improve on the
 * current best (see [Milestone.beats]), so a milestone's record list is a
 * strictly-improving chain. That's what makes the progress chart a clean
 * staircase and lets "how much did I improve by?" be answered by comparing
 * two neighbouring entries.
 *
 * @param id       Stable identifier, so a single entry can be deleted without
 *                 disturbing the rest of the chain.
 * @param value    The reading itself, interpreted by the milestone's
 *                 [MilestoneUnit] (seconds for `Time`, otherwise as-is).
 * @param epochDay The day it happened, as `LocalDate.toEpochDay()`. Defaults
 *                 to today in the UI but the user can back-date.
 * @param note     Optional free text — "PB at the Sunday parkrun".
 */
@Serializable
data class MilestoneRecord(
    val id: String = UUID.randomUUID().toString(),
    val value: Double,
    val epochDay: Long,
    val note: String = "",
)
