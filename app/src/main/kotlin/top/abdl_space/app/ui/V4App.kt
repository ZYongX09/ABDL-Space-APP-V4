package top.abdl_space.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import top.abdl_space.app.core.designsystem.component.V4PillNavItem
import top.abdl_space.app.core.designsystem.component.V4PillNavigationBar
import top.abdl_space.app.core.designsystem.glass.V4GlassBackdropProvider
import top.abdl_space.app.core.designsystem.icon.V4Icons
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.abdl_space.app.core.designsystem.theme.V4Text
import top.abdl_space.app.core.designsystem.theme.V4Theme

private const val EditorRoute = "local-editor"
private const val SettingsRoute = "me/settings"
private const val NavigationFadeMillis = 120

/**
 * V4 UI entry point. No networking, fake account, token storage or production publishing lives here.
 *
 * Null appearance inputs use rememberSaveable for this UI session. Non-null values are controlled
 * by the caller; change callbacks are the seam for DataStore. The caller owns real authentication
 * and feeds a session projection back in. A missing sign-in callback leads to an honest gate page.
 */
@Composable
fun V4App(
    modifier: Modifier = Modifier,
    themePreference: V4ThemePreference? = null,
    onThemePreferenceChange: (V4ThemePreference) -> Unit = {},
    session: V4SessionUiState = V4SessionUiState.Guest,
    onSignInRequested: (() -> Unit)? = null,
    onSignOutRequested: (() -> Unit)? = null,
    reduceTransparency: Boolean? = null,
    onReduceTransparencyChange: (Boolean) -> Unit = {},
    reduceMotion: Boolean? = null,
    onReduceMotionChange: (Boolean) -> Unit = {},
    timelineState: Map<V4TimelineKind, V4TimelineUiState> = emptyMap(),
    onTimelineSelected: (V4TimelineKind) -> Unit = {},
    onTimelineRefresh: (V4TimelineKind) -> Unit = {},
    onLoadMore: (V4TimelineKind) -> Unit = {},
) {
    var localThemeName by rememberSaveable { mutableStateOf(V4ThemePreference.System.name) }
    var localReduceTransparency by rememberSaveable { mutableStateOf(true) }
    var localReduceMotion by rememberSaveable { mutableStateOf(false) }
    val theme = themePreference ?: V4ThemePreference.valueOf(localThemeName)
    val opaque = reduceTransparency ?: localReduceTransparency
    val motionReduced = reduceMotion ?: localReduceMotion
    val dark = when (theme) {
        V4ThemePreference.System -> isSystemInDarkTheme()
        V4ThemePreference.Light -> false
        V4ThemePreference.Dark -> true
    }

    V4Theme(darkTheme = dark) {
        V4AppContent(
            modifier = modifier,
            session = session,
            onSignInRequested = onSignInRequested,
            onSignOutRequested = onSignOutRequested,
            theme = theme,
            onTheme = { localThemeName = it.name; onThemePreferenceChange(it) },
            reduceTransparency = opaque,
            onReduceTransparency = { localReduceTransparency = it; onReduceTransparencyChange(it) },
            reduceMotion = motionReduced,
            onReduceMotion = { localReduceMotion = it; onReduceMotionChange(it) },
            hostOwnsAppearance = themePreference != null || reduceTransparency != null || reduceMotion != null,
            timelineState = timelineState,
            onTimelineSelected = onTimelineSelected,
            onTimelineRefresh = onTimelineRefresh,
            onLoadMore = onLoadMore,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V4AppContent(
    modifier: Modifier,
    session: V4SessionUiState,
    onSignInRequested: (() -> Unit)?,
    onSignOutRequested: (() -> Unit)?,
    theme: V4ThemePreference,
    onTheme: (V4ThemePreference) -> Unit,
    reduceTransparency: Boolean,
    onReduceTransparency: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
    hostOwnsAppearance: Boolean,
    timelineState: Map<V4TimelineKind, V4TimelineUiState>,
    onTimelineSelected: (V4TimelineKind) -> Unit,
    onTimelineRefresh: (V4TimelineKind) -> Unit,
    onLoadMore: (V4TimelineKind) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val activeTab = V4TopLevelDestination.entries.firstOrNull { tab ->
        destination?.hierarchy?.any { it.route == tab.route } == true
    }
    val selectedTab = activeTab ?: V4TopLevelDestination.Home
    val isEditor = destination?.route == EditorRoute
    var draft by rememberSaveable { mutableStateOf("") }
    var showPublishActions by rememberSaveable { mutableStateOf(false) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val initialBarHeight = V4Dimens.navBarHeight + V4UiDimens.navBottomPadding +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = with(density) { bottomBarHeightPx.toDp() }.coerceAtLeast(initialBarHeight) + V4Dimens.cardGap
    val navItems = listOf(
        V4PillNavItem(ImageVector.vectorResource(V4Icons.Home), "首页"),
        V4PillNavItem(ImageVector.vectorResource(V4Icons.Discover), "发现"),
        null,
        V4PillNavItem(ImageVector.vectorResource(V4Icons.Message), "消息"),
        V4PillNavItem(ImageVector.vectorResource(V4Icons.Me), "我的"),
    )
    val onBack: () -> Unit = { navController.popBackStack() }
    fun openFeature(tab: V4TopLevelDestination, feature: V4Feature) {
        navController.navigate(tab.featureRoute(feature)) { launchSingleTop = true }
    }
    fun signIn(tab: V4TopLevelDestination) {
        if (onSignInRequested != null) onSignInRequested()
        else navController.navigate(tab.accountRoute) { launchSingleTop = true }
    }
    fun openEditor() {
        navController.navigate(EditorRoute) { launchSingleTop = true }
    }

    V4GlassBackdropProvider(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        reduceTransparency = reduceTransparency,
        content = {
            NavHost(
                navController = navController,
                startDestination = V4TopLevelDestination.Home.route,
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(NavigationFadeMillis)) },
                exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(NavigationFadeMillis)) },
                popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(NavigationFadeMillis)) },
                popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(NavigationFadeMillis)) },
            ) {
                V4TopLevelDestination.entries.forEach { tab ->
                    navigation(startDestination = tab.startRoute, route = tab.route) {
                        composable(tab.startRoute) {
                            when (tab) {
                                V4TopLevelDestination.Home -> V4HomeScreen(
                                    session = session, bottomPadding = bottomPadding, reduceMotion = reduceMotion,
                                    onSearch = { navController.navigate(tab.searchRoute) { launchSingleTop = true } },
                                    onFeature = { openFeature(tab, it) }, onSignIn = { signIn(tab) },
                                    timelineState = timelineState, onTimelineSelected = onTimelineSelected,
                                    onTimelineRefresh = onTimelineRefresh, onLoadMore = onLoadMore,
                                )
                                V4TopLevelDestination.Discover -> V4DiscoverScreen(
                                    bottomPadding = bottomPadding,
                                    onSearch = { navController.navigate(tab.searchRoute) { launchSingleTop = true } },
                                    onFeature = { openFeature(tab, it) },
                                )
                                V4TopLevelDestination.Messages -> V4MessagesScreen(
                                    session = session, bottomPadding = bottomPadding, onSignIn = { signIn(tab) },
                                )
                                V4TopLevelDestination.Me -> V4MeScreen(
                                    session = session, bottomPadding = bottomPadding, hasDraft = draft.isNotBlank(),
                                    onAccount = { navController.navigate(tab.accountRoute) { launchSingleTop = true } },
                                    onSignIn = { signIn(tab) }, onSignOut = onSignOutRequested,
                                    onSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                                    onFeature = { openFeature(tab, it) }, onDraft = ::openEditor,
                                )
                            }
                        }
                        composable(tab.searchRoute) {
                            V4SearchScreen(bottomPadding, onBack, onFeature = { openFeature(tab, it) })
                        }
                        composable(
                            tab.featurePattern,
                            arguments = listOf(navArgument("feature") { type = NavType.StringType }),
                        ) { entry ->
                            V4FeatureScreen(
                                feature = V4FeatureCatalog.fromId(entry.arguments?.getString("feature")),
                                session = session, bottomPadding = bottomPadding, onBack = onBack,
                                onSignIn = { signIn(tab) },
                            )
                        }
                        composable(tab.accountRoute) {
                            V4AccountScreen(session, bottomPadding, onBack, onSignInRequested, onSignOutRequested)
                        }
                        if (tab == V4TopLevelDestination.Me) {
                            composable(SettingsRoute) {
                                V4SettingsScreen(
                                    theme = theme, onTheme = onTheme,
                                    reduceMotion = reduceMotion, onReduceMotion = onReduceMotion,
                                    reduceTransparency = reduceTransparency, onReduceTransparency = onReduceTransparency,
                                    hostOwnsAppearance = hostOwnsAppearance, bottomPadding = bottomPadding,
                                    onBack = onBack,
                                    onAccount = { navController.navigate(tab.accountRoute) { launchSingleTop = true } },
                                    onFeature = { openFeature(tab, it) },
                                )
                            }
                        }
                    }
                }
                composable(EditorRoute) {
                    V4EditorScreen(savedDraft = draft, onSaveDraft = { draft = it }, onExit = onBack)
                }
            }
        },
        overlay = { backdrop ->
            if (!isEditor) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .onSizeChanged { bottomBarHeightPx = it.height }
                        .testTag(V4TestTags.BottomNavigation),
                ) {
                    V4PillNavigationBar(
                        items = navItems,
                        selectedIndex = selectedTab.pillIndex,
                        onSelect = { index -> V4TopLevelDestination.fromPillIndex(index)?.let(navController::selectTab) },
                        onPublishClick = ::openEditor,
                        onPublishLongClick = { showPublishActions = true },
                        backdrop = backdrop,
                    )
                }
            }
        },
    )

    // Registered after NavHost so this wins only at a tab root; nested pages/editor pop normally.
    BackHandler(enabled = activeTab != null && activeTab != V4TopLevelDestination.Home && destination?.route == activeTab.startRoute) {
        navController.selectTab(V4TopLevelDestination.Home)
    }

    if (showPublishActions) {
        ModalBottomSheet(onDismissRequest = { showPublishActions = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                V4SectionTitle("创作与工具", Modifier.padding(V4Dimens.cardPadding))
                Text("仅文字草稿可在本地编辑；其他入口仍在开发中。", style = V4Text.bodySecondary,
                    modifier = Modifier.padding(horizontal = V4Dimens.cardPadding, vertical = V4Dimens.cardGap))
                V4ActionRow("发帖", "打开本地文字草稿，不会联网发布", V4Glyph.Edit) {
                    showPublishActions = false
                    openEditor()
                }
                listOf(V4Feature.FriendRequest, V4Feature.FriendCard, V4Feature.Album, V4Feature.Novel, V4Feature.Scan).forEach { feature ->
                    V4ActionRow(feature.title, "开发中 · 查看尚未接入的范围", feature.glyph()) {
                        showPublishActions = false
                        openFeature(selectedTab, feature)
                    }
                }
            }
        }
    }
}

/** Navigation Compose's multiple-back-stack path, not manual widget replacement or route flags. */
private fun NavHostController.selectTab(tab: V4TopLevelDestination) {
    if (currentDestination?.hierarchy?.any { it.route == tab.route } == true) return
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
