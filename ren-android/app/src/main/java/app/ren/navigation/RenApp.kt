package app.ren.navigation

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import app.ren.designsystem.LocalBack
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    val navigator = remember(nav) { NavNavigator(nav) }
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
                TodayRoute()
            }
            composable<PathPageRoute> { Pushed(nav) { PathRoute(it) } }
            composable<ReaderPageRoute> { Pushed(nav) { ReaderRoute(it) } }
            composable<ProgressPageRoute> { Pushed(nav) { MatrixRoute(it) } }
            composable<SessionRoute> { SessionScreenRoute() }
            composable<LessonRoute> { SessionScreenRoute() }
            composable<StoryRoute> { StoryScreenRoute() }
            composable<ItemRoute> { ItemDetailRoute() }
            composable<StageRoute> { StageDetailRoute() }
            composable<SettingsRoute> { SettingsScreenRoute() }
        }
    }
}

/** A shortcut screen reached from the main screen: its title row gets a "‹ Ren" back button. */
@Composable
private fun Pushed(nav: NavHostController, content: @Composable (Dp) -> Unit) {
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalBack provides { nav.popBackStack() }) { content(inset) }
}

private class NavNavigator(private val nav: NavHostController) : Navigator {
    override fun session(quick: Boolean) = nav.navigate(SessionRoute(quick)) { launchSingleTop = true }
    override fun lesson(unitId: String) = nav.navigate(LessonRoute(unitId)) { launchSingleTop = true }
    override fun story(storyId: String) = nav.navigate(StoryRoute(storyId))
    override fun item(itemId: String) = nav.navigate(ItemRoute(itemId))
    override fun stage(index: Int) = nav.navigate(StageRoute(index))
    override fun settings() = nav.navigate(SettingsRoute) { launchSingleTop = true }
    override fun path() = nav.navigate(PathPageRoute) { launchSingleTop = true }
    override fun reader() = nav.navigate(ReaderPageRoute) { launchSingleTop = true }
    override fun progress() = nav.navigate(ProgressPageRoute) { launchSingleTop = true }
    override fun back() {
        nav.popBackStack()
    }
}
