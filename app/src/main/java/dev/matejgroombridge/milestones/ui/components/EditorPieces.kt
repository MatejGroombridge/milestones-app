package dev.matejgroombridge.milestones.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import dev.matejgroombridge.milestones.ui.theme.MilestoneIcons

/**
 * The shared building blocks for the app's dialogs — the rounded section
 * card, its uppercase caption, the icon/colour picker, and the compact
 * stepper.
 *
 * They live in one file (rather than duplicated privately inside each dialog)
 * because the editor, the log-a-record sheet and any future dialog all need
 * the same rhythm: 12dp between sections, 4dp between a caption and its card,
 * `surfaceContainer` fills, 20dp corners.
 */

/**
 * Rounded container that groups related controls — the dialog equivalent of
 * `SettingsCard`. Keeps padding consistent and makes new sections cheap.
 */
@Composable
fun EditorSection(
    padding: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(padding)) { content() }
    }
}

/**
 * Uppercase caption + optional help icon, then the contained card. Tight 4dp
 * spacing so the pair reads as a unit; the parent Column owns the spacing
 * between sections.
 */
@Composable
fun CaptionedSection(
    caption: String,
    helpText: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp),
        ) {
            Text(
                text = caption.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (helpText != null) {
                Spacer(Modifier.width(4.dp))
                HelpIcon(helpText = helpText)
            }
        }
        EditorSection(padding = 12.dp) { content() }
    }
}

/**
 * Compact help "?" icon. Tapping opens a small popover containing [helpText].
 * Sized to sit flush with caption text without inflating the row height —
 * an `IconButton`'s 48dp minimum would add a visible gap.
 */
@Composable
fun HelpIcon(helpText: String) {
    var showHelp by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .clickable { showHelp = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = "What's this?",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        if (showHelp) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { showHelp = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .widthIn(max = 280.dp),
                ) {
                    Text(
                        text = helpText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * One cell in a grid of mutually-exclusive options. Tinted with the theme
 * primary when selected, plain surface when not. Sized by the caller via
 * `Modifier.weight(1f)` so every cell in a row is visually identical
 * regardless of label length.
 */
@Composable
fun ChoiceCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = fg,
                maxLines = 1,
            )
            if (subtitle != null) {
                Spacer(Modifier.size(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Pill-shaped −/+ stepper with a formatted label in the middle. */
@Composable
fun CompactStepper(
    value: Int,
    onChange: (Int) -> Unit,
    label: (Int) -> String,
    min: Int = 0,
    max: Int = 30,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            icon = Icons.Outlined.Remove,
            description = "Decrease",
            enabled = value > min,
            onClick = { onChange(value - 1) },
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label(value),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Spacer(Modifier.width(4.dp))
        StepperButton(
            icon = Icons.Outlined.Add,
            description = "Increase",
            enabled = value < max,
            onClick = { onChange(value + 1) },
        )
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Tappable badge showing the current icon on the current accent colour. */
@Composable
fun IconBadge(iconKey: String, colorKey: String, onClick: () -> Unit) {
    val color = MilestoneColors.entry(colorKey)
    val icon = MilestoneIcons.entry(iconKey)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon.icon,
            contentDescription = "Change icon",
            tint = Color.Black.copy(alpha = 0.85f),
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Combined icon + colour picker, opened from [IconBadge]. */
@Composable
fun IconAndColorPickerDialog(
    selectedIconKey: String,
    selectedColorKey: String,
    onIconSelected: (String) -> Unit,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an Icon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ColorRow(selectedKey = selectedColorKey, onSelected = onColorSelected)
                IconGrid(
                    selectedKey = selectedIconKey,
                    accent = MilestoneColors.entry(selectedColorKey).accent,
                    onSelected = onIconSelected,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun ColorRow(selectedKey: String, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MilestoneColors.palette.forEach { entry ->
            val selected = entry.key == selectedKey
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(entry.accent)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else Color.Black.copy(alpha = 0.10f),
                        shape = CircleShape,
                    )
                    .clickable { onSelected(entry.key) },
            )
        }
    }
}

@Composable
private fun IconGrid(selectedKey: String, accent: Color, onSelected: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            count = MilestoneIcons.catalog.size,
            key = { MilestoneIcons.catalog[it].key },
        ) { index ->
            val entry = MilestoneIcons.catalog[index]
            val selected = entry.key == selectedKey
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) accent
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { onSelected(entry.key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.label,
                    tint = if (selected) Color.Black.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
