package app.ren

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.ren.designsystem.CircadianPhase
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ren.core.designsystem.theme.RenTheme
import app.ren.designsystem.LocalHaptics
import app.ren.designsystem.rememberHaptics
import app.ren.domain.model.ThemeMode
import app.ren.domain.model.UserPreferences
import app.ren.domain.repository.PreferencesRepository
import app.ren.navigation.RenApp
import app.ren.work.DeepLinks
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var preferences: PreferencesRepository

    /** ren://session links from the reminder and the widget; true means a quick five-card round. */
    private val deepLinks = Channel<Boolean>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val prefs = preferences.preferences.map<UserPreferences, UserPreferences?> { it }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        splash.setKeepOnScreenCondition { prefs.value == null }
        if (savedInstanceState == null) handle(intent)

        setContent {
            val p by prefs.collectAsStateWithLifecycle()
            val loaded = p ?: return@setContent
            val dark = when (loaded.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            val hapticsOn by rememberUpdatedState(loaded.hapticsEnabled)
            val haptics = rememberHaptics { hapticsOn }
            var phase by remember { mutableStateOf(CircadianPhase.now()) }
            LaunchedEffect(Unit) { while (true) { delay(5 * 60_000L); phase = CircadianPhase.now() } }
            RenTheme(dark = dark, phase = phase) {
                CompositionLocalProvider(LocalHaptics provides haptics) {
                    RenApp(onboarded = loaded.onboarded, deepLinks = deepLinks.receiveAsFlow())
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        DeepLinks.parse(intent)?.let { deepLinks.trySend(it) }
    }
}
