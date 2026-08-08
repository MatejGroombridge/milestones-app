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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.EmojiEvents
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dev.matejgroombridge.milestones.data.model.Milestone
import dev.matejgroombridge.milestones.data.model.MilestoneDirection
import dev.matejgroombridge.milestones.data.model.MilestoneUnit
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * The dialog behind a card tap: log a value and, if it beats the current
 * best, set a new personal best.
 *
 * Save is deliberately gated on actually beating the record. A milestone's
 * history is a strictly-improving chain (see `MilestoneRecord`), so accepting
 * a weaker value would either corrupt that or need a second, quieter kind of
 * entry — and neither is what "milestones" means. Instead the dialog shows,
 * live as you type, exactly how far off you are.
 *
 * A mistyped record is recovered by deleting it from the overview dialog,
 * which promotes the previous best back to the top.
 */
@Composable
fun LogRecordDialog(
    milestone: Milestone,
    todayEpochDay: Long,
    onDismiss: () -> Unit,
    onSave: (value: Double, epochDay: Long, note: String) -> Unit,
) {
    val context = LocalContext.current
    val color = MilestoneColors.entry(milestone.colorKey)

    var rawValue by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var epochDay by remember { mutableLongStateOf(todayEpochDay) }

    val parsed = remember(rawValue, milestone.unit) { milestone.unit.parse(rawValue) }
    val beatsRecord = parsed != null && milestone.beats(parsed)
    val canSave = beatsRecord

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = if (milestone.hasRecord) "New Record" else "First Record")
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
                CurrentBestBanner(milestone = milestone, accent = color.accent)

                CaptionedSection(caption = "Your result") {
                    OutlinedTextField(
                        value = rawValue,
                        onValueChange = { rawValue = it },
                        label = { Text(milestone.unit.fieldLabel) },
                        placeholder = { Text(milestone.unit.fieldPlaceholder) },
                        singleLine = true,
                        isError = rawValue.isNotBlank() && !beatsRecord,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardTypeFor(milestone.unit),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    ValueFeedback(
                        milestone = milestone,
                        raw = rawValue,
                        parsed = parsed,
                        beatsRecord = beatsRecord,
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
                        placeholder = { Text("e.g. Sunday parkrun") },
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
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * The number to beat, front and centre. Doubles as the empty-state message
 * for a milestone that has never been logged.
 */
@Composable
private fun CurrentBestBanner(
    milestone: Milestone,
    accent: androidx.compose.ui.graphics.Color,
) {
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
                    imageVector = Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (milestone.hasRecord) "Record to beat" else "No record yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (milestone.hasRecord) milestone.formattedBest
                    else "Anything you log becomes your first",
                    style = if (milestone.hasRecord) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (milestone.hasRecord) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Live commentary under the value field: how much better this would be, or
 * exactly how much short it falls. Showing the gap is far more useful than a
 * generic "not a record" — it tells the user what they're actually chasing.
 */
@Composable
private fun ValueFeedback(
    milestone: Milestone,
    raw: String,
    parsed: Double?,
    beatsRecord: Boolean,
) {
    val (text, tone) = when {
        raw.isBlank() -> hintFor(milestone) to Tone.Muted
        parsed == null -> unparseableHint(milestone.unit) to Tone.Error
        beatsRecord -> {
            val best = milestone.bestValue
            if (best == null) "Sets your first record" to Tone.Positive
            else {
                val delta = milestone.unit.formatMagnitude(abs(parsed - best))
                when (milestone.direction) {
                    MilestoneDirection.HigherIsBetter -> "$delta better than your record"
                    MilestoneDirection.LowerIsBetter -> "$delta faster than your record"
                } to Tone.Positive
            }
        }
        else -> {
            val best = milestone.bestValue
            val shortfall = if (best == null) null
            else milestone.unit.formatMagnitude(abs(parsed - best))
            when {
                best != null && parsed == best -> "That matches your record — beat it to log it"
                shortfall != null -> when (milestone.direction) {
                    MilestoneDirection.HigherIsBetter -> "$shortfall short of your record"
                    MilestoneDirection.LowerIsBetter -> "$shortfall slower than your record"
                }
                else -> "Not a new record"
            } to Tone.Error
        }
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

private fun hintFor(milestone: Milestone): String = when (milestone.direction) {
    MilestoneDirection.HigherIsBetter -> "Higher than your record counts"
    MilestoneDirection.LowerIsBetter -> "Lower than your record counts"
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
private fun keyboardTypeFor(unit: MilestoneUnit): KeyboardType = when (unit) {
    is MilestoneUnit.Time -> KeyboardType.Text
    is MilestoneUnit.Money -> KeyboardType.Decimal
    is MilestoneUnit.Numeric -> if (unit.decimals > 0) KeyboardType.Decimal else KeyboardType.Number
}

/**
 * Date picker row. Defaults to today but back-dating matters — you often log
 * a record the evening after, or catch up on a few at once.
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
                    // A record can't be set in the future, so don't offer one.
                    // `maxDate` is local-clock millis, so the current instant
                    // is the correct bound — deriving it from the epoch day
                    // would land on UTC midnight and hide today east of UTC.
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
