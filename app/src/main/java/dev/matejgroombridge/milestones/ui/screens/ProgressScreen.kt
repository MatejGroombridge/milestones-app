package dev.matejgroombridge.milestones.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import dev.matejgroombridge.milestones.data.model.MilestoneCadence
import dev.matejgroombridge.milestones.data.model.MilestoneKind
import dev.matejgroombridge.milestones.data.model.MilestoneStats
import dev.matejgroombridge.milestones.data.model.yearOf
import dev.matejgroombridge.milestones.ui.HomeViewModel
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import dev.matejgroombridge.milestones.ui.theme.MilestoneIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Per-milestone progression, plotted against real dates.
 *
 * Dates rather than entry index is the point of this screen: it's where a long
 * plateau between two records actually looks like a long flat stretch, and
 * where a yearly goal's pace against the calendar becomes visible. The
 * overview dialog's sparkline covers the "shape of the journey" glance.
 *
 * What gets drawn depends on the milestone:
 *  - **Tally** — the running total climbing from zero, so the line shows
 *    accumulation rather than a row of identical `+1`s.
 *  - **Record** — the values themselves.
 *  - **Yearly** — the x-axis spans the calendar year, so how far through it
 *    you are is legible at a glance, with past years summarised beneath.
 *  - **Lifetime** — the x-axis spans first entry to today.
 *
 * Targets overlay as dashed lines: the active one in full accent, cleared ones
 * faded, which turns the goal history into a picture of how far it has come.
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
        if (state.hasNoMilestones) {
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
            items(items = state.activeStats, key = { it.milestone.id }) { stats ->
                ProgressRow(stats = stats, today = state.todayEpochDay)
            }
        }
    }
}

/** A point on the chart: when, and what the line was worth then. */
private data class Plot(val epochDay: Long, val value: Double)

@Composable
private fun ProgressRow(stats: MilestoneStats, today: Long) {
    val milestone = stats.milestone
    val color = MilestoneColors.entry(milestone.colorKey)
    val iconEntry = MilestoneIcons.entry(milestone.iconKey)
    val isTally = stats.kind == MilestoneKind.Tally

    val contentColor = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    // The window the chart spans. A yearly milestone always shows the whole
    // calendar year so "how far through am I?" is answerable; a lifetime one
    // spans from its first entry to now.
    val range = remember(stats, today) { chartRange(stats, today) }
    val plots = remember(stats, range) { plotsFor(stats, range.first) }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                        contentDescription = if (isTally) "Total" else "Personal best",
                        tint = color.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = stats.formattedValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Outlined.Timeline,
                        contentDescription = "Entries",
                        tint = color.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = stats.periodEntryCount.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (plots.isEmpty()) {
            Text(
                text = "Nothing logged yet — tap the card on the Milestones tab.",
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            ProgressChart(
                stats = stats,
                plots = plots,
                range = range,
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
                    text = LocalDate.ofEpochDay(range.first).format(AXIS_DATE_FORMAT),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                )
                stats.formattedTarget?.let { target ->
                    Text(
                        text = "Target $target",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                    )
                }
                Text(
                    text = if (stats.cadence == MilestoneCadence.Yearly) {
                        LocalDate.ofEpochDay(range.second).format(AXIS_DATE_FORMAT)
                    } else "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                )
            }
        }

        if (stats.cadence == MilestoneCadence.Yearly) {
            PastYears(stats = stats, today = today, mutedColor = mutedColor)
        }
    }
}

/** Inclusive epoch-day window the chart covers. */
private fun chartRange(stats: MilestoneStats, today: Long): Pair<Long, Long> {
    if (stats.cadence == MilestoneCadence.Yearly) {
        val year = yearOf(today)
        return LocalDate.of(year, 1, 1).toEpochDay() to LocalDate.of(year, 12, 31).toEpochDay()
    }
    val first = stats.periodEntries.firstOrNull()?.epochDay ?: today
    val last = stats.periodEntries.lastOrNull()?.epochDay ?: today
    return first to maxOf(today, last)
}

/**
 * The points to draw.
 *
 * A tally becomes its running total and gains a zero point at the start of the
 * window, so the line climbs from the floor rather than starting mid-air at
 * whatever the first entry happened to be worth.
 */
private fun plotsFor(stats: MilestoneStats, rangeStart: Long): List<Plot> {
    val entries = stats.periodEntries
    if (entries.isEmpty()) return emptyList()
    if (stats.kind != MilestoneKind.Tally) {
        return entries.map { Plot(it.epochDay, it.value) }
    }
    var running = 0.0
    val climb = entries.map { entry ->
        running += entry.value
        Plot(entry.epochDay, running)
    }
    val floorDay = minOf(rangeStart, climb.first().epochDay)
    return listOf(Plot(floorDay, 0.0)) + climb
}

@Composable
private fun ProgressChart(
    stats: MilestoneStats,
    plots: List<Plot>,
    range: Pair<Long, Long>,
    accent: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    val targets = remember(stats) {
        stats.milestone.targetsIn(stats.periodKey).sortedBy { it.setOnEpochDay }
    }

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
            if (plots.isEmpty()) return@Canvas

            val values = plots.map { it.value }
            // Targets join the y-range so an unmet goal is always on the
            // chart, rather than sitting off the top edge where it can't be
            // judged against.
            val targetValues = targets.map { it.value }
            val minValue = (values + targetValues).min()
            val maxValue = (values + targetValues).max()
            val valueSpan = (maxValue - minValue).takeIf { it > 0.0 }

            val (rangeStart, rangeEnd) = range
            val daySpan = (rangeEnd - rangeStart).takeIf { it > 0L }

            fun yFor(value: Double): Float {
                val normalised = valueSpan?.let { ((value - minValue) / it).toFloat() } ?: 0.5f
                return size.height * (1f - normalised)
            }

            /**
             * Everything logged on a single day gives a zero-width date range
             * — the case every milestone starts in. Rather than stacking the
             * points into a vertical bar at the midpoint, fall back to even
             * index spacing so the shape is still readable on day one.
             */
            fun xFor(epochDay: Long, index: Int): Float {
                val span = daySpan ?: return when {
                    plots.size == 1 -> size.width / 2f
                    else -> size.width * (index.toFloat() / (plots.size - 1))
                }
                return size.width *
                    ((epochDay - rangeStart).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            }

            // Baseline so an otherwise-sparse chart still has structure.
            drawLine(
                color = accent.copy(alpha = 0.20f),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )

            targets.forEach { target ->
                val y = yFor(target.value)
                drawLine(
                    // A cleared target is history, so it recedes; the one
                    // still being chased stays loud.
                    color = accent.copy(alpha = if (target.isReached) 0.28f else 0.65f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                    ),
                )
            }

            if (plots.size > 1) {
                val path = Path().apply {
                    moveTo(xFor(plots.first().epochDay, 0), yFor(plots.first().value))
                    plots.forEachIndexed { index, plot ->
                        if (index > 0) lineTo(xFor(plot.epochDay, index), yFor(plot.value))
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

            plots.forEachIndexed { index, plot ->
                drawCircle(
                    color = accent,
                    radius = 4.dp.toPx(),
                    center = Offset(xFor(plot.epochDay, index), yFor(plot.value)),
                )
            }
        }
    }
}

/**
 * How previous years went — the payoff of a yearly cadence, and the context
 * that makes this year's number mean something.
 */
@Composable
private fun PastYears(stats: MilestoneStats, today: Long, mutedColor: Color) {
    val currentYear = yearOf(today)
    val summaries = remember(stats.milestone.entries, currentYear) {
        stats.milestone.entriesByDate
            .groupBy { yearOf(it.epochDay) }
            .filterKeys { it != currentYear }
            .toSortedMap(compareByDescending { it })
            .map { (year, entries) -> year to stats.milestone.valueOf(entries) }
            .filter { it.second != null }
    }
    if (summaries.isEmpty()) return

    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        summaries.forEach { (year, value) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                    )
                    Text(
                        text = stats.unit.format(value ?: 0.0),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val AXIS_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
