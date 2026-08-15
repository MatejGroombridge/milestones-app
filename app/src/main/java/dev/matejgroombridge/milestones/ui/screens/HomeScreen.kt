package dev.matejgroombridge.milestones.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.milestones.data.model.MilestoneCadence
import dev.matejgroombridge.milestones.data.model.MilestoneKind
import dev.matejgroombridge.milestones.data.model.MilestoneStats
import dev.matejgroombridge.milestones.data.model.MilestoneTarget
import dev.matejgroombridge.milestones.ui.HomeViewModel
import dev.matejgroombridge.milestones.ui.SettingsViewModel
import dev.matejgroombridge.milestones.ui.components.ConfettiOverlay
import dev.matejgroombridge.milestones.ui.components.LogEntryDialog
import dev.matejgroombridge.milestones.ui.components.MilestoneCard
import dev.matejgroombridge.milestones.ui.components.MilestoneEditorDialog
import dev.matejgroombridge.milestones.ui.components.MilestoneEditorResult
import dev.matejgroombridge.milestones.ui.components.MilestoneOverviewDialog
import dev.matejgroombridge.milestones.ui.components.TargetReachedDialog
import kotlinx.coroutines.launch

/** Which dialog (if any) the milestones screen is currently showing. */
private sealed interface HomeDialog {
    data object Create : HomeDialog

    /** Log an entry — a record's value, or a tally amount with a note. */
    data class Log(val milestoneId: String) : HomeDialog

    /** Read-only snapshot of stats and history — opened by long-pressing a card. */
    data class Overview(val milestoneId: String) : HomeDialog

    /** Full editor — reachable from [Overview]'s edit button. */
    data class Edit(val milestoneId: String) : HomeDialog

    /** Celebration after clearing a target, offering the next one. */
    data class TargetReached(
        val milestoneId: String,
        val target: MilestoneTarget,
    ) : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenSettings: () -> Unit,
    onOpenArchive: () -> Unit,
    /**
     * Owned by the shell rather than this screen so that Scaffold can lift the
     * FAB clear of the snackbar. Two sibling Scaffolds can't coordinate, and
     * the FAB was covering the Undo action.
     */
    snackbar: SnackbarHostState,
    contentPadding: PaddingValues = PaddingValues(),
    requestCreate: Boolean = false,
    onCreateDialogConsumed: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(requestCreate) {
        if (requestCreate) {
            dialog = HomeDialog.Create
            onCreateDialogConsumed()
        }
    }

    var fireConfetti by remember { mutableStateOf(false) }

    // Everything that follows a log lands on one channel, because a single
    // tap can legitimately do all three: store an undoable entry, be worth
    // confetti, and clear a target worth a celebration.
    LaunchedEffect(settings.celebrateRecords, settings.zenMode) {
        viewModel.logEvents.collect { event ->
            if (!event.accepted) return@collect

            if (settings.celebrateRecords && event.worthCelebrating) {
                // Toggle through to retrigger ConfettiOverlay's LaunchedEffect.
                fireConfetti = false
                fireConfetti = true
            }

            // Zen mode shows no dialogs beyond logging, so a cleared target
            // waits quietly on the card until Zen is switched off.
            event.reachedTarget?.let { target ->
                if (!settings.zenMode) {
                    dialog = HomeDialog.TargetReached(event.milestoneId, target)
                }
            }

            val label = event.undoLabel
            val entryId = event.entryId
            if (label != null && entryId != null) {
                scope.launch {
                    val result = snackbar.showSnackbar(
                        message = label,
                        actionLabel = "Undo",
                        withDismissAction = false,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.deleteEntry(event.milestoneId, entryId)
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // We render the title + actions inline (instead of in `topBar`)
            // so the cards can sit directly underneath the title with no
            // extra padding left over from the top bar's vertical centring.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                MilestonesHeader(
                    onOpenArchive = onOpenArchive,
                    onOpenSettings = onOpenSettings,
                    // The archive icon disappears in Zen mode, but Settings
                    // stays so the user has a way to flip Zen back off.
                    showArchive = !settings.zenMode,
                )
                if (state.hasNoMilestones) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        message = "No milestones yet.\nTap + to set your first goal.",
                    )
                } else {
                    MilestonesGrid(
                        yearly = state.yearly,
                        lifetime = state.lifetime,
                        showHeaders = state.showSectionHeaders,
                        bottomPadding = contentPadding.calculateBottomPadding() + 24.dp,
                        onTap = { stats -> onCardTap(stats, viewModel) { dialog = it } },
                        // No long-press → overview dialog while in Zen.
                        onLongPress = if (settings.zenMode) ({ _ -> })
                        else ({ stats -> dialog = HomeDialog.Overview(stats.milestone.id) }),
                    )
                }
            }
            ConfettiOverlay(trigger = fireConfetti)
        }
    }

    // Zen mode keeps exactly one dialog reachable — logging an entry, which
    // is the whole point of a tap. Everything else is dropped.
    LaunchedEffect(settings.zenMode) {
        if (settings.zenMode && dialog !is HomeDialog.Log) dialog = null
    }
    if (settings.zenMode && dialog !is HomeDialog.Log) return

    // Every branch re-reads its milestone from live state, so a dialog left
    // open reflects changes made underneath it — and closes itself if the
    // milestone is deleted or archived elsewhere.
    when (val d = dialog) {
        is HomeDialog.Create -> MilestoneEditorDialog(
            existing = null,
            activeTarget = null,
            onDismiss = { dialog = null },
            onResult = { result ->
                if (result is MilestoneEditorResult.Save) {
                    viewModel.addMilestone(
                        name = result.name,
                        description = result.description,
                        iconKey = result.iconKey,
                        colorKey = result.colorKey,
                        kind = result.kind,
                        cadence = result.cadence,
                        unit = result.unit,
                        direction = result.direction,
                        target = result.target,
                        startingValue = result.startingValue,
                    )
                }
                dialog = null
            },
        )

        is HomeDialog.Log -> {
            val live = state.statsFor(d.milestoneId)
            if (live == null) dialog = null
            else LogEntryDialog(
                stats = live,
                todayEpochDay = state.todayEpochDay,
                onDismiss = { dialog = null },
                onSave = { value, epochDay, note ->
                    viewModel.logEntry(live.milestone.id, value, epochDay, note)
                    dialog = null
                },
            )
        }

        is HomeDialog.Overview -> {
            val live = state.statsFor(d.milestoneId)
            if (live == null) dialog = null
            else MilestoneOverviewDialog(
                stats = live,
                todayEpochDay = state.todayEpochDay,
                onDismiss = { dialog = null },
                onEdit = { dialog = HomeDialog.Edit(d.milestoneId) },
                onAddEntry = { dialog = HomeDialog.Log(d.milestoneId) },
                onDeleteEntry = { entryId -> viewModel.deleteEntry(d.milestoneId, entryId) },
            )
        }

        is HomeDialog.Edit -> {
            val live = state.statsFor(d.milestoneId)
            if (live == null) dialog = null
            else MilestoneEditorDialog(
                existing = live.milestone,
                activeTarget = live.activeTarget?.value,
                onDismiss = { dialog = null },
                onResult = { result ->
                    when (result) {
                        is MilestoneEditorResult.Save -> viewModel.updateMilestone(
                            milestoneId = d.milestoneId,
                            name = result.name,
                            description = result.description,
                            iconKey = result.iconKey,
                            colorKey = result.colorKey,
                            kind = result.kind,
                            cadence = result.cadence,
                            unit = result.unit,
                            direction = result.direction,
                            target = result.target,
                        )
                        is MilestoneEditorResult.Archive ->
                            viewModel.setArchived(d.milestoneId, result.archived)
                    }
                    dialog = null
                },
            )
        }

        is HomeDialog.TargetReached -> {
            val live = state.statsFor(d.milestoneId)
            if (live == null) dialog = null
            else TargetReachedDialog(
                stats = live,
                reachedTarget = d.target,
                onDismiss = { dialog = null },
                onSetTarget = { value ->
                    viewModel.setTarget(d.milestoneId, value)
                    dialog = null
                },
            )
        }

        null -> Unit
    }
}

/**
 * A tap means different things per kind. Adding to a tally is a single,
 * reversible act, so it happens immediately with an undo — that speed is most
 * of why counting things in this app feels good. Setting a record needs a
 * value and a judgement about whether it beats the best, so it opens the sheet.
 */
private fun onCardTap(
    stats: MilestoneStats,
    viewModel: HomeViewModel,
    showDialog: (HomeDialog) -> Unit,
) {
    if (stats.kind == MilestoneKind.Tally) {
        viewModel.quickAdd(
            milestoneId = stats.milestone.id,
            name = stats.milestone.name,
            unit = stats.unit,
        )
    } else {
        showDialog(HomeDialog.Log(stats.milestone.id))
    }
}

/**
 * Inline title + actions row. Larger headline, bottom-aligned so it sits
 * "down the page" (similar to an M3 LargeTopAppBar in its expanded state),
 * but with no baseline padding below the title so cards can sit directly
 * underneath.
 */
@Composable
private fun MilestonesHeader(
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
    showArchive: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showArchive) {
                IconButton(onClick = onOpenArchive) {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = "Archived Milestones",
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                )
            }
        }
        Text(
            text = "Milestones",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 18.dp),
        )
    }
}

/**
 * The grid, split into "This year" and "All time".
 *
 * Labelled sections rather than a segmented control: with a personal-scale
 * list, seeing both groups at once is worth more than the tidiness of hiding
 * one behind a tap. Headings only appear when both groups exist, so a user
 * with only lifetime records never sees a header explaining a division that
 * doesn't apply to them.
 */
@Composable
private fun MilestonesGrid(
    yearly: List<MilestoneStats>,
    lifetime: List<MilestoneStats>,
    showHeaders: Boolean,
    bottomPadding: Dp,
    onTap: (MilestoneStats) -> Unit,
    onLongPress: (MilestoneStats) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 0.dp,
            bottom = bottomPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(
            MilestoneCadence.Yearly to yearly,
            MilestoneCadence.Lifetime to lifetime,
        ).forEach { (cadence, group) ->
            if (group.isEmpty()) return@forEach
            if (showHeaders) {
                item(
                    key = "header-${cadence.name}",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    SectionHeader(cadence.sectionTitle)
                }
            }
            items(items = group, key = { it.milestone.id }) { stats ->
                MilestoneCard(
                    stats = stats,
                    onClick = { onTap(stats) },
                    onLongClick = { onLongPress(stats) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 2.dp),
    )
}

/** Shared empty state used by every list screen. */
@Composable
internal fun EmptyState(modifier: Modifier = Modifier, message: String) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
