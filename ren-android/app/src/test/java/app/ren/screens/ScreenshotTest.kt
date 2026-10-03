package app.ren.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.ren.core.audio.StoryPlayback
import app.ren.core.designsystem.components.TabBar
import app.ren.core.designsystem.components.TabBarHeight
import app.ren.core.designsystem.components.TabItem
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.core.designsystem.theme.RenTheme
import app.ren.domain.fsrs.Rating
import app.ren.domain.model.Japanese
import app.ren.domain.model.Segment
import app.ren.domain.model.StudyCard
import app.ren.domain.model.UserPreferences
import app.ren.domain.usecase.CurriculumPlanner
import app.ren.domain.usecase.KnowledgeGraphBuilder
import app.ren.domain.usecase.StoryAnalyzer
import app.ren.domain.usecase.TodaySummary
import app.ren.feature.common.PartUi
import app.ren.feature.common.UnitPresenter
import app.ren.feature.detail.ItemDetailScreen
import app.ren.feature.detail.ItemDetailUiState
import app.ren.feature.matrix.MatrixScreen
import app.ren.feature.matrix.MatrixUiState
import app.ren.feature.onboarding.OnboardingScreen
import app.ren.feature.path.PathScreen
import app.ren.feature.path.PathUiState
import app.ren.feature.path.StageStatus
import app.ren.feature.path.StageUi
import app.ren.feature.reader.LibraryScreen
import app.ren.feature.reader.LibraryUiState
import app.ren.feature.reader.MiningUi
import app.ren.feature.reader.PitchUi
import app.ren.feature.reader.SentenceUi
import app.ren.feature.reader.StoryScreen
import app.ren.feature.reader.StoryUiState
import app.ren.feature.reader.WordSheetUi
import app.ren.feature.reader.WordUi
import app.ren.feature.session.LessonPlanner
import app.ren.feature.session.LessonUi
import app.ren.feature.session.ReviewPhase
import app.ren.feature.session.ReviewUi
import app.ren.feature.session.SessionPage
import app.ren.feature.session.SessionScreen
import app.ren.feature.session.SessionUiState
import app.ren.feature.session.SummaryUi
import app.ren.feature.settings.SettingsScreen
import app.ren.feature.settings.SettingsUiState
import app.ren.feature.today.TodayScreen
import app.ren.feature.today.TodayUiState
import com.android.resources.NightMode
import org.junit.Rule
import org.junit.Test

/**
 * Renders every screen from real seed content, in light and dark. Record with
 * `./gradlew :app:recordPaparazziDebug`; images land in app/src/test/snapshots.
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 0.5,
    )

    private val lib = SampleData.library
    private val cards = SampleData.day34
    private val units = SampleData.completedUnits

    private fun shot(dark: Boolean = false, tabs: Int? = null, content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(
            deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT),
        )
        paparazzi.snapshot {
            // Inspection mode starts entrance animations at their final frame.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                RenTheme(dark = dark, glass = false) {
                    PhoneFrame(tabs) { content() }
                }
            }
        }
    }

    // ------------------------------------------------------------------ tabs

    private fun todayState(): TodayUiState {
        val kyuu = SampleData.unitFor("休").copy(completedAt = SampleData.NOW)
        val newToday = listOf(kyuu, SampleData.unitFor("好"), SampleData.unitFor("明"))
        return TodayUiState(
            loading = false,
            dateLabel = "10月1日 木曜日",
            summary = TodaySummary(
                dueReviews = 12, newRemaining = 2, reviewsDone = 18, reviewGoal = 30, newDone = 1, newGoal = 3,
                minutesDone = 9, minutesGoal = 15, estimatedMinutes = 8, streak = 14, freezes = 2,
                newToday = newToday, nextDueAt = null,
            ),
            newToday = newToday.map { UnitPresenter.describe(it, lib) },
            stageName = "Foundation Kanji",
        )
    }

    @Test fun today() = shot(tabs = 0) { TodayScreen(todayState(), TabBarHeight) {} }
    @Test fun todayDark() = shot(dark = true, tabs = 0) { TodayScreen(todayState(), TabBarHeight) {} }

    @Test fun path() = shot(tabs = 1) {
        val progress = CurriculumPlanner.stageProgress(units, cards.values.toList(), unlockAll = false)
            .mapIndexed { i, p -> if (i < 2) p.copy(graduatedShare = 1.0, unlocked = true) else p.copy(unlocked = i == 2) }
        val stages = progress.map { p ->
            val i = p.stage.index
            StageUi(
                p.stage,
                when (i) { 0, 1 -> StageStatus.Done; 2 -> StageStatus.Current; else -> StageStatus.Locked },
                p, if (i == 0) "Kana" else if (i == 1) "Radicals" else "Kanji", if (p.secondaryTotal > 0) "Words" else null,
                if (i == 2) SampleData.unitFor("好").id else null,
            )
        }
        PathScreen(PathUiState(false, 34, stages), TabBarHeight) {}
    }

    private fun matrixState(): MatrixUiState {
        val graph = KnowledgeGraphBuilder.build(lib, cards)
        return MatrixUiState(false, graph, graph.nodes.first { it.glyph == "休" }.id)
    }

    @Test fun matrix() = shot(tabs = 2) { MatrixScreen(matrixState(), TabBarHeight) {} }
    @Test fun matrixDark() = shot(dark = true, tabs = 2) { MatrixScreen(matrixState(), TabBarHeight) {} }

    @Test fun reader() = shot(tabs = 3) {
        val insights = lib.stories.map { StoryAnalyzer.analyze(it, lib, SampleData.day70) }
        LibraryScreen(LibraryUiState(false, 0, insights), TabBarHeight) {}
    }

    // ------------------------------------------------------------------ reading

    private fun storyState(id: String, sheetWord: String?, mining: MiningUi?): StoryUiState {
        val story = lib.stories.first { it.id == id }
        val sentences = story.sentences.mapIndexed { si, s ->
            SentenceUi(si, s.jp, s.en, s.segments.filterIsInstance<Segment.Word>().mapIndexed { wi, w ->
                val v = lib.vocabByText[w.lemma]
                WordUi(
                    showRuby = Japanese.hasKanji(w.text) && StoryAnalyzer.needsFurigana(w.text, lib, cards),
                    isNew = v != null && StoryAnalyzer.isTeachable(v, cards) && cards[v.id]?.isIntroduced != true,
                    selected = mining != null && mining.sentence == si && wi in mining.start..mining.end,
                )
            })
        }
        val sheet = sheetWord?.let { word ->
            val v = lib.vocabByText.getValue(word)
            val morae = Japanese.morae(v.reading)
            WordSheetUi(
                v, listOf(PartUi("亻", "person"), PartUi("木", "tree"), PartUi("日", "sun")),
                PitchUi(morae, Japanese.pitchContour(morae.size, v.pitchAccent), Japanese.pitchName(v.pitchAccent, morae.size)),
                inReviews = false, status = "New word",
            )
        }
        return StoryUiState(false, story, sentences, StoryPlayback(story.id, true, 0, 18f, story.durationSec.toFloat()), sheet, mining)
    }

    @Test fun storyWordSheet() = shot { StoryScreen(storyState("konbini", "休日", null), {}, {}) }
    @Test fun storyWordSheetDark() = shot(dark = true) { StoryScreen(storyState("konbini", "休日", null), {}, {}) }

    @Test fun storyMining() = shot {
        val m = MiningUi(1, 1, 2, "{電車|でんしゃ}に {乗ります|のります|乗る}", "電車に 乗ります", "to board the train", audioFirst = true)
        StoryScreen(storyState("eki", null, m), {}, {})
    }

    // ------------------------------------------------------------------ the six-step kanji flow

    private fun lessonShot(step: Int, ready: Boolean = true, dark: Boolean = false) = shot(dark) {
        val unit = SampleData.unitFor("休")
        val steps = LessonPlanner.steps(unit, lib, showRomaji = false)
        SessionScreen(SessionUiState(SessionPage.Lesson(LessonUi(unit, steps, step, ready)), 1, 14, library = lib)) {}
    }

    @Test fun step1PullApart() = lessonShot(0)
    @Test fun step2PictureIt() = lessonShot(1)
    @Test fun step3WriteIt() = lessonShot(2, ready = false)
    @Test fun step4FindIt() = lessonShot(3, ready = false)
    @Test fun step2PictureItDark() = lessonShot(1, dark = true)

    private fun review(word: String, phase: ReviewPhase, dark: Boolean = false) = shot(dark) {
        val v = lib.vocabByText.getValue(word)
        val card: StudyCard = cards.getValue(v.id)
        val prompt = LessonPlanner.prompt(card, lib)!!
        val intervals = mapOf(Rating.Again to "10 min", Rating.Hard to "3 days", Rating.Good to "6 days", Rating.Easy to "14 days")
        SessionScreen(SessionUiState(SessionPage.Review(ReviewUi(prompt, phase, intervals)), 7, 14, library = lib)) {}
    }

    @Test fun step5RecallTyped() = review("休日", ReviewPhase.Answering)
    @Test fun step6Rate() = review("休日", ReviewPhase.Result(true, "きゅうじつ", Rating.Good))
    @Test fun step6RateDark() = review("休日", ReviewPhase.Result(true, "きゅうじつ", Rating.Good), dark = true)
    @Test fun reviewPairTyped() = review("電車に乗る", ReviewPhase.Answering)

    @Test fun reviewDrawKanji() = shot {
        val k = lib.kanjiByChar.getValue("明")
        val prompt = LessonPlanner.prompt(cards.getValue(k.id), lib)!!
        SessionScreen(SessionUiState(SessionPage.Review(ReviewUi(prompt)), 3, 14, library = lib)) {}
    }

    @Test fun learnThePair() = shot {
        val unit = SampleData.units.first { it.title == "電車に乗る" }
        val steps = LessonPlanner.steps(unit, lib, false)
        SessionScreen(SessionUiState(SessionPage.Lesson(LessonUi(unit, steps, 0, true)), 9, 14, library = lib)) {}
    }

    @Test fun kanaLesson() = shot {
        val unit = SampleData.units.first()
        val steps = LessonPlanner.steps(unit, lib, showRomaji = true)
        SessionScreen(SessionUiState(SessionPage.Lesson(LessonUi(unit, steps, 0, true)), 1, 5, library = lib)) {}
    }

    @Test fun summary() = shot {
        SessionScreen(SessionUiState(SessionPage.Summary(SummaryUi(14, 12, listOf("好", "明"), 11, 15, false, 0)), 14, 14)) {}
    }

    // ------------------------------------------------------------------ everything else

    @Test fun itemDetail() = shot {
        val k = lib.kanjiByChar.getValue("休")
        ItemDetailScreen(ItemDetailUiState(false, k.id, lib, cards[k.id], SampleData.NOW, cards), {}, {})
    }

    @Test fun onboarding() = shot { OnboardingScreen {} }

    @Test fun settings() = shot {
        SettingsScreen(SettingsUiState(UserPreferences(onboarded = true, streakDays = 14, longestStreak = 21, streakFreezes = 2), 34), {}) {}
    }
}

/** A plain phone status bar so the screenshots read like the prototype's frames. */
@Composable
private fun PhoneFrame(tab: Int?, content: @Composable () -> Unit) {
    val c = Ren.colors
    Column(Modifier.fillMaxSize().background(c.canvas)) {
        Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("7:42", Ren.type.subhead.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Txt("●●●  ▮", Ren.type.caption, color = c.ink)
        }
        Box(Modifier.weight(1f)) {
            content()
            if (tab != null) {
                TabBar(
                    listOf(TabItem("Today", RenIcons.Today), TabItem("Path", RenIcons.Path), TabItem("Matrix", RenIcons.Matrix), TabItem("Reader", RenIcons.Reader)),
                    tab, null, Modifier.align(Alignment.BottomCenter),
                ) {}
            }
        }
    }
}
