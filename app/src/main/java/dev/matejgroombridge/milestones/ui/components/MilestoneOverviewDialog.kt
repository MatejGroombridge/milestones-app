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
import dev.matejgroombridge.milestones.data.model.Milestone
import dev.matejgroombridge.milestones.data.model.MilestoneRecord
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import dev.matejgroombridge.milestones.ui.theme.MilestoneIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Read-only "milestone overview" sheet shown when the user long-presses a
 * card. Designed as a polished snapshot rather than a control surface — the
 * only edits available are the Edit button (which routes to
 * [MilestoneEditorDialog]) and deleting an individual record.
 *
 * Surfaced metrics:
 *  - Large coloured icon badge using the milestone's accent.
 *  - Name + description + a chip describing how it's measured.
 *  - Three big stats: personal best, records set, total improvement.
 *  - A sparkline of the progression so the shape of the journey is visible.
 *  - The record history, newest first, each row deletable.
 *  - Footer line with tracking age / target progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneOverviewDialog(
    milestone: Milestone,
    todayEpochDay: Long,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRecord: (String) -> Unit,
) {
    val color = MilestoneColors.entry(milestone.colorKey)
    val iconEntry = MilestoneIcons.entry(milestone.iconKey)

    val chain = remember(milestone.records) { milestone.recordsByDate }
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
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                // Header row: large icon, name + measurement chip on the
                // left; edit pinned to the right.
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
                        MeasurementChip(
                            text = "${milestone.unit.label} · ${milestone.direction.shortLabel}",
                            accent = color.accent,
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

                // Three big stat tiles. Equal-weight Row so they stretch to
                // fill the dialog width regardless of value length.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        label = "Best",
                        value = milestone.formattedBest,
                        icon = Icons.Outlined.EmojiEvents,
                        accent = color.accent,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Records",
                        value = milestone.records.size.toString(),
                        icon = Icons.Outlined.Timeline,
                        accent = color.accent,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Improved",
                        value = milestone.totalImprovement
                            ?.let { milestone.unit.formatMagnitude(it) } ?: "—",
                        icon = Icons.Outlined.Straighten,
                        accent = color.accent,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (chain.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Progression",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Sparkline(
                        values = chain.map { it.value },
                        accent = color.accent,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    )

                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Record history",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Newest first: the most recent entries are the ones a
                    // user is most likely to want to correct.
                    chain.asReversed().forEachIndexed { indexFromEnd, record ->
                        val previous = chain.getOrNull(chain.lastIndex - indexFromEnd - 1)
                        RecordRow(
                            milestone = milestone,
                            record = record,
                            improvement = previous?.let { abs(record.value - it.value) },
                            isBest = record.id == milestone.best?.id,
                            accent = color.accent,
                            onDelete = { onDeleteRecord(record.id) },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                } else {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "No records yet — tap the card to log your first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))
                FooterStats(milestone = milestone, daysSinceCreated = daysSinceCreated)
            }
        }
    }
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

/**
 * One row of the record history: when, what, how much better, and a delete
 * affordance.
 *
 * Delete is the only way to undo a mistyped record — an unbeatable typo would
 * otherwise lock the milestone forever — so it's always visible rather than
 * hidden behind a long-press.
 */
@Composable
private fun RecordRow(
    milestone: Milestone,
    record: MilestoneRecord,
    improvement: Double?,
    isBest: Boolean,
    accent: Color,
    onDelete: () -> Unit,
) {
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
                        text = milestone.unit.format(record.value),
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
                        append(LocalDate.ofEpochDay(record.epochDay).format(RECORD_DATE_FORMAT))
                        if (improvement != null) {
                            append(" · +")
                            append(milestone.unit.formatMagnitude(improvement))
                        }
                        if (record.note.isNotBlank()) {
                            append(" · ")
                            append(record.note)
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
                    contentDescription = "Delete record",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Minimal line chart of the record chain, plotted by index rather than date.
 *
 * Index spacing is deliberate here: this is a "shape of the journey" glance,
 * and evenly-spaced points keep a milestone whose records are years apart
 * from collapsing into a flat line with one spike. The Progress screen plots
 * the same data against real dates when the timing matters.
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
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (values.isEmpty()) return@Canvas
            val minValue = values.min()
            val maxValue = values.max()
            val span = (maxValue - minValue).takeIf { it > 0.0 }

            fun pointAt(index: Int): Offset {
                val x = if (values.size == 1) size.width / 2f
                else size.width * (index.toFloat() / (values.size - 1))
                // A flat run (single record, or several identical values)
                // has no range to scale against, so it sits on the midline.
                val normalised = span?.let { ((values[index] - minValue) / it).toFloat() } ?: 0.5f
                return Offset(x, size.height * (1f - normalised))
            }

            if (values.size > 1) {
                val path = Path().apply {
                    moveTo(pointAt(0).x, pointAt(0).y)
                    for (i in 1 until values.size) {
                        val p = pointAt(i)
                        lineTo(p.x, p.y)
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
private fun FooterStats(milestone: Milestone, daysSinceCreated: Long) {
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
        val progress = milestone.targetProgress
        Text(
            text = when {
                progress == null -> "No target"
                milestone.targetReached -> "Target reached"
                else -> "${(progress * 100).toInt()}% of ${milestone.formattedTarget}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val RECORD_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
