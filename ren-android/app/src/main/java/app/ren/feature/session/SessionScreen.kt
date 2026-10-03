package app.ren.feature.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ren.core.designsystem.components.AnswerField
import app.ren.core.designsystem.components.Card
import app.ren.core.designsystem.components.CircleIconButton
import app.ren.core.designsystem.components.FlickKeyboard
import app.ren.core.designsystem.components.HSpace
import app.ren.core.designsystem.components.KanaInputState
import app.ren.core.designsystem.components.Pill
import app.ren.core.designsystem.components.PrimaryButton
import app.ren.core.designsystem.components.ProgressBar
import app.ren.core.designsystem.components.SoftButton
import app.ren.core.designsystem.components.StepDashes
import app.ren.core.designsystem.components.Tag
import app.ren.core.designsystem.components.TextAction
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.components.VSpace
import app.ren.core.designsystem.components.rememberStrokePadState
import app.ren.core.designsystem.icons.RenIcon
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.core.designsystem.theme.RenMotion
import app.ren.core.haptics.LocalHaptics
import app.ren.core.mvi.CollectEffects
import app.ren.domain.fsrs.Rating
import app.ren.navigation.LocalNavigator

@Composable
fun SessionRoute(vm: SessionViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val haptics = LocalHaptics.current
    CollectEffects(vm.effects) { e ->
        when (e) {
            SessionEffect.PlayErrorHaptic -> haptics.error()
            SessionEffect.PlaySuccessHaptic -> haptics.success()
            SessionEffect.Close -> nav.back()
            SessionEffect.Finished -> Unit
        }
    }
    SessionScreen(state, vm::onIntent)
}

@Composable
fun SessionScreen(state: SessionUiState, onIntent: (SessionIntent) -> Unit) {
    val c = Ren.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(Modifier.fillMaxSize().background(c.canvas).padding(top = top)) {
        TopBar(state, onIntent)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = state.page,
                contentKey = { page ->
                    when (page) {
                        is SessionPage.Review -> "r:${page.ui.prompt.card.itemId}:${state.position}"
                        is SessionPage.Lesson -> "l:${page.ui.unit.id}:${page.ui.index}"
                        else -> page::class.simpleName
                    }
                },
                transitionSpec = {
                    (slideInHorizontally(RenMotion.snappy()) { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally(RenMotion.snappy()) { -it / 4 } + fadeOut())
                },
                label = "page",
            ) { page ->
                when (page) {
                    SessionPage.Loading -> Unit
                    SessionPage.Empty -> EmptyPage(onIntent)
                    is SessionPage.Lesson -> LessonPage(page.ui, onIntent)
                    is SessionPage.Review -> ReviewPage(page.ui, state, onIntent)
                    is SessionPage.Summary -> SummaryPage(page.ui, onIntent)
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: SessionUiState, onIntent: (SessionIntent) -> Unit) {
    val c = Ren.colors
    val page = state.page
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (page !is SessionPage.Summary) {
            CircleIconButton(RenIcons.Close, "Close", onClick = { onIntent(SessionIntent.Close) })
        }
        HSpace(14.dp)
        when (page) {
            is SessionPage.Lesson -> {
                val ui = page.ui
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (ui.steps.size <= 6) StepDashes(ui.steps.size, ui.index)
                    else ProgressBar((ui.index + 1f) / ui.steps.size, c.akane)
                }
                HSpace(48.dp)
            }
            is SessionPage.Review -> {
                ProgressBar((state.position - 1f).coerceAtLeast(0f) / state.total.coerceAtLeast(1), c.akane, Modifier.weight(1f))
                HSpace(12.dp)
                Txt("${state.position} / ${state.total}", Ren.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.ink2)
            }
            else -> Box(Modifier.weight(1f))
        }
    }
}

// ------------------------------------------------------------------ lessons

@Composable
private fun LessonPage(ui: LessonUi, onIntent: (SessionIntent) -> Unit) {
    val c = Ren.colors
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val step = ui.step
    val key = "${ui.unit.id}:${ui.index}"
    val pad = rememberStrokePadState(key, (step as? LessonStep.WriteIt)?.strokes?.size ?: 0)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            VSpace(8.dp)
            Txt(step.title, Ren.type.title1.copy(fontSize = 30.sp, lineHeight = 36.sp), modifier = Modifier.padding(horizontal = 4.dp))
            VSpace(2.dp)
            Txt(step.subtitle, Ren.type.body, color = c.ink2, modifier = Modifier.padding(horizontal = 4.dp))
            VSpace(16.dp)
            LessonStepContent(
                step, key, pad,
                onReady = { onIntent(SessionIntent.StepReady) },
                onSpeak = { onIntent(SessionIntent.SpeakText(it)) },
            )
            VSpace(20.dp)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = bottom + 12.dp, top = 4.dp)) {
            val next = ui.steps.getOrNull(ui.index + 1)
            val label = ctaLabel(step, next, ui.ready)
            if (step is LessonStep.WriteIt && !ui.ready) {
                SoftButton(label, enabled = pad.completed > 0) { pad.clear() }
            } else {
                PrimaryButton(label, enabled = ui.ready) { onIntent(SessionIntent.NextStep) }
            }
        }
    }
}

// ------------------------------------------------------------------ reviews

@Composable
private fun ReviewPage(ui: ReviewUi, state: SessionUiState, onIntent: (SessionIntent) -> Unit) {
    val c = Ren.colors
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val p = ui.prompt
    val answering = ui.phase == ReviewPhase.Answering
    val typed = p.kind in setOf(ReviewKind.TYPE_READING, ReviewKind.TYPE_WORD, ReviewKind.TYPE_PAIR, ReviewKind.LISTEN)
    val input = remember(p.card.itemId, state.position) { KanaInputState() }

    if (answering && typed) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                VSpace(8.dp)
                TypedPrompt(ui) { onIntent(SessionIntent.Speak) }
                VSpace(14.dp)
                AnswerField(
                    input, { onIntent(SessionIntent.SubmitTyped(input.text)) },
                    verdict = if (ui.attempt > 0) false else null, shakeKey = ui.shake,
                    placeholder = if (p.kind == ReviewKind.LISTEN) "What did you hear?" else "",
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(
                        if (ui.attempt > 0) "Not quite. Check small kana and long vowels, then try once more."
                        else "Flick a key in any direction to pick a kana.",
                        Ren.type.footnote, color = if (ui.attempt > 0) c.amber else c.ink2, modifier = Modifier.weight(1f),
                    )
                    if (p.kind == ReviewKind.LISTEN) TextAction("Reveal", bold = true, color = c.radical) { onIntent(SessionIntent.Reveal(input.text)) }
                }
            }
            FlickKeyboard(input, onSubmit = { onIntent(SessionIntent.SubmitTyped(input.text)) })
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            VSpace(8.dp)
            when {
                answering && (p.kind == ReviewKind.DRAW || p.kind == ReviewKind.DRAW_KANA) ->
                    DrawPrompt(ui, state.showRomaji, onSpeak = { onIntent(SessionIntent.Speak) }) { m, h -> onIntent(SessionIntent.DrawingFinished(m, h)) }
                answering && p.kind == ReviewKind.READ -> ReadPrompt(ui)
                else -> ResultCard(ui, state.library) { onIntent(SessionIntent.Speak) }
            }
            VSpace(20.dp)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = bottom + 14.dp)) {
            when (val phase = ui.phase) {
                ReviewPhase.Answering -> if (p.kind == ReviewKind.READ) {
                    PrimaryButton("Reveal") { onIntent(SessionIntent.Reveal("")) }
                }
                is ReviewPhase.Revealed -> RatingBar(ui.intervals, null) { onIntent(SessionIntent.Rate(it)) }
                is ReviewPhase.Result -> if (phase.correct) {
                    RatingBar(ui.intervals, phase.suggested) { onIntent(SessionIntent.Rate(it)) }
                } else {
                    Txt("It comes back in ${ui.intervals[Rating.Again] ?: "a few minutes"}.", Ren.type.footnote, color = c.ink2,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), align = TextAlign.Center)
                    PrimaryButton("Continue") { onIntent(SessionIntent.ContinueAfterMiss) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ end states

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryPage(ui: SummaryUi, onIntent: (SessionIntent) -> Unit) {
    val c = Ren.colors
    val still = LocalInspectionMode.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pop = remember { Animatable(if (still) 1f else 0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, RenMotion.spring()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            VSpace(40.dp)
            Box(Modifier.size(96.dp).scale(pop.value).clip(CircleShape).background(c.bamboo), Alignment.Center) {
                RenIcon(RenIcons.Check, c.onAccent, size = 52.dp)
            }
            VSpace(22.dp)
            Txt(if (ui.lessonOnly) "Lesson complete" else "Session complete", Ren.type.title1, align = TextAlign.Center)
            VSpace(6.dp)
            Txt(
                when {
                    ui.lessonOnly -> "Its cards are in your queue now. First recall is best done soon."
                    ui.reviewed == 0 -> "Nothing more is due. Well done."
                    else -> "Small and daily beats long and rare."
                },
                Ren.type.body, color = c.ink2, align = TextAlign.Center,
            )
            VSpace(26.dp)
            if (!ui.lessonOnly) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("${ui.reviewed}", "Reviewed", Modifier.weight(1f))
                    Stat(if (ui.reviewed == 0) "–" else "${ui.correct * 100 / ui.reviewed}%", "Recalled", Modifier.weight(1f))
                    Stat("${ui.minutes}", "Minutes today", Modifier.weight(1f))
                }
                VSpace(14.dp)
            }
            if (ui.streak > 0) Pill("${ui.streak}-day streak", icon = RenIcons.Flame, iconTint = c.amber)
            if (ui.learned.isNotEmpty()) {
                VSpace(20.dp)
                Txt("NEW TODAY", Ren.type.caption.copy(letterSpacing = 0.6.sp), color = c.ink2)
                VSpace(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.learned.forEach { Tag(it, c.akane) }
                }
            }
        }
        Column(Modifier.padding(bottom = bottom + 14.dp)) {
            if (ui.canReviewNow > 0) {
                PrimaryButton("Recall them now (${ui.canReviewNow})") { onIntent(SessionIntent.ReviewNow) }
                VSpace(10.dp)
                SoftButton("Done") { onIntent(SessionIntent.Close) }
            } else {
                PrimaryButton("Done") { onIntent(SessionIntent.Close) }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Card(modifier, padding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
        Txt(value, Ren.type.title2.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
        Txt(label, Ren.type.caption, color = Ren.colors.ink2, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
    }
}

@Composable
private fun EmptyPage(onIntent: (SessionIntent) -> Unit) {
    val c = Ren.colors
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RenIcon(RenIcons.Sparkle, c.bamboo, size = 54.dp)
                VSpace(14.dp)
                Txt("Nothing is due", Ren.type.title2.copy(fontWeight = FontWeight.Bold))
                VSpace(6.dp)
                Txt("Your cards are resting. Come back when they are ready, or read a story.", Ren.type.body, color = c.ink2, align = TextAlign.Center)
            }
        }
        Box(Modifier.padding(bottom = bottom + 14.dp)) { PrimaryButton("Done") { onIntent(SessionIntent.Close) } }
    }
}
