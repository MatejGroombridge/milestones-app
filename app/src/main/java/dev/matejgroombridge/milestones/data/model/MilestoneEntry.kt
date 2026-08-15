package dev.matejgroombridge.milestones.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One thing that happened, on one day.
 *
 * What [value] means depends on the milestone's [MilestoneKind]:
 *  - **Record** — the result achieved. Only entries that beat the current
 *    best are stored, so a record milestone's entries form a
 *    strictly-improving chain.
 *  - **Tally** — the amount to add, usually `1.0`. The milestone's value is
 *    the sum of its entries.
 *
 * Storing tallies as events rather than running totals is what makes the
 * History screen worth reading: [note] becomes the headline ("Japan", a book
 * title) instead of a bare number, and per-year grouping comes free from
 * [epochDay].
 *
 * @param id       Stable identifier, so a single entry can be deleted without
 *                 disturbing the rest.
 * @param value    Interpreted by the milestone's [MilestoneUnit] — seconds for
 *                 `Time`, otherwise as-is.
 * @param epochDay The day it happened, as `LocalDate.toEpochDay()`. Defaults to
 *                 today in the UI but the user can back-date.
 * @param note     Optional free text — "PB at the Sunday parkrun", "Japan".
 */
@Serializable
data class MilestoneEntry(
    val id: String = UUID.randomUUID().toString(),
    val value: Double,
    val epochDay: Long,
    val note: String = "",
)
