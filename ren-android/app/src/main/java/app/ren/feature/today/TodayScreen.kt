package app.ren.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ren.core.designsystem.components.ActivityRings
import app.ren.core.designsystem.components.Card
import app.ren.designsystem.GroupedList
import app.ren.core.designsystem.components.HSpace
import app.ren.core.designsystem.components.Kicker
import app.ren.designsystem.LargeTitleScaffold
import app.ren.core.designsystem.components.Pill
import app.ren.core.designsystem.components.PrimaryButton
import app.ren.core.designsystem.components.RingSpec
import app.ren.core.designsystem.components.SectionFooter
import app.ren.core.designsystem.components.SectionHeader
import app.ren.core.designsystem.components.SmallButton
import app.ren.core.designsystem.components.ToolButton
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.components.VSpace
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.core.mvi.CollectEffects
import app.ren.domain.usecase.TodaySummary
import app.ren.feature.common.UnitRowContent
import app.ren.navigation.LocalNavigator
import app.ren.navigation.Tabs

@Composable
fun TodayRoute(bottomInset: Dp, vm: TodayViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    CollectEffects(vm.effects) { e ->
        when (e) {
            TodayEffect.Session -> nav.session()
            is TodayEffect.Lesson -> nav.lesson(e.unitId)
            is TodayEffect.Item -> nav.item(e.itemId)
            TodayEffect.Settings -> nav.settings()
            TodayEffect.Reader -> nav.tab(Tabs.READER)
        }
    }
    TodayScreen(state, bottomInset, vm::onIntent)
}

@Composable
fun TodayScreen(state: TodayUiState, bottomInset: Dp, onIntent: (TodayIntent) -> Unit) {
    LargeTitleScaffold(
        title = "Today",
        subtitle = state.dateLabel,
        bottomInset = bottomInset,
        trailing = { ToolButton(RenIcons.Sliders, "Settings") { onIntent(TodayIntent.OpenSettings) } },
    ) {
        val s = state.summary ?: return@LargeTitleScaffold
        item(key = "hero") { Hero(state, s, Modifier.padding(horizontal = 20.dp)) }
        item(key = "session") { SessionCard(state, s, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), onIntent) }
        if (state.newToday.isNotEmpty()) {
            item(key = "new-header") { SectionHeader("New today", Modifier.padding(horizontal = 16.dp)) }
            item(key = "new-list") {
                GroupedList(Modifier.padding(horizontal = 16.dp), plain = true) {
                    state.newToday.forEach { unit ->
                        row(inset = 52.dp, onClick = { onIntent(TodayIntent.OpenUnit(unit)) }) { UnitRowContent(unit) }
                    }
                }
            }
            item(key = "new-footer") {
                SectionFooter("${state.stageName}. Each new card brings its own words.", Modifier.padding(horizontal = 16.dp))
            }
        } else if (state.waitingOnGraduation) {
            item(key = "waiting") {
                SectionFooter(
                    "Your next lessons open once today's cards graduate. Keep reviewing and they will unlock.",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Rings beside one calm sentence, as in Sigil's check-up. */
@Composable
private fun Hero(state: TodayUiState, s: TodaySummary, modifier: Modifier) {
    val c = Ren.colors
    Column(modifier.padding(top = 6.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityRings(
                listOf(
                    RingSpec(s.reviewsDone / s.reviewGoal.coerceAtLeast(1).toFloat(), c.akane),
                    RingSpec(s.newDone / s.newGoal.coerceAtLeast(1).toFloat(), c.bamboo),
                    RingSpec(s.minutesDone / s.minutesGoal.coerceAtLeast(1).toFloat(), c.radical),
                ),
                Modifier.size(68.dp), stroke = 7.dp, gap = 1.5.dp,
            )
            HSpace(16.dp)
            Column(Modifier.weight(1f)) {
                Txt(headline(state, s), Ren.type.title2)
                VSpace(3.dp)
                Txt(
                    "${s.reviewsDone} of ${s.reviewGoal} reviews · ${s.newDone} of ${s.newGoal} new · ${s.minutesDone} of ${s.minutesGoal} min",
                    Ren.type.footnote, color = c.ink2,
                )
            }
        }
        VSpace(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(if (s.streak == 0) "Start a streak" else "${s.streak}-day streak", icon = RenIcons.Flame, iconTint = c.amber)
            Pill(
                when (s.freezes) { 0 -> "No freezes yet"; 1 -> "1 freeze"; else -> "${s.freezes} freezes" },
                icon = RenIcons.Shield, iconTint = c.radical,
            )
        }
    }
}

private fun headline(state: TodayUiState, s: TodaySummary): String = when {
    state.caughtUp -> "You're all caught up."
    s.dueReviews == 0 -> "${s.newRemaining} new ${if (s.newRemaining == 1) "card is" else "cards are"} ready."
    s.dueReviews == 1 -> "One review is ready."
    else -> "${s.dueReviews} reviews are ready."
}

/** The one raised card on the screen: what to do next. */
@Composable
private fun SessionCard(state: TodayUiState, s: TodaySummary, modifier: Modifier, onIntent: (TodayIntent) -> Unit) {
    val c = Ren.colors
    Card(modifier, elevated = true, padding = PaddingValues(18.dp)) {
        Kicker("Up next")
        VSpace(8.dp)
        if (!state.caughtUp) {
            Txt("Daily session", Ren.type.title3)
            VSpace(2.dp)
            val parts = buildList {
                if (s.dueReviews > 0) add("${s.dueReviews} ${if (s.dueReviews == 1) "review" else "reviews"}")
                if (s.newRemaining > 0) add("${s.newRemaining} new")
                add("about ${s.estimatedMinutes} min")
            }
            Txt(parts.joinToString(" · ").replaceFirstChar { it.uppercase() }, Ren.type.callout, color = c.ink2)
            VSpace(14.dp)
            PrimaryButton("Start", trailing = {
                HSpace(8.dp)
                Txt(
                    "${s.sessionItems}", Ren.type.caption.copy(fontWeight = FontWeight.Bold), color = c.akane,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }) { onIntent(TodayIntent.StartSession) }
        } else {
            Txt("Read something", Ren.type.title3)
            VSpace(2.dp)
            Txt(
                state.nextDueLabel?.let { "Next review in $it. A short story keeps the words warm." }
                    ?: "Nothing is due. A short story keeps the words warm.",
                Ren.type.callout, color = c.ink2,
            )
            VSpace(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Open Reader", primary = true) { onIntent(TodayIntent.ReadAStory) }
                if (state.extraUnitId != null) SmallButton("Learn one more") { onIntent(TodayIntent.LearnOneMore) }
            }
        }
    }
}
