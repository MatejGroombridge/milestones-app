package dev.matejgroombridge.milestones.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.matejgroombridge.milestones.data.model.MilestoneCadence
import dev.matejgroombridge.milestones.data.model.MilestoneEntry
import dev.matejgroombridge.milestones.data.model.MilestoneKind
import dev.matejgroombridge.milestones.data.model.MilestoneStats
import dev.matejgroombridge.milestones.data.model.MilestoneTarget
import dev.matejgroombridge.milestones.data.model.yearOf
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import dev.matejgroombridge.milestones.ui.theme.MilestoneIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Read-only "milestone overview" sheet shown when the user long-presses a
 * card — a polished snapshot rather than a control surface.
 *
 * The only mutations available are the ones you can't reach from the grid:
 * adding an entry with a note (a tally card's tap is an instant +1, so this is
 * the way to record *what* you did), editing the milestone, and deleting an
 * individual entry.
 *
 * Surfaced, all scoped to the current period:
 *  - Name, description, and a chip describing how it's measured.
 *  - Three stats, chosen by kind and cadence.
 *  - A sparkline of the progression.
 *  - The targets set against it, cleared and outstanding.
 *  - The entry history, newest first, each row deletable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneOverviewDialog(
    stats: MilestoneStats,
    todayEpochDay: Long,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAddEntry: () -> Unit,
    onDeleteEntry: (String) -> Unit,
) {
    val milestone = stats.milestone
    val color = MilestoneColors.entry(milestone.colorKey)
    val iconEntry = MilestoneIcons.entry(milestone.iconKey)
    val isTally = stats.kind == MilestoneKind.Tally

    val chain = stats.periodEntries
    val targets = remember(milestone.targets, stats.periodKey) {
        milestone.targetsIn(stats.periodKey).sortedBy { it.setOnEpochDay }
    }
    val daysSinceCreated = (todayEpochDay - milestone.createdAtEpochDay).coerceAtLeast(0L)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LargeIconBadge(accent = color.accent, icon = iconEntry.icon)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = milestone.name,
                            // Single-line + ellipsis so unusually long names
                            // don't push the icon row down. Tap-outside still
                            // dismisses, so no explicit close button is needed.
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                        Spacer(Modifier.height(4.dp))
                        MeasurementChip(text = descriptorFor(stats), accent = color.accent)
                    }
                    IconButton(onClick = onAddEntry) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = if (isTally) "Add an entry" else "Log a record",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit milestone",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (milestone.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = milestone.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(18.dp))
                StatRow(stats = stats, todayEpochDay = todayEpochDay, accent = color.accent)

                if (chain.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    SectionHeading("Progression")
                    Spacer(Modifier.height(8.dp))
                    Sparkline(
                        // A tally's shape is its climb, so plot the running
                        // total rather than the individual amounts — otherwise
                        // a column of +1s draws a flat line that says nothing.
                        values = if (isTally) chain.runningTotals() else chain.map { it.value },
                        accent = color.accent,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    )
                }

                if (targets.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    SectionHeading("Targets")
                    Spacer(Modifier.height(8.dp))
                    targets.asReversed().forEach { target ->
                        TargetRow(target = target, stats = stats, accent = color.accent)
                        Spacer(Modifier.height(6.dp))
                    }
                }

                Spacer(Modifier.height(18.dp))
                if (chain.isEmpty()) {
                    Text(
                        text = if (isTally) "Nothing logged yet — tap the card to add one."
                        else "No records yet — tap the card to set your first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SectionHeading(if (isTally) "Entries" else "Record history")
                    Spacer(Modifier.height(8.dp))
                    // Newest first: the most recent entries are the ones a
                    // user is most likely to want to correct.
                    chain.asReversed().forEachIndexed { indexFromEnd, entry ->
                        val previous = chain.getOrNull(chain.lastIndex - indexFromEnd - 1)
                        EntryRow(
                            stats = stats,
                            entry = entry,
                            improvement = if (isTally) null
                            else previous?.let { abs(entry.value - it.value) },
                            isBest = !isTally && entry.id == stats.bestEntry?.id,
                            accent = color.accent,
                            onDelete = { onDeleteEntry(entry.id) },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))
                FooterStats(stats = stats, daysSinceCreated = daysSinceCreated)
            }
        }
    }
}

/** Running totals for a tally, in entry order. */
private fun List<MilestoneEntry>.runningTotals(): List<Double> {
    var running = 0.0
    return map { running += it.value; running }
}

/** "Running total · Each year · books" — how this milestone is set up, in one line. */
private fun descriptorFor(stats: MilestoneStats): String = buildString {
    append(stats.kind.label)
    append(" · ")
    append(stats.cadence.label)
    if (stats.kind == MilestoneKind.Record) {
        append(" · ")
        append(stats.milestone.direction.shortLabel)
    }
}

/**
 * Three stats. The first two are always the headline number and how many
 * entries produced it; the third is whichever comparison is most useful —
 * the all-time figure for a yearly milestone, and otherwise how far the
 * milestone has travelled.
 */
@Composable
private fun StatRow(stats: MilestoneStats, todayEpochDay: Long, accent: Color) {
    val isTally = stats.kind == MilestoneKind.Tally
    val thirdLabel: String
    val thirdValue: String
    when {
        // A yearly milestone's most useful comparison is how this year sits
        // against every year; a lifetime one's is how far it has travelled,
        // except for a lifetime tally where "how much of it was this year"
        // is the more interesting cut.
        stats.cadence == MilestoneCadence.Yearly -> {
            thirdLabel = "All time"
            thirdValue = stats.allTimeValue?.let { stats.unit.format(it) } ?: "—"
        }
        isTally -> {
            thirdLabel = "This year"
            val currentYear = yearOf(todayEpochDay)
            thirdValue = stats.unit.format(
                stats.milestone.entriesByDate
                    .filter { yearOf(it.epochDay) == currentYear }
                    .sumOf { it.value },
            )
        }
        else -> {
            thirdLabel = "Improved"
            val first = stats.periodEntries.firstOrNull()?.value
            val best = stats.value
            thirdValue = if (first != null && best != null && stats.periodEntries.size > 1) {
                stats.unit.formatMagnitude(abs(best - first))
            } else "—"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatTile(
            label = if (isTally) "Total" else "Best",
            value = stats.formattedValue,
            icon = Icons.Outlined.EmojiEvents,
            accent = accent,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Entries",
            value = stats.periodEntryCount.toString(),
            icon = Icons.Outlined.Timeline,
            accent = accent,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = thirdLabel,
            value = thirdValue,
            icon = Icons.Outlined.Straighten,
            accent = accent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LargeIconBadge(accent: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.85f),
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun MeasurementChip(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.25f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One target: what it was, and whether it's been cleared. */
@Composable
private fun TargetRow(target: MilestoneTarget, stats: MilestoneStats, accent: Color) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (target.isReached) accent.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stats.unit.format(target.value),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = target.reachedOnEpochDay?.let {
                        "Reached ${LocalDate.ofEpochDay(it).format(ENTRY_DATE_FORMAT)}"
                    } ?: "Chasing since ${
                        LocalDate.ofEpochDay(target.setOnEpochDay).format(ENTRY_DATE_FORMAT)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (target.isReached) {
                Icon(
                    imageVector = Icons.Outlined.EmojiEvents,
                    contentDescription = "Reached",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * One row of the entry history: what, when, and a delete affordance.
 *
 * Delete is the only way to undo a mistyped entry — an unbeatable typo would
 * otherwise lock a record milestone forever — so it's always visible rather
 * than hidden behind a long-press.
 */
@Composable
private fun EntryRow(
    stats: MilestoneStats,
    entry: MilestoneEntry,
    improvement: Double?,
    isBest: Boolean,
    accent: Color,
    onDelete: () -> Unit,
) {
    val isTally = stats.kind == MilestoneKind.Tally
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isBest) accent.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // A tally's note is the interesting part — "Japan"
                        // beats "+1" — so it leads when there is one.
                        text = if (isTally && entry.note.isNotBlank()) entry.note
                        else stats.unit.format(entry.value),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isBest) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.EmojiEvents,
                            contentDescription = "Personal best",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = buildString {
                        append(LocalDate.ofEpochDay(entry.epochDay).format(ENTRY_DATE_FORMAT))
                        if (isTally && entry.note.isNotBlank()) {
                            append(" · +")
                            append(stats.unit.formatMagnitude(entry.value))
                        }
                        if (improvement != null) {
                            append(" · +")
                            append(stats.unit.formatMagnitude(improvement))
                        }
                        if (!isTally && entry.note.isNotBlank()) {
                            append(" · ")
                            append(entry.note)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Minimal line chart, plotted by index rather than date.
 *
 * Index spacing is deliberate: this is a "shape of the journey" glance, and
 * evenly-spaced points keep a milestone whose entries are years apart from
 * collapsing into a flat line with one spike. The Progress screen plots the
 * same data against real dates when the timing matters.
 */
@Composable
private fun Sparkline(
    values: List<Double>,
    accent: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = trackColor.copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (values.isEmpty()) return@Canvas
            val minValue = values.min()
            val maxValue = values.max()
            val span = (maxValue - minValue).takeIf { it > 0.0 }

            fun pointAt(index: Int): Offset {
                val x = if (values.size == 1) size.width / 2f
                else size.width * (index.toFloat() / (values.size - 1))
                // A flat run (single entry, or several identical values) has
                // no range to scale against, so it sits on the midline.
                val normalised = span?.let { ((values[index] - minValue) / it).toFloat() } ?: 0.5f
                return Offset(x, size.height * (1f - normalised))
            }

            if (values.size > 1) {
                val path = Path().apply {
                    moveTo(pointAt(0).x, pointAt(0).y)
                    for (i in 1 until values.size) {
                        val point = pointAt(i)
                        lineTo(point.x, point.y)
                    }
                }
                drawPath(
                    path = path,
                    color = accent,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            values.indices.forEach { index ->
                drawCircle(color = accent, radius = 3.dp.toPx(), center = pointAt(index))
            }
        }
    }
}

@Composable
private fun FooterStats(stats: MilestoneStats, daysSinceCreated: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (daysSinceCreated == 0L) "Created today"
            else "Tracking for $daysSinceCreated day${if (daysSinceCreated == 1L) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val progress = stats.targetProgress
        Text(
            text = when {
                stats.awaitingNewTarget -> "Target reached"
                progress == null -> "No target"
                else -> "${(progress * 100).toInt()}% of ${stats.formattedTarget}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val ENTRY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
