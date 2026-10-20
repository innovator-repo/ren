package app.ren.core.designsystem.components

import app.ren.designsystem.pressable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ren.core.designsystem.icons.RenIcon
import app.ren.core.designsystem.icons.RenIcons
import app.ren.core.designsystem.theme.Ren
import app.ren.core.designsystem.theme.jp
import app.ren.core.designsystem.theme.RenMotion
import app.ren.core.designsystem.theme.RenShapes
import app.ren.designsystem.LocalHaptics

// ------------------------------------------------------------------ text

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Ren.colors.ink,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    weight: FontWeight? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align ?: TextAlign.Unspecified, fontWeight = weight ?: style.fontWeight),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Txt(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Ren.colors.ink,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align ?: TextAlign.Unspecified),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// ------------------------------------------------------------------ touch

/** List-row highlight: a soft wash while the finger is down. */
@Composable
fun Modifier.rowHighlight(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val wash by animateColorAsState(if (pressed) Ren.colors.fill else Color.Transparent, tween(if (pressed) 0 else 220), label = "row")
    return this
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .drawBehind { drawRect(wash) }
}

// ------------------------------------------------------------------ buttons

/** The main action of a screen: one per screen, full width, Akane. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ren.colors.akane,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = Ren.colors
    val haptics = LocalHaptics.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .pressable(enabled = enabled) { haptics.click(); onClick() }
            .clip(RoundedCornerShape(RenShapes.button))
            .background(if (enabled) color else c.fill)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(text, Ren.type.button, color = if (enabled) c.onAccent else c.ink2, maxLines = 1)
        trailing?.invoke(this)
    }
}

@Composable
fun SoftButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ren.colors.ink,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(RenShapes.button))
            .background(Ren.colors.fill),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, Ren.type.button, color = if (enabled) color else Ren.colors.ink2, maxLines = 1)
    }
}

/** Compact in-card button, as in Sigil (`.btn`): primary in Akane, otherwise a soft fill. */
@Composable
fun SmallButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Ren.colors
    Box(
        modifier
            .pressable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(9.dp))
            .background(if (primary) c.akane.copy(alpha = if (enabled) 1f else 0.4f) else c.fill)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, Ren.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (primary) c.onAccent else c.ink, maxLines = 1)
    }
}

@Composable
fun TextAction(text: String, modifier: Modifier = Modifier, color: Color = Ren.colors.akane, bold: Boolean = true, onClick: () -> Unit) {
    Txt(
        text, if (bold) Ren.type.headline else Ren.type.callout, color = color, maxLines = 1,
        modifier = modifier.pressable(onClick = onClick).padding(vertical = 8.dp, horizontal = 6.dp),
    )
}

/** Toolbar icon: a quiet line icon with a full-size touch target. */
@Composable
fun ToolButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = Ren.colors.ink2, onClick: () -> Unit) {
    Box(
        modifier.size(44.dp).pressable(label = label, scaleDown = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { RenIcon(icon, tint, size = 21.dp) }
}

/** Small round icon button (lesson close, word speaker). */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    background: Color = Ren.colors.fill,
    tint: Color = Ren.colors.ink2,
    size: Dp = 30.dp,
    iconSize: Dp = 15.dp,
    onClick: () -> Unit,
) {
    // A 44dp touch target around a small visual.
    Box(modifier.size(maxOf(size, 44.dp)).pressable(label = label, scaleDown = 0.9f, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            RenIcon(icon, tint, size = iconSize)
        }
    }
}

/** Blue speaker bubble used on word cards and sheets. */
@Composable
fun SpeakerButton(modifier: Modifier = Modifier, size: Dp = 36.dp, playing: Boolean = false, onClick: () -> Unit) {
    val c = Ren.colors
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(playing) {
        if (playing) {
            pulse.animateTo(1.1f, tween(160)); pulse.animateTo(1f, RenMotion.spring())
        }
    }
    Box(
        modifier.size(maxOf(size, 44.dp)).pressable(label = "Play audio", scaleDown = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }.clip(CircleShape).background(c.soft(c.radical)),
            contentAlignment = Alignment.Center,
        ) { RenIcon(RenIcons.Speaker, c.radical, size = size * 0.46f) }
    }
}

// ------------------------------------------------------------------ surfaces

/**
 * A soft inset group (Sigil's `.group`). [elevated] lifts it onto a white card with a quiet
 * shadow: use it for the one thing on a screen that matters most.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    elevated: Boolean = false,
    color: Color = if (elevated) Ren.colors.surface else Ren.colors.group,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ren.colors
    val radius = if (elevated) RenShapes.card + 2.dp else RenShapes.card
    Column(
        modifier
            .fillMaxWidth()
            .then(if (elevated) Modifier.shadow(14.dp, RoundedCornerShape(radius), ambientColor = c.shadow, spotColor = c.shadow) else Modifier)
            .clip(RoundedCornerShape(radius))
            .background(color)
            .padding(padding),
        content = content,
    )
}

/** Group heading in lists: sentence case, small and quiet. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Txt(text, Ren.type.sectionHeader, color = Ren.colors.ink2, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Uppercase label above a card's content ("UP NEXT"). */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Ren.colors.ink2) {
    Txt(text.uppercase(), Ren.type.kicker, color = color, modifier = modifier)
}

@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    Txt(text, Ren.type.caption, color = Ren.colors.ink2, modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp))
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Ren.colors.hairline) {
    val px = with(LocalDensity.current) { 0.5.dp.toPx().coerceAtLeast(1f) }
    Canvas(modifier.fillMaxWidth().height(0.5.dp)) {
        drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = px)
    }
}

@Composable
fun Chevron(modifier: Modifier = Modifier) = RenIcon(RenIcons.Chevron, Ren.colors.ink3, modifier, size = 13.dp)

/** A rounded square holding one glyph, like Sigil's row tiles. */
@Composable
fun GlyphTile(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    background: Color = Ren.colors.fill,
    color: Color = Ren.colors.ink,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.27f)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Txt(glyph, Ren.type.body.jp((size.value * 0.52f).sp).copy(lineHeight = (size.value * 0.6f).sp), color = color, maxLines = 1)
    }
}

// ------------------------------------------------------------------ pills and tags

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Ren.colors.ink,
    background: Color = Ren.colors.group,
    color: Color = Ren.colors.ink,
    bold: Boolean = true,
) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RenIcon(icon, iconTint, size = 13.dp)
            Spacer(Modifier.width(5.dp))
        }
        Txt(text, Ren.type.caption.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium), color = color, maxLines = 1)
    }
}

/** Small status tag like "1 new word" or "Mature, 34 days": tinted fill, coloured text. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Txt(
        text, Ren.type.caption2.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold), color = color, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Ren.colors.soft(color)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** A radical or component chip: glyph plus meaning, blue when it is a known radical. */
@Composable
fun PartChip(glyph: String, meaning: String, modifier: Modifier = Modifier, color: Color = Ren.colors.radical, onClick: (() -> Unit)? = null) {
    val c = Ren.colors
    Row(
        modifier
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(c.soft(color))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(glyph, Ren.type.body.copy(fontSize = 15.sp), color = color)
        Spacer(Modifier.width(5.dp))
        Txt(meaning, Ren.type.caption.copy(fontWeight = FontWeight.Medium), color = color, maxLines = 1)
    }
}

// ------------------------------------------------------------------ controls

@Composable
fun SegmentedControl(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val c = Ren.colors
    val haptics = LocalHaptics.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(c.fill)
            .padding(2.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, RenMotion.snappy(), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .fillMaxHeight()
                .shadow(1.5.dp, RoundedCornerShape(7.dp), ambientColor = c.shadow, spotColor = c.shadow)
                .clip(RoundedCornerShape(7.dp))
                .background(if (c.isDark) Color(0xFF636366) else Color.White),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) {
                            if (i != selected) haptics.tick()
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(label, Ren.type.footnote.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun CupertinoSwitch(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = Ren.colors
    val haptics = LocalHaptics.current
    val track by animateColorAsState(if (checked) c.bamboo else c.fill, tween(180), label = "track")
    val x by animateDpAsState(if (checked) 20.dp else 2.dp, RenMotion.spring(), label = "thumb")
    Box(
        modifier
            .size(46.dp, 28.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(50))
            .background(track)
            .clickable(remember { MutableInteractionSource() }, null, enabled = enabled, role = Role.Switch) {
                haptics.tick(); onChange(!checked)
            },
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(24.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, minusEnabled: Boolean = true, plusEnabled: Boolean = true) {
    val c = Ren.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Txt(value, Ren.type.callout, color = c.ink2, modifier = Modifier.padding(end = 10.dp))
        Row(Modifier.clip(RoundedCornerShape(8.dp)).background(c.fill), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp, 30.dp).pressable(enabled = minusEnabled, label = "Decrease", haptic = true, onClick = onMinus), Alignment.Center) {
                RenIcon(RenIcons.Minus, if (minusEnabled) c.ink else c.ink3, size = 14.dp)
            }
            Box(Modifier.size(0.5.dp, 16.dp).background(c.hairline))
            Box(Modifier.size(40.dp, 30.dp).pressable(enabled = plusEnabled, label = "Increase", haptic = true, onClick = onPlus), Alignment.Center) {
                RenIcon(RenIcons.Plus, if (plusEnabled) c.ink else c.ink3, size = 14.dp)
            }
        }
    }
}

// ------------------------------------------------------------------ progress

@Composable
fun ProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 4.dp, track: Color = Ren.colors.fill) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), RenMotion.gentle(), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

/** The short dashes at the top of a lesson. */
@Composable
fun StepDashes(count: Int, current: Int, modifier: Modifier = Modifier) {
    val c = Ren.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            val color by animateColorAsState(if (i <= current) c.akane else c.fill, tween(260), label = "dash")
            Box(Modifier.size(width = 22.dp, height = 3.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

data class RingSpec(val progress: Float, val color: Color)

/** Activity-style concentric rings (spec §5 Today). Outer ring first. */
@Composable
fun ActivityRings(rings: List<RingSpec>, modifier: Modifier = Modifier, stroke: Dp = 9.dp, gap: Dp = 2.dp) {
    val animated = rings.map { spec ->
        animateFloatAsState(spec.progress.coerceIn(0f, 1.5f), tween(900), label = "ring").value
    }
    Canvas(modifier) {
        val sw = stroke.toPx()
        val g = gap.toPx()
        rings.forEachIndexed { i, spec ->
            val inset = sw / 2 + i * (sw + g)
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(spec.color.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            val sweep = 360f * animated[i]
            if (sweep > 0.5f) drawArc(spec.color, -90f, sweep, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun ColumnScope.Fill() = Spacer(Modifier.weight(1f))

@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

/** iOS slider: thin track filled in Akane up to a white thumb. Drag or tap anywhere on the track. */
@Composable
fun CupertinoSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, label: String = "Slider") {
    val c = Ren.colors
    val thumb = 24.dp
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                val pad = thumb.toPx() / 2
                fun at(x: Float) = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onChange(at(down.position.x))
                    horizontalDrag(down.id) { change ->
                        change.consume()
                        onChange(at(change.position.x))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = maxWidth - thumb
        Box(Modifier.padding(horizontal = thumb / 2).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(c.fill))
        Box(Modifier.padding(start = thumb / 2).width(travel * value).height(3.dp).clip(RoundedCornerShape(50)).background(c.akane))
        Box(
            Modifier
                .offset(x = travel * value)
                .size(thumb)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
