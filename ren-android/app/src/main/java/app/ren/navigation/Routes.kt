package app.ren.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data class MainRoute(val tab: Int = 0)
@Serializable data class SessionRoute(val quick: Boolean = false)
@Serializable data class LessonRoute(val unitId: String)
@Serializable data class StoryRoute(val storyId: String)
@Serializable data class ItemRoute(val itemId: String)
@Serializable data class StageRoute(val index: Int)
@Serializable data object SettingsRoute

/** Navigation verbs available to every screen. */
interface Navigator {
    fun session(quick: Boolean = false)
    fun lesson(unitId: String)
    fun story(storyId: String)
    fun item(itemId: String)
    fun stage(index: Int)
    fun settings()
    fun tab(index: Int)
    fun back()
}

object NoNavigator : Navigator {
    override fun session(quick: Boolean) = Unit
    override fun lesson(unitId: String) = Unit
    override fun story(storyId: String) = Unit
    override fun item(itemId: String) = Unit
    override fun stage(index: Int) = Unit
    override fun settings() = Unit
    override fun tab(index: Int) = Unit
    override fun back() = Unit
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { NoNavigator }

object Tabs {
    const val TODAY = 0
    const val PATH = 1
    const val MATRIX = 2
    const val READER = 3
}
