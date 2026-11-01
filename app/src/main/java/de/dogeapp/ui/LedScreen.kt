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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun LedScreen(vm: DogeViewModel, modifier: Modifier = Modifier) {
    val leds by vm.leds.collectAsStateWithLifecycle()
    var group by rememberSaveable { mutableStateOf(0) }
    val targets = GROUPS[group].second

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

    BoxWithConstraints(modifier.padding(16.dp)) {
        val wide = maxWidth >= 700.dp
        val wheel: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorWheel(
                    hue = hue, saturation = sat,
                    onChange = { h, s -> hue = h; sat = s; sendColor() },
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f),
                )
                Text("☀️ Helligkeit", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = value,
                    onValueChange = { value = it; sendColor() },
                    valueRange = 0.05f..1f,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
            }
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("💡", "Welche Lichter?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GROUPS.forEachIndexed { i, (label, _) ->
                        FilterChip(selected = group == i, onClick = { group = i }, label = { Text(label) })
                    }
                }
                DogPreview(leds)
                SectionTitle("🎨", "Schnellfarben")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.forEach { (name, rgb) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .background(rgbColor(rgb), CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable { vm.setLedColor(targets, rgb) },
                            )
                            Text(name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                OutlinedButton(onClick = { vm.rainbow() }) { Text("🌈 Regenbogen") }
            }
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Box(Modifier.weight(1f)) { wheel() }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { controls() }
            }
        } else {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                wheel()
                controls()
            }
        }
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
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(t.label, style = MaterialTheme.typography.labelSmall)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { dot(LedTarget.LEFT_EYE); dot(LedTarget.RIGHT_EYE) }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { dot(LedTarget.CHEST); dot(LedTarget.TAIL) }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { dot(LedTarget.LEFT_FOOT); dot(LedTarget.RIGHT_FOOT) }
    }
}
