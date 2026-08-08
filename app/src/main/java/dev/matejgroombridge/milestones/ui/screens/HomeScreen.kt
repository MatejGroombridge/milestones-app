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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.milestones.data.model.Milestone
import dev.matejgroombridge.milestones.ui.HomeViewModel
import dev.matejgroombridge.milestones.ui.SettingsViewModel
import dev.matejgroombridge.milestones.ui.components.ConfettiOverlay
import dev.matejgroombridge.milestones.ui.components.LogRecordDialog
import dev.matejgroombridge.milestones.ui.components.MilestoneCard
import dev.matejgroombridge.milestones.ui.components.MilestoneEditorDialog
import dev.matejgroombridge.milestones.ui.components.MilestoneEditorResult
import dev.matejgroombridge.milestones.ui.components.MilestoneOverviewDialog

/** Which dialog (if any) the milestones screen is currently showing. */
private sealed interface HomeDialog {
    data object Create : HomeDialog

    /** Log a new personal best — the primary action, opened by tapping a card. */
    data class Log(val milestone: Milestone) : HomeDialog

    /** Read-only snapshot of stats and history — opened by long-pressing a card. */
    data class Overview(val milestone: Milestone) : HomeDialog

    /** Full editor — reachable from [Overview]'s edit button. */
    data class Edit(val milestone: Milestone) : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenSettings: () -> Unit,
    onOpenArchive: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    requestCreate: Boolean = false,
    onCreateDialogConsumed: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }

    LaunchedEffect(requestCreate) {
        if (requestCreate) {
            dialog = HomeDialog.Create
            onCreateDialogConsumed()
        }
    }

    // Confetti is driven by the view model rather than by the dialog's own
    // validation, so a value that loses a race and gets rejected by the
    // repository never fires a celebration for a record that wasn't stored.
    var fireConfetti by remember { mutableStateOf(false) }
    LaunchedEffect(settings.celebrateRecords) {
        if (!settings.celebrateRecords) return@LaunchedEffect
        viewModel.celebrations.collect {
            // Toggle through to retrigger ConfettiOverlay's LaunchedEffect.
            fireConfetti = false
            fireConfetti = true
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
                if (state.activeMilestones.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        message = "No milestones yet.\nTap + to set your first goal.",
                    )
                } else {
                    MilestonesGrid(
                        milestones = state.activeMilestones,
                        bottomPadding = contentPadding.calculateBottomPadding() + 24.dp,
                        onTap = { milestone -> dialog = HomeDialog.Log(milestone) },
                        // No long-press → overview dialog while in Zen.
                        onLongPress = if (settings.zenMode) ({ _ -> })
                        else ({ milestone -> dialog = HomeDialog.Overview(milestone) }),
                    )
                }
            }
            ConfettiOverlay(trigger = fireConfetti)
        }
    }

    // Zen mode keeps exactly one dialog reachable — logging a record, which
    // is the whole point of a tap. Everything else is dropped.
    LaunchedEffect(settings.zenMode) {
        if (settings.zenMode && dialog !is HomeDialog.Log) dialog = null
    }
    if (settings.zenMode && dialog !is HomeDialog.Log) return

    when (val d = dialog) {
        is HomeDialog.Create -> MilestoneEditorDialog(
            existing = null,
            onDismiss = { dialog = null },
            onResult = { result ->
                if (result is MilestoneEditorResult.Save) {
                    viewModel.addMilestone(
                        name = result.name,
                        description = result.description,
                        iconKey = result.iconKey,
                        colorKey = result.colorKey,
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
            // Source the latest version from state so the "record to beat"
            // banner stays live if the record changes while the dialog is open.
            val live = state.activeMilestones.firstOrNull { it.id == d.milestone.id } ?: d.milestone
            LogRecordDialog(
                milestone = live,
                todayEpochDay = state.todayEpochDay,
                onDismiss = { dialog = null },
                onSave = { value, epochDay, note ->
                    viewModel.logRecord(live.id, value, epochDay, note)
                    dialog = null
                },
            )
        }
        is HomeDialog.Overview -> {
            val live = state.activeMilestones.firstOrNull { it.id == d.milestone.id } ?: d.milestone
            MilestoneOverviewDialog(
                milestone = live,
                todayEpochDay = state.todayEpochDay,
                onDismiss = { dialog = null },
                onEdit = { dialog = HomeDialog.Edit(live) },
                onDeleteRecord = { recordId -> viewModel.deleteRecord(live.id, recordId) },
            )
        }
        is HomeDialog.Edit -> MilestoneEditorDialog(
            existing = d.milestone,
            onDismiss = { dialog = null },
            onResult = { result ->
                when (result) {
                    is MilestoneEditorResult.Save -> viewModel.updateMilestone(
                        milestoneId = d.milestone.id,
                        name = result.name,
                        description = result.description,
                        iconKey = result.iconKey,
                        colorKey = result.colorKey,
                        unit = result.unit,
                        direction = result.direction,
                        target = result.target,
                    )
                    is MilestoneEditorResult.Archive ->
                        viewModel.setArchived(d.milestone.id, result.archived)
                }
                dialog = null
            },
        )
        null -> Unit
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
        // Action icons pinned to the top-right.
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
        // Headline aligned to the bottom-start so cards immediately below sit
        // flush with the underline of the text.
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

@Composable
private fun MilestonesGrid(
    milestones: List<Milestone>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onTap: (Milestone) -> Unit,
    onLongPress: (Milestone) -> Unit,
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
        items(items = milestones, key = { it.id }) { milestone ->
            MilestoneCard(
                milestone = milestone,
                onClick = { onTap(milestone) },
                onLongClick = { onLongPress(milestone) },
            )
        }
    }
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
