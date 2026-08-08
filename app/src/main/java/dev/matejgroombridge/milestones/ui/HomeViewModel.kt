package dev.matejgroombridge.milestones.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.milestones.data.model.Milestone
import dev.matejgroombridge.milestones.data.model.MilestoneDirection
import dev.matejgroombridge.milestones.data.model.MilestoneRecord
import dev.matejgroombridge.milestones.data.model.MilestoneUnit
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
 * One row in the History feed: a record, the milestone it belongs to, and how
 * much it improved on the entry before it.
 *
 * Flattened up-front in [HomeUiState] rather than computed per-row so the
 * History screen can sort the whole feed by date without re-walking every
 * milestone's chain on each recomposition.
 *
 * @param chainIndex Position within the milestone's own chronological chain.
 *                   Used to break ties when several records share a day.
 */
data class RecordEntry(
    val milestone: Milestone,
    val record: MilestoneRecord,
    val improvement: Double?,
    val chainIndex: Int,
)

/**
 * Top-level UI state shared by every screen that lists milestones. Splits the
 * list into active vs archived so each screen can take only what it needs
 * without re-filtering.
 */
data class HomeUiState(
    val activeMilestones: List<Milestone> = emptyList(),
    val archivedMilestones: List<Milestone> = emptyList(),
    val recentRecords: List<RecordEntry> = emptyList(),
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
)

class HomeViewModel(
    private val repository: MilestoneRepository,
) : ViewModel() {

    private val today: Long get() = LocalDate.now().toEpochDay()

    /**
     * Emits the id of a milestone whenever a genuinely new personal best
     * lands. The Milestones screen listens for this to fire confetti.
     *
     * Driven by the repository's return value rather than by the dialog's own
     * validation, so a value that loses a race and gets rejected never
     * triggers a celebration.
     */
    private val _celebrations = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val celebrations: SharedFlow<String> = _celebrations.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = repository.milestones
        .map { milestones ->
            val active = milestones.filterNot { it.archived }
            HomeUiState(
                activeMilestones = active,
                archivedMilestones = milestones.filter { it.archived },
                recentRecords = buildRecentRecords(active),
                todayEpochDay = today,
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
        unit: MilestoneUnit,
        direction: MilestoneDirection,
        target: Double?,
    ) {
        viewModelScope.launch {
            repository.updateMilestone(
                milestoneId, name, description, iconKey, colorKey, unit, direction, target,
            )
        }
    }

    /**
     * Logs a new personal best. The repository silently ignores values that
     * don't beat the current record; [celebrations] only emits when one is
     * actually stored.
     */
    fun logRecord(milestoneId: String, value: Double, epochDay: Long, note: String) {
        viewModelScope.launch {
            if (repository.logRecord(milestoneId, value, epochDay, note)) {
                _celebrations.tryEmit(milestoneId)
            }
        }
    }

    fun deleteRecord(milestoneId: String, recordId: String) {
        viewModelScope.launch { repository.deleteRecord(milestoneId, recordId) }
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
     * Flattens every active milestone's chain into one newest-first feed,
     * annotating each entry with the improvement over its predecessor.
     *
     * Records with no timestamp beyond their day are ordered by their position
     * in the chain, so several bests set on the same day still read newest
     * first rather than in arbitrary order.
     */
    private fun buildRecentRecords(milestones: List<Milestone>): List<RecordEntry> =
        milestones.flatMap { milestone ->
            val chain = milestone.recordsByDate
            chain.mapIndexed { index, record ->
                RecordEntry(
                    milestone = milestone,
                    record = record,
                    improvement = chain.getOrNull(index - 1)
                        ?.let { previous -> abs(record.value - previous.value) },
                    chainIndex = index,
                )
            }
        }.sortedWith(
            compareByDescending<RecordEntry> { it.record.epochDay }
                .thenByDescending { it.chainIndex },
        )
}
