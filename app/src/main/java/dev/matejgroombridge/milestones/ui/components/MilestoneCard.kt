package dev.matejgroombridge.milestones.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.milestones.data.model.MilestoneKind
import dev.matejgroombridge.milestones.data.model.MilestoneStats
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import dev.matejgroombridge.milestones.ui.theme.MilestoneIcons
import dev.matejgroombridge.milestones.ui.theme.containerColor
import dev.matejgroombridge.milestones.ui.theme.contentColor
import dev.matejgroombridge.milestones.ui.util.rememberHaptics

/**
 * One milestone tile. Background uses the milestone's chosen pastel colour;
 * the icon tile uses the matching accent.
 *
 * The card's content is what actually separates the app's two purposes, so it
 * varies by kind × cadence:
 *
 * | | shows |
 * |---|---|
 * | Record × Lifetime | the best, and when it was set |
 * | Record × Yearly   | this year's best, with the all-time best beneath |
 * | Tally × Lifetime  | the running count |
 * | Tally × Yearly    | this year's count, with the all-time total beneath |
 *
 * A target replaces the secondary line with a progress bar in every case, and
 * a cleared target replaces it with the prompt to set the next one.
 *
 * Tap logs (instantly for a tally, via a dialog for a record); long-press
 * opens the overview.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MilestoneCard(
    stats: MilestoneStats,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val milestone = stats.milestone
    val color = MilestoneColors.entry(milestone.colorKey)
    val iconEntry = MilestoneIcons.entry(milestone.iconKey)
    val haptics = rememberHaptics()

    val hasSomething = stats.periodEntryCount > 0
    val baseContainer = color.containerColor()
    val baseContent = color.contentColor()
    val screenBg = MaterialTheme.colorScheme.background

    val targetContainer = when {
        // A cleared target is the loudest state on the grid — it's both an
        // achievement and an outstanding decision.
        stats.awaitingNewTarget -> blend(baseContainer, color.accent, 0.35f)
        hasSomething -> baseContainer
        else -> blend(baseContainer, screenBg, 0.35f)
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        label = "containerColor",
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    haptics.completion()
                    onClick()
                },
                onLongClick = {
                    haptics.longPress()
                    onLongClick()
                },
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MilestoneIconTile(
                    accent = color.accent,
                    tinted = hasSomething,
                    icon = iconEntry.icon,
                    contentDescription = iconEntry.label,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = milestone.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = baseContent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(10.dp))

            // The number is the point of the card, so it gets the largest
            // type on the tile rather than sitting in a subtitle.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = headlineFor(stats),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasSomething) baseContent else baseContent.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (stats.awaitingNewTarget) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.EmojiEvents,
                        contentDescription = "Target reached",
                        tint = baseContent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            MilestoneSubtitle(
                stats = stats,
                accent = color.accent,
                color = baseContent.copy(alpha = 0.75f),
            )
        }
    }
}

/**
 * The big number. A tally with a target reads as a fraction — "12 / 30" says
 * more at a glance than "12" plus a percentage underneath.
 */
private fun headlineFor(stats: MilestoneStats): String {
    val target = stats.activeTarget
    if (stats.kind == MilestoneKind.Tally && target != null) {
        return "${stats.formattedValue} / ${stats.unit.format(target.value)}"
    }
    return stats.formattedValue
}

@Composable
private fun MilestoneIconTile(
    accent: Color,
    tinted: Boolean,
    icon: ImageVector,
    contentDescription: String?,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(if (tinted) accent else accent.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.Black.copy(alpha = if (tinted) 0.85f else 0.55f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The line under the number, in priority order: an outstanding decision beats
 * a live goal, which beats context about where the number came from.
 */
@Composable
private fun MilestoneSubtitle(
    stats: MilestoneStats,
    accent: Color,
    color: Color,
) {
    val progress = stats.targetProgress
    when {
        stats.awaitingNewTarget -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(3.dp))
            SubtitleText("Set a new target", color)
        }

        progress != null -> TargetProgress(
            progress = progress,
            // A tally already shows "12 / 30" up top, so repeating the target
            // here would be noise — the percentage is the new information.
            label = if (stats.kind == MilestoneKind.Tally) {
                "${(progress * 100).toInt()}%"
            } else {
                "${(progress * 100).toInt()}% of ${stats.formattedTarget}"
            },
            accent = accent,
            color = color,
        )

        stats.periodEntryCount == 0 -> SubtitleText(emptyPrompt(stats), color)

        else -> SubtitleText(contextLine(stats), color)
    }
}

private fun emptyPrompt(stats: MilestoneStats): String = when (stats.kind) {
    MilestoneKind.Tally -> "Tap to add your first"
    MilestoneKind.Record -> "Tap to set your first record"
}

/**
 * Where the number came from, when there's no goal to show instead. Yearly
 * milestones lead with the all-time figure — the most useful thing you can
 * say about this year's number is how it sits against every other year.
 */
private fun contextLine(stats: MilestoneStats): String {
    stats.formattedAllTimeIfDistinct?.let { allTime ->
        return when (stats.kind) {
            MilestoneKind.Tally -> "$allTime all time"
            MilestoneKind.Record -> "Best ever $allTime"
        }
    }
    return when (stats.kind) {
        MilestoneKind.Tally -> {
            val count = stats.periodEntryCount
            "$count entr${if (count == 1) "y" else "ies"}"
        }
        MilestoneKind.Record -> {
            val improvement = stats.lastImprovement
            if (improvement != null) {
                "${stats.unit.formatMagnitude(improvement)} better than before"
            } else {
                "First record"
            }
        }
    }
}

/**
 * Slim progress bar plus its caption. Drawn by hand rather than with
 * `LinearProgressIndicator` so the track can sit on the card's pastel
 * background at a low alpha, instead of introducing a theme colour that
 * clashes with the milestone's own palette entry.
 */
@Composable
private fun TargetProgress(
    progress: Float,
    label: String,
    accent: Color,
    color: Color,
) {
    val animated by animateFloatAsState(targetValue = progress, label = "targetProgress")
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.10f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated.coerceIn(0f, 1f))
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        SubtitleText(label, color)
    }
}

@Composable
private fun SubtitleText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Linear blend between [a] and [b] in straight RGB (good enough for pastels). */
internal fun blend(a: Color, b: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(
        red = a.red * (1 - u) + b.red * u,
        green = a.green * (1 - u) + b.green * u,
        blue = a.blue * (1 - u) + b.blue * u,
        alpha = a.alpha * (1 - u) + b.alpha * u,
    )
}
