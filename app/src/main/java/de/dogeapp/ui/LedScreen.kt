package de.dogeapp.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import de.dogeapp.ble.ConnState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import de.dogeapp.ui.theme.Ink
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dogeapp.data.LedTarget
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** The 12 colors of the official app's palette (LedColor enum), as quick buttons. */
private val PALETTE = listOf(
    "Magenta" to 0xFF00FF, "Pink" to 0xFF1493, "Rot" to 0xFF0000, "Orange" to 0xFF6000,
    "Koralle" to 0xFF5050, "Gelb" to 0xFFD000, "Cyan" to 0x00FFFF, "Meeresgrün" to 0x00FF80,
    "Grün" to 0x00FF00, "Blau" to 0x0000FF, "Lila" to 0x8000FF, "Hellrosa" to 0xFF80C0,
    "Weiß" to 0xFFFFFF, "Aus" to 0x000000,
)

private val GROUPS = listOf(
    "Alle" to LedTarget.entries.toSet(),
    "Augen" to setOf(LedTarget.LEFT_EYE, LedTarget.RIGHT_EYE),
    "Brust" to setOf(LedTarget.CHEST),
    "Schwanz" to setOf(LedTarget.TAIL),
    "Füße" to setOf(LedTarget.LEFT_FOOT, LedTarget.RIGHT_FOOT),
)

private fun rgbColor(rgb: Int) = Color(0xFF000000.toInt() or rgb)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedScreen(vm: DogeViewModel, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    val leds by vm.leds.collectAsStateWithLifecycle()
    val state by vm.ble.state.collectAsStateWithLifecycle()
    val connected = state == ConnState.READY
    var group by rememberSaveable { mutableStateOf(0) }
    val targets = GROUPS[group].second
    val feedback = LocalFeedback.current

    // Wheel state (hue/saturation) + brightness, initialised from the first selected LED.
    val start = remember(group) {
        FloatArray(3).also { AColor.colorToHSV(0xFF000000.toInt() or (leds[targets.first()] ?: 0xFFFFFF), it) }
    }
    var hue by remember(group) { mutableFloatStateOf(start[0]) }
    var sat by remember(group) { mutableFloatStateOf(start[1]) }
    var value by remember(group) { mutableFloatStateOf(if (start[2] == 0f) 1f else start[2]) }

    fun sendColor() {
        val rgb = AColor.HSVToColor(floatArrayOf(hue, sat, value)) and 0xFFFFFF
        vm.setLedColor(targets, rgb)
    }

    BoxWithConstraints(modifier) {
        val wide = maxWidth >= 700.dp
        val hero: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                PosterHeadline(
                    "Licht an!",
                    "in deiner Lieblingsfarbe",
                    modifier = Modifier.weight(1f),
                    bigSize = if (wide) 64.sp else 48.sp,
                    scriptSize = if (wide) 38.sp else 28.sp,
                )
                Box {
                    MascotWithNote("so schön!", size = if (wide) 140.dp else 96.dp)
                    DoodleStar(Modifier.align(Alignment.TopEnd).size(18.dp), points = 8)
                }
            }
        }
        val notConnected: @Composable () -> Unit = {
            if (!connected) {
                HintBox("Dog-E ist nicht verbunden – die Farben leuchten erst nach dem Verbinden.") {
                    InkTextButton("Verbinden", onClick = onConnect)
                }
            }
        }
        val groups: @Composable () -> Unit = {
            SectionCard(emoji = "💡", title = "Welche Lichter?", colorIndex = 2) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GROUPS.forEachIndexed { i, (label, _) ->
                        GroupPill(label, selected = group == i, onClick = { group = i })
                    }
                }
                DogPreview(leds)
            }
        }
        val wheel: @Composable () -> Unit = {
            SectionCard(emoji = "🎨", title = "Farbe malen", colorIndex = 3) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                        ColorWheel(
                            hue = hue, saturation = sat,
                            onChange = { h, s -> hue = h; sat = s; sendColor() },
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp),
                        )
                        Caption(
                            "tipp mich!",
                            Modifier.align(Alignment.TopStart).rotate(-12f),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        DoodleBurst(Modifier.align(Alignment.TopEnd))
                    }
                    Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🌑", fontSize = 22.sp)
                        Slider(
                            value = value,
                            onValueChange = { value = it; sendColor() },
                            valueRange = 0.05f..1f,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text("☀️", fontSize = 22.sp)
                    }
                }
            }
        }
        val palette: @Composable () -> Unit = {
            SectionCard(emoji = "🖍️", title = "Schnellfarben", colorIndex = 4) {
                EqualGrid(PALETTE, minCellWidth = 68.dp, spacing = 6.dp) { i, (name, rgb) ->
                    val fill = rgbColor(rgb)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(role = Role.Button) { vm.setLedColor(targets, rgb); feedback.show("💡 $name") }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Little round colour sticker, each one tilted a bit differently.
                        StickerBase(
                            diameter = 54.dp,
                            rotation = ((i * 37) % 21 - 10).toFloat(),
                            fill = fill,
                            ring = if (fill.luminance() < 0.4f) Color.White else Ink,
                            outline = MaterialTheme.colorScheme.outline,
                        ) {}
                        Text(
                            name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                PillButton(
                    "Regenbogen",
                    onClick = { vm.rainbow(); feedback.show("🌈 Regenbogen!") },
                    modifier = Modifier.fillMaxWidth(),
                    trailing = "🌈",
                    minHeight = 56.dp,
                )
            }
        }
        if (wide) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                hero()
                Spacer(Modifier.height(12.dp))
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) { notConnected(); wheel(); Spacer(Modifier.height(64.dp)) }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) { groups(); palette(); Spacer(Modifier.height(64.dp)) }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                hero()
                notConnected()
                groups()
                wheel()
                palette()
                Spacer(Modifier.height(64.dp))
            }
        }
    }
}

/** Selectable pill for the LED group (orange when chosen, outlined otherwise). */
@Composable
private fun GroupPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val ink = inkColor()
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else ink, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else ink)
    }
}

/** Hue around the circle, saturation from the center (white) to the edge (full color). */
@Composable
fun ColorWheel(hue: Float, saturation: Float, onChange: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    val latestOnChange by rememberUpdatedState(onChange)
    fun pick(pos: Offset, w: Float, h: Float) {
        val cx = w / 2; val cy = h / 2; val r = min(w, h) / 2
        val dx = pos.x - cx; val dy = pos.y - cy
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f
        latestOnChange(angle, (hypot(dx, dy) / r).coerceIn(0f, 1f))
    }
    val hues = remember { (0..360 step 30).map { Color.hsv(it % 360f, 1f, 1f) } }
    val ring = inkColor()
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> pick(change.position, size.width.toFloat(), size.height.toFloat()) }
            },
    ) {
        val r = min(size.width, size.height) / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Brush.sweepGradient(hues, c), r, c)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), c, r), r, c)
        drawCircle(ring, r, c, style = Stroke(2.dp.toPx()))
        val a = Math.toRadians(hue.toDouble())
        val p = Offset(c.x + (cos(a) * saturation * r).toFloat(), c.y + (sin(a) * saturation * r).toFloat())
        drawCircle(Color.hsv(hue, saturation, 1f), 18.dp.toPx(), p)
        drawCircle(Color.Black, 18.dp.toPx(), p, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, 15.dp.toPx(), p, style = Stroke(2.dp.toPx()))
    }
}

/** Shows the six LEDs as colored dots arranged roughly like on the dog. */
@Composable
private fun DogPreview(leds: Map<LedTarget, Int>) {
    @Composable
    fun dot(t: LedTarget) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(30.dp)
                .background(rgbColor(leds[t] ?: 0), CircleShape)
                .border(2.dp, inkColor(), CircleShape),
        )
        Text(t.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .dashedBorder(MaterialTheme.colorScheme.outline, 1.5.dp, 16.dp)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { dot(LedTarget.LEFT_EYE); dot(LedTarget.RIGHT_EYE) }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { dot(LedTarget.CHEST); dot(LedTarget.TAIL) }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { dot(LedTarget.LEFT_FOOT); dot(LedTarget.RIGHT_FOOT) }
    }
}
