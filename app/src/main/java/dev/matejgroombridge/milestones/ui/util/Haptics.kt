package dev.matejgroombridge.milestones.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Small named wrapper around Compose's [HapticFeedback] so call sites read
 * intent-first ("the user just set a new record") rather than gesture-first
 * ("perform a long-press buzz"). Mapping a couple of named events through one
 * place also makes it easy later to add a "Haptics" Settings toggle.
 *
 * Compose only exposes two coarse haptic primitives, so we map our richer
 * vocabulary onto them sensibly.
 */
class Haptics(private val raw: HapticFeedback) {

    /** Strong tick for a primary "thing happened" action — logging a new record. */
    fun completion() = raw.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Same primitive as completion — kept named so call sites stay readable. */
    fun longPress() = raw.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Light tick for navigation / lower-stakes interactions (page swipe, tab tap). */
    fun light() = raw.performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

@Composable
fun rememberHaptics(): Haptics {
    val raw = LocalHapticFeedback.current
    return remember(raw) { Haptics(raw) }
}
