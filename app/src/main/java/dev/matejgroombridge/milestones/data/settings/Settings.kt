package dev.matejgroombridge.milestones.data.settings

import dev.matejgroombridge.milestones.ui.theme.ThemeMode

/**
 * All user-configurable settings, exposed as a single immutable snapshot.
 * Adding a new setting? Add a property here, a `Preferences.Key` + a
 * mapping in [SettingsRepository], and a row in `SettingsScreen`.
 */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** When [ThemeMode] resolves to dark, render with pure black backgrounds. */
    val amoled: Boolean = false,
    /**
     * When `true` the user can swipe horizontally between History /
     * Milestones / Progress. When `false` the pager only responds to
     * bottom-bar taps, which helps users whose swipe gestures conflict with
     * horizontally-scrollable content like the Progress charts.
     */
    val swipeToNavigate: Boolean = true,
    /**
     * Whether beating a personal best fires the confetti burst. On by
     * default — the celebration is most of the point of the app — but
     * available to turn off for anyone who finds it noisy.
     */
    val celebrateRecords: Boolean = true,
    /**
     * "Zen mode" — collapses the entire app down to a single screen of
     * milestone cards, each of which opens the log-a-record dialog. Hides:
     *   * The bottom navigation (Milestones is the only page).
     *   * The FAB and "Create" entry points.
     *   * The archive icon in the Milestones header.
     *   * Long-press overview, the editor dialog, and any other modals.
     *   * History, Progress, Reorder and Archived screens.
     *
     * The only other thing the user can do while Zen mode is on is open
     * Settings (via the gear icon) and toggle Zen back off again. All other
     * settings rows are hidden.
     */
    val zenMode: Boolean = false,
)
