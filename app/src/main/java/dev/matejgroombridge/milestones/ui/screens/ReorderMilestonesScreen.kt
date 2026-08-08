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
import androidx.compose.foundation.lazy.items
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
import dev.matejgroombridge.milestones.data.model.Milestone
import dev.matejgroombridge.milestones.ui.HomeViewModel
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import dev.matejgroombridge.milestones.ui.theme.MilestoneIcons
import dev.matejgroombridge.milestones.ui.util.rememberHaptics

/**
 * Lets the user reorder their active milestones. Uses up/down icon buttons
 * rather than a drag-and-drop pointer modifier to stay accessible and
 * dependency-free.
 *
 * Order is held in local state while the user is rearranging, then committed
 * via [HomeViewModel.setOrdering] on every change so closing the screen never
 * leaves a stale order behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderMilestonesScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Mirror the active list locally so reorders are instantly reflected
    // without waiting for the upstream flow to round-trip through DataStore.
    var localOrder by remember(state.activeMilestones) {
        mutableStateOf(state.activeMilestones)
    }
    val haptics = rememberHaptics()

    fun commit(newOrder: List<Milestone>) {
        localOrder = newOrder
        viewModel.setOrdering(newOrder.map { it.id })
        haptics.light()
    }

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
        if (localOrder.isEmpty()) {
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
            items(localOrder, key = { it.id }) { milestone ->
                val index = localOrder.indexOfFirst { it.id == milestone.id }
                ReorderRow(
                    milestone = milestone,
                    canMoveUp = index > 0,
                    canMoveDown = index < localOrder.lastIndex,
                    onMoveUp = {
                        if (index > 0) {
                            val mutable = localOrder.toMutableList()
                            val item = mutable.removeAt(index)
                            mutable.add(index - 1, item)
                            commit(mutable)
                        }
                    },
                    onMoveDown = {
                        if (index < localOrder.lastIndex) {
                            val mutable = localOrder.toMutableList()
                            val item = mutable.removeAt(index)
                            mutable.add(index + 1, item)
                            commit(mutable)
                        }
                    },
                )
            }
        }
    }

    // If the upstream list adds/removes milestones while we're on this screen
    // (e.g. one created elsewhere), pull in the new authoritative order so
    // localOrder doesn't drift permanently.
    LaunchedEffect(state.activeMilestones.map { it.id }) {
        localOrder = state.activeMilestones
    }
}

@Composable
private fun ReorderRow(
    milestone: Milestone,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
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
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Move up",
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
            ) {
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
