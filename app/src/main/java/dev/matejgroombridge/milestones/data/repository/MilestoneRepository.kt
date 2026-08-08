package dev.matejgroombridge.milestones.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.milestones.data.model.Milestone
import dev.matejgroombridge.milestones.data.model.MilestoneDirection
import dev.matejgroombridge.milestones.data.model.MilestoneRecord
import dev.matejgroombridge.milestones.data.model.MilestoneUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.milestonesDataStore: DataStore<Preferences> by preferencesDataStore(name = "milestones")

/**
 * Single source of truth for the user's milestones. Backed by a Preferences
 * DataStore that stores the whole list as a JSON-encoded string under one key.
 *
 * For a personal-scale tracker this is intentionally simple — no Room, no
 * migrations, just one JSON blob. Adding new fields to [Milestone] is safe
 * because the parser is configured with `ignoreUnknownKeys = true` and every
 * new field has a default value.
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
     * immediately as the first record, so a user who already knows their
     * current best doesn't have to create-then-log in two steps.
     */
    suspend fun addMilestone(
        name: String,
        todayEpochDay: Long,
        description: String = "",
        iconKey: String = Milestone.DEFAULT_ICON_KEY,
        colorKey: String = Milestone.DEFAULT_COLOR_KEY,
        unit: MilestoneUnit = MilestoneUnit.Default,
        direction: MilestoneDirection = MilestoneDirection.Default,
        target: Double? = null,
        startingValue: Double? = null,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update { current ->
            current + Milestone(
                name = trimmed,
                description = description.trim(),
                iconKey = iconKey,
                colorKey = colorKey,
                unit = unit,
                direction = direction,
                target = target,
                createdAtEpochDay = todayEpochDay,
                records = startingValue?.let {
                    listOf(MilestoneRecord(value = it, epochDay = todayEpochDay))
                } ?: emptyList(),
            )
        }
    }

    /**
     * Replaces the editable fields on an existing milestone. Record history
     * and id are preserved.
     *
     * Changing [unit] does *not* convert stored values — the numbers stay put
     * and only their presentation changes. Switching a "km" milestone to
     * "miles" therefore relabels rather than recalculates, which is the
     * honest behaviour: the app has no idea whether the user meant to
     * reinterpret old readings or correct a mislabelled one.
     */
    suspend fun updateMilestone(
        milestoneId: String,
        name: String,
        description: String,
        iconKey: String,
        colorKey: String,
        unit: MilestoneUnit,
        direction: MilestoneDirection,
        target: Double?,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        update { current ->
            current.map { m ->
                if (m.id == milestoneId) {
                    m.copy(
                        name = trimmedName,
                        description = description.trim(),
                        iconKey = iconKey,
                        colorKey = colorKey,
                        unit = unit,
                        direction = direction,
                        target = target,
                    )
                } else m
            }
        }
    }

    /**
     * Logs [value] against [milestoneId] if — and only if — it beats the
     * current best. Returns `true` when a new record was stored.
     *
     * The check happens here rather than only in the dialog so the
     * strictly-improving invariant holds no matter which entry point calls
     * in, and so two rapid saves can't both slip past a stale UI check.
     */
    suspend fun logRecord(
        milestoneId: String,
        value: Double,
        epochDay: Long,
        note: String = "",
    ): Boolean {
        var accepted = false
        update { current ->
            // DataStore may re-run this transform if another write lands
            // first, so the flag is reset per attempt rather than latched.
            accepted = false
            current.map { m ->
                if (m.id != milestoneId || !m.beats(value)) m
                else {
                    accepted = true
                    m.withRecord(
                        MilestoneRecord(value = value, epochDay = epochDay, note = note.trim()),
                    )
                }
            }
        }
        return accepted
    }

    /**
     * Removes a single record — the escape hatch for a mistyped value.
     *
     * Deleting the current best promotes the previous one back to the top,
     * which is exactly what you want after fat-fingering an unbeatable number.
     */
    suspend fun deleteRecord(milestoneId: String, recordId: String) {
        update { current ->
            current.map { m -> if (m.id == milestoneId) m.withoutRecord(recordId) else m }
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
     * reordered active ones, mirroring how the main grid filters them out
     * anyway.
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
        val current = load(prefs[KEY_MILESTONES_JSON])
        return json.encodeToString(listSerializer, current)
    }

    /**
     * Replace the milestone list with the contents of [rawJson]. Returns the
     * number of milestones imported, or `null` if the JSON couldn't be parsed
     * (the existing list is left untouched in that case).
     */
    suspend fun importJson(rawJson: String): Int? {
        val parsed = runCatching { json.decodeFromString(listSerializer, rawJson) }.getOrNull()
            ?: return null
        update { parsed }
        return parsed.size
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
    }

    private companion object {
        val KEY_MILESTONES_JSON = stringPreferencesKey("milestones_json")
    }
}
