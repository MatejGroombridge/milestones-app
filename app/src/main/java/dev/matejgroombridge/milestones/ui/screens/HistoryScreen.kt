package dev.matejgroombridge.milestones.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.milestones.ui.HomeViewModel
import dev.matejgroombridge.milestones.ui.RecordEntry
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import dev.matejgroombridge.milestones.ui.theme.MilestoneIcons
import dev.matejgroombridge.milestones.ui.theme.containerColor
import dev.matejgroombridge.milestones.ui.theme.contentColor
import dev.matejgroombridge.milestones.ui.util.rememberHaptics
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Every personal best across every active milestone, newest first — the
 * "what have I actually beaten lately?" view.
 *
 * Entries are grouped under month headings, which is the natural granularity
 * for records: they arrive in ones and twos over weeks, not many per day.
 * Long-pressing a row offers to delete that record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<RecordEntry?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("History") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (state.recentRecords.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                message = if (state.activeMilestones.isEmpty()) {
                    "No milestones yet.\nAdd one on the Milestones tab."
                } else {
                    "No records yet.\nTap a milestone to log your first."
                },
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The feed is already sorted newest-first, so a heading is needed
            // exactly when an entry's month differs from its predecessor's.
            state.recentRecords.forEachIndexed { index, entry ->
                val month = LocalDate.ofEpochDay(entry.record.epochDay).withDayOfMonth(1)
                val previousMonth = state.recentRecords.getOrNull(index - 1)
                    ?.let { LocalDate.ofEpochDay(it.record.epochDay).withDayOfMonth(1) }
                if (month != previousMonth) {
                    item(key = "header-${month.year}-${month.monthValue}") {
                        MonthHeader(month.format(MONTH_FORMAT))
                    }
                }
                item(key = entry.record.id) {
                    HistoryRow(
                        entry = entry,
                        isCurrentBest = entry.record.id == entry.milestone.best?.id,
                        onRequestDelete = { pendingDelete = entry },
                    )
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Record?") },
            text = {
                Text(
                    "${entry.milestone.unit.format(entry.record.value)} on " +
                        "${LocalDate.ofEpochDay(entry.record.epochDay).format(FULL_DATE_FORMAT)} " +
                        "will be removed from \"${entry.milestone.name}\". If it's the " +
                        "current best, the record before it takes over.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(entry.milestone.id, entry.record.id)
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
private fun MonthHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entry: RecordEntry,
    isCurrentBest: Boolean,
    onRequestDelete: () -> Unit,
) {
    val milestone = entry.milestone
    val color = MilestoneColors.entry(milestone.colorKey)
    val iconEntry = MilestoneIcons.entry(milestone.iconKey)
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = color.containerColor(),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { },
                    onLongClick = {
                        haptics.longPress()
                        menuOpen = true
                    },
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconEntry.icon,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = milestone.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = color.contentColor().copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isCurrentBest) {
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Outlined.EmojiEvents,
                                contentDescription = "Current best",
                                tint = color.contentColor().copy(alpha = 0.75f),
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = milestone.unit.format(entry.record.value),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color.contentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.record.note.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = entry.record.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = color.contentColor().copy(alpha = 0.75f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = LocalDate.ofEpochDay(entry.record.epochDay).format(DAY_FORMAT),
                        style = MaterialTheme.typography.labelMedium,
                        color = color.contentColor().copy(alpha = 0.75f),
                    )
                    if (entry.improvement != null) {
                        Spacer(Modifier.height(4.dp))
                        ImprovementPill(
                            text = milestone.unit.formatMagnitude(entry.improvement),
                            accent = color.accent,
                            contentColor = color.contentColor(),
                        )
                    }
                }
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text("Delete Record") },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.DeleteOutline, contentDescription = null)
                },
                onClick = {
                    menuOpen = false
                    onRequestDelete()
                },
            )
        }
    }
}

/** Small "how much better" badge — an up arrow reads as progress in either direction. */
@Composable
private fun ImprovementPill(text: String, accent: Color, contentColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.45f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.ArrowUpward,
            contentDescription = "Improvement",
            tint = contentColor,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val FULL_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
