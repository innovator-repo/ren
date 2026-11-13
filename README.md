# Ren 練

Japanese language & kanji learning Android application featuring FSRS spaced repetition, digital ink stroke handwriting recognition, and interactive reading. Built with Kotlin and Jetpack Compose.

## Key Features

- **FSRS Spaced Repetition Engine (`FSRSScheduler.kt`)**:
  - Modern Free Spaced Repetition Schedule (FSRS) domain implementation optimizing memory stability, retrievability, and review intervals.
  - Daily review planner and insights tracking (`Planner.kt`, `Insights.kt`).
- **Digital Ink Stroke Handwriting Recognition**:
  - Real-time stroke capture and directional matching (`StrokePad.kt`, `StrokeMatcher.kt`).
  - Evaluates stroke order, count, and geometry for authentic kanji writing practice.
- **Native Ruby & Furigana Rendering (`Ruby.kt`, `JapaneseText.kt`)**:
  - Custom Jetpack Compose text components providing typesetting for furigana above kanji base text.
- **Custom Japanese Flick Keyboard (`FlickKeyboard.kt`)**:
  - In-app 12-key Japanese flick input component supporting standard kana gestures.
- **Interactive Reader & Guided Study**:
  - Graded reader screens with inline lookups (`ReaderScreens.kt`).
  - Study sessions with safe failure mechanics (`SafeFailure.kt`, `LessonPlanner.kt`).
  - Matrix and Path curriculum visualizations for step-by-step kanji progression.
- **Today Dashboard & "Now First" Queue**:
  - Focused daily dashboard prioritizing due cards and daily word milestones (`TodayScreen.kt`, `NowFirst.kt`).
  - Glanceable App Widgets (`RenNowWidgets.kt`, `RenWidget.kt`) displaying due card counts and Word of the Day.
- **Lume Ecosystem & Circadian Integration**:
  - Inter-process communication (`LumeIpc.kt`, `RenStatusProvider.kt`) and Floor Mode receiver.
  - Circadian theme integration adjusting color temperature according to the time of day.
- **Automated Verification**:
  - Full suite of unit tests for scheduler, stroke matcher, and text parsing.
  - Roborazzi screenshot and store graphic regression tests.

## Architecture

```
ren-android/
└── app/
    ├── src/main/java/app/ren/
    │   ├── domain/
    │   │   ├── fsrs/            # FSRSScheduler, retention calculation
    │   │   ├── ink/             # StrokeMatcher, directional parsing
    │   │   ├── model/           # JapaneseText, Models, Preferences
    │   │   └── usecase/         # Planner, Insights, StudySnapshot
    │   ├── core/
    │   │   ├── designsystem/    # StrokePad, Ruby, FlickKeyboard, Glyph
    │   │   └── audio/           # AudioEngineManager (pronunciation)
    │   ├── feature/
    │   │   ├── session/         # ReviewCards, SafeFailure, LessonSteps
    │   │   ├── today/           # TodayScreen, NowFirst queue
    │   │   ├── reader/          # ReaderScreens, ReaderViewModels
    │   │   ├── matrix/          # MatrixScreen (Kanji grid)
    │   │   └── path/            # PathScreen (Progression)
    │   ├── ecosystem/           # LumeIpc, RenStatusProvider, FloorModeReceiver
    │   └── widget/              # RenNowWidgets, RenWidget
    └── src/test/                # FSRSSchedulerTest, StrokeMatcherTest, ScreenshotTest
```

## Build & Test

```bash
cd ren-android

# Run unit & Robolectric tests
./gradlew testDebugUnitTest -q --console=plain --no-scan

# Run a specific test class
./gradlew testDebugUnitTest --tests "app.ren.domain.FSRSSchedulerTest" -q --console=plain --no-scan

# Build debug APK
./gradlew assembleDebug -q --console=plain --no-scan

# Lint check
./gradlew lintDebug -q --console=plain --no-scan
```
