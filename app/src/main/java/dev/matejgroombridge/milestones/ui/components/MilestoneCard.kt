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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.milestones.data.model.Milestone
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import dev.matejgroombridge.milestones.ui.theme.MilestoneIcons
import dev.matejgroombridge.milestones.ui.theme.containerColor
import dev.matejgroombridge.milestones.ui.theme.contentColor
import dev.matejgroombridge.milestones.ui.util.rememberHaptics

/**
 * One milestone tile. Background uses the milestone's chosen pastel colour;
 * the icon tile uses the matching accent. Tap opens the log-a-record dialog;
 * long-press triggers [onLongClick] (used by the caller to open the overview).
 *
 * The card's tint tracks how far along the goal is:
 *  - **No record yet** — pulled toward the screen background so it reads as
 *    an empty slot waiting to be filled.
 *  - **Has a record** — full pastel container.
 *  - **Target reached** — blended toward the accent so a completed goal pops
 *    out of the grid, with a small trophy next to the value.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MilestoneCard(
    milestone: Milestone,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = MilestoneColors.entry(milestone.colorKey)
    val iconEntry = MilestoneIcons.entry(milestone.iconKey)
    val haptics = rememberHaptics()

    val baseContainer = color.containerColor()
    val baseContent = color.contentColor()
    val screenBg = MaterialTheme.colorScheme.background

    val targetContainer = when {
        milestone.targetReached -> blend(baseContainer, color.accent, 0.35f)
        milestone.hasRecord -> baseContainer
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
                    tinted = milestone.hasRecord,
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

            // The record itself is the point of the card, so it gets the
            // largest type on the tile rather than sitting in a subtitle.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = milestone.formattedBest,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (milestone.hasRecord) baseContent else baseContent.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (milestone.targetReached) {
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
                milestone = milestone,
                accent = color.accent,
                color = baseContent.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun MilestoneIconTile(
    accent: Color,
    tinted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
 * The line under the value. Priority order — a target is the most useful
 * thing to show, then how stale the record is, then a prompt to get started.
 */
@Composable
private fun MilestoneSubtitle(
    milestone: Milestone,
    accent: Color,
    color: Color,
) {
    val progress = milestone.targetProgress
    when {
        !milestone.hasRecord -> SubtitleText("Tap to set your first record", color)
        progress != null -> TargetProgress(
            progress = progress,
            label = if (milestone.targetReached) "Target reached"
            else "${(progress * 100).toInt()}% of ${milestone.formattedTarget}",
            accent = accent,
            color = color,
        )
        else -> {
            val improvement = milestone.lastImprovement
            val text = if (improvement != null) {
                "${milestone.unit.formatMagnitude(improvement)} better than before"
            } else {
                "${milestone.records.size} record${if (milestone.records.size == 1) "" else "s"}"
            }
            SubtitleText(text, color)
        }
    }
}

/**
 * Slim progress bar plus its caption. Drawn by hand rather than with
 * `LinearProgressIndicator` so the track can sit on the card's pastel
 * background at a low alpha instead of introducing a theme colour that
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
