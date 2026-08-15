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
import dev.matejgroombridge.milestones.data.model.MilestoneKind
import dev.matejgroombridge.milestones.ui.HistoryItem
import dev.matejgroombridge.milestones.ui.HomeViewModel
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import dev.matejgroombridge.milestones.ui.theme.MilestoneIcons
import dev.matejgroombridge.milestones.ui.theme.containerColor
import dev.matejgroombridge.milestones.ui.theme.contentColor
import dev.matejgroombridge.milestones.ui.util.rememberHaptics
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Everything that has happened across every active milestone, newest first —
 * the "what have I actually done lately?" view.
 *
 * Entries are grouped under month headings, the natural granularity for a
 * tracker where things arrive in ones and twos over weeks. Clearing a target
 * gets its own row, because it's the moment worth remembering.
 * Long-pressing a logged row offers to delete it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<HistoryItem.Logged?>(null) }

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
        if (state.history.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                message = if (state.hasNoMilestones) {
                    "No milestones yet.\nAdd one on the Milestones tab."
                } else {
                    "Nothing logged yet.\nTap a milestone to get started."
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
            // exactly when an item's month differs from its predecessor's.
            state.history.forEachIndexed { index, item ->
                val month = LocalDate.ofEpochDay(item.epochDay).withDayOfMonth(1)
                val previousMonth = state.history.getOrNull(index - 1)
                    ?.let { LocalDate.ofEpochDay(it.epochDay).withDayOfMonth(1) }
                if (month != previousMonth) {
                    item(key = "header-${month.year}-${month.monthValue}") {
                        MonthHeader(month.format(MONTH_FORMAT))
                    }
                }
                when (item) {
                    is HistoryItem.Logged -> item(key = "entry-${item.entry.id}") {
                        LoggedRow(item = item, onRequestDelete = { pendingDelete = item })
                    }
                    is HistoryItem.TargetCleared -> item(key = "target-${item.target.id}") {
                        TargetClearedRow(item = item)
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        val milestone = item.milestone
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Entry?") },
            text = {
                Text(
                    "${milestone.unit.format(item.entry.value)} on " +
                        "${LocalDate.ofEpochDay(item.entry.epochDay).format(FULL_DATE_FORMAT)} " +
                        "will be removed from \"${milestone.name}\"." +
                        if (milestone.kind == MilestoneKind.Record) {
                            " If it's the current best, the record before it takes over."
                        } else {
                            " The total drops by that much."
                        },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(milestone.id, item.entry.id)
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
private fun LoggedRow(
    item: HistoryItem.Logged,
    onRequestDelete: () -> Unit,
) {
    val milestone = item.milestone
    val color = MilestoneColors.entry(milestone.colorKey)
    val iconEntry = MilestoneIcons.entry(milestone.iconKey)
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    val isTally = milestone.kind == MilestoneKind.Tally

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
                        if (item.isPeriodBest) {
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
                        // A tally's note is what actually happened — "Japan"
                        // says more than "+1" ever could.
                        text = if (isTally && item.entry.note.isNotBlank()) item.entry.note
                        else milestone.unit.format(item.entry.value),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color.contentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val caption = buildCaption(item)
                    if (caption != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = caption,
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
                        text = LocalDate.ofEpochDay(item.entry.epochDay).format(DAY_FORMAT),
                        style = MaterialTheme.typography.labelMedium,
                        color = color.contentColor().copy(alpha = 0.75f),
                    )
                    val badge = item.improvement?.let { milestone.unit.formatMagnitude(it) }
                        ?: item.runningTotal?.let { milestone.unit.format(it) }
                    if (badge != null) {
                        Spacer(Modifier.height(4.dp))
                        ValuePill(
                            text = badge,
                            showArrow = item.improvement != null,
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
                text = { Text("Delete Entry") },
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

/**
 * Whatever didn't make it into the headline. A tally leading with its note
 * still owes the reader the amount; a record leading with its value still
 * owes them the note.
 */
private fun buildCaption(item: HistoryItem.Logged): String? {
    val milestone = item.milestone
    val isTally = milestone.kind == MilestoneKind.Tally
    return when {
        isTally && item.entry.note.isNotBlank() ->
            "+${milestone.unit.formatMagnitude(item.entry.value)}"
        !isTally && item.entry.note.isNotBlank() -> item.entry.note
        else -> null
    }
}

/** The moment a target fell — visually distinct from a plain entry. */
@Composable
private fun TargetClearedRow(item: HistoryItem.TargetCleared) {
    val milestone = item.milestone
    val color = MilestoneColors.entry(milestone.colorKey)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.accent.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
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
                    imageVector = Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = milestone.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = color.contentColor().copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Target reached · ${milestone.unit.format(item.target.value)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color.contentColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = LocalDate.ofEpochDay(item.epochDay).format(DAY_FORMAT),
                style = MaterialTheme.typography.labelMedium,
                color = color.contentColor().copy(alpha = 0.75f),
            )
        }
    }
}

/** Trailing badge — an improvement for a record, the running total for a tally. */
@Composable
private fun ValuePill(
    text: String,
    showArrow: Boolean,
    accent: Color,
    contentColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.45f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        if (showArrow) {
            Icon(
                imageVector = Icons.Outlined.ArrowUpward,
                contentDescription = "Improvement",
                tint = contentColor,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(2.dp))
        }
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
