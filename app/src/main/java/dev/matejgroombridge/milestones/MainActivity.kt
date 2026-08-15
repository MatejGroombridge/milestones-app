package dev.matejgroombridge.milestones

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.matejgroombridge.milestones.ui.HomeViewModel
import dev.matejgroombridge.milestones.ui.SettingsViewModel
import dev.matejgroombridge.milestones.ui.screens.ArchivedMilestonesScreen
import dev.matejgroombridge.milestones.ui.screens.HistoryScreen
import dev.matejgroombridge.milestones.ui.screens.HomeScreen
import dev.matejgroombridge.milestones.ui.screens.ProgressScreen
import dev.matejgroombridge.milestones.ui.screens.ReorderMilestonesScreen
import dev.matejgroombridge.milestones.ui.screens.SettingsScreen
import dev.matejgroombridge.milestones.ui.theme.AppTheme
import dev.matejgroombridge.milestones.ui.util.rememberHaptics
import kotlinx.coroutines.launch

private object Routes {
    /** Single host route for the swipeable History / Milestones / Progress pager. */
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val ARCHIVE = "archive"
    const val REORDER = "reorder"
}

private data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

// Order is intentional: pager index 0 → History, 1 → Milestones, 2 → Progress.
// Milestones sits in the middle so the user can swipe to it from either side;
// it's also the page the app launches on (see [MILESTONES_PAGE_INDEX] /
// initialPage). Adjust both this list AND the `when (page)` switch in
// MainPager() to add a tab.
private const val MILESTONES_PAGE_INDEX = 1
private val BOTTOM_TABS = listOf(
    BottomTab("History", Icons.Outlined.History),
    BottomTab("Milestones", Icons.Outlined.EmojiEvents),
    BottomTab("Progress", Icons.Outlined.Insights),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(application),
            )
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            AppTheme(
                themeMode = settings.themeMode,
                amoled = settings.amoled,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppShell(settingsViewModel = settingsViewModel)
                }
            }
        }
    }
}

@Composable
private fun rememberApplication(): Application {
    val ctx = LocalContext.current.applicationContext
    return ctx as Application
}

@Composable
private fun AppShell(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val app = rememberApplication()

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(app),
    )

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.MAIN) {
            MainPager(
                homeViewModel = homeViewModel,
                settingsViewModel = settingsViewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                homeViewModel = homeViewModel,
                onBack = { navController.popBackStack() },
                onOpenReorder = { navController.navigate(Routes.REORDER) },
                onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
            )
        }
        composable(Routes.ARCHIVE) {
            ArchivedMilestonesScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.REORDER) {
            ReorderMilestonesScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Hosts the three top-level screens (History, Milestones, Progress) inside a
 * [HorizontalPager], so the user can swipe between them. The bottom
 * NavigationBar mirrors the pager's selected index — tapping a tab animates
 * the pager, swiping the pager updates the highlighted tab.
 *
 * The FAB only appears on the Milestones page; we hide it on the others to
 * avoid a misleading affordance ("Add milestone" doesn't belong on Progress).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainPager(
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenSettings: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = MILESTONES_PAGE_INDEX,
        pageCount = { BOTTOM_TABS.size },
    )
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    // The snackbar lives here rather than inside HomeScreen so that this
    // Scaffold owns both it and the FAB, and can lift the FAB clear when one
    // shows. Sibling Scaffolds can't do that, and the FAB ended up sitting on
    // top of the Undo action.
    val snackbar = remember { SnackbarHostState() }

    // Light buzz whenever the pager actually settles on a new page (whether
    // initiated by a swipe or a tab tap). We snapshot the previous page so the
    // initial composition (page == initialPage) doesn't fire a buzz.
    var lastPage by remember { mutableStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastPage) {
            haptics.light()
            lastPage = pagerState.currentPage
        }
    }

    // Driving the FAB from the shell so it sits above the bottom bar correctly.
    var requestCreate by remember { mutableStateOf(false) }

    // When zen mode is enabled the user is locked to the Milestones page —
    // snap there so the bottom-nav-less view doesn't strand them on History
    // or Progress after re-entry.
    LaunchedEffect(settings.zenMode) {
        if (settings.zenMode && pagerState.currentPage != MILESTONES_PAGE_INDEX) {
            pagerState.scrollToPage(MILESTONES_PAGE_INDEX)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // Zen mode hides the bottom navigation completely — there's
            // nothing to navigate to, only Milestones exists.
            if (settings.zenMode) return@Scaffold
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                BOTTOM_TABS.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                // The page-change LaunchedEffect above will
                                // emit the haptic once the pager settles — no
                                // need to duplicate here.
                                scope.launch { pagerState.animateScrollToPage(index) }
                            } else {
                                // Tapping the already-selected tab still gives
                                // a small confirmation tick.
                                haptics.light()
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            // No milestone creation while in Zen mode.
            if (settings.zenMode) return@Scaffold
            if (pagerState.currentPage == MILESTONES_PAGE_INDEX) {
                FloatingActionButton(
                    onClick = {
                        haptics.completion()
                        requestCreate = true
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add milestone")
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Tiny prefetch keeps adjacent pages composed so swiping is
            // instant; setting beyondViewportPageCount to 1 means at most 3
            // pages exist at once which is fine for our screens.
            beyondViewportPageCount = 1,
            // Honour the "Swipe to navigate" general setting, and force it off
            // entirely while Zen mode is on so the user can't swipe away.
            userScrollEnabled = settings.swipeToNavigate && !settings.zenMode,
        ) { page ->
            when (page) {
                0 -> HistoryScreen(
                    viewModel = homeViewModel,
                    contentPadding = padding,
                )
                MILESTONES_PAGE_INDEX -> HomeScreen(
                    viewModel = homeViewModel,
                    settingsViewModel = settingsViewModel,
                    onOpenSettings = onOpenSettings,
                    onOpenArchive = onOpenArchive,
                    snackbar = snackbar,
                    contentPadding = padding,
                    requestCreate = requestCreate,
                    onCreateDialogConsumed = { requestCreate = false },
                )
                2 -> ProgressScreen(
                    viewModel = homeViewModel,
                    contentPadding = padding,
                )
            }
        }
    }

    // Defensive: if a non-Milestones page is somehow showing while a create
    // request is pending (e.g. swipe just after tapping the FAB), drop it so
    // we never auto-open the editor on the wrong page.
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != MILESTONES_PAGE_INDEX && requestCreate) requestCreate = false
    }
}
