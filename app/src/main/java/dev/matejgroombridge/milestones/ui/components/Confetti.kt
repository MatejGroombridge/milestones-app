package dev.matejgroombridge.milestones.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.milestones.ui.theme.MilestoneColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fires a one-shot burst of falling confetti when [trigger] flips from false
 * to true — used when the user beats a personal best. The animation is tuned
 * so particles travel all the way down the screen: they only begin fading
 * once they're near the bottom edge, giving a satisfying "rain through to the
 * floor" feel rather than dissolving in mid-air.
 *
 * Particle count is generous (default 500) for a celebratory shower; the
 * canvas-based renderer makes that very cheap.
 */
@Composable
fun ConfettiOverlay(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    particleCount: Int = 500,
) {
    // Each call to this composable owns one batch of particles. Re-keying
    // the state on a counter that bumps when `trigger` becomes true gives us
    // a fresh batch every time.
    var burstId by remember { mutableStateOf(0) }
    LaunchedEffect(trigger) {
        if (trigger) burstId++
    }

    if (burstId == 0) return

    val particles = remember(burstId) { generateParticles(particleCount) }
    val progress = remember(burstId) { Animatable(0f) }

    LaunchedEffect(burstId) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                // Long enough for the slowest particles to clear the screen.
                durationMillis = 5500,
                easing = androidx.compose.animation.core.LinearEasing,
            ),
        )
        // Tiny grace period before the next burst can render.
        delay(50)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = progress.value
        // Note: we intentionally do *not* short-circuit at t >= 1f. The
        // animation runs to 1.0 and any remaining particles smoothly fade
        // out via [globalFade] below — exiting at exactly t==1 was what
        // produced the abrupt "snap" the user reported.
        if (t > 1f) return@Canvas

        // A whole-canvas fade applied during the final 12% of the timeline
        // so any particles still in flight don't pop off-screen when the
        // animation completes. Combined with the bottom-edge fade below,
        // the result is: particles either reach the floor (and fade as they
        // land) OR fade gracefully in mid-air at the very end — never snap.
        val tailFadeStart = 0.88f
        val globalFade = if (t < tailFadeStart) 1f
        else (1f - (t - tailFadeStart) / (1f - tailFadeStart)).coerceIn(0f, 1f)

        if (globalFade <= 0f) return@Canvas

        particles.forEach { p ->
            // Position: launched from a horizontal band near the top; the
            // initial velocity carries it outward, gravity pulls it down.
            val tx = p.startXFrac * w
            val ty = -20f
            val vx = p.vx * w
            val vy = p.vy * h
            // Stronger gravity gives the confetti enough downward push to
            // reach the bottom of the screen even from slow upward launches.
            val gravity = 2.4f * h

            // Stagger each particle's start so a long, sustained shower
            // forms instead of one big synchronised wave.
            val localT = (t - p.startDelay).coerceAtLeast(0f)

            val x = tx + vx * localT
            val y = ty + vy * localT + 0.5f * gravity * localT * localT

            // Don't draw particles that haven't started yet or have fully
            // exited the bottom of the screen (with a small margin).
            if (localT <= 0f) return@forEach
            if (y > h + 60f) return@forEach

            val rotation = p.spin * localT * 360f

            // Per-particle alpha: stays at 1 for most of the descent, fades
            // out as the particle approaches / passes the bottom edge.
            val fadeStart = h * 0.85f
            val fadeEnd = h + 60f
            val edgeFade = when {
                y < fadeStart -> 1f
                y >= fadeEnd -> 0f
                else -> 1f - ((y - fadeStart) / (fadeEnd - fadeStart))
            }.coerceIn(0f, 1f)

            val alpha = edgeFade * globalFade
            if (alpha <= 0f) return@forEach

            rotate(degrees = rotation, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.width / 2f, y - p.height / 2f),
                    size = androidx.compose.ui.geometry.Size(p.width, p.height),
                )
            }
        }
    }
}

private data class Particle(
    val startXFrac: Float,
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val width: Float,
    val height: Float,
    val color: Color,
    /** Fraction of [0,1] before this particle starts falling — staggers the shower. */
    val startDelay: Float,
)

private fun generateParticles(n: Int): List<Particle> {
    val palette = MilestoneColors.palette.map { it.accent }
    return List(n) {
        // Wider launch spread (almost 180° fan) and stronger initial speed
        // so confetti shoots upward and outward across most of the screen.
        val angle = Random.nextDouble(-PI / 2 - PI / 2.4, -PI / 2 + PI / 2.4)
        val speed = Random.nextDouble(0.9, 1.7).toFloat()
        Particle(
            // Spread the launch points across the full width of the screen
            // for a confetti-cannon feel rather than a tight column.
            startXFrac = Random.nextFloat() * 0.95f + 0.025f,
            vx = (cos(angle).toFloat()) * speed * 1.1f,
            vy = (sin(angle).toFloat()) * speed * 0.9f,
            spin = if (Random.nextBoolean()) Random.nextFloat() * 1.5f + 0.7f
            else -(Random.nextFloat() * 1.5f + 0.7f),
            width = (6 + Random.nextInt(8)).dp.value * 1.8f,
            height = (10 + Random.nextInt(10)).dp.value * 1.8f,
            color = palette.random(),
            // Up to ~25% of the timeline used as a launch stagger so the
            // shower feels continuous rather than a single synced wave.
            startDelay = Random.nextFloat() * 0.25f,
        )
    }
}
