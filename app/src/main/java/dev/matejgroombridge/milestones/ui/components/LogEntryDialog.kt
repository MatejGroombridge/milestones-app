package dev.matejgroombridge.milestones.ui.components

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.milestones.data.model.MilestoneCadence
import dev.matejgroombridge.milestones.data.model.MilestoneDirection
import dev.matejgroombridge.milestones.data.model.MilestoneKind
import dev.matejgroombridge.milestones.data.model.MilestoneStats
import dev.matejgroombridge.milestones.data.model.MilestoneUnit
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * The sheet for logging something, in both flavours.
 *
 * **Records** gate saving on actually beating the current best. A milestone's
 * record history is a strictly-improving chain, so accepting a weaker value
 * would either corrupt that or need a second, quieter kind of entry — and
 * neither is what "personal best" means. Instead the dialog shows, live as
 * you type, exactly how far off you are.
 *
 * **Tallies** accept anything non-zero, because every event counts. The
 * feedback line previews where the entry lands you instead of judging it.
 *
 * Either way, a mistyped entry is recovered by deleting it from the overview
 * dialog, which promotes the previous best back to the top.
 */
@Composable
fun LogEntryDialog(
    stats: MilestoneStats,
    todayEpochDay: Long,
    onDismiss: () -> Unit,
    onSave: (value: Double, epochDay: Long, note: String) -> Unit,
) {
    val milestone = stats.milestone
    val context = LocalContext.current
    val color = MilestoneColors.entry(milestone.colorKey)
    val isTally = stats.kind == MilestoneKind.Tally

    // A tally almost always means "one more", so it opens pre-filled and the
    // user can hit save immediately.
    var rawValue by remember { mutableStateOf(if (isTally) "1" else "") }
    var note by remember { mutableStateOf("") }
    var epochDay by remember { mutableLongStateOf(todayEpochDay) }

    val parsed = remember(rawValue, stats.unit) { stats.unit.parse(rawValue) }
    val canSave = if (isTally) {
        parsed != null && parsed != 0.0
    } else {
        parsed != null && milestone.accepts(parsed, todayEpochDay)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = when {
                        isTally -> "Add"
                        stats.periodEntryCount > 0 -> "New Record"
                        else -> "First Record"
                    },
                )
                Text(
                    text = milestone.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StandingBanner(stats = stats, accent = color.accent)

                CaptionedSection(caption = if (isTally) "How much" else "Your result") {
                    OutlinedTextField(
                        value = rawValue,
                        onValueChange = { rawValue = it },
                        label = { Text(if (isTally) "Amount" else stats.unit.fieldLabel) },
                        placeholder = { Text(stats.unit.fieldPlaceholder) },
                        singleLine = true,
                        isError = rawValue.isNotBlank() && !canSave,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardTypeFor(stats.unit),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Whole-number tallies are the common case — counting
                    // books, countries, articles — so offer the usual steps
                    // rather than making the user open the keypad.
                    val numeric = stats.unit as? MilestoneUnit.Numeric
                    if (isTally && numeric != null && numeric.decimals == 0) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 5).forEach { step ->
                                ChoiceCell(
                                    label = "+$step",
                                    selected = parsed == step.toDouble(),
                                    onClick = { rawValue = step.toString() },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ValueFeedback(
                        stats = stats,
                        raw = rawValue,
                        parsed = parsed,
                        accepted = canSave,
                    )
                }

                CaptionedSection(caption = "When") {
                    DateRow(
                        epochDay = epochDay,
                        todayEpochDay = todayEpochDay,
                        context = context,
                        onPick = { epochDay = it },
                    )
                }

                CaptionedSection(caption = "Note") {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        placeholder = {
                            Text(if (isTally) "e.g. Japan" else "e.g. Sunday parkrun")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onSave(it, epochDay, note) } },
                enabled = canSave,
            ) { Text(if (isTally) "Add" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Where the milestone stands right now — the number to beat for a record, the
 * running count for a tally. Doubles as the empty state.
 */
@Composable
private fun StandingBanner(stats: MilestoneStats, accent: Color) {
    val isTally = stats.kind == MilestoneKind.Tally
    val hasSomething = stats.periodEntryCount > 0

    val caption = when {
        isTally && stats.cadence == MilestoneCadence.Yearly -> "So far this year"
        isTally -> "Running total"
        hasSomething && stats.cadence == MilestoneCadence.Yearly -> "This year's record to beat"
        hasSomething -> "Record to beat"
        else -> "No record yet"
    }
    val headline = when {
        isTally || hasSomething -> stats.formattedValue
        else -> "Anything you log becomes your first"
    }

    EditorSection(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isTally) Icons.Outlined.Timeline
                    else Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = headline,
                    style = if (isTally || hasSomething) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isTally || hasSomething) FontWeight.SemiBold
                    else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Live commentary under the value field.
 *
 * For a record: how much better this would be, or exactly how much short it
 * falls — showing the gap is far more useful than a generic "not a record",
 * because it names what you're actually chasing.
 *
 * For a tally: where the entry lands you, including against the target.
 */
@Composable
private fun ValueFeedback(
    stats: MilestoneStats,
    raw: String,
    parsed: Double?,
    accepted: Boolean,
) {
    val (text, tone) = when {
        raw.isBlank() -> hintFor(stats) to Tone.Muted
        parsed == null -> unparseableHint(stats.unit) to Tone.Error
        stats.kind == MilestoneKind.Tally -> tallyPreview(stats, parsed, accepted)
        accepted -> recordGain(stats, parsed) to Tone.Positive
        else -> recordShortfall(stats, parsed) to Tone.Error
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (tone == Tone.Positive) {
            Icon(
                imageVector = Icons.Outlined.ArrowUpward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = when (tone) {
                Tone.Positive -> MaterialTheme.colorScheme.primary
                Tone.Error -> MaterialTheme.colorScheme.error
                Tone.Muted -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private enum class Tone { Positive, Error, Muted }

private fun tallyPreview(
    stats: MilestoneStats,
    parsed: Double,
    accepted: Boolean,
): Pair<String, Tone> {
    if (!accepted) return "Enter an amount other than zero" to Tone.Error
    val next = (stats.value ?: 0.0) + parsed
    val target = stats.activeTarget
    return when {
        target == null -> "Takes you to ${stats.unit.format(next)}" to Tone.Positive
        next >= target.value ->
            "Takes you to ${stats.unit.format(next)} — reaches your target" to Tone.Positive
        else ->
            "Takes you to ${stats.unit.format(next)} of ${stats.unit.format(target.value)}" to Tone.Positive
    }
}

private fun recordGain(stats: MilestoneStats, parsed: Double): String {
    val best = stats.value ?: return "Sets your first record"
    val delta = stats.unit.formatMagnitude(abs(parsed - best))
    return when (stats.milestone.direction) {
        MilestoneDirection.HigherIsBetter -> "$delta better than your record"
        MilestoneDirection.LowerIsBetter -> "$delta faster than your record"
    }
}

private fun recordShortfall(stats: MilestoneStats, parsed: Double): String {
    val best = stats.value ?: return "Not a new record"
    if (parsed == best) return "That matches your record — beat it to log it"
    val delta = stats.unit.formatMagnitude(abs(parsed - best))
    return when (stats.milestone.direction) {
        MilestoneDirection.HigherIsBetter -> "$delta short of your record"
        MilestoneDirection.LowerIsBetter -> "$delta slower than your record"
    }
}

private fun hintFor(stats: MilestoneStats): String = when (stats.kind) {
    MilestoneKind.Tally -> "Every entry adds to the total"
    MilestoneKind.Record -> when (stats.milestone.direction) {
        MilestoneDirection.HigherIsBetter -> "Higher than your record counts"
        MilestoneDirection.LowerIsBetter -> "Lower than your record counts"
    }
}

private fun unparseableHint(unit: MilestoneUnit): String = when (unit) {
    is MilestoneUnit.Time -> "Enter a time like 24:30 or 1:23:45"
    else -> "Enter a number"
}

/**
 * Times need a colon, which the numeric keypads don't offer, so they get the
 * text keyboard. Everything else gets a numeric one — decimal where the unit
 * actually has decimal places.
 */
internal fun keyboardTypeFor(unit: MilestoneUnit): KeyboardType = when (unit) {
    is MilestoneUnit.Time -> KeyboardType.Text
    is MilestoneUnit.Money -> KeyboardType.Decimal
    is MilestoneUnit.Numeric -> if (unit.decimals > 0) KeyboardType.Decimal else KeyboardType.Number
}

/**
 * Date picker row. Defaults to today, but back-dating matters — you often log
 * the evening after, or catch up on a few at once.
 */
@Composable
private fun DateRow(
    epochDay: Long,
    todayEpochDay: Long,
    context: Context,
    onPick: (Long) -> Unit,
) {
    val date = LocalDate.ofEpochDay(epochDay)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onPick(LocalDate.of(year, month + 1, dayOfMonth).toEpochDay())
                    },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth,
                ).apply {
                    // Nothing can be logged in the future. `maxDate` is
                    // local-clock millis, so the current instant is the right
                    // bound — deriving it from the epoch day would land on UTC
                    // midnight and hide today east of UTC.
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            }
            .padding(horizontal = 2.dp, vertical = 6.dp),
    ) {
        Text(
            text = "Date",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = if (epochDay == todayEpochDay) "Today" else date.format(DATE_FORMAT),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
