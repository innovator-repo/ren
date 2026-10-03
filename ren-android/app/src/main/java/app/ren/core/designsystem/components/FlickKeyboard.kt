package app.ren.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ren.core.designsystem.icons.RenIcon
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.core.designsystem.theme.jp
import app.ren.core.haptics.LocalHaptics
import app.ren.domain.model.Japanese
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Text being typed on the flick keyboard, with a caret. */
@Stable
class KanaInputState(initial: String = "") {
    var text by mutableStateOf(initial)
        private set
    var cursor by mutableIntStateOf(initial.length)
        private set

    fun insert(s: String) {
        text = text.substring(0, cursor) + s + text.substring(cursor)
        cursor += s.length
    }

    fun backspace() {
        if (cursor == 0) return
        text = text.removeRange(cursor - 1, cursor)
        cursor--
    }

    fun left() { if (cursor > 0) cursor-- }
    fun right() { if (cursor < text.length) cursor++ }
    fun clear() { text = ""; cursor = 0 }

    /** The 小゛゜ key: か→が→か, つ→っ→づ→つ, は→ば→ぱ→は on the character before the caret. */
    fun cycleModifier() {
        if (cursor == 0) return
        val prev = text[cursor - 1]
        val katakana = Japanese.isKatakana(prev)
        val hira = Japanese.toHiragana(prev.toString())
        val next = CYCLES.firstNotNullOfOrNull { cycle ->
            val i = cycle.indexOf(hira)
            if (i >= 0) cycle[(i + 1) % cycle.length] else null
        } ?: return
        val out = if (katakana) (next + 0x60).toString() else next.toString()
        text = text.substring(0, cursor - 1) + out + text.substring(cursor)
    }

    private companion object {
        val CYCLES = listOf(
            "かが", "きぎ", "くぐ", "けげ", "こご", "さざ", "しじ", "すず", "せぜ", "そぞ", "ただ", "ちぢ", "つっづ",
            "てで", "とど", "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ", "やゃ", "ゆゅ", "よょ", "あぁ", "いぃ",
            "うぅゔ", "えぇ", "おぉ", "わゎ",
        )
    }
}

/** Center, left (i-row), up (u-row), right (e-row), down (o-row), as on a Japanese phone (spec §6C). */
private val FLICK = linkedMapOf(
    "あ" to "あいうえお", "か" to "かきくけこ", "さ" to "さしすせそ",
    "た" to "たちつてと", "な" to "なにぬねの", "は" to "はひふへほ",
    "ま" to "まみむめも", "や" to "や「ゆ」よ", "ら" to "らりるれろ",
    "わ" to "わをんー", "、" to "、。？！",
)

private data class Flick(val key: String, val dir: Int)

/**
 * The 12-key flick keyboard. Touch a key to open its translucent flower, drag toward a petal and
 * release to commit. Petals tick as the finger moves between them.
 */
@Composable
fun FlickKeyboard(
    input: KanaInputState,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    submitLabel: String = "確定",
) {
    val c = Ren.colors
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Key positions are resolved against the keyboard only when a flower opens, so callback order never matters.
    val keyCoords = remember { mutableMapOf<String, LayoutCoordinates>() }
    var active by remember { mutableStateOf<Flick?>(null) }
    var katakana by remember { mutableStateOf(false) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gap = 6.dp

    fun out(ch: Char): String = if (katakana && Japanese.isHiragana(ch)) (ch + 0x60).toString() else ch.toString()

    @Composable
    fun kanaKey(key: String, label: String = key) {
        val set = FLICK.getValue(key)
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onGloballyPositioned { keyCoords[key] = it }
                .keyFace(c.key, pressed = active?.key == key)
                .semantics { contentDescription = "$label key" }
                .pointerInput(key) {
                    val threshold = 22.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        var dir = 0
                        active = Flick(key, 0)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                set.getOrNull(dir)?.let { input.insert(out(it)); haptics.click() }
                                change.consume()
                                break
                            }
                            val d = change.position - down.position
                            var nd = when {
                                hypot(d.x, d.y) < threshold -> 0
                                abs(d.x) > abs(d.y) -> if (d.x < 0) 1 else 3
                                else -> if (d.y < 0) 2 else 4
                            }
                            if (set.getOrNull(nd) == null) nd = 0
                            if (nd != dir) {
                                dir = nd
                                active = Flick(key, dir)
                                haptics.tick()
                            }
                            change.consume()
                        }
                        active = null
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Txt(if (katakana) label.map { out(it) }.joinToString("") else label, Ren.type.body.jp(22.sp, FontWeight.Medium), maxLines = 1)
        }
    }

    @Composable
    fun fnKey(label: String, modifier: Modifier = Modifier, height: Dp = 48.dp, color: Color = c.keyFunction, textColor: Color = c.ink, onTap: () -> Unit, content: (@Composable () -> Unit)? = null) {
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .keyFace(color)
                .pressable(label = label, scaleDown = 0.95f, haptic = true, onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            if (content != null) content() else Txt(label, Ren.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = textColor, maxLines = 1)
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .background(c.keyboard)
            .onGloballyPositioned { root = it }
            .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 8.dp + bottom),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Col(Modifier.width(54.dp), gap) {
                fnKey("ー", onTap = { input.insert("ー") }) { Txt("ー", Ren.type.body.jp(20.sp)) }
                fnKey("Move left", onTap = input::left) { RenIcon(RenIcons.Back, c.ink, size = 20.dp) }
                fnKey("Move right", onTap = input::right) { RenIcon(RenIcons.Chevron, c.ink, size = 20.dp) }
                fnKey(if (katakana) "カナ" else "かな", onTap = { katakana = !katakana })
            }
            Col(Modifier.weight(1f), gap) { kanaKey("あ"); kanaKey("た"); kanaKey("ま"); fnKey("小゛゜", onTap = input::cycleModifier, color = c.key) }
            Col(Modifier.weight(1f), gap) { kanaKey("か"); kanaKey("な"); kanaKey("や"); kanaKey("わ") }
            Col(Modifier.weight(1f), gap) { kanaKey("さ"); kanaKey("は"); kanaKey("ら"); kanaKey("、", "、。?!") }
            Col(Modifier.width(54.dp), gap) {
                fnKey("Delete", onTap = input::backspace) { RenIcon(RenIcons.Backspace, c.ink, size = 22.dp) }
                fnKey("空白", onTap = { input.insert(" ") })
                fnKey(submitLabel, height = 48.dp * 2 + gap, color = c.akane, textColor = c.onAccent, onTap = onSubmit)
            }
        }

        // The flower: five petals around the touched key, the chosen one in Akane.
        val flick = active
        val rect: Rect? = flick?.let { f ->
            val r = root
            val k = keyCoords[f.key]
            if (r != null && k != null && r.isAttached && k.isAttached) r.localBoundingBoxOf(k) else null
        }
        if (flick != null && rect != null) {
            val set = FLICK.getValue(flick.key)
            val g = with(density) { gap.toPx() }
            val at = listOf(
                Offset(rect.left, rect.top),
                Offset(rect.left - rect.width - g, rect.top),
                Offset(rect.left, rect.top - rect.height - g),
                Offset(rect.left + rect.width + g, rect.top),
                Offset(rect.left, rect.top + rect.height + g),
            )
            val w = with(density) { rect.width.toDp() }
            val h = with(density) { rect.height.toDp() }
            set.forEachIndexed { i, ch ->
                val on = i == flick.dir
                Box(
                    Modifier
                        .offset { IntOffset(at[i].x.roundToInt(), at[i].y.roundToInt()) }
                        .size(w, h)
                        .shadow(8.dp, RoundedCornerShape(9.dp))
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (on) c.akane else c.key.copy(alpha = 0.94f))
                        .border(0.5.dp, c.hairline, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(out(ch), Ren.type.body.jp(24.sp, FontWeight.Medium), color = if (on) c.onAccent else c.ink)
                }
            }
        }
    }
}

@Composable
private fun Col(modifier: Modifier, gap: Dp, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap), content = content)

private fun Modifier.keyFace(color: Color, pressed: Boolean = false) = this
    .shadow(0.6.dp, RoundedCornerShape(7.dp), clip = false)
    .clip(RoundedCornerShape(7.dp))
    .background(if (pressed) color.copy(alpha = 0.55f) else color)

/** Answer field above the keyboard: typed kana, a blinking Akane caret and the round submit arrow. */
@Composable
fun AnswerField(
    input: KanaInputState,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    verdict: Boolean? = null,
    shakeKey: Int = 0,
    placeholder: String = "",
) {
    val c = Ren.colors
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) shake.animateTo(0f, keyframes { durationMillis = 350; -12f at 50; 10f at 120; -6f at 190; 3f at 260 })
    }
    val blink = rememberInfiniteTransition(label = "caret")
    val caret by blink.animateFloat(1f, 0f, infiniteRepeatable(keyframes { durationMillis = 1100; 1f at 0; 1f at 549; 0f at 550; 0f at 1100 }, RepeatMode.Restart), label = "blink")
    val ring = when (verdict) {
        true -> c.bamboo
        false -> c.amber
        null -> Color.Transparent
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .graphicsLayer { translationX = shake.value }
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(2.dp, ring, RoundedCornerShape(16.dp))
            .padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            val text = input.text
            if (text.isEmpty()) {
                Box(Modifier.width(2.dp).height(28.dp).alpha(caret).background(c.akane))
                Txt(placeholder, Ren.type.body.jp(20.sp), color = c.ink3, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
            } else {
                Txt(text.substring(0, input.cursor), Ren.type.body.jp(26.sp, FontWeight.Medium), maxLines = 1)
                Box(Modifier.padding(horizontal = 1.dp).width(2.dp).height(28.dp).alpha(caret).background(c.akane))
                Txt(text.substring(input.cursor), Ren.type.body.jp(26.sp, FontWeight.Medium), maxLines = 1)
            }
        }
        Box(
            Modifier.size(42.dp).pressable(label = "Check answer", scaleDown = 0.92f, onClick = onSubmit)
                .clip(CircleShape).background(c.akane),
            contentAlignment = Alignment.Center,
        ) { RenIcon(RenIcons.Up, c.onAccent, size = 22.dp) }
    }
}
