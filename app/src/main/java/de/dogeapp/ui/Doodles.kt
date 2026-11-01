package de.dogeapp.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dogeapp.R
import de.dogeapp.ui.theme.Cream
import de.dogeapp.ui.theme.Ink
import de.dogeapp.ui.theme.Mustard
import de.dogeapp.ui.theme.Orange
import de.dogeapp.ui.theme.ScriptStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// =============================================================================================
// Collage / poster decorations: all Canvas-drawn (no bitmaps), paths cached per size.
// =============================================================================================

/** Ink colour of the current theme (black on paper, cream at night). */
@Composable
fun inkColor(): Color = MaterialTheme.colorScheme.onBackground

// ---------------------------------------------------------------------------------------------
// Doodles
// ---------------------------------------------------------------------------------------------

/** Star path with [points] tips around [c]. */
internal fun starPath(c: Offset, outer: Float, inner: Float, points: Int): Path {
    val p = Path()
    val step = PI / points
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * step
        val x = c.x + (cos(a) * r).toFloat()
        val y = c.y + (sin(a) * r).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

/** ✶ Filled star doodle (4 points = sparkle, 8 points = asterisk-like burst). */
@Composable
fun DoodleStar(
    modifier: Modifier = Modifier,
    color: Color = inkColor(),
    points: Int = 4,
    outline: Boolean = false,
) {
    Canvas(modifier.size(22.dp)) {
        val r = size.minDimension / 2
        val inner = r * (if (points <= 4) 0.26f else 0.45f)
        val path = starPath(center, r, inner, points)
        if (outline) {
            drawPath(path, color, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
        } else {
            drawPath(path, color)
        }
    }
}

/** Three short "attention" strokes fanning out (like the lines next to the cat's head). */
@Composable
fun DoodleBurst(modifier: Modifier = Modifier, color: Color = inkColor()) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val sw = 2.2.dp.toPx()
        drawLine(color, Offset(w * 0.10f, h * 0.55f), Offset(w * 0.32f, h * 0.70f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, h * 0.10f), Offset(w * 0.50f, h * 0.45f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.90f, h * 0.22f), Offset(w * 0.68f, h * 0.50f), sw, StrokeCap.Round)
    }
}

/** Wavy squiggle line. */
@Composable
fun DoodleSquiggle(modifier: Modifier = Modifier, color: Color = inkColor(), waves: Int = 3) {
    Canvas(modifier.size(width = 56.dp, height = 16.dp)) {
        val p = Path()
        val w = size.width
        val h = size.height
        val seg = w / (waves * 2)
        p.moveTo(0f, h / 2)
        for (i in 0 until waves * 2) {
            val x0 = i * seg
            val dir = if (i % 2 == 0) -1f else 1f
            p.cubicTo(x0 + seg * 0.3f, h / 2 + dir * h / 2, x0 + seg * 0.7f, h / 2 + dir * h / 2, x0 + seg, h / 2)
        }
        drawPath(p, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Hand-drawn curved arrow pointing to the top right (rotate/mirror it with modifiers). */
@Composable
fun DoodleArrow(modifier: Modifier = Modifier, color: Color = inkColor()) {
    Canvas(modifier.size(48.dp)) {
        val w = size.width
        val h = size.height
        val sw = 2.2.dp.toPx()
        val body = Path().apply {
            moveTo(w * 0.08f, h * 0.92f)
            cubicTo(w * 0.10f, h * 0.45f, w * 0.45f, h * 0.20f, w * 0.88f, h * 0.18f)
        }
        drawPath(body, color, style = Stroke(sw, cap = StrokeCap.Round))
        val head = Path().apply {
            moveTo(w * 0.66f, h * 0.04f)
            lineTo(w * 0.90f, h * 0.18f)
            lineTo(w * 0.70f, h * 0.36f)
        }
        drawPath(head, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A handful of confetti dots and dashes in orange, mustard and ink (deterministic layout). */
@Composable
fun ConfettiDots(modifier: Modifier = Modifier) {
    val ink = inkColor()
    Canvas(modifier.size(80.dp)) {
        val w = size.width
        val h = size.height
        val colors = listOf(Orange, Mustard, ink)
        // (x, y, kind, angle) in fractions of the canvas.
        val bits = listOf(
            floatArrayOf(0.10f, 0.20f, 0f, 0f), floatArrayOf(0.35f, 0.08f, 1f, 30f),
            floatArrayOf(0.62f, 0.28f, 0f, 0f), floatArrayOf(0.88f, 0.12f, 1f, -40f),
            floatArrayOf(0.20f, 0.58f, 1f, 70f), floatArrayOf(0.50f, 0.66f, 0f, 0f),
            floatArrayOf(0.80f, 0.55f, 1f, 15f), floatArrayOf(0.30f, 0.92f, 0f, 0f),
            floatArrayOf(0.72f, 0.90f, 1f, -65f),
        )
        bits.forEachIndexed { i, b ->
            val c = colors[i % colors.size]
            val p = Offset(b[0] * w, b[1] * h)
            if (b[2] == 0f) {
                drawCircle(c, 2.6.dp.toPx(), p)
            } else {
                val a = Math.toRadians(b[3].toDouble())
                val d = 4.dp.toPx()
                val dx = (cos(a) * d).toFloat()
                val dy = (sin(a) * d).toFloat()
                drawLine(c, Offset(p.x - dx, p.y - dy), Offset(p.x + dx, p.y + dy), 2.4.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

/** Hand-drawn underline swoosh under the content (used for the script words). */
fun Modifier.swoosh(color: Color, strokeWidth: Dp = 3.dp): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val sw = strokeWidth.toPx()
    val main = Path().apply {
        moveTo(w * 0.02f, h * 0.98f)
        cubicTo(w * 0.30f, h * 0.88f, w * 0.70f, h * 0.90f, w * 0.98f, h * 0.84f)
    }
    drawPath(main, color, style = Stroke(sw, cap = StrokeCap.Round))
    val flick = Path().apply {
        moveTo(w * 0.20f, h * 1.06f)
        cubicTo(w * 0.40f, h * 1.00f, w * 0.55f, h * 1.01f, w * 0.68f, h * 0.99f)
    }
    drawPath(flick, color, style = Stroke(sw * 0.7f, cap = StrokeCap.Round))
}

/** Dashed rounded outline (e.g. the "Für Eltern / Experten" area). */
fun Modifier.dashedBorder(color: Color, width: Dp = 1.5.dp, radius: Dp = 16.dp): Modifier = drawBehind {
    val sw = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(sw / 2, sw / 2),
        size = Size(size.width - sw, size.height - sw),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()), 0f)),
    )
}

// ---------------------------------------------------------------------------------------------
// Paper background with halftone corners and torn-paper scraps
// ---------------------------------------------------------------------------------------------

/** Small deterministic pseudo random generator so the torn edges look the same every frame. */
private class Lcg(seed: Int) {
    private var s = seed.toLong()
    fun next(): Float {
        s = (s * 1103515245L + 12345L) and 0x7fffffffL
        return (s % 1000L) / 1000f
    }
}

/** A rectangle with a ragged (torn) outline, rotated by [angle] degrees around its centre. */
private fun tornRect(rect: Rect, angle: Float, jag: Float, step: Float, seed: Int): Path {
    val rnd = Lcg(seed)
    val pts = ArrayList<Offset>()
    fun edge(from: Offset, to: Offset, nx: Float, ny: Float) {
        val len = hypot(to.x - from.x, to.y - from.y)
        val n = (len / step).toInt().coerceAtLeast(2)
        for (i in 0 until n) {
            val t = i / n.toFloat()
            val j = (rnd.next() - 0.5f) * 2f * jag
            pts += Offset(from.x + (to.x - from.x) * t + nx * j, from.y + (to.y - from.y) * t + ny * j)
        }
    }
    edge(rect.topLeft, rect.topRight, 0f, 1f)
    edge(rect.topRight, rect.bottomRight, 1f, 0f)
    edge(rect.bottomRight, rect.bottomLeft, 0f, 1f)
    edge(rect.bottomLeft, rect.topLeft, 1f, 0f)
    val c = rect.center
    val a = Math.toRadians(angle.toDouble())
    val ca = cos(a).toFloat()
    val sa = sin(a).toFloat()
    val path = Path()
    pts.forEachIndexed { i, p ->
        val dx = p.x - c.x
        val dy = p.y - c.y
        val x = c.x + dx * ca - dy * sa
        val y = c.y + dx * sa + dy * ca
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Halftone dots that get smaller away from [corner]. All dots go into one path (one draw call). */
private fun halftone(corner: Offset, radius: Float, spacing: Float, maxDot: Float): Path {
    val path = Path()
    val n = (radius / spacing).toInt()
    for (ix in -n..n) {
        for (iy in -n..n) {
            val x = corner.x + ix * spacing + (if (iy % 2 == 0) 0f else spacing / 2)
            val y = corner.y + iy * spacing
            val d = hypot(x - corner.x, y - corner.y)
            if (d > radius) continue
            val r = maxDot * (1f - d / radius)
            if (r < 0.6f) continue
            path.addOval(Rect(Offset(x, y), r))
        }
    }
    return path
}

/**
 * The paper every screen sits on: background colour, halftone dots in two corners and two torn
 * scraps of coloured paper at the top edge. Everything is built once per size (drawWithCache).
 */
@Composable
fun PaperBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    val bg = MaterialTheme.colorScheme.background
    val dotColor = if (dark) Cream.copy(alpha = 0.07f) else Ink.copy(alpha = 0.08f)
    val scrapYellow = if (dark) Mustard.copy(alpha = 0.22f) else Mustard.copy(alpha = 0.85f)
    val scrapOrange = if (dark) Orange.copy(alpha = 0.28f) else Orange.copy(alpha = 0.9f)
    Box(
        modifier
            .background(bg)
            .drawWithCache {
                val dots1 = halftone(Offset(size.width, 0f), min(size.width, size.height) * 0.42f, 9.dp.toPx(), 2.4.dp.toPx())
                val dots2 = halftone(Offset(0f, size.height), min(size.width, size.height) * 0.50f, 9.dp.toPx(), 2.4.dp.toPx())
                val yellow = tornRect(
                    Rect(Offset(size.width - 70.dp.toPx(), -26.dp.toPx()), Size(120.dp.toPx(), 64.dp.toPx())),
                    angle = -14f, jag = 3.dp.toPx(), step = 7.dp.toPx(), seed = 7,
                )
                val orange = tornRect(
                    Rect(Offset(-40.dp.toPx(), -18.dp.toPx()), Size(86.dp.toPx(), 42.dp.toPx())),
                    angle = 10f, jag = 3.dp.toPx(), step = 7.dp.toPx(), seed = 23,
                )
                onDrawBehind {
                    drawPath(dots1, dotColor)
                    drawPath(dots2, dotColor)
                    drawPath(yellow, scrapYellow)
                    drawPath(orange, scrapOrange)
                }
            },
    ) { content() }
}

// ---------------------------------------------------------------------------------------------
// Typography helpers
// ---------------------------------------------------------------------------------------------

/**
 * Poster headline: HUGE condensed uppercase line ([big]) plus an orange hand-written [script]
 * line with an underline swoosh, and a little star doodle.
 */
@Composable
fun PosterHeadline(
    big: String,
    script: String?,
    modifier: Modifier = Modifier,
    bigSize: TextUnit = 52.sp,
    scriptSize: TextUnit = 34.sp,
    centered: Boolean = false,
    star: Boolean = true,
) {
    val ink = inkColor()
    Column(
        modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                big.uppercase(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = bigSize, lineHeight = bigSize * 1.02f),
                color = ink,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (star) {
                DoodleStar(Modifier.padding(start = 6.dp, top = 2.dp).size(20.dp), color = ink)
            }
        }
        if (script != null) {
            Text(
                script,
                style = ScriptStyle.copy(fontSize = scriptSize, lineHeight = scriptSize * 1.1f),
                color = Orange,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .padding(start = if (centered) 0.dp else 4.dp, top = 0.dp, bottom = 8.dp)
                    .rotate(-3f)
                    .swoosh(ink, 2.5.dp),
            )
        }
    }
}

/** Small tilted hand-written note ("Los geht's!", "hilfe."). */
@Composable
fun ScriptNote(
    text: String,
    modifier: Modifier = Modifier,
    rotation: Float = -8f,
    color: Color = inkColor(),
    fontSize: TextUnit = 24.sp,
) {
    Text(
        text,
        style = ScriptStyle.copy(fontSize = fontSize, lineHeight = fontSize * 1.05f),
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier.rotate(rotation),
    )
}

// ---------------------------------------------------------------------------------------------
// Stickers
// ---------------------------------------------------------------------------------------------

/** Scalloped circle path (sticker edge). */
private fun scallopPath(c: Offset, r: Float, bumps: Int): Path {
    val p = Path()
    val steps = bumps * 8
    for (i in 0..steps) {
        val a = 2 * PI * i / steps
        val rr = r * (0.94f + 0.06f * cos(bumps * a).toFloat())
        val x = c.x + (cos(a) * rr).toFloat()
        val y = c.y + (sin(a) * rr).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

/**
 * Round sticker base: scalloped [fill] disc with a dashed inner ring, rotated by [rotation].
 * Clickable when [onClick] is set (semantic [role], e.g. RadioButton for the speed chips).
 */
@Composable
fun StickerBase(
    modifier: Modifier = Modifier,
    diameter: Dp = 104.dp,
    rotation: Float = -12f,
    fill: Color = Mustard,
    ring: Color = Ink,
    outline: Color? = null,
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickMod = if (onClick != null) {
        Modifier.clip(CircleShape).clickable(role = role, onClick = onClick)
    } else Modifier
    Box(
        modifier.size(diameter).rotate(rotation).then(clickMod),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2
            val scallop = scallopPath(center, r, 16)
            drawPath(scallop, fill)
            if (outline != null) drawPath(scallop, outline, style = Stroke(1.5.dp.toPx()))
            drawCircle(
                ring,
                r * 0.80f,
                center,
                style = Stroke(1.3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)),
            )
        }
        Column(
            Modifier.padding(diameter * 0.17f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/**
 * Mustard "Certified Genius* *kind of" sticker: bold tilted [text] and an optional hand-written
 * [footnote].
 */
@Composable
fun Sticker(
    text: String,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    diameter: Dp = 104.dp,
    rotation: Float = -12f,
    fill: Color = Mustard,
    onClick: (() -> Unit)? = null,
) {
    StickerBase(modifier, diameter = diameter, rotation = rotation, fill = fill, onClick = onClick) {
        Text(
            text,
            color = Ink,
            fontWeight = FontWeight.Bold,
            fontSize = (diameter.value * 0.125f).sp,
            lineHeight = (diameter.value * 0.14f).sp,
            textAlign = TextAlign.Center,
        )
        if (footnote != null) {
            Text(
                footnote,
                style = ScriptStyle,
                color = Ink,
                fontSize = (diameter.value * 0.13f).sp,
                lineHeight = (diameter.value * 0.14f).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cards, buttons, circles, bars
// ---------------------------------------------------------------------------------------------

/**
 * Cream paper card with a thin outline and 16 dp corners. [dashed] draws a dashed outline on a
 * transparent background (used to set the expert area apart). [container] overrides the fill.
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    dashed: Boolean = false,
    container: Color? = null,
    padding: Dp = 16.dp,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val line = MaterialTheme.colorScheme.outline
    val fill = container ?: if (dashed) Color.Transparent else MaterialTheme.colorScheme.surface
    var m = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(fill)
    m = if (dashed) m.dashedBorder(line, 1.8.dp, 16.dp) else m.border(1.5.dp, line.copy(alpha = 0.7f), shape)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    // Untinted text inside the card follows the theme (ink on paper, cream at night).
    val contentColor = if (dashed) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(m.padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** Emoji in a soft pastel circle (left part of the "subject rows"). */
@Composable
fun IconCircle(emoji: String, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}

/** Orange progress bar like in the reference cards. [fraction] null = indeterminate. */
@Composable
fun OrangeBar(fraction: Float?, modifier: Modifier = Modifier) {
    val track = Orange.copy(alpha = 0.18f)
    if (fraction == null) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
            color = Orange,
            trackColor = track,
        )
    } else {
        Box(
            modifier
                .fillMaxWidth()
                .heightIn(min = 6.dp)
                .clip(RoundedCornerShape(50))
                .background(track)
                .drawBehind {
                    drawRoundRect(
                        Orange,
                        size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                },
        )
    }
}

enum class PillStyle { Orange, Ink, Outline }

/**
 * Big pill button (≥ 60 dp high): orange with white bold text and a trailing 🐾 by default,
 * "→" shows an arrow icon. Bounces a little when pressed.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: String? = "🐾",
    style: PillStyle = PillStyle.Orange,
    minHeight: Dp = 60.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "pill")
    val ink = inkColor()
    val (bg, fg) = when (style) {
        PillStyle.Orange -> MaterialTheme.colorScheme.primary to Color.White
        PillStyle.Ink -> ink to MaterialTheme.colorScheme.background
        PillStyle.Outline -> Color.Transparent to ink
    }
    val shape = RoundedCornerShape(50)
    var m = modifier
        .scale(scale)
        .alpha(if (enabled) 1f else 0.45f)
        .heightIn(min = minHeight)
        .clip(shape)
        .background(bg)
    if (style == PillStyle.Outline) m = m.border(2.dp, ink, shape)
    Row(
        m.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        ).padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 19.sp, textAlign = TextAlign.Center)
        when (trailing) {
            null -> Unit
            "→" -> {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = fg)
            }
            else -> {
                Spacer(Modifier.width(10.dp))
                Text(trailing, fontSize = 20.sp)
            }
        }
    }
}

/** Small text button in ink (orange small text would be too faint on cream). */
@Composable
fun InkTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = inkColor()),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
    }
}

// ---------------------------------------------------------------------------------------------
// Mascot
// ---------------------------------------------------------------------------------------------

/** The robot dog mascot (own vector illustration), gently rocking on its feet. */
@Composable
fun Mascot(modifier: Modifier = Modifier, size: Dp = 160.dp, animate: Boolean = true) {
    val angle = if (animate) {
        val t = rememberInfiniteTransition(label = "mascot")
        val a by t.animateFloat(
            initialValue = -3f, targetValue = 3f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
            label = "mascotAngle",
        )
        a
    } else 0f
    Image(
        painter = painterResource(R.drawable.mascot_dog),
        contentDescription = null,
        modifier = modifier
            .size(width = size, height = size * 1.1f)
            .graphicsLayer {
                rotationZ = angle
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
    )
}

/** Mascot with a small hand-written note floating at its upper left. */
@Composable
fun MascotWithNote(note: String?, modifier: Modifier = Modifier, size: Dp = 160.dp) {
    Box(modifier) {
        Mascot(Modifier.padding(top = if (note != null) 18.dp else 0.dp), size = size)
        if (note != null) {
            ScriptNote(note, Modifier.align(Alignment.TopStart).offset(x = (-8).dp), rotation = -10f, fontSize = 22.sp)
        }
    }
}
