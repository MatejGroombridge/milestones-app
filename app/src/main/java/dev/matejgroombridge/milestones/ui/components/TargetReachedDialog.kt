package dev.matejgroombridge.milestones.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.milestones.data.model.MilestoneDirection
import dev.matejgroombridge.milestones.data.model.MilestoneKind
import dev.matejgroombridge.milestones.data.model.MilestoneStats
import dev.matejgroombridge.milestones.data.model.MilestoneTarget
import dev.matejgroombridge.milestones.data.model.MilestoneUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Shown the moment an entry clears the active target.
 *
 * This exists because reaching a goal used to be a dead end — the milestone
 * just said "target reached" forever, and pushing further meant creating a
 * whole new milestone and orphaning the history. Offering the next target
 * right here keeps "bench 100kg" and "bench 110kg" as one continuing story.
 *
 * The next target arrives pre-filled with a suggestion so the common case is
 * a single tap, but it's a plain editable field — the guess is a convenience,
 * not a decision.
 */
@Composable
fun TargetReachedDialog(
    stats: MilestoneStats,
    reachedTarget: MilestoneTarget,
    onDismiss: () -> Unit,
    onSetTarget: (Double) -> Unit,
) {
    val suggestion = remember(stats, reachedTarget) { suggestNextTarget(stats) }
    var rawTarget by remember {
        mutableStateOf(suggestion?.let { stats.unit.format(it) }.orEmpty())
    }
    val parsed = stats.unit.parse(rawTarget)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = "Target reached",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${stats.milestone.name} — you hit " +
                        "${stats.unit.format(reachedTarget.value)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                CaptionedSection(caption = "Next target") {
                    OutlinedTextField(
                        value = rawTarget,
                        onValueChange = { rawTarget = it },
                        label = { Text("Target") },
                        placeholder = { Text(stats.unit.fieldPlaceholder) },
                        singleLine = true,
                        isError = rawTarget.isNotBlank() && parsed == null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardTypeFor(stats.unit),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onSetTarget) },
                enabled = parsed != null,
            ) { Text("Set target") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

/**
 * Guesses the next target: keep going by roughly as much as last time.
 *
 * The step is the gap between the last two targets when there's a history to
 * learn from, and 10% of the current value otherwise. Results are rounded to
 * a friendly magnitude, because "110 kg" is a goal and "108.4 kg" is a
 * calculation.
 *
 * Returns null when there's nothing sensible to suggest — the field then
 * opens empty rather than showing a made-up number.
 */
private fun suggestNextTarget(stats: MilestoneStats): Double? {
    val current = stats.value ?: return null
    val cleared = stats.milestone.targetsIn(stats.periodKey)
        .filter { it.isReached }
        .sortedBy { it.setOnEpochDay }

    val step = if (cleared.size >= 2) {
        abs(cleared[cleared.lastIndex].value - cleared[cleared.lastIndex - 1].value)
    } else {
        abs(current) * 0.1
    }
    if (step <= 0.0) return null

    val climbing = stats.kind == MilestoneKind.Tally ||
        stats.milestone.direction == MilestoneDirection.HigherIsBetter
    val raw = if (climbing) current + step else current - step
    // A "lower is better" milestone can't sensibly aim at zero or below.
    if (!climbing && raw <= 0.0) return null

    return roundFriendly(raw, stats.unit)
}

/** Rounds to a magnitude a person would actually choose as a goal. */
private fun roundFriendly(value: Double, unit: MilestoneUnit): Double {
    val decimals = (unit as? MilestoneUnit.Numeric)?.decimals ?: 0
    if (decimals > 0) {
        val factor = generateSequence(1.0) { it * 10 }.take(decimals + 1).last()
        return (value * factor).roundToLong() / factor
    }
    val magnitude = when {
        abs(value) >= 1000 -> 100.0
        abs(value) >= 100 -> 10.0
        else -> 1.0
    }
    return (value / magnitude).roundToLong() * magnitude
}
