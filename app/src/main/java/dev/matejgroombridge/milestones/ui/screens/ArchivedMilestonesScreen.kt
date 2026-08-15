package dev.matejgroombridge.milestones.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.milestones.data.model.Milestone
import dev.matejgroombridge.milestones.data.model.MilestoneKind
import dev.matejgroombridge.milestones.ui.HomeViewModel
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import dev.matejgroombridge.milestones.ui.theme.MilestoneIcons
import dev.matejgroombridge.milestones.ui.theme.containerColor
import dev.matejgroombridge.milestones.ui.theme.contentColor

/**
 * The "hidden section" for milestones the user has archived. Each row offers
 * a Restore action (un-archive) and a Delete action (with confirmation) —
 * deleting is only reachable from here so record history can't be lost by a
 * stray tap in the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedMilestonesScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Milestone?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Archived Milestones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (state.archivedMilestones.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                message = "Nothing archived.\nLong-press a milestone and use Edit to archive it.",
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = state.archivedMilestones, key = { it.id }) { milestone ->
                ArchivedRow(
                    milestone = milestone,
                    onRestore = { viewModel.setArchived(milestone.id, false) },
                    onDelete = { pendingDelete = milestone },
                )
            }
        }
    }

    pendingDelete?.let { milestone ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Milestone?") },
            text = {
                Text(
                    "\"${milestone.name}\" will be permanently removed along with " +
                        "all ${milestone.entries.size} of its entries.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMilestone(milestone.id)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ArchivedRow(
    milestone: Milestone,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = MilestoneColors.entry(milestone.colorKey)
    val iconEntry = MilestoneIcons.entry(milestone.iconKey)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.containerColor().copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.accent.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconEntry.icon,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.padding(horizontal = 6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = milestone.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color.contentColor(),
                )
                Text(
                    // Archived milestones are shown at their all-time figure
                    // rather than the current period's — a shelved yearly goal
                    // reading "0 this year" would say nothing about what it was.
                    text = milestone.valueOf(milestone.entriesByDate)
                        ?.takeIf { milestone.hasEntries }
                        ?.let { value ->
                            val figure = milestone.unit.format(value)
                            if (milestone.kind == MilestoneKind.Tally) figure else "Best $figure"
                        } ?: "Nothing logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = color.contentColor().copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
            IconButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Outlined.Unarchive,
                    contentDescription = "Restore",
                    tint = color.contentColor(),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
