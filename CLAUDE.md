# CLAUDE.md - ren

## App Overview & Key Packages

**Ren 練** (`ren-android/`) is a Japanese language and kanji study application for Android featuring FSRS spaced repetition, digital ink stroke order recognition, native ruby text rendering, and a custom flick keyboard.

- **Working Directory**: `ren-android/`
- **Key Modules & Packages**: `app.ren`
  - `domain/fsrs/`: `FSRSScheduler.kt` — Free Spaced Repetition Schedule (FSRS) implementation managing card stability, difficulty, and review intervals.
  - `domain/ink/`: `StrokeMatcher.kt` — digital ink handwriting engine validating stroke sequence, direction, and shape against kanji stroke definitions.
  - `domain/model/`: `JapaneseText.kt` (parsing and handling kanji with reading annotations), `Models.kt`, `Preferences.kt`.
  - `domain/usecase/`: `Planner.kt`, `Insights.kt`, `StudySnapshot.kt`, `Time.kt`.
  - `core/designsystem/components/`:
    - `StrokePad.kt`: Interactive handwriting canvas for kanji tracing and writing.
    - `Ruby.kt`: Jetpack Compose ruby/furigana text layout engine.
    - `FlickKeyboard.kt`: Authentic 12-key Japanese flick input component.
    - `Glyph.kt` & `Components.kt`: Reusable UI primitives.
  - `feature/`:
    - `session/`: `SessionScreen.kt`, `ReviewCards.kt`, `SafeFailure.kt`, `LessonPlanner.kt`.
    - `today/`: `TodayScreen.kt`, `NowFirst.kt` (immediate daily review queue).
    - `reader/`: `ReaderScreens.kt`, `ReaderViewModels.kt` (graded reading with interactive lookups).
    - `matrix/` & `path/`: `MatrixScreen.kt`, `PathScreen.kt` (curriculum progress maps).
  - `ecosystem/`: `LumeIpc.kt`, `RenStatusProvider.kt`, `FloorModeReceiver.kt` (Lume ecosystem companion integration).
  - `widget/`: `RenNowWidgets.kt`, `RenWidget.kt` (home screen widgets for due reviews and Word of the Day).

## Build, Test & Lint Commands (Quiet Mode for Token Saving)

Execute all commands from the `ren-android/` project folder:

```bash
cd ren-android

# Run unit & Robolectric tests
./gradlew testDebugUnitTest -q --console=plain --no-scan

# Run a single test class
./gradlew testDebugUnitTest --tests "app.ren.domain.FSRSSchedulerTest" -q --console=plain --no-scan

# Build debug APK
./gradlew assembleDebug -q --console=plain --no-scan

# Lint check
./gradlew lintDebug -q --console=plain --no-scan
```

## Android & Cloud Container Engineering Rules (from innovator-repo/memory)

- **No Android Emulator in Cloud Containers**: Cloud containers lack KVM virtualization; do NOT attempt to launch emulators. Use Robolectric (+ Roborazzi where configured) for fast, headless UI and screenshot verification.
- **Compose Performance & Phases**: Defer rapidly changing State reads (such as scroll offsets and animation values) to the Layout or Draw phases using lambda modifiers (`Modifier.offset { ... }`, `Modifier.graphicsLayer { ... }`, `Modifier.drawBehind { ... }`) to avoid re-triggering composition. Never annotate mutable non-snapshot types with `@Immutable` or `@Stable`.
- **Platform Gotchas**:
  - **AccessibilityEvent recycling**: Copy `AccessibilityEvent` fields (`packageName`, `eventType`, `className`) synchronously on API 26–32 before crossing coroutine or async boundaries to avoid recycled event crashes.
  - **Device Admin Policy XML**: Android `<device-admin>` XML must declare at least one active policy (e.g., `<force-lock />`) to be valid on newer Android releases.
  - **System Settings Intents**: Always wrap system settings intents (`com.android.vpndialogs`, Private DNS settings, Device Admin enrollment) with fallback intents and written in-app user directions to prevent `ActivityNotFoundException` on heavily customized OEM ROMs (Samsung OneUI / Android 16).
- **APK Delivery Pattern**: When pushing debug APKs to an `apk/*` orphan branch, use a separate git worktree (`/tmp/apkwt`) and `git add --force` to prevent binary bloat on source branches.

## Token Hygiene & Compaction Keep-List

- **Token Hygiene**: Never read `.gradle/`, `build/`, `captures/`, `*.apk`, `*.aab`, `*.keystore`, or `*.dex`. Use targeted `read_file` with specific line ranges.
- **Cross-Session Memory**: Long-term project memory and cross-session insights live in `innovator-repo/memory` (`MEMORY.md`).
- **Compaction Keep-List**: When running `/compact`, always preserve:
  1. Current task goal and acceptance criteria.
  2. Kotlin/XML files created or modified.
  3. Build, test, or crash stack traces and identified root causes.
  4. Architectural decisions and design trade-offs.
  5. Pending Gradle, test, or APK delivery commands.

