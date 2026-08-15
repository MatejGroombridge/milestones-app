package dev.matejgroombridge.milestones.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.milestones.data.model.Milestone
import dev.matejgroombridge.milestones.data.model.MilestoneCadence
import dev.matejgroombridge.milestones.data.model.MilestoneDirection
import dev.matejgroombridge.milestones.data.model.MilestoneKind
import dev.matejgroombridge.milestones.data.model.MilestoneUnit
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors

/** Result emitted by [MilestoneEditorDialog] when the user takes an action. */
sealed interface MilestoneEditorResult {
    data class Save(
        val name: String,
        val description: String,
        val iconKey: String,
        val colorKey: String,
        val kind: MilestoneKind,
        val cadence: MilestoneCadence,
        val unit: MilestoneUnit,
        val direction: MilestoneDirection,
        val target: Double?,
        /**
         * Seeds the first entry on creation, so a user who already knows
         * their current best (or count) doesn't have to create-then-log.
         * Always null in edit mode — existing entries are managed from the
         * overview dialog.
         */
        val startingValue: Double?,
    ) : MilestoneEditorResult

    /**
     * Delete is intentionally not exposed from this dialog — milestones can
     * only be deleted from the Archive screen. Use [Archive] to archive first.
     */
    data class Archive(val archived: Boolean) : MilestoneEditorResult
}

/** Which unit family the editor is currently showing. */
private enum class UnitKind { Number, Time, Money }

/**
 * Single dialog for both creating and editing a milestone. The icon badge in
 * the top-left opens a combined icon + colour picker; colour is randomised on
 * creation so a fresh grid isn't all one shade.
 *
 * The two pickers that matter most sit directly under the name, because they
 * decide everything below them: [MilestoneKind] changes what an entry means
 * (and whether direction applies at all), and [MilestoneCadence] changes which
 * section of the grid the milestone lands in.
 *
 * For edit mode, archive is a small icon-only action at the top-right of the
 * title row. Deletion is intentionally not available here — to delete a
 * milestone the user must first archive it and then delete from the Archive
 * screen, which protects against accidental loss of history.
 */
@Composable
fun MilestoneEditorDialog(
    existing: Milestone?,
    /** The target currently being chased, so edit mode can show and change it. */
    activeTarget: Double?,
    onDismiss: () -> Unit,
    onResult: (MilestoneEditorResult) -> Unit,
) {
    val isEdit = existing != null

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: Milestone.DEFAULT_ICON_KEY) }
    var colorKey by remember {
        mutableStateOf(existing?.colorKey ?: MilestoneColors.palette.random().key)
    }
    var kind by remember { mutableStateOf(existing?.kind ?: MilestoneKind.Default) }
    var cadence by remember { mutableStateOf(existing?.cadence ?: MilestoneCadence.Default) }
    var direction by remember {
        mutableStateOf(existing?.direction ?: MilestoneDirection.Default)
    }

    val initialUnit = existing?.unit ?: MilestoneUnit.Default
    var unitKind by remember {
        mutableStateOf(
            when (initialUnit) {
                is MilestoneUnit.Numeric -> UnitKind.Number
                MilestoneUnit.Time -> UnitKind.Time
                is MilestoneUnit.Money -> UnitKind.Money
            },
        )
    }
    var suffix by remember {
        mutableStateOf((initialUnit as? MilestoneUnit.Numeric)?.suffix.orEmpty())
    }
    var decimals by remember {
        mutableIntStateOf((initialUnit as? MilestoneUnit.Numeric)?.decimals ?: 0)
    }
    var symbol by remember {
        mutableStateOf((initialUnit as? MilestoneUnit.Money)?.symbol ?: "£")
    }

    // Rebuilt on every keystroke so the target and starting-value fields parse
    // against the unit the user is currently configuring.
    val unit: MilestoneUnit = when (unitKind) {
        UnitKind.Number -> MilestoneUnit.Numeric(
            suffix = suffix.trim(),
            decimals = decimals.coerceIn(0, MilestoneUnit.MAX_DECIMALS),
        )
        UnitKind.Time -> MilestoneUnit.Time
        UnitKind.Money -> MilestoneUnit.Money(symbol = symbol.trim().ifBlank { "£" })
    }

    var rawTarget by remember {
        mutableStateOf(activeTarget?.let { initialUnit.format(it) }.orEmpty())
    }
    var rawStartingValue by remember { mutableStateOf("") }

    var showIconPicker by remember { mutableStateOf(false) }

    val target = unit.parse(rawTarget)
    val startingValue = if (isEdit) null else unit.parse(rawStartingValue)
    val canSave = name.trim().isNotEmpty()

    fun submit() {
        if (!canSave) return
        onResult(
            MilestoneEditorResult.Save(
                name = name,
                description = description,
                iconKey = iconKey,
                colorKey = colorKey,
                kind = kind,
                cadence = cadence,
                unit = unit,
                // A tally only climbs; whatever the direction picker last
                // showed is irrelevant, and the repository enforces this too.
                direction = if (kind == MilestoneKind.Tally) {
                    MilestoneDirection.HigherIsBetter
                } else direction,
                target = target,
                startingValue = startingValue,
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isEdit) "Edit Milestone" else "New Milestone",
                    modifier = Modifier.weight(1f),
                )
                if (isEdit) {
                    val archived = existing?.archived == true
                    IconButton(onClick = {
                        onResult(MilestoneEditorResult.Archive(!archived))
                    }) {
                        Icon(
                            imageVector = if (archived) Icons.Outlined.Unarchive
                            else Icons.Outlined.Archive,
                            contentDescription = if (archived) "Restore" else "Archive",
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                // Tighter rhythm — captions sit just above their cards with
                // 4dp inside CaptionedSection, and 12dp between sections.
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // --- Identity card: icon + title + description ---
                EditorSection(padding = 12.dp) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconBadge(
                            iconKey = iconKey,
                            colorKey = colorKey,
                            onClick = { showIconPicker = true },
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Title") },
                            placeholder = { Text("e.g. Fastest 5km") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3,
                    )
                }

                // --- Kind ---
                CaptionedSection(
                    caption = "What you're tracking",
                    helpText = "A personal best keeps only your single best " +
                        "result — a 5km time, a bench press. A running total " +
                        "counts events as they happen — books read, countries " +
                        "visited — so each entry adds to the tally.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MilestoneKind.entries.forEach { option ->
                            ChoiceCell(
                                label = option.label,
                                subtitle = option.blurb,
                                selected = kind == option,
                                onClick = { kind = option },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // --- Cadence ---
                CaptionedSection(
                    caption = "Scored over",
                    helpText = "All time is a lifetime record that never " +
                        "resets. Each year gives every calendar year its own " +
                        "scoreboard and its own target, so this year's goals " +
                        "sit apart from your lifetime bests on the grid.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MilestoneCadence.entries.forEach { option ->
                            ChoiceCell(
                                label = option.label,
                                subtitle = option.blurb,
                                selected = cadence == option,
                                onClick = { cadence = option },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // --- Unit ---
                CaptionedSection(
                    caption = "Measured in",
                    helpText = "How values are typed in and displayed. Times " +
                        "are entered as 24:30 or 1:23:45. Changing this later " +
                        "only relabels your existing entries — it doesn't " +
                        "convert them.",
                ) {
                    UnitPicker(
                        kind = unitKind,
                        onKindChange = { unitKind = it },
                        suffix = suffix,
                        onSuffixChange = { suffix = it },
                        decimals = decimals,
                        onDecimalsChange = { decimals = it.coerceIn(0, MilestoneUnit.MAX_DECIMALS) },
                        symbol = symbol,
                        onSymbolChange = { symbol = it },
                    )
                }

                // --- Direction (records only; a tally can only climb) ---
                if (kind.hasDirection) {
                    CaptionedSection(
                        caption = "Beating it means",
                        helpText = "Distance and revenue go up; a 5km time or " +
                            "a weight to cut goes down. This decides which " +
                            "values count as a new record.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceCell(
                                label = "Higher",
                                subtitle = "e.g. distance",
                                selected = direction == MilestoneDirection.HigherIsBetter,
                                onClick = { direction = MilestoneDirection.HigherIsBetter },
                                modifier = Modifier.weight(1f),
                            )
                            ChoiceCell(
                                label = "Lower",
                                subtitle = "e.g. a time",
                                selected = direction == MilestoneDirection.LowerIsBetter,
                                onClick = { direction = MilestoneDirection.LowerIsBetter },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // --- Target ---
                CaptionedSection(
                    caption = "Target",
                    helpText = "Optional. Setting one turns the card into a " +
                        "progress bar, and reaching it offers you the next " +
                        "target on the same milestone rather than ending there.",
                ) {
                    OutlinedTextField(
                        value = rawTarget,
                        onValueChange = { rawTarget = it },
                        label = { Text("Target (optional)") },
                        placeholder = { Text(unit.fieldPlaceholder) },
                        singleLine = true,
                        isError = rawTarget.isNotBlank() && target == null,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardTypeFor(unit)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // --- Starting value (create only) ---
                // Editing an existing milestone's entries happens in the
                // overview dialog, so this only makes sense at creation time.
                if (!isEdit) {
                    CaptionedSection(
                        caption = if (kind == MilestoneKind.Tally) "Starting count" else "Current best",
                        helpText = "Optional. If you're already partway there, " +
                            "enter where you stand and it becomes your first " +
                            "entry — no need to create then log.",
                    ) {
                        OutlinedTextField(
                            value = rawStartingValue,
                            onValueChange = { rawStartingValue = it },
                            label = {
                                Text(
                                    if (kind == MilestoneKind.Tally) "Starting count (optional)"
                                    else "Current best (optional)",
                                )
                            },
                            placeholder = { Text(unit.fieldPlaceholder) },
                            singleLine = true,
                            isError = rawStartingValue.isNotBlank() && startingValue == null,
                            keyboardOptions = KeyboardOptions(keyboardType = keyboardTypeFor(unit)),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = canSave) {
                Text(if (isEdit) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showIconPicker) {
        IconAndColorPickerDialog(
            selectedIconKey = iconKey,
            selectedColorKey = colorKey,
            onIconSelected = { iconKey = it },
            onColorSelected = { colorKey = it },
            onDismiss = { showIconPicker = false },
        )
    }
}

/**
 * Three equally-weighted cells for the unit families, with the options that
 * belong to the selected one appearing directly underneath. Same shape as the
 * other pickers so the cards read as a set.
 */
@Composable
private fun UnitPicker(
    kind: UnitKind,
    onKindChange: (UnitKind) -> Unit,
    suffix: String,
    onSuffixChange: (String) -> Unit,
    decimals: Int,
    onDecimalsChange: (Int) -> Unit,
    symbol: String,
    onSymbolChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceCell(
                label = "Number",
                selected = kind == UnitKind.Number,
                onClick = { onKindChange(UnitKind.Number) },
                modifier = Modifier.weight(1f),
            )
            ChoiceCell(
                label = "Time",
                selected = kind == UnitKind.Time,
                onClick = { onKindChange(UnitKind.Time) },
                modifier = Modifier.weight(1f),
            )
            ChoiceCell(
                label = "Money",
                selected = kind == UnitKind.Money,
                onClick = { onKindChange(UnitKind.Money) },
                modifier = Modifier.weight(1f),
            )
        }
        when (kind) {
            UnitKind.Number -> {
                OutlinedTextField(
                    value = suffix,
                    onValueChange = onSuffixChange,
                    label = { Text("Unit label (optional)") },
                    placeholder = { Text("e.g. books, km, reps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactStepper(
                        value = decimals,
                        onChange = onDecimalsChange,
                        min = 0,
                        max = MilestoneUnit.MAX_DECIMALS,
                        label = { v ->
                            if (v == 0) "Whole numbers"
                            else "$v decimal place${if (v == 1) "" else "s"}"
                        },
                    )
                }
            }
            UnitKind.Time -> Text(
                text = "Entered and shown as 24:30 or 1:23:45.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UnitKind.Money -> OutlinedTextField(
                value = symbol,
                onValueChange = { if (it.length <= 3) onSymbolChange(it) },
                label = { Text("Currency symbol") },
                placeholder = { Text("£") },
                singleLine = true,
                modifier = Modifier.width(160.dp),
            )
        }
    }
}
