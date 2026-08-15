package dev.matejgroombridge.milestones.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.milestones.data.model.Milestone
import dev.matejgroombridge.milestones.data.model.MilestoneCadence
import dev.matejgroombridge.milestones.data.model.MilestoneDirection
import dev.matejgroombridge.milestones.data.model.MilestoneEntry
import dev.matejgroombridge.milestones.data.model.MilestoneKind
import dev.matejgroombridge.milestones.data.model.MilestoneTarget
import dev.matejgroombridge.milestones.data.model.MilestoneUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.milestonesDataStore: DataStore<Preferences> by preferencesDataStore(name = "milestones")

/**
 * What happened when an entry was logged.
 *
 * @param accepted      False only when a record milestone rejected a value
 *                      that didn't beat its best. Tallies always accept.
 * @param entry         The stored entry, so the caller can offer an undo.
 * @param reachedTarget Non-null when this entry cleared the active target —
 *                      the cue for the "set a new one" celebration.
 */
data class LogOutcome(
    val accepted: Boolean,
    val entry: MilestoneEntry? = null,
    val reachedTarget: MilestoneTarget? = null,
    /** The milestone's kind, so callers can tell a new best from a +1. */
    val kind: MilestoneKind? = null,
)

/**
 * Single source of truth for the user's milestones. Backed by a Preferences
 * DataStore that stores the whole list as a JSON-encoded string under one key.
 *
 * For a personal-scale tracker this is intentionally simple — no Room, no
 * migrations table, just one JSON blob. Schema changes stay safe because the
 * parser ignores unknown keys, every field has a default, and [Milestone.migrated]
 * folds legacy shapes forward on load.
 */
class MilestoneRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // Permit defaults to be omitted in serialized form for older blobs.
        isLenient = true
    }

    private val listSerializer = ListSerializer(Milestone.serializer())

    val milestones: Flow<List<Milestone>> = context.milestonesDataStore.data.map { prefs ->
        load(prefs[KEY_MILESTONES_JSON])
    }

    /**
     * Creates a new milestone. When [startingValue] is non-null it's logged
     * immediately as the first entry, so a user who already knows their
     * current best (or count) doesn't have to create-then-log in two steps.
     */
    suspend fun addMilestone(
        name: String,
        todayEpochDay: Long,
        description: String = "",
        iconKey: String = Milestone.DEFAULT_ICON_KEY,
        colorKey: String = Milestone.DEFAULT_COLOR_KEY,
        kind: MilestoneKind = MilestoneKind.Default,
        cadence: MilestoneCadence = MilestoneCadence.Default,
        unit: MilestoneUnit = MilestoneUnit.Default,
        direction: MilestoneDirection = MilestoneDirection.Default,
        target: Double? = null,
        startingValue: Double? = null,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update { current ->
            val created = Milestone(
                name = trimmed,
                description = description.trim(),
                iconKey = iconKey,
                colorKey = colorKey,
                kind = kind,
                cadence = cadence,
                unit = unit,
                // A tally only ever climbs, so its direction is fixed rather
                // than left to whatever the editor happened to be showing.
                direction = if (kind == MilestoneKind.Tally) {
                    MilestoneDirection.HigherIsBetter
                } else direction,
                createdAtEpochDay = todayEpochDay,
                entries = startingValue?.let {
                    listOf(MilestoneEntry(value = it, epochDay = todayEpochDay))
                } ?: emptyList(),
            )
            current + created
                .withTarget(target, todayEpochDay)
                .settleTargets(todayEpochDay)
        }
    }

    /**
     * Replaces the editable fields on an existing milestone. Entry history is
     * preserved.
     *
     * Changing [unit] does *not* convert stored values — the numbers stay put
     * and only their presentation changes. Switching a "km" milestone to
     * miles therefore relabels rather than recalculates, which is the honest
     * behaviour: the app has no idea whether the user meant to reinterpret
     * old readings or fix a mislabelled one.
     *
     * Changing [kind] or [cadence] likewise leaves entries alone, but does
     * re-settle targets — flipping a milestone to Yearly moves the goalposts
     * to this year's numbers, which can legitimately reopen a cleared target.
     */
    suspend fun updateMilestone(
        milestoneId: String,
        name: String,
        description: String,
        iconKey: String,
        colorKey: String,
        kind: MilestoneKind,
        cadence: MilestoneCadence,
        unit: MilestoneUnit,
        direction: MilestoneDirection,
        target: Double?,
        todayEpochDay: Long,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        update { current ->
            current.map { m ->
                if (m.id != milestoneId) m
                else m.copy(
                    name = trimmedName,
                    description = description.trim(),
                    iconKey = iconKey,
                    colorKey = colorKey,
                    kind = kind,
                    cadence = cadence,
                    unit = unit,
                    direction = if (kind == MilestoneKind.Tally) {
                        MilestoneDirection.HigherIsBetter
                    } else direction,
                ).withTarget(target, todayEpochDay).settleTargets(todayEpochDay)
            }
        }
    }

    /**
     * Logs [value] against [milestoneId].
     *
     * A record only accepts a value that beats its current best; a tally
     * accepts everything. The check lives here rather than only in the dialog
     * so the invariant holds no matter which entry point calls in, and so two
     * rapid saves can't both slip past a stale UI check.
     */
    suspend fun logEntry(
        milestoneId: String,
        value: Double,
        epochDay: Long,
        note: String = "",
        todayEpochDay: Long,
    ): LogOutcome {
        var outcome = LogOutcome(accepted = false)
        update { current ->
            // DataStore may re-run this transform if another write lands
            // first, so the outcome is reset per attempt rather than latched.
            outcome = LogOutcome(accepted = false)
            current.map { m ->
                if (m.id != milestoneId || !m.accepts(value, todayEpochDay)) return@map m

                val entry = MilestoneEntry(value = value, epochDay = epochDay, note = note.trim())
                val periodKey = m.currentPeriodKey(todayEpochDay)
                val before = m.activeTargetIn(periodKey)
                val after = m.withEntry(entry).settleTargets(todayEpochDay)
                // A target counts as "just reached" only if it was the one
                // being chased a moment ago and is now stamped — so a
                // back-dated entry that lands in a closed period can't fire
                // a celebration for a goal that was already done.
                val reached = before
                    ?.let { active -> after.targets.firstOrNull { it.id == active.id } }
                    ?.takeIf { it.isReached }

                outcome = LogOutcome(
                    accepted = true,
                    entry = entry,
                    reachedTarget = reached,
                    kind = m.kind,
                )
                after
            }
        }
        return outcome
    }

    /**
     * Removes a single entry — the escape hatch for a mistyped value.
     *
     * Deleting a record's current best promotes the previous one back to the
     * top, which is exactly what's needed after fat-fingering an unbeatable
     * number. Targets are re-settled afterwards, so a goal that was only
     * cleared by the deleted entry reopens.
     */
    suspend fun deleteEntry(milestoneId: String, entryId: String, todayEpochDay: Long) {
        update { current ->
            current.map { m ->
                if (m.id == milestoneId) m.withoutEntry(entryId).settleTargets(todayEpochDay) else m
            }
        }
    }

    /** Sets (or with `null`, clears) the target for the current period. */
    suspend fun setTarget(milestoneId: String, value: Double?, todayEpochDay: Long) {
        update { current ->
            current.map { m ->
                if (m.id == milestoneId) {
                    m.withTarget(value, todayEpochDay).settleTargets(todayEpochDay)
                } else m
            }
        }
    }

    suspend fun setArchived(milestoneId: String, archived: Boolean) {
        update { current ->
            current.map { m -> if (m.id == milestoneId) m.copy(archived = archived) else m }
        }
    }

    suspend fun deleteMilestone(milestoneId: String) {
        update { current -> current.filterNot { it.id == milestoneId } }
    }

    /**
     * Reorder the active (non-archived) milestones to match [orderedActiveIds].
     * Archived milestones keep their relative order and are appended after the
     * reordered active ones, mirroring how the grid filters them out anyway.
     *
     * IDs that don't correspond to a current milestone are silently ignored,
     * and any active milestone missing from the list is appended in its
     * previous relative order — this makes the call idempotent and tolerant
     * of rapid updates.
     */
    suspend fun setOrdering(orderedActiveIds: List<String>) {
        update { current ->
            val byId = current.associateBy { it.id }
            val activeOrdered = orderedActiveIds.mapNotNull { byId[it] }
                .filterNot { it.archived }
            val activeOrderedIds = activeOrdered.map { it.id }.toSet()
            val activeRemaining = current.filterNot { it.archived || it.id in activeOrderedIds }
            val archived = current.filter { it.archived }
            activeOrdered + activeRemaining + archived
        }
    }

    /**
     * Serialise the current list to a JSON string suitable for export to a
     * file. Encodes defaults so older versions of the app round-trip cleanly.
     */
    suspend fun exportJson(): String {
        val prefs = context.milestonesDataStore.data.first()
        return json.encodeToString(listSerializer, load(prefs[KEY_MILESTONES_JSON]))
    }

    /**
     * Replace the milestone list with the contents of [rawJson]. Returns the
     * number imported, or `null` if the JSON couldn't be parsed (the existing
     * list is left untouched in that case).
     */
    suspend fun importJson(rawJson: String): Int? {
        val parsed = runCatching { json.decodeFromString(listSerializer, rawJson) }.getOrNull()
            ?: return null
        val migrated = parsed.map { it.migrated() }
        update { migrated }
        return migrated.size
    }

    private suspend fun update(block: (List<Milestone>) -> List<Milestone>) {
        context.milestonesDataStore.edit { prefs ->
            val existing = load(prefs[KEY_MILESTONES_JSON])
            val updated = block(existing)
            prefs[KEY_MILESTONES_JSON] = json.encodeToString(listSerializer, updated)
        }
    }

    private fun load(raw: String?): List<Milestone> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }
            .getOrDefault(emptyList())
            // Fold v0.1.0's single `target` field forward on the way out, so
            // nothing downstream has to know the old shape ever existed.
            .map { it.migrated() }
    }

    private companion object {
        val KEY_MILESTONES_JSON = stringPreferencesKey("milestones_json")
    }
}
