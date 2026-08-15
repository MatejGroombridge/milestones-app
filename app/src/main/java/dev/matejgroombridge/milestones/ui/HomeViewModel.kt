package dev.matejgroombridge.milestones.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.milestones.data.model.Milestone
import dev.matejgroombridge.milestones.data.model.MilestoneCadence
import dev.matejgroombridge.milestones.data.model.MilestoneDirection
import dev.matejgroombridge.milestones.data.model.MilestoneEntry
import dev.matejgroombridge.milestones.data.model.MilestoneKind
import dev.matejgroombridge.milestones.data.model.MilestoneStats
import dev.matejgroombridge.milestones.data.model.MilestoneTarget
import dev.matejgroombridge.milestones.data.model.MilestoneUnit
import dev.matejgroombridge.milestones.data.model.statsOn
import dev.matejgroombridge.milestones.data.repository.MilestoneRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/**
 * One row in the History feed. Records and tallies read very differently —
 * "2.1 km better" versus "Japan · 33 this year" — and clearing a target is
 * worth a row of its own, so the feed is a small sealed hierarchy rather than
 * a list of entries.
 */
sealed interface HistoryItem {
    val milestone: Milestone
    val epochDay: Long

    /**
     * Ties are broken by this within a day. Entries use their position in the
     * period so several logged on one day still read newest-first, and a
     * cleared target uses [TARGET_SORT_KEY] so it sits above the entry that
     * cleared it.
     */
    val sortKey: Int

    data class Logged(
        override val milestone: Milestone,
        val entry: MilestoneEntry,
        /** Records: how much this beat the previous best by. */
        val improvement: Double?,
        /** Tallies: the running total within this entry's period, after it. */
        val runningTotal: Double?,
        /** Whether this is the best entry of its period. Records only. */
        val isPeriodBest: Boolean,
        override val sortKey: Int,
    ) : HistoryItem {
        override val epochDay: Long get() = entry.epochDay
    }

    data class TargetCleared(
        override val milestone: Milestone,
        val target: MilestoneTarget,
        override val sortKey: Int = TARGET_SORT_KEY,
    ) : HistoryItem {
        override val epochDay: Long get() = target.reachedOnEpochDay ?: 0L
    }

    companion object {
        const val TARGET_SORT_KEY = Int.MAX_VALUE
    }
}

/**
 * What the Milestones screen should react to after something is logged.
 *
 * One channel rather than three, because a single tap can legitimately do all
 * of it at once: store an undoable entry, be a new personal best worth
 * confetti, and clear a target worth a celebration sheet.
 */
data class LogEvent(
    val milestoneId: String,
    val entryId: String?,
    val accepted: Boolean,
    val reachedTarget: MilestoneTarget?,
    /** Non-null when the UI should offer an undo snackbar — the quick +1 path. */
    val undoLabel: String? = null,
    /**
     * Whether this is worth a celebration.
     *
     * A record entry always is — it only gets stored if it beat the previous
     * best. A tally increment is not: counting your twelfth book shouldn't
     * fire confetti, or a session of catching up would strobe. Clearing a
     * target counts either way.
     */
    val worthCelebrating: Boolean = false,
)

/**
 * Top-level UI state shared by every screen. Active milestones arrive already
 * split by cadence and resolved to [MilestoneStats], so screens never
 * re-derive the kind × cadence matrix themselves.
 */
data class HomeUiState(
    val yearly: List<MilestoneStats> = emptyList(),
    val lifetime: List<MilestoneStats> = emptyList(),
    val archivedMilestones: List<Milestone> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
) {
    /**
     * Active milestones in display order — yearly group first, then lifetime.
     * This is also the order reordering writes back, which keeps the stored
     * list contiguous by group instead of interleaved.
     */
    val activeStats: List<MilestoneStats> get() = yearly + lifetime

    val activeMilestones: List<Milestone> get() = activeStats.map { it.milestone }

    val hasNoMilestones: Boolean get() = yearly.isEmpty() && lifetime.isEmpty()

    /** Headings only earn their place once both groups actually exist. */
    val showSectionHeaders: Boolean get() = yearly.isNotEmpty() && lifetime.isNotEmpty()

    fun statsFor(milestoneId: String): MilestoneStats? =
        activeStats.firstOrNull { it.milestone.id == milestoneId }
}

class HomeViewModel(
    private val repository: MilestoneRepository,
) : ViewModel() {

    private val today: Long get() = LocalDate.now().toEpochDay()

    /**
     * Emitted after every log attempt. Driven by the repository's return
     * value rather than by the dialog's own validation, so a value that loses
     * a race and gets rejected never fires a celebration for something that
     * wasn't stored.
     */
    private val _logEvents = MutableSharedFlow<LogEvent>(extraBufferCapacity = 8)
    val logEvents: SharedFlow<LogEvent> = _logEvents.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = repository.milestones
        .map { milestones ->
            val todayEpochDay = today
            val active = milestones.filterNot { it.archived }
            val stats = active.map { it.statsOn(todayEpochDay) }
            HomeUiState(
                yearly = stats.filter { it.cadence == MilestoneCadence.Yearly },
                lifetime = stats.filter { it.cadence == MilestoneCadence.Lifetime },
                archivedMilestones = milestones.filter { it.archived },
                history = buildHistory(active),
                todayEpochDay = todayEpochDay,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(todayEpochDay = today),
        )

    fun addMilestone(
        name: String,
        description: String,
        iconKey: String,
        colorKey: String,
        kind: MilestoneKind,
        cadence: MilestoneCadence,
        unit: MilestoneUnit,
        direction: MilestoneDirection,
        target: Double?,
        startingValue: Double?,
    ) {
        viewModelScope.launch {
            repository.addMilestone(
                name = name,
                todayEpochDay = today,
                description = description,
                iconKey = iconKey,
                colorKey = colorKey,
                kind = kind,
                cadence = cadence,
                unit = unit,
                direction = direction,
                target = target,
                startingValue = startingValue,
            )
        }
    }

    fun updateMilestone(
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
    ) {
        viewModelScope.launch {
            repository.updateMilestone(
                milestoneId, name, description, iconKey, colorKey,
                kind, cadence, unit, direction, target, today,
            )
        }
    }

    /** Logs an entry from the full sheet — a record's value, or a tally's amount. */
    fun logEntry(milestoneId: String, value: Double, epochDay: Long, note: String) {
        submit(milestoneId, value, epochDay, note, undoLabel = null)
    }

    /**
     * The quick +1 behind a tap on a tally card. Emits an undo label so the
     * screen can offer a snackbar — the tap is instant precisely because
     * taking it back is instant too.
     */
    fun quickAdd(milestoneId: String, name: String, unit: MilestoneUnit) {
        submit(
            milestoneId = milestoneId,
            value = 1.0,
            epochDay = today,
            note = "",
            undoLabel = "${unit.formatMagnitude(1.0)} · $name",
        )
    }

    private fun submit(
        milestoneId: String,
        value: Double,
        epochDay: Long,
        note: String,
        undoLabel: String?,
    ) {
        viewModelScope.launch {
            val outcome = repository.logEntry(
                milestoneId = milestoneId,
                value = value,
                epochDay = epochDay,
                note = note,
                todayEpochDay = today,
            )
            _logEvents.tryEmit(
                LogEvent(
                    milestoneId = milestoneId,
                    entryId = outcome.entry?.id,
                    accepted = outcome.accepted,
                    reachedTarget = outcome.reachedTarget,
                    undoLabel = undoLabel?.takeIf { outcome.accepted },
                    worthCelebrating = outcome.accepted && (
                        outcome.reachedTarget != null ||
                            outcome.kind == MilestoneKind.Record
                        ),
                ),
            )
        }
    }

    fun deleteEntry(milestoneId: String, entryId: String) {
        viewModelScope.launch { repository.deleteEntry(milestoneId, entryId, today) }
    }

    /** Sets the next target to chase, or clears it with `null`. */
    fun setTarget(milestoneId: String, value: Double?) {
        viewModelScope.launch { repository.setTarget(milestoneId, value, today) }
    }

    fun setArchived(milestoneId: String, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(milestoneId, archived) }
    }

    fun deleteMilestone(milestoneId: String) {
        viewModelScope.launch { repository.deleteMilestone(milestoneId) }
    }

    /** Reorder the active milestones to match [orderedActiveIds]. */
    fun setOrdering(orderedActiveIds: List<String>) {
        viewModelScope.launch { repository.setOrdering(orderedActiveIds) }
    }

    /** Returns the JSON of the user's current milestone list, or null if the call fails. */
    suspend fun exportJson(): String? = runCatching { repository.exportJson() }.getOrNull()

    /** Imports the JSON, returning the count of imported milestones, or null on failure. */
    suspend fun importJson(rawJson: String): Int? = repository.importJson(rawJson)

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(MilestoneRepository(application.applicationContext))
            }
        }
    }

    /**
     * Flattens every active milestone into one newest-first feed.
     *
     * Entries are walked per *period* rather than across the whole list, so a
     * yearly milestone's running total restarts each January and its
     * "improvement" is measured against that year's previous best rather than
     * an all-time peak the user isn't currently chasing.
     */
    private fun buildHistory(milestones: List<Milestone>): List<HistoryItem> {
        val items = mutableListOf<HistoryItem>()
        milestones.forEach { milestone ->
            milestone.entriesByDate
                .groupBy { milestone.cadence.periodKeyFor(it.epochDay) }
                .forEach { (_, periodEntries) ->
                    val periodBest = if (milestone.kind == MilestoneKind.Record) {
                        milestone.bestOf(periodEntries)
                    } else null
                    var running = 0.0
                    periodEntries.forEachIndexed { index, entry ->
                        running += entry.value
                        items += HistoryItem.Logged(
                            milestone = milestone,
                            entry = entry,
                            improvement = if (milestone.kind == MilestoneKind.Record) {
                                milestone.bestOf(periodEntries.take(index))
                                    ?.let { abs(entry.value - it.value) }
                            } else null,
                            runningTotal = running.takeIf { milestone.kind == MilestoneKind.Tally },
                            isPeriodBest = periodBest != null && entry.id == periodBest.id,
                            sortKey = index,
                        )
                    }
                }
            milestone.targets.filter { it.isReached }.forEach { target ->
                items += HistoryItem.TargetCleared(milestone = milestone, target = target)
            }
        }
        return items.sortedWith(
            compareByDescending<HistoryItem> { it.epochDay }.thenByDescending { it.sortKey },
        )
    }
}
