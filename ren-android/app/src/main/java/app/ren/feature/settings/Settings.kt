package app.ren.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ren.BuildConfig
import app.ren.core.audio.AudioEngineManager
import app.ren.core.audio.VoiceStatus
import app.ren.core.designsystem.components.Chevron
import app.ren.core.designsystem.components.CupertinoSwitch
import app.ren.core.designsystem.components.SmallButton
import app.ren.core.designsystem.components.GroupedList
import app.ren.core.designsystem.components.GroupedListBuilder
import app.ren.core.designsystem.components.NavBar
import app.ren.core.designsystem.components.NavBarHeight
import app.ren.core.designsystem.components.SectionFooter
import app.ren.core.designsystem.components.SectionHeader
import app.ren.core.designsystem.components.SegmentedControl
import app.ren.core.designsystem.components.Stepper
import app.ren.core.designsystem.components.TextAction
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.components.glassSource
import app.ren.core.designsystem.theme.Ren
import app.ren.core.mvi.MviViewModel
import app.ren.domain.model.Curriculum
import app.ren.domain.model.FuriganaMode
import app.ren.domain.model.ThemeMode
import app.ren.domain.model.UserPreferences
import app.ren.domain.repository.PreferencesRepository
import app.ren.domain.usecase.Clock
import app.ren.domain.usecase.epochDay
import app.ren.navigation.LocalNavigator
import app.ren.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val pathDay: Long = 1,
    val voice: VoiceStatus = VoiceStatus.Ready,
)

const val PRIVACY_URL = "https://github.com/Bharat100-cpu/android-apps/blob/clean/ren/ren-android/PRIVACY.md"

sealed interface SettingsIntent {
    data class Update(val transform: (UserPreferences) -> UserPreferences) : SettingsIntent
    data object CompleteOnboarding : SettingsIntent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
    private val audio: AudioEngineManager,
) : MviViewModel<SettingsUiState, SettingsIntent, Unit>(SettingsUiState()) {

    init {
        viewModelScope.launch { audio.voice.collect { v -> setState { copy(voice = v) } } }
        viewModelScope.launch {
            prefs.preferences.collect { p ->
                val day = if (p.startEpochDay <= 0) 1 else clock.epochDay() - p.startEpochDay + 1
                setState { copy(prefs = p, pathDay = day) }
            }
        }
    }

    /** Re-checks the Japanese voice, e.g. on returning from the system's voice-data installer. */
    fun refreshVoice() = audio.checkVoice()

    override fun onIntent(intent: SettingsIntent) {
        viewModelScope.launch {
            when (intent) {
                is SettingsIntent.Update -> {
                    val before = current.prefs
                    prefs.update(intent.transform)
                    val after = prefs.current()
                    if (before.reminderEnabled != after.reminderEnabled || before.reminderMinuteOfDay != after.reminderMinuteOfDay) {
                        ReminderScheduler.schedule(context, after.reminderEnabled, after.reminderMinuteOfDay)
                    }
                }
                SettingsIntent.CompleteOnboarding -> {
                    prefs.update { it.copy(onboarded = true, startEpochDay = clock.epochDay()) }
                    val p = prefs.current()
                    ReminderScheduler.schedule(context, p.reminderEnabled, p.reminderMinuteOfDay)
                }
            }
        }
    }
}

@Composable
fun SettingsRoute(vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshVoice() }
    SettingsScreen(
        state,
        onDone = nav::back,
        onInstallVoice = { context.openSafely(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) },
        onPrivacy = { context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) },
    ) { vm.onIntent(SettingsIntent.Update(it)) }
}

private fun Context.openSafely(intent: Intent) {
    try { startActivity(intent) } catch (_: ActivityNotFoundException) { }
}

private fun minuteLabel(m: Int): String {
    val h = m / 60
    val min = m % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, min, if (h < 12) "AM" else "PM")
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onDone: () -> Unit,
    onInstallVoice: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    update: ((UserPreferences) -> UserPreferences) -> Unit,
) {
    val c = Ren.colors
    val haze = remember { HazeState() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val p = state.prefs
    val romajiAllowed = state.pathDay <= Curriculum.ROMAJI_LAST_DAY

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        LazyColumn(
            Modifier.fillMaxSize().glassSource(haze),
            contentPadding = PaddingValues(top = top + NavBarHeight, bottom = bottom + 40.dp, start = 16.dp, end = 16.dp),
        ) {
            item { SectionHeader("Daily goal") }
            item {
                GroupedList {
                    row { Label("New cards"); Stepper("${p.newUnitsPerDay}", { update { it.copy(newUnitsPerDay = (it.newUnitsPerDay - 1).coerceAtLeast(1)) } }, { update { it.copy(newUnitsPerDay = (it.newUnitsPerDay + 1).coerceAtMost(10)) } }, minusEnabled = p.newUnitsPerDay > 1, plusEnabled = p.newUnitsPerDay < 10) }
                    row { Label("Reviews"); Stepper("${p.reviewGoal}", { update { it.copy(reviewGoal = (it.reviewGoal - 5).coerceAtLeast(10)) } }, { update { it.copy(reviewGoal = (it.reviewGoal + 5).coerceAtMost(150)) } }, minusEnabled = p.reviewGoal > 10, plusEnabled = p.reviewGoal < 150) }
                    row { Label("Minutes"); Stepper("${p.minutesGoal}", { update { it.copy(minutesGoal = (it.minutesGoal - 5).coerceAtLeast(5)) } }, { update { it.copy(minutesGoal = (it.minutesGoal + 5).coerceAtMost(60)) } }, minusEnabled = p.minutesGoal > 5, plusEnabled = p.minutesGoal < 60) }
                }
            }
            item { SectionFooter("Ten to fifteen minutes a day works best. Each new card is one lesson: a kanji with its words, a radical, or five kana.") }

            item { SectionHeader("Reminder") }
            item {
                GroupedList {
                    row { Label("Daily reminder"); CupertinoSwitch(p.reminderEnabled) { on -> update { it.copy(reminderEnabled = on) } } }
                    row(enabled = p.reminderEnabled) {
                        Label("Time")
                        Stepper(
                            minuteLabel(p.reminderMinuteOfDay),
                            { update { it.copy(reminderMinuteOfDay = (it.reminderMinuteOfDay - 30 + 1440) % 1440) } },
                            { update { it.copy(reminderMinuteOfDay = (it.reminderMinuteOfDay + 30) % 1440) } },
                            minusEnabled = p.reminderEnabled, plusEnabled = p.reminderEnabled,
                        )
                    }
                }
            }
            item { SectionFooter("One notification a day, and only when reviews are waiting.") }

            item { SectionHeader("Learning") }
            item {
                GroupedList {
                    row { Label("Show romaji"); CupertinoSwitch(p.showRomaji && romajiAllowed, enabled = romajiAllowed) { on -> update { it.copy(showRomaji = on) } } }
                    row { Label("Unlock all stages"); CupertinoSwitch(p.unlockAllStages) { on -> update { it.copy(unlockAllStages = on) } } }
                }
            }
            item {
                SectionFooter(
                    if (romajiAllowed) "Romaji helps for your first few days only, then it switches off for good."
                    else "Romaji is off from day ${Curriculum.ROMAJI_LAST_DAY + 1}. You are reading kana now.",
                )
            }
            item { SectionHeader("Furigana") }
            item {
                SegmentedControl(listOf("Fade", "Always", "Never"), p.furigana.ordinal) { i -> update { it.copy(furigana = FuriganaMode.entries[i]) } }
            }
            item { SectionFooter("Fade hides the reading once a kanji is mature, after about three weeks of good recall. Tap a word to see it again.") }
            item { SectionHeader("Saved sentences start with") }
            item {
                SegmentedControl(listOf("Audio", "Text"), if (p.minedAudioFirst) 0 else 1) { i -> update { it.copy(minedAudioFirst = i == 0) } }
            }
            item { SectionHeader("Voice") }
            item {
                GroupedList {
                    row {
                        Label("Japanese voice")
                        when (state.voice) {
                            VoiceStatus.Ready -> Txt("Ready", Ren.type.body, color = c.ink2)
                            VoiceStatus.Checking -> Txt("Checking", Ren.type.body, color = c.ink3)
                            VoiceStatus.Missing -> SmallButton("Install", primary = true, onClick = onInstallVoice)
                            VoiceStatus.NoEngine -> Txt("Unavailable", Ren.type.body, color = c.ink2)
                        }
                    }
                }
            }
            item {
                val idx = when { p.speechRate < 0.8f -> 0; p.speechRate < 1f -> 1; else -> 2 }
                SegmentedControl(listOf("Slow", "Learner", "Natural"), idx, Modifier.padding(top = 10.dp)) { i ->
                    update { it.copy(speechRate = listOf(0.7f, 0.9f, 1.0f)[i]) }
                }
            }
            item {
                SectionFooter(
                    when (state.voice) {
                        VoiceStatus.Missing -> "Words are read aloud by your phone's speech engine. Install its Japanese voice to hear them."
                        VoiceStatus.NoEngine -> "No speech engine was found. Install Speech Services by Google to hear words read aloud."
                        else -> "Words are read aloud by your phone's Japanese voice."
                    },
                )
            }

            item { SectionHeader("Feel") }
            item {
                GroupedList {
                    row { Label("Haptics"); CupertinoSwitch(p.hapticsEnabled) { on -> update { it.copy(hapticsEnabled = on) } } }
                    row { Label("Sounds"); CupertinoSwitch(p.soundEnabled) { on -> update { it.copy(soundEnabled = on) } } }
                }
            }
            item { SectionHeader("Appearance") }
            item {
                SegmentedControl(listOf("System", "Light", "Dark"), p.theme.ordinal) { i -> update { it.copy(theme = ThemeMode.entries[i]) } }
            }

            item { SectionHeader("Streak") }
            item {
                GroupedList {
                    infoRow("Current", "${p.streakDays} days")
                    infoRow("Longest", "${p.longestStreak} days")
                    infoRow("Streak freezes", "${p.streakFreezes} of 2")
                }
            }
            item { SectionFooter("A freeze is earned every 7 days and covers a missed day automatically.") }

            item { SectionHeader("About") }
            item {
                GroupedList {
                    infoRow("Version", BuildConfig.VERSION_NAME)
                    infoRow("Day on your path", "${state.pathDay}")
                    row(onClick = onPrivacy) { Label("Privacy policy"); Chevron() }
                }
            }
            item {
                SectionFooter(
                    "Stroke data: KanjiVG by Ulrich Apel (CC BY-SA 3.0). Readings: KANJIDIC2, EDRDG (CC BY-SA 4.0). " +
                        "Pitch accents: Kanjium (CC BY-SA 4.0). Type: Geist, Newsreader and Noto Serif JP (SIL OFL). Scheduling: FSRS v5.",
                )
            }
        }
        NavBar("Settings", haze = haze, trailing = { TextAction("Done", onClick = onDone) })
    }
}

@Composable
private fun RowScope.Label(text: String) = Txt(text, Ren.type.body, modifier = Modifier.weight(1f))

private fun GroupedListBuilder.infoRow(label: String, value: String) = row {
    Label(label)
    Txt(value, Ren.type.body, color = Ren.colors.ink2)
}
