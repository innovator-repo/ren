package app.ren.navigation

import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.ren.designsystem.TabBar
import app.ren.designsystem.TabBarHeight
import app.ren.designsystem.TabItem
import app.ren.designsystem.glassSource
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.feature.detail.ItemDetailRoute
import app.ren.feature.matrix.MatrixRoute
import app.ren.feature.onboarding.OnboardingRoute as OnboardingScreenRoute
import app.ren.feature.path.PathRoute
import app.ren.feature.path.StageDetailRoute
import app.ren.feature.reader.ReaderRoute
import app.ren.feature.reader.StoryRoute as StoryScreenRoute
import app.ren.feature.session.SessionRoute as SessionScreenRoute
import app.ren.feature.settings.SettingsRoute as SettingsScreenRoute
import app.ren.feature.today.TodayRoute
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.Flow

/** iOS-like navigation curve: quick start, long soft settle. */
private val IosEase = CubicBezierEasing(0.2f, 0.9f, 0.22f, 1f)
private const val PUSH_MS = 420

private fun isModal(entry: NavBackStackEntry) =
    entry.destination.route?.let { r -> MODAL.any { r.startsWith(it) } } == true

private val MODAL = listOf(
    SessionRoute::class.qualifiedName!!, LessonRoute::class.qualifiedName!!, SettingsRoute::class.qualifiedName!!,
)

@Composable
fun RenApp(onboarded: Boolean, deepLinks: Flow<Boolean>) {
    val nav = rememberNavController()
    var tab by rememberSaveable { mutableIntStateOf(Tabs.TODAY) }
    val navigator = remember(nav) { NavNavigator(nav) { tab = it } }
    // Fixed at launch: finishing onboarding navigates on its own, so the graph is never rebuilt.
    val start: Any = remember { if (onboarded) MainRoute() else OnboardingRoute }

    LaunchedEffect(deepLinks) {
        deepLinks.collect { quick -> navigator.session(quick) }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.fillMaxSize().background(Ren.colors.canvas),
            enterTransition = {
                if (isModal(targetState)) slideInVertically(tween(PUSH_MS, easing = IosEase)) { it }
                else slideInHorizontally(tween(PUSH_MS, easing = IosEase)) { it }
            },
            exitTransition = {
                if (isModal(targetState)) fadeOut(tween(PUSH_MS), targetAlpha = 0.9f)
                else slideOutHorizontally(tween(PUSH_MS, easing = IosEase)) { -it / 3 } + fadeOut(tween(PUSH_MS), targetAlpha = 0.85f)
            },
            popEnterTransition = {
                if (isModal(initialState)) fadeIn(tween(PUSH_MS), initialAlpha = 0.9f)
                else slideInHorizontally(tween(PUSH_MS, easing = IosEase)) { -it / 3 } + fadeIn(tween(PUSH_MS), initialAlpha = 0.85f)
            },
            popExitTransition = {
                if (isModal(initialState)) slideOutVertically(tween(PUSH_MS, easing = IosEase)) { it }
                else slideOutHorizontally(tween(PUSH_MS, easing = IosEase)) { it }
            },
        ) {
            composable<OnboardingRoute>(enterTransition = { EnterTransition.None }, exitTransition = { fadeOut(tween(300)) }) {
                OnboardingScreenRoute(onDone = {
                    nav.navigate(MainRoute()) { popUpTo<OnboardingRoute> { inclusive = true } }
                })
            }
            composable<MainRoute>(enterTransition = { fadeIn(tween(300)) }, popExitTransition = { ExitTransition.None }) {
                MainScreen(tab) { tab = it }
            }
            composable<SessionRoute> { SessionScreenRoute() }
            composable<LessonRoute> { SessionScreenRoute() }
            composable<StoryRoute> { StoryScreenRoute() }
            composable<ItemRoute> { ItemDetailRoute() }
            composable<StageRoute> { StageDetailRoute() }
            composable<SettingsRoute> { SettingsScreenRoute() }
        }
    }
}

private class NavNavigator(private val nav: NavHostController, private val setTab: (Int) -> Unit) : Navigator {
    override fun session(quick: Boolean) = nav.navigate(SessionRoute(quick)) { launchSingleTop = true }
    override fun lesson(unitId: String) = nav.navigate(LessonRoute(unitId)) { launchSingleTop = true }
    override fun story(storyId: String) = nav.navigate(StoryRoute(storyId))
    override fun item(itemId: String) = nav.navigate(ItemRoute(itemId))
    override fun stage(index: Int) = nav.navigate(StageRoute(index))
    override fun settings() = nav.navigate(SettingsRoute) { launchSingleTop = true }
    override fun tab(index: Int) {
        setTab(index)
        nav.popBackStack<MainRoute>(inclusive = false)
    }
    override fun back() {
        nav.popBackStack()
    }
}

/** The four tabs (spec §5) over one frosted tab bar. Each tab keeps its own scroll position. */
@Composable
fun MainScreen(tab: Int, onTab: (Int) -> Unit) {
    val haze = remember { HazeState() }
    val holder = rememberSaveableStateHolder()
    val bottomInset = TabBarHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val items = listOf(
        TabItem("Today", RenIcons.Today),
        TabItem("Path", RenIcons.Path),
        TabItem("Matrix", RenIcons.Matrix),
        TabItem("Reader", RenIcons.Reader),
    )
    Box(Modifier.fillMaxSize().background(Ren.colors.canvas)) {
        Box(Modifier.fillMaxSize().glassSource(haze)) {
            // A quick crossfade, so switching tabs never flashes a blank frame.
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(160, delayMillis = 40)) togetherWith fadeOut(tween(90)) },
                label = "tab",
            ) { shown ->
                holder.SaveableStateProvider(shown) {
                    when (shown) {
                        Tabs.TODAY -> TodayRoute(bottomInset)
                        Tabs.PATH -> PathRoute(bottomInset)
                        Tabs.MATRIX -> MatrixRoute(bottomInset)
                        else -> ReaderRoute(bottomInset)
                    }
                }
            }
        }
        TabBar(items, tab, haze, Modifier.align(Alignment.BottomCenter), onSelect = onTab)
    }
}
