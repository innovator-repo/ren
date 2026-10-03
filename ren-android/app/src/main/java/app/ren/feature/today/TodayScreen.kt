package app.ren.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ren.core.designsystem.components.ActivityRings
import app.ren.core.designsystem.components.Card
import app.ren.core.designsystem.components.CircleIconButton
import app.ren.core.designsystem.components.GroupedList
import app.ren.core.designsystem.components.HSpace
import app.ren.core.designsystem.components.LargeTitleScaffold
import app.ren.core.designsystem.components.Pill
import app.ren.core.designsystem.components.PrimaryButton
import app.ren.core.designsystem.components.RingSpec
import app.ren.core.designsystem.components.SectionFooter
import app.ren.core.designsystem.components.SectionHeader
import app.ren.core.designsystem.components.SoftButton
import app.ren.core.designsystem.components.TextAction
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
    val c = Ren.colors
    LargeTitleScaffold(
        title = "Today",
        subtitle = state.dateLabel,
        bottomInset = bottomInset,
        trailing = {
            CircleIconButton(RenIcons.Sliders, "Settings", background = c.surface, tint = c.akane, size = 36.dp, iconSize = 20.dp) {
                onIntent(TodayIntent.OpenSettings)
            }
        },
    ) {
        val s = state.summary ?: return@LargeTitleScaffold
        item(key = "pills") { StreakPills(s) }
        item(key = "rings") { RingsCard(s, Modifier.padding(horizontal = 16.dp)) }
        item(key = "session") {
            SessionCard(state, s, Modifier.padding(horizontal = 16.dp, vertical = 16.dp), onIntent)
        }
        if (state.newToday.isNotEmpty()) {
            item(key = "new-header") { SectionHeader("New today", Modifier.padding(horizontal = 16.dp)) }
            item(key = "new-list") {
                GroupedList(Modifier.padding(horizontal = 16.dp)) {
                    state.newToday.forEach { unit ->
                        row(inset = 82.dp, onClick = { onIntent(TodayIntent.OpenUnit(unit)) }) { UnitRowContent(unit) }
                    }
                }
            }
            item(key = "new-footer") {
                SectionFooter(
                    "${state.stageName}. Each new card brings its own words. Done ones come back as reviews.",
                    Modifier.padding(horizontal = 16.dp),
                )
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

@Composable
private fun StreakPills(s: TodaySummary) {
    val c = Ren.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(
            if (s.streak == 0) "Start a streak today" else "${s.streak}-day streak",
            icon = RenIcons.Flame, iconTint = c.amber,
        )
        Pill(
            when (s.freezes) {
                0 -> "Shield builds at 7 days"
                1 -> "1 freeze ready"
                else -> "${s.freezes} freezes ready"
            },
            icon = RenIcons.Shield, iconTint = c.radical,
        )
    }
    VSpace(12.dp)
}

@Composable
private fun RingsCard(s: TodaySummary, modifier: Modifier) {
    val c = Ren.colors
    Card(modifier, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityRings(
                listOf(
                    RingSpec(s.reviewsDone / s.reviewGoal.coerceAtLeast(1).toFloat(), c.akane),
                    RingSpec(s.newDone / s.newGoal.coerceAtLeast(1).toFloat(), c.bamboo),
                    RingSpec(s.minutesDone / s.minutesGoal.coerceAtLeast(1).toFloat(), c.radical),
                ),
                Modifier.size(112.dp),
            )
            HSpace(22.dp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Legend("Reviews", s.reviewsDone, s.reviewGoal, c.akane)
                Legend("New cards", s.newDone, s.newGoal, c.bamboo)
                Legend("Minutes", s.minutesDone, s.minutesGoal, c.radical)
            }
        }
    }
}

@Composable
private fun Legend(label: String, value: Int, goal: Int, color: Color) {
    val c = Ren.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color))
            HSpace(6.dp)
            Txt(label, Ren.type.caption.copy(fontWeight = FontWeight.Medium), color = c.ink2)
        }
        Txt(
            buildAnnotatedString {
                append("$value")
                withStyle(SpanStyle(fontSize = 14.sp, color = c.ink2, fontWeight = FontWeight.Medium)) { append("/$goal") }
            },
            Ren.type.title2.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun SessionCard(state: TodayUiState, s: TodaySummary, modifier: Modifier, onIntent: (TodayIntent) -> Unit) {
    val c = Ren.colors
    Card(modifier, padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        if (!state.caughtUp) {
            Txt(
                when {
                    s.dueReviews == 0 -> "${s.newRemaining} new ${if (s.newRemaining == 1) "card" else "cards"} today"
                    s.dueReviews == 1 -> "1 review due"
                    else -> "${s.dueReviews} reviews due"
                },
                Ren.type.title2.copy(fontWeight = FontWeight.Bold),
            )
            VSpace(4.dp)
            val plus = if (s.dueReviews > 0 && s.newRemaining > 0) "Plus ${s.newRemaining} new ${if (s.newRemaining == 1) "card" else "cards"}. " else ""
            Txt("${plus}About ${s.estimatedMinutes} ${if (s.estimatedMinutes == 1) "minute" else "minutes"}.", Ren.type.body, color = c.ink2)
            VSpace(18.dp)
            PrimaryButton("Start Daily Session", trailing = {
                HSpace(10.dp)
                Txt(
                    "${s.sessionItems}", Ren.type.footnote.copy(fontWeight = FontWeight.Bold), color = c.akane,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }) { onIntent(TodayIntent.StartSession) }
        } else {
            Txt("All caught up", Ren.type.title2.copy(fontWeight = FontWeight.Bold))
            VSpace(4.dp)
            Txt(
                state.nextDueLabel?.let { "Next review in $it. A short story keeps the words warm." }
                    ?: "Nothing is due. A short story keeps the words warm.",
                Ren.type.body, color = c.ink2,
            )
            VSpace(18.dp)
            SoftButton("Read a story", color = c.akane) { onIntent(TodayIntent.ReadAStory) }
            if (state.extraUnitId != null) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    TextAction("Learn one more", bold = false) { onIntent(TodayIntent.LearnOneMore) }
                }
            }
        }
    }
}
