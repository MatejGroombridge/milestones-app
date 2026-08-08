package dev.matejgroombridge.milestones.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.milestones.BuildConfig
import dev.matejgroombridge.milestones.R
import dev.matejgroombridge.milestones.ui.HomeViewModel
import dev.matejgroombridge.milestones.ui.SettingsViewModel
import dev.matejgroombridge.milestones.ui.theme.ThemeMode
import dev.matejgroombridge.milestones.ui.util.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Settings is intentionally kept calm and uncluttered: a couple of grouped
 * tiles on a soft container background, with each section having its own card
 * and labelled with a small caption above. Adding a setting? Drop a new row
 * inside the relevant [SettingsCard], or add a new card entirely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    homeViewModel: HomeViewModel,
    onBack: () -> Unit,
    onOpenReorder: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = homeViewModel.exportJson() ?: return@launch
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) }
            }.onSuccess {
                snackbar.showSnackbar("Milestones exported")
            }.onFailure {
                snackbar.showSnackbar("Export failed")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (raw == null) {
                snackbar.showSnackbar("Couldn't read file")
                return@launch
            }
            val count = homeViewModel.importJson(raw)
            if (count != null) snackbar.showSnackbar("Imported $count milestones")
            else snackbar.showSnackbar("Import failed — invalid JSON")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Zen mode --------------------------------------------------
            // Always rendered, even when Zen mode is on, so the user can turn
            // it back off. When on it's the *only* card visible.
            SectionCaption("Zen Mode")
            SettingsCard(contentPadding = 0.dp) {
                Column {
                    CompactSwitchRow(
                        label = "Zen mode",
                        checked = settings.zenMode,
                        onCheckedChange = {
                            haptics.light()
                            viewModel.setZenMode(it)
                        },
                    )
                    if (settings.zenMode) {
                        Divider()
                        Text(
                            text = "Only the Milestones screen is reachable. Tap a " +
                                "milestone to log a new record. Turn this off to " +
                                "bring back the rest of the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            // Everything below is hidden while Zen mode is on. Wrapping them
            // in a single conditional keeps the spacing rhythm identical when
            // Zen is off — the Column's spacedBy(20.dp) applies between
            // visible sections only.
            if (settings.zenMode) {
                Spacer(Modifier.height(20.dp))
                return@Column
            }

            // Appearance ----------------------------------------------------
            SectionCaption("Appearance")
            SettingsCard(contentPadding = 0.dp) {
                Column {
                    ThemePickerRow(
                        selected = settings.themeMode,
                        onChange = {
                            haptics.light()
                            viewModel.setThemeMode(it)
                        },
                    )
                    Divider()
                    CompactSwitchRow(
                        label = "AMOLED dark mode",
                        checked = settings.amoled,
                        onCheckedChange = {
                            haptics.light()
                            viewModel.setAmoled(it)
                        },
                    )
                }
            }

            // General -------------------------------------------------------
            // Consolidates the behaviour toggles and the simple nav rows into
            // one visual unit, matching what a user expects under "general".
            SectionCaption("General")
            SettingsCard(contentPadding = 0.dp) {
                Column {
                    CompactSwitchRow(
                        label = "Swipe to navigate",
                        checked = settings.swipeToNavigate,
                        onCheckedChange = {
                            haptics.light()
                            viewModel.setSwipeToNavigate(it)
                        },
                    )
                    Divider()
                    CompactSwitchRow(
                        label = "Celebrate new records",
                        checked = settings.celebrateRecords,
                        onCheckedChange = {
                            haptics.light()
                            viewModel.setCelebrateRecords(it)
                        },
                    )
                    Divider()
                    NavRow(
                        icon = Icons.Outlined.Reorder,
                        label = "Reorder Milestones",
                        onClick = {
                            haptics.light()
                            onOpenReorder()
                        },
                    )
                    Divider()
                    NavRow(
                        icon = Icons.Outlined.Archive,
                        label = "Archived Milestones",
                        onClick = {
                            haptics.light()
                            onOpenArchive()
                        },
                    )
                    Divider()
                    NavRow(
                        icon = Icons.Outlined.Upload,
                        label = "Export to JSON",
                        onClick = {
                            haptics.light()
                            exportLauncher.launch("milestones-backup.json")
                        },
                    )
                    Divider()
                    NavRow(
                        icon = Icons.Outlined.Download,
                        label = "Import from JSON",
                        onClick = {
                            haptics.light()
                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                    )
                }
            }

            // About ---------------------------------------------------------
            SectionCaption("About")
            SettingsCard(contentPadding = 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// --- Building blocks -------------------------------------------------------

/**
 * Shared minimum height for every row in a SettingsCard. Picking a single
 * value (rather than letting padding drive the height) means a switch row
 * (whose Switch is ~32dp) and a chevron nav row (whose text is ~24dp) line up
 * exactly.
 */
private val SETTINGS_ROW_MIN_HEIGHT = 56.dp

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
private fun ThemePickerRow(
    selected: ThemeMode,
    onChange: (ThemeMode) -> Unit,
) {
    // Wrapped in identical horizontal/vertical padding to the rest of the rows
    // in zero-padded SettingsCards. Title text sits where a row label would,
    // the chip row underneath fills the rest.
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeButton(
                label = "System", icon = Icons.Outlined.SettingsBrightness,
                selected = selected == ThemeMode.System, modifier = Modifier.weight(1f),
                onClick = { onChange(ThemeMode.System) },
            )
            ThemeButton(
                label = "Light", icon = Icons.Outlined.LightMode,
                selected = selected == ThemeMode.Light, modifier = Modifier.weight(1f),
                onClick = { onChange(ThemeMode.Light) },
            )
            ThemeButton(
                label = "Dark", icon = Icons.Outlined.DarkMode,
                selected = selected == ThemeMode.Dark, modifier = Modifier.weight(1f),
                onClick = { onChange(ThemeMode.Dark) },
            )
        }
    }
}

@Composable
private fun ThemeButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = border,
        ),
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Icon(icon, null, tint = onContainer, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = onContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Compact switch row with no subtitle — used for self-explanatory toggles
 * where the label alone is enough.
 */
@Composable
private fun CompactSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // All rows in a unified card are pinned to the same minimum height so
    // toggles and nav rows line up exactly. Vertical padding is small (4dp)
    // because the Switch + bodyLarge already fill ~48dp; heightIn does the rest.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    // The icon parameter is kept for source-compat with existing call sites
    // (and so the room is there if we re-introduce iconography later) but is
    // intentionally not rendered — the goal is a text-first, uncluttered look
    // that matches the toggle rows.
    @Suppress("UNUSED_PARAMETER") val unused = icon
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 64.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}
