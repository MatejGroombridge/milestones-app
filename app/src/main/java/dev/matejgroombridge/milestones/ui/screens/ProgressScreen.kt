package dev.matejgroombridge.milestones.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.milestones.data.model.Milestone
import dev.matejgroombridge.milestones.ui.HomeViewModel
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import dev.matejgroombridge.milestones.ui.theme.MilestoneIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Per-milestone progression. Each row shows the milestone's headline stats
 * followed by a chart of every record plotted against the day it was set,
 * with the target (if any) as a dashed line to aim at.
 *
 * Plotting against real dates — rather than by index as the overview
 * sparkline does — is the point of this screen: it's where a long plateau
 * between two records actually shows up as a long flat stretch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Progress") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (state.activeMilestones.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                message = "No milestones yet.\nAdd one on the Milestones tab.",
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(items = state.activeMilestones, key = { it.id }) { milestone ->
                ProgressRow(milestone = milestone, today = state.todayEpochDay)
            }
        }
    }
}

@Composable
private fun ProgressRow(milestone: Milestone, today: Long) {
    val color = MilestoneColors.entry(milestone.colorKey)
    val iconEntry = MilestoneIcons.entry(milestone.iconKey)
    val chain = remember(milestone.records) { milestone.recordsByDate }

    val contentColor = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header sits inside the standard horizontal padding so it lines up
        // with the rest of the app.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconEntry.icon,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = milestone.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEvents,
                        contentDescription = "Personal best",
                        tint = color.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = milestone.formattedBest,
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Outlined.Timeline,
                        contentDescription = "Records set",
                        tint = color.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = milestone.records.size.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (chain.isEmpty()) {
            Text(
                text = "No records yet — tap the card on the Milestones tab to log one.",
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            ProgressChart(
                milestone = milestone,
                today = today,
                accent = color.accent,
                gridColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(120.dp),
            )

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = LocalDate.ofEpochDay(chain.first().epochDay).format(AXIS_DATE_FORMAT),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                )
                milestone.formattedTarget?.let { target ->
                    Text(
                        text = if (milestone.targetReached) "Target $target reached"
                        else "Target $target",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                    )
                }
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                )
            }
        }
    }
}

/**
 * Line chart of the record chain against real dates.
 *
 * The y-axis range spans every record *and* the target, so an unmet goal is
 * always visible on the chart rather than sitting off the top edge. A flat
 * run — one record, or several identical values — has no range to scale
 * against and is drawn on the midline.
 */
@Composable
private fun ProgressChart(
    milestone: Milestone,
    today: Long,
    accent: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    val chain = milestone.recordsByDate

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = gridColor.copy(alpha = 0.45f),
        modifier = modifier,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            if (chain.isEmpty()) return@Canvas

            val values = chain.map { it.value }
            val target = milestone.target
            val minValue = minOf(values.min(), target ?: values.min())
            val maxValue = maxOf(values.max(), target ?: values.max())
            val valueSpan = (maxValue - minValue).takeIf { it > 0.0 }

            val firstDay = chain.first().epochDay
            val lastDay = maxOf(today, chain.last().epochDay)
            val daySpan = (lastDay - firstDay).takeIf { it > 0L }

            fun yFor(value: Double): Float {
                val normalised = valueSpan?.let { ((value - minValue) / it).toFloat() } ?: 0.5f
                return size.height * (1f - normalised)
            }

            fun xFor(epochDay: Long): Float {
                val normalised = daySpan?.let { (epochDay - firstDay).toFloat() / it.toFloat() }
                    ?: 0.5f
                return size.width * normalised
            }

            // Baseline so an otherwise-empty chart still has structure.
            drawLine(
                color = accent.copy(alpha = 0.20f),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )

            if (target != null) {
                val y = yFor(target)
                drawLine(
                    color = accent.copy(alpha = 0.65f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                    ),
                )
            }

            if (chain.size > 1) {
                val path = Path().apply {
                    moveTo(xFor(chain.first().epochDay), yFor(chain.first().value))
                    chain.drop(1).forEach { record ->
                        lineTo(xFor(record.epochDay), yFor(record.value))
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

            chain.forEach { record ->
                drawCircle(
                    color = accent,
                    radius = 4.dp.toPx(),
                    center = Offset(xFor(record.epochDay), yFor(record.value)),
                )
            }
        }
    }
}

private val AXIS_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
