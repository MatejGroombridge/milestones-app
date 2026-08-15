package dev.matejgroombridge.milestones.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.milestones.data.model.MilestoneCadence
import dev.matejgroombridge.milestones.data.model.MilestoneStats
import dev.matejgroombridge.milestones.ui.HomeViewModel
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import dev.matejgroombridge.milestones.ui.theme.MilestoneIcons
import dev.matejgroombridge.milestones.ui.util.rememberHaptics

/**
 * Lets the user reorder their active milestones. Uses up/down icon buttons
 * rather than a drag-and-drop pointer modifier to stay accessible and
 * dependency-free.
 *
 * Reordering happens *within* a cadence group, mirroring how the grid is laid
 * out — moving a yearly goal "up" past the last lifetime record would be
 * meaningless, since they're drawn in separate sections. Committing the two
 * groups back concatenated also normalises the stored list to be contiguous by
 * group instead of interleaved.
 *
 * Order is held locally while rearranging and committed on every change, so
 * closing the screen never leaves a stale order behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderMilestonesScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Mirror the lists locally so reorders are instantly reflected without
    // waiting for the upstream flow to round-trip through DataStore.
    var yearly by remember(state.yearly) { mutableStateOf(state.yearly) }
    var lifetime by remember(state.lifetime) { mutableStateOf(state.lifetime) }
    val haptics = rememberHaptics()

    fun commit(newYearly: List<MilestoneStats>, newLifetime: List<MilestoneStats>) {
        yearly = newYearly
        lifetime = newLifetime
        viewModel.setOrdering((newYearly + newLifetime).map { it.milestone.id })
        haptics.light()
    }

    fun move(cadence: MilestoneCadence, index: Int, delta: Int) {
        val group = if (cadence == MilestoneCadence.Yearly) yearly else lifetime
        val destination = index + delta
        if (destination !in group.indices) return
        val reordered = group.toMutableList().apply {
            add(destination, removeAt(index))
        }
        if (cadence == MilestoneCadence.Yearly) commit(reordered, lifetime)
        else commit(yearly, reordered)
    }

    val showHeaders = yearly.isNotEmpty() && lifetime.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Reorder Milestones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (yearly.isEmpty() && lifetime.isEmpty()) {
            EmptyReorderHint(padding)
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                MilestoneCadence.Yearly to yearly,
                MilestoneCadence.Lifetime to lifetime,
            ).forEach { (cadence, group) ->
                if (group.isEmpty()) return@forEach
                if (showHeaders) {
                    item(key = "header-${cadence.name}") {
                        SectionCaption(cadence.sectionTitle)
                    }
                }
                milestoneRows(group, cadence) { index, stats ->
                    ReorderRow(
                        stats = stats,
                        canMoveUp = index > 0,
                        canMoveDown = index < group.lastIndex,
                        onMoveUp = { move(cadence, index, -1) },
                        onMoveDown = { move(cadence, index, +1) },
                    )
                }
            }
        }
    }

    // If the upstream list changes while we're on this screen (e.g. a
    // milestone created elsewhere), pull in the new authoritative order so
    // the local copies don't drift permanently.
    LaunchedEffect(state.activeMilestones.map { it.id }) {
        yearly = state.yearly
        lifetime = state.lifetime
    }
}

/**
 * Emits one row per milestone, keyed by cadence as well as id so the two
 * groups can't collide. Named distinctly from the stdlib `itemsIndexed` to
 * avoid a confusing overload at the call site.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.milestoneRows(
    group: List<MilestoneStats>,
    cadence: MilestoneCadence,
    content: @Composable (Int, MilestoneStats) -> Unit,
) {
    group.forEachIndexed { index, stats ->
        item(key = "${cadence.name}-${stats.milestone.id}") { content(index, stats) }
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp),
    )
}

@Composable
private fun ReorderRow(
    stats: MilestoneStats,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val milestone = stats.milestone
    val color = MilestoneColors.entry(milestone.colorKey)
    val icon = MilestoneIcons.entry(milestone.iconKey)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon.icon,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = milestone.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Move up",
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Move down",
                )
            }
        }
    }
}

@Composable
private fun EmptyReorderHint(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No milestones yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = "Add a milestone from the Milestones tab — once you have a " +
                "few, come back here to reorder them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
