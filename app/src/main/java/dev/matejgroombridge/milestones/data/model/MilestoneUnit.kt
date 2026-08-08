package dev.matejgroombridge.milestones.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * How a milestone's value is entered and displayed.
 *
 * Every record is stored as a plain [Double] regardless of unit — the unit
 * only decides how that number is rendered and how the user's typed text is
 * turned back into a number. Keeping one numeric type means [MilestoneDirection]
 * comparisons work identically for "42 books", "£7,000" and a 24:30 5km.
 *
 * The three cases cover the goals this app is built for:
 *  - [Numeric] — counts and measurements ("42 books", "21.75 km", "180 kg").
 *  - [Time]    — anything clocked. Stored as **seconds**.
 *  - [Money]   — revenue, savings, sales.
 *
 * Sealed + `@Serializable` so it round-trips through the JSON blob. New cases
 * can be added later: the repository's parser ignores unknown keys and every
 * field has a default, so existing data keeps loading.
 */
@Serializable
sealed interface MilestoneUnit {

    /**
     * A plain number with an optional trailing unit word.
     *
     * Named `Numeric` rather than `Number` so it never gets confused with
     * `kotlin.Number` at a call site.
     *
     * @param suffix   Rendered after the number, e.g. "books", "km", "reps".
     *                 Blank for a bare count.
     * @param decimals How many decimal places to show (0..[MAX_DECIMALS]). A
     *                 distance milestone wants 2; a book count wants 0.
     */
    @Serializable
    @SerialName("number")
    data class Numeric(
        val suffix: String = "",
        val decimals: Int = 0,
    ) : MilestoneUnit {
        init { require(decimals in 0..MAX_DECIMALS) { "decimals must be 0..$MAX_DECIMALS, got $decimals" } }
    }

    /** A duration or time, stored in seconds and rendered as `[h:]mm:ss`. */
    @Serializable
    @SerialName("time")
    data object Time : MilestoneUnit

    /**
     * An amount of money rendered with [symbol] in front.
     *
     * Whole amounts print without decimals ("£7,000") because that's how
     * revenue and savings figures read; fractional amounts get two ("£7,000.50").
     */
    @Serializable
    @SerialName("money")
    data class Money(val symbol: String = "£") : MilestoneUnit

    /**
     * Full display form of [value] — the string shown on cards and in stats,
     * including the suffix or currency symbol.
     */
    fun format(value: Double): String = when (this) {
        is Numeric -> {
            val number = groupedDecimal(value, decimals)
            if (suffix.isBlank()) number else "$number $suffix"
        }
        Time -> formatSeconds(value)
        is Money -> "$symbol${groupedDecimal(value, if (isWhole(value)) 0 else 2)}"
    }

    /**
     * Display form of an *amount* rather than a reading — used for deltas
     * ("2.10 km better", "1:15 faster"). Always positive; the caller supplies
     * the wording that gives it direction.
     */
    fun formatMagnitude(value: Double): String = format(abs(value))

    /**
     * Turns user-typed [raw] into a value, or `null` when it isn't a number.
     *
     * Deliberately lenient: grouping separators, spaces and the unit's own
     * symbol/suffix are all stripped before parsing, so pasting a formatted
     * value straight back in works.
     */
    fun parse(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return when (this) {
            is Numeric -> parseDecimal(trimmed.removeSuffix(suffix))
            Time -> parseSeconds(trimmed)
            is Money -> parseDecimal(trimmed.removePrefix(symbol))
        }
    }

    /** Label for the value field in the log-record dialog. */
    val fieldLabel: String
        get() = when (this) {
            is Numeric -> if (suffix.isBlank()) "Value" else "Value ($suffix)"
            Time -> "Time"
            is Money -> "Amount ($symbol)"
        }

    /** Placeholder showing the expected input shape. */
    val fieldPlaceholder: String
        get() = when (this) {
            is Numeric -> if (decimals == 0) "42" else "21." + "5".repeat(decimals)
            Time -> "24:30"
            is Money -> "7000"
        }

    /** Short description used in the editor's unit summary and overview chip. */
    val label: String
        get() = when (this) {
            is Numeric -> if (suffix.isBlank()) "Number" else "Number · $suffix"
            Time -> "Time"
            is Money -> "Money · $symbol"
        }

    companion object {
        const val MAX_DECIMALS = 3

        val Default: MilestoneUnit = Numeric()

        /** Renders [value] with exactly [decimals] places and locale grouping, e.g. `1,234.50`. */
        private fun groupedDecimal(value: Double, decimals: Int): String =
            String.format(Locale.getDefault(), "%,.${decimals}f", value)

        private fun isWhole(value: Double): Boolean = abs(value - value.roundToLong()) < 0.005

        /**
         * Seconds → `h:mm:ss`, dropping the hours block when it's zero and
         * appending hundredths only when the value actually has a fraction —
         * so a 10.52s sprint reads `0:10.52` while a 24:30 5km stays clean.
         */
        private fun formatSeconds(totalSeconds: Double): String {
            val negative = totalSeconds < 0
            val absolute = abs(totalSeconds)
            val whole = floor(absolute).toLong()
            val fraction = absolute - whole
            val hours = whole / 3600
            val minutes = (whole % 3600) / 60
            val seconds = whole % 60

            val body = if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
            }
            val tail = if (fraction >= 0.005) {
                String.format(Locale.getDefault(), ".%02d", (fraction * 100).roundToLong())
            } else ""
            return (if (negative) "-" else "") + body + tail
        }

        /**
         * Accepts `45`, `24:30`, `1:23:45` and `10.52`, plus the same with a
         * fractional seconds part. Returns seconds.
         */
        private fun parseSeconds(raw: String): Double? {
            val parts = raw.replace(" ", "").split(":")
            if (parts.size > 3 || parts.any { it.isEmpty() }) return null
            val numbers = parts.map { it.toDoubleOrNull() ?: return null }
            // Only the last component may carry a fraction; the rest are whole
            // hour/minute counts, so reject "1.5:30" as the typo it almost
            // certainly is rather than silently inventing a value.
            if (numbers.dropLast(1).any { it != floor(it) }) return null
            return when (numbers.size) {
                1 -> numbers[0]
                2 -> numbers[0] * 60 + numbers[1]
                else -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
            }
        }

        /** Strips grouping separators and stray spaces, then parses. */
        private fun parseDecimal(raw: String): Double? =
            raw.trim().replace(",", "").replace(" ", "").toDoubleOrNull()
    }
}
